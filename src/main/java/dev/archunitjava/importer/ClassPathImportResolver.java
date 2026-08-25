package dev.archunitjava.importer;

import dev.archunitjava.model.DeclarationDependencyExtractor;
import dev.archunitjava.model.ExternalTypeStub;
import dev.archunitjava.model.JavaBootstrapArgument;
import dev.archunitjava.model.JavaConstantEvidence;
import dev.archunitjava.model.JavaDynamicCallSite;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMethodHandle;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeName;
import dev.archunitjava.model.JvmArrayType;
import dev.archunitjava.model.JvmDescriptors;
import dev.archunitjava.model.JvmMethodType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.JvmType;
import dev.archunitjava.model.JvmVoidType;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Resolves selected class files and gaps without class loading or runtime lookup. */
public final class ClassPathImportResolver {
    private final ClassPathAssemblyOptions assemblyOptions;
    private final ClassFileReaderOptions readerOptions;
    private final ImportFailurePolicy failurePolicy;

    public ClassPathImportResolver() {
        this(
                ClassPathAssemblyOptions.classPathDefaults(),
                ClassFileReaderOptions.defaults(),
                ImportFailurePolicy.collectDiagnostics());
    }

    public ClassPathImportResolver(
            ClassPathAssemblyOptions assemblyOptions,
            ClassFileReaderOptions readerOptions,
            ImportFailurePolicy failurePolicy) {
        this.assemblyOptions = Objects.requireNonNull(assemblyOptions, "assemblyOptions");
        this.readerOptions = Objects.requireNonNull(readerOptions, "readerOptions");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    public ImportResolutionResult resolve(List<ClassFileInput> inputs) {
        return resolve(inputs, List.of());
    }

    public ImportResolutionResult resolve(
            List<ClassFileInput> inputs, Collection<ExternalTypeStub> suppliedExternalTypes) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(suppliedExternalTypes, "suppliedExternalTypes");
        ClassPathAssemblyResult assembly = new ClassPathAssembler(assemblyOptions).assemble(inputs);
        List<ImportResolutionDiagnostic> diagnostics = new ArrayList<>();
        boolean damagedArchive = false;
        for (InputDiagnostic diagnostic : assembly.diagnostics()) {
            if (!damagedArchive(diagnostic)) continue;
            damagedArchive = true;
            diagnostics.add(new ImportResolutionDiagnostic(
                    ImportResolutionDiagnosticCode.DAMAGED_ARCHIVE,
                    diagnostic.input(),
                    diagnostic.context()));
        }
        if (damagedArchive && failurePolicy.failOnDamagedArchive()) {
            throw new ImportResolutionException(ImportFailureKind.DAMAGED_ARCHIVE);
        }

        TreeMap<ResourceKey, ScopedResource> resources = resourcesToInspect(assembly);
        ClassFileReadResult read = new ClassFileReader(readerOptions).readAll(resources.values().stream()
                .map(ScopedResource::resource)
                .toList());
        boolean unsupportedVersion = false;
        for (ClassFileDiagnostic diagnostic : read.diagnostics()) {
            if (diagnostic.code() != ClassFileDiagnosticCode.UNSUPPORTED_CLASS_VERSION) continue;
            unsupportedVersion = true;
            diagnostics.add(new ImportResolutionDiagnostic(
                    ImportResolutionDiagnosticCode.UNSUPPORTED_CLASS_VERSION,
                    diagnostic.resourceName(),
                    diagnostic.context()));
        }
        if (unsupportedVersion && failurePolicy.failOnUnsupportedClassVersion()) {
            throw new ImportResolutionException(ImportFailureKind.UNSUPPORTED_CLASS_VERSION);
        }

        TreeMap<GroupKey, List<ParsedClassFile>> groups = new TreeMap<>();
        for (ParsedClassFile parsed : read.classes()) {
            ScopedResource scoped = resources.get(ResourceKey.of(parsed));
            if (scoped == null) {
                throw new IllegalArgumentException("Parsed class has no assembly scope");
            }
            GroupKey key = new GroupKey(scoped.scope(), parsed.binaryName());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(parsed);
        }
        List<ParsedClassFile> winners = groups.values().stream()
                .map(values -> selectedWinner(values, resources))
                .flatMap(Optional::stream)
                .toList();
        TypeModelResult model = new TypeModelBuilder().build(
                new ClassFileReadResult(winners, read.diagnostics()));
        List<ResolvedImportedType> imported = resolvedTypes(groups, resources, model, diagnostics);
        List<ExternalTypeStub> external = externalTypes(
                model, suppliedExternalTypes, diagnostics);
        return new ImportResolutionResult(assembly, model, imported, external, diagnostics);
    }

    private static TreeMap<ResourceKey, ScopedResource> resourcesToInspect(
            ClassPathAssemblyResult assembly) {
        TreeMap<ResourceKey, ScopedResource> result = new TreeMap<>();
        for (SelectedClassResource selection : assembly.selections()) {
            put(result, selection.lookupScope(), selection.winner(), true);
            for (ClassFileResource alternative : selection.shadowedAlternatives()) {
                if (!alternative.origin().input().equals(selection.winner().origin().input())) {
                    put(result, selection.lookupScope(), alternative, false);
                }
            }
        }
        return result;
    }

    private static void put(
            Map<ResourceKey, ScopedResource> resources,
            String scope,
            ClassFileResource resource,
            boolean selectedWinner) {
        ResourceKey key = ResourceKey.of(resource);
        ScopedResource previous = resources.get(key);
        resources.put(
                key,
                new ScopedResource(
                        scope,
                        resource,
                        selectedWinner || previous != null && previous.selectedWinner()));
    }

    private static List<ResolvedImportedType> resolvedTypes(
            Map<GroupKey, List<ParsedClassFile>> groups,
            Map<ResourceKey, ScopedResource> resources,
            TypeModelResult model,
            List<ImportResolutionDiagnostic> diagnostics) {
        List<ResolvedImportedType> result = new ArrayList<>();
        for (Map.Entry<GroupKey, List<ParsedClassFile>> entry : groups.entrySet()) {
            List<ParsedClassFile> definitions = entry.getValue().stream().sorted().toList();
            Optional<ParsedClassFile> selected = selectedWinner(definitions, resources);
            if (selected.isEmpty()) continue;
            ParsedClassFile winner = selected.orElseThrow();
            if (winner.moduleDescriptor()) continue;
            Optional<JavaType> imported = model.types().stream()
                    .filter(value -> value.binaryName().equals(winner.binaryName()))
                    .filter(value -> value.resourceName().equals(winner.resourceName()))
                    .filter(value -> value.precedence() == winner.precedence())
                    .findFirst();
            if (imported.isEmpty()) continue;
            List<ShadowedTypeDefinition> shadows = definitions.stream()
                    .filter(value -> value != winner)
                    .map(value -> new ShadowedTypeDefinition(
                            new JavaTypeName(value.binaryName()),
                            value.resourceName(),
                            value.origin(),
                            value.precedence()))
                    .toList();
            result.add(new ResolvedImportedType(entry.getKey().scope(), imported.orElseThrow(), shadows));
            if (!shadows.isEmpty()) {
                diagnostics.add(new ImportResolutionDiagnostic(
                        ImportResolutionDiagnosticCode.DUPLICATE_DEFINITION,
                        winner.binaryName(),
                        Map.of(
                                "lookupScope", entry.getKey().scope(),
                                "shadowedCount", Integer.toString(shadows.size()),
                                "winnerResource", winner.resourceName())));
            }
        }
        return result.stream().sorted().toList();
    }

    private static Optional<ParsedClassFile> selectedWinner(
            Collection<ParsedClassFile> definitions,
            Map<ResourceKey, ScopedResource> resources) {
        return definitions.stream()
                .filter(value -> {
                    ScopedResource resource = resources.get(ResourceKey.of(value));
                    return resource != null && resource.selectedWinner();
                })
                .sorted()
                .findFirst();
    }

    private static List<ExternalTypeStub> externalTypes(
            TypeModelResult model,
            Collection<ExternalTypeStub> supplied,
            List<ImportResolutionDiagnostic> diagnostics) {
        TreeMap<String, ExternalTypeStub> suppliedByName = new TreeMap<>();
        for (ExternalTypeStub stub : supplied) {
            ExternalTypeStub value = Objects.requireNonNull(stub, "externalType");
            if (suppliedByName.put(value.name().binaryName(), value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate external stub: " + value.name().binaryName());
            }
        }
        TreeSet<String> imported = model.types().stream()
                .map(JavaType::binaryName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        TreeSet<String> referenced = referencedTypes(model);
        TreeMap<String, ExternalTypeStub> result = new TreeMap<>();
        suppliedByName.forEach((name, stub) -> {
            if (!imported.contains(name)) result.put(name, stub);
        });
        for (String name : referenced) {
            if (imported.contains(name)) continue;
            ExternalTypeStub suppliedStub = suppliedByName.get(name);
            if (suppliedStub != null) {
                diagnostics.add(new ImportResolutionDiagnostic(
                        ImportResolutionDiagnosticCode.EXTERNAL_STUB_USED,
                        name,
                        Map.of("hierarchyComplete", Boolean.toString(suppliedStub.hierarchyComplete()))));
                continue;
            }
            result.put(name, ExternalTypeStub.incomplete(name));
            diagnostics.add(new ImportResolutionDiagnostic(
                    ImportResolutionDiagnosticCode.MISSING_TARGET,
                    name,
                    Map.of("resolution", "incomplete-external-stub")));
        }
        return List.copyOf(result.values());
    }

    private static TreeSet<String> referencedTypes(TypeModelResult model) {
        TreeSet<String> names = new TreeSet<>();
        new DeclarationDependencyExtractor().extract(model.types()).dependencies()
                .forEach(value -> names.add(value.target().binaryName()));
        for (JavaType type : model.types()) {
            for (JavaMember member : type.declaredMembers()) {
                member.codeAccesses().forEach(access -> {
                    addType(access.target().ownerType(), names);
                    if (access.target().method()) {
                        addMethodType(JvmDescriptors.parseMethod(access.target().descriptor()), names);
                    } else {
                        addType(JvmDescriptors.parseField(access.target().descriptor()), names);
                    }
                });
                member.dynamicCallSites().forEach(site -> addDynamicCallSite(site, names));
                member.exceptionEvidence().forEach(value -> value.targetType()
                        .ifPresent(target -> names.add(target.binaryName())));
            }
            for (JavaConstantEvidence constant : type.constantPoolEvidence().constants()) {
                constant.referencedTypes().forEach(value -> addType(value, names));
                constant.methodHandle().ifPresent(value -> addMethodHandle(value, names));
                constant.dynamicConstant().ifPresent(value -> {
                    addType(value.constantType(), names);
                    addMethodHandle(value.bootstrapMethod(), names);
                    value.bootstrapArguments().forEach(argument ->
                            addBootstrapArgument(argument, names));
                });
            }
        }
        for (JavaModule module : model.modules()) {
            module.uses().forEach(value -> names.add(value.binaryName()));
            module.provides().forEach(value -> {
                names.add(value.service().binaryName());
                value.providers().forEach(provider -> names.add(provider.binaryName()));
            });
        }
        return names;
    }

    private static void addDynamicCallSite(JavaDynamicCallSite site, TreeSet<String> names) {
        addMethodType(site.invocationType(), names);
        addMethodHandle(site.bootstrapMethod(), names);
        site.lambdaImplementation().ifPresent(value -> addMethodHandle(value, names));
        site.functionalInterfaces().forEach(value -> names.add(value.binaryName()));
        for (JavaBootstrapArgument argument : site.bootstrapArguments()) {
            addBootstrapArgument(argument, names);
        }
    }

    private static void addBootstrapArgument(
            JavaBootstrapArgument argument, TreeSet<String> names) {
        if (argument.methodHandle().isPresent()) {
            addMethodHandle(argument.methodHandle().orElseThrow(), names);
            return;
        }
        try {
            if (argument.kind().equals("METHOD_TYPE")) {
                addMethodType(JvmDescriptors.parseMethod(argument.encodedValue()), names);
            } else if (argument.kind().equals("CLASS")) {
                addType(JvmDescriptors.parseField(argument.encodedValue()), names);
            }
        } catch (IllegalArgumentException ignored) {
            // Raw malformed evidence remains in the model but cannot name a typed resolution target.
        }
    }

    private static void addMethodHandle(JavaMethodHandle handle, TreeSet<String> names) {
        addType(handle.ownerType(), names);
        if (handle.lookupDescriptor().startsWith("(")) {
            addMethodType(JvmDescriptors.parseMethod(handle.lookupDescriptor()), names);
        } else {
            addType(JvmDescriptors.parseField(handle.lookupDescriptor()), names);
        }
    }

    private static void addMethodType(JvmMethodType method, TreeSet<String> names) {
        method.parameterTypes().forEach(value -> addType(value, names));
        addType(method.returnType(), names);
    }

    private static void addType(JvmType type, TreeSet<String> names) {
        if (type instanceof JvmReferenceType reference) {
            names.add(reference.binaryName());
        } else if (type instanceof JvmArrayType array) {
            addType(array.elementType(), names);
        } else if (type instanceof JvmVoidType) {
            // Void is not a resolvable type target.
        }
    }

    private static boolean damagedArchive(InputDiagnostic diagnostic) {
        if (diagnostic.code() != InputDiagnosticCode.IO_FAILURE) return false;
        String operation = diagnostic.context().get("operation");
        return "archive-traversal".equals(operation) || "manifest-read".equals(operation);
    }

    private record ScopedResource(
            String scope, ClassFileResource resource, boolean selectedWinner) {
        private ScopedResource {
            if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope must not be blank");
            Objects.requireNonNull(resource, "resource");
        }
    }

    private record ResourceKey(
            ClassFileOrigin origin, String resourceName, int precedence)
            implements Comparable<ResourceKey> {
        private ResourceKey {
            Objects.requireNonNull(origin, "origin");
            if (resourceName == null || resourceName.isBlank()) {
                throw new IllegalArgumentException("resourceName must not be blank");
            }
            if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
        }

        static ResourceKey of(ClassFileResource resource) {
            return new ResourceKey(resource.origin(), resource.name(), resource.precedence());
        }

        static ResourceKey of(ParsedClassFile parsed) {
            return new ResourceKey(parsed.origin(), parsed.resourceName(), parsed.precedence());
        }

        @Override
        public int compareTo(ResourceKey other) {
            int result = Integer.compare(precedence, other.precedence);
            if (result != 0) return result;
            result = resourceName.compareTo(other.resourceName);
            return result != 0 ? result : origin.compareTo(other.origin);
        }
    }

    private record GroupKey(String scope, String binaryName) implements Comparable<GroupKey> {
        private GroupKey {
            if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope must not be blank");
            if (binaryName == null || binaryName.isBlank()) {
                throw new IllegalArgumentException("binaryName must not be blank");
            }
        }

        @Override
        public int compareTo(GroupKey other) {
            int result = scope.compareTo(other.scope);
            return result != 0 ? result : binaryName.compareTo(other.binaryName);
        }
    }
}
