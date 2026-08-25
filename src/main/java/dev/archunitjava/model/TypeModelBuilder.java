package dev.archunitjava.model;

import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ParsedClassFile;
import java.lang.classfile.ClassFile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import dev.archunitjava.importer.ParsedMember;
import dev.archunitjava.importer.ParsedCodeAccess;
import dev.archunitjava.importer.ParsedLineNumber;
import dev.archunitjava.importer.ParsedAnnotation;
import dev.archunitjava.importer.ParsedAnnotationDefault;
import dev.archunitjava.importer.ParsedAnnotationElement;
import dev.archunitjava.importer.ParsedAnnotationOccurrence;
import dev.archunitjava.importer.ParsedAnnotationValue;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts backend-neutral class headers into immutable Java type descriptions. */
public final class TypeModelBuilder {
    private static final int KNOWN_CLASS_FLAGS = ClassFile.ACC_PUBLIC
            | ClassFile.ACC_FINAL
            | ClassFile.ACC_SUPER
            | ClassFile.ACC_INTERFACE
            | ClassFile.ACC_ABSTRACT
            | ClassFile.ACC_SYNTHETIC
            | ClassFile.ACC_ANNOTATION
            | ClassFile.ACC_ENUM
            | ClassFile.ACC_MODULE;

    public TypeModelResult build(ClassFileReadResult readResult) {
        Objects.requireNonNull(readResult, "readResult");
        List<JavaType> types = new ArrayList<>();
        List<TypeModelDiagnostic> diagnostics = new ArrayList<>();
        for (ParsedClassFile parsed : readResult.classes()) {
            adapt(parsed, types, diagnostics);
        }
        return new TypeModelResult(types, readResult.diagnostics(), diagnostics);
    }

    private static void adapt(
            ParsedClassFile parsed,
            List<JavaType> types,
            List<TypeModelDiagnostic> diagnostics) {
        if (parsed.moduleDescriptor() || has(parsed.accessFlags(), ClassFile.ACC_MODULE)) {
            diagnostics.add(new TypeModelDiagnostic(
                    TypeModelDiagnosticCode.MODULE_DESCRIPTOR_IS_NOT_A_TYPE,
                    parsed.resourceName(),
                    parsed.origin(),
                    Map.of("binaryName", parsed.binaryName())));
            return;
        }
        JavaTypeName name;
        try {
            name = new JavaTypeName(parsed.binaryName());
        } catch (IllegalArgumentException failure) {
            diagnostics.add(new TypeModelDiagnostic(
                    TypeModelDiagnosticCode.INVALID_BINARY_NAME,
                    parsed.resourceName(),
                    parsed.origin(),
                    Map.of("binaryName", parsed.binaryName())));
            return;
        }
        int flags = parsed.accessFlags();
        DeclarationLocation location = new DeclarationLocation(
                ClassResourceLocation.from(parsed.origin(), parsed.precedence()),
                parsed.sourceFile().flatMap(SourceFileName::fromUntrusted));
        EnumSet<JavaModifier> modifiers = EnumSet.noneOf(JavaModifier.class);
        if (has(flags, ClassFile.ACC_PUBLIC)) modifiers.add(JavaModifier.PUBLIC);
        if (has(flags, ClassFile.ACC_ABSTRACT)) modifiers.add(JavaModifier.ABSTRACT);
        if (has(flags, ClassFile.ACC_FINAL)) modifiers.add(JavaModifier.FINAL);
        if (has(flags, ClassFile.ACC_SYNTHETIC)) modifiers.add(JavaModifier.SYNTHETIC);
        JavaTypeKind typeKind = kind(flags, parsed.recordDeclaration());
        var superclass = typeKind == JavaTypeKind.INTERFACE || typeKind == JavaTypeKind.ANNOTATION
                ? java.util.Optional.<JvmReferenceType>empty()
                : parsed.superclassBinaryName().map(JvmReferenceType::new);
        List<JvmReferenceType> interfaces = parsed.interfaceBinaryNames().stream()
                .map(JvmReferenceType::new)
                .toList();
        types.add(new JavaType(
                name,
                typeKind,
                modifiers,
                flags,
                flags & ~KNOWN_CLASS_FLAGS,
                new ClassFileVersion(parsed.majorVersion(), parsed.minorVersion()),
                parsed.resourceName(),
                parsed.precedence(),
                location,
                superclass,
                interfaces,
                typeAnnotations(name, parsed.annotations()),
                GenericClassView.create(superclass, interfaces, parsed.genericSignature()),
                recordComponents(name, parsed, location),
                parsed.sealedDeclaration(),
                parsed.permittedSubclassBinaryNames().stream().map(JvmReferenceType::new).toList(),
                JavaNesting.from(name, parsed.nestingMetadata()),
                constantPoolEvidence(name, parsed, location),
                members(
                        name,
                        parsed.declaredMembers(),
                        parsed.annotations(),
                        parsed.annotationDefaults(),
                        location)));
    }

    private static JavaConstantPoolEvidence constantPoolEvidence(
            JavaTypeName owner, ParsedClassFile parsed, DeclarationLocation location) {
        List<JavaConstantEvidence> constants = parsed.constantPoolEvidence().constants().stream()
                .map(value -> {
                    Optional<JavaMethodHandle> handle = value.methodHandle()
                            .map(TypeModelBuilder::methodHandle);
                    Optional<JavaDynamicConstant> dynamic = value.dynamicConstant()
                            .map(TypeModelBuilder::dynamicConstant);
                    Optional<JavaConstantLoadSite> loadSite = value.loadSite().map(site -> {
                        ParsedMember member = parsed.declaredMembers().stream()
                                .filter(candidate -> candidate.name().equals(site.memberName())
                                        && candidate.descriptor().equals(site.memberDescriptor()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Constant load site refers to an unknown member"));
                        return new JavaConstantLoadSite(
                                new JavaMemberSignature(owner, site.memberName(), site.memberDescriptor()),
                                new BytecodeLocation(
                                        location.resource(),
                                        location.sourceFile(),
                                        site.bytecodeOffset(),
                                        lineNumbers(member.lineNumbers()).lineAt(site.bytecodeOffset())));
                    });
                    List<JvmType> referencedTypes = descriptorTypes(value.descriptor());
                    if (handle.isPresent()) {
                        List<JvmType> withOwner = new ArrayList<>(referencedTypes);
                        withOwner.add(handle.orElseThrow().ownerType());
                        referencedTypes = withOwner;
                    }
                    return new JavaConstantEvidence(
                            JavaConstantEvidenceKind.valueOf(value.kind().name()),
                            value.constantPoolIndex(),
                            value.descriptor(),
                            referencedTypes,
                            handle,
                            dynamic,
                            loadSite);
                })
                .sorted()
                .toList();
        return new JavaConstantPoolEvidence(
                constants,
                parsed.constantPoolEvidence().originalEvidenceCount(),
                parsed.constantPoolEvidence().truncated());
    }

    private static JavaDynamicConstant dynamicConstant(
            dev.archunitjava.importer.ParsedDynamicConstant parsed) {
        return new JavaDynamicConstant(
                parsed.name(),
                JvmDescriptors.parseField(parsed.descriptor()),
                methodHandle(parsed.bootstrapMethod()),
                parsed.bootstrapArguments().stream()
                        .map(value -> new JavaBootstrapArgument(
                                value.kind(),
                                value.encodedValue(),
                                value.methodHandle().map(TypeModelBuilder::methodHandle)))
                        .toList(),
                parsed.originalBootstrapArgumentCount(),
                parsed.bootstrapArgumentsTruncated());
    }

    private static List<JvmType> descriptorTypes(String descriptor) {
        if (!descriptor.startsWith("(")) return List.of(JvmDescriptors.parseField(descriptor));
        JvmMethodType method = JvmDescriptors.parseMethod(descriptor);
        List<JvmType> result = new ArrayList<>(method.parameterTypes());
        if (!(method.returnType() instanceof JvmVoidType)) result.add(method.returnType());
        return result;
    }

    private static List<JavaRecordComponent> recordComponents(
            JavaTypeName owner, ParsedClassFile parsed, DeclarationLocation location) {
        return parsed.recordComponents().stream()
                .map(component -> new JavaRecordComponent(
                        owner,
                        component.name(),
                        component.descriptor(),
                        GenericFieldView.create(
                                JvmDescriptors.parseField(component.descriptor()),
                                component.genericSignature()),
                        parsed.annotations().stream()
                                .filter(annotation -> annotation.container()
                                        == ParsedAnnotationOccurrence.Container.RECORD_COMPONENT)
                                .filter(annotation -> annotation.ownerName().equals(component.name())
                                        && annotation.ownerDescriptor().equals(component.descriptor()))
                                .map(annotation -> annotationOccurrence(
                                        annotation,
                                        owner.binaryName(),
                                        null))
                                .sorted()
                                .toList(),
                        location))
                .sorted()
                .toList();
    }

    private static List<JavaMember> members(
            JavaTypeName owner,
            List<ParsedMember> parsedMembers,
            List<ParsedAnnotationOccurrence> parsedAnnotations,
            List<ParsedAnnotationDefault> parsedDefaults,
            DeclarationLocation location) {
        List<JavaMember> members = new ArrayList<>();
        for (ParsedMember parsed : parsedMembers) {
            JavaMemberKind kind = memberKind(parsed);
            int flags = parsed.accessFlags();
            EnumSet<JavaMemberModifier> modifiers = EnumSet.noneOf(JavaMemberModifier.class);
            addMemberModifiers(flags, modifiers);
            int knownFlags = parsed.kind() == ParsedMember.Kind.FIELD
                    ? fieldFlags()
                    : methodFlags();
            JavaMemberSignature signature = new JavaMemberSignature(
                    owner, parsed.name(), parsed.descriptor());
            Optional<GenericFieldView> genericFieldView = kind == JavaMemberKind.FIELD
                    ? Optional.of(GenericFieldView.create(
                            JvmDescriptors.parseField(parsed.descriptor()),
                            parsed.genericSignature()))
                    : Optional.empty();
            Optional<GenericMethodView> genericMethodView = kind == JavaMemberKind.FIELD
                    ? Optional.empty()
                    : Optional.of(GenericMethodView.create(
                            JvmDescriptors.parseMethod(parsed.descriptor()),
                            parsed.genericSignature()));
            members.add(new JavaMember(
                    signature,
                    kind,
                    modifiers,
                    flags,
                    flags & ~knownFlags,
                    parsed.hasCode(),
                    location,
                    lineNumbers(parsed.lineNumbers()),
                    memberAnnotations(signature, kind, parsedAnnotations),
                    annotationDefault(parsed, parsedDefaults),
                    genericFieldView,
                    genericMethodView,
                    codeAccesses(signature, parsed, location),
                    dynamicCallSites(signature, parsed, location),
                    exceptionEvidence(signature, parsed, location)));
        }
        return List.copyOf(members);
    }

    private static List<JavaCodeAccess> codeAccesses(
            JavaMemberSignature caller, ParsedMember parsed, DeclarationLocation location) {
        LineNumberTable lineNumbers = lineNumbers(parsed.lineNumbers());
        return parsed.codeAccesses().stream()
                .map(access -> {
                    JvmType owner = JvmDescriptors.parseField(access.targetOwnerDescriptor());
                    boolean method = access.kind() == ParsedCodeAccess.Kind.METHOD_CALL
                            || access.kind() == ParsedCodeAccess.Kind.CONSTRUCTOR_CALL;
                    return new JavaCodeAccess(
                            caller,
                            new JavaCodeAccessTarget(
                                    owner,
                                    access.targetName(),
                                    access.targetDescriptor(),
                                    method),
                            JavaCodeAccessKind.valueOf(access.kind().name()),
                            JavaCodeAccessOpcode.valueOf(access.opcode().name()),
                            access.interfaceTarget(),
                            new BytecodeLocation(
                                    location.resource(),
                                    location.sourceFile(),
                                    access.bytecodeOffset(),
                                    lineNumbers.lineAt(access.bytecodeOffset())));
                })
                .sorted()
                .toList();
    }

    private static List<JavaDynamicCallSite> dynamicCallSites(
            JavaMemberSignature caller, ParsedMember parsed, DeclarationLocation location) {
        LineNumberTable lines = lineNumbers(parsed.lineNumbers());
        return parsed.dynamicCallSites().stream()
                .map(site -> dynamicCallSite(caller, site, location, lines))
                .sorted()
                .toList();
    }

    private static List<JavaExceptionEvidence> exceptionEvidence(
            JavaMemberSignature owner, ParsedMember parsed, DeclarationLocation location) {
        LineNumberTable lines = lineNumbers(parsed.lineNumbers());
        return parsed.exceptionEvidence().stream()
                .map(value -> {
                    Optional<JvmReferenceType> target = value.targetDescriptor()
                            .map(JvmDescriptors::parseField)
                            .map(type -> {
                                if (!(type instanceof JvmReferenceType reference)) {
                                    throw new IllegalArgumentException(
                                            "Exception target must be a reference type");
                                }
                                return reference;
                            });
                    Optional<BytecodeLocation> bytecode = value.bytecodeOffset().isPresent()
                            ? Optional.of(new BytecodeLocation(
                                    location.resource(),
                                    location.sourceFile(),
                                    value.bytecodeOffset().getAsInt(),
                                    lines.lineAt(value.bytecodeOffset().getAsInt())))
                            : Optional.empty();
                    return new JavaExceptionEvidence(
                            owner,
                            JavaExceptionEvidenceKind.valueOf(value.kind().name()),
                            target,
                            location,
                            bytecode);
                })
                .sorted()
                .toList();
    }

    private static JavaDynamicCallSite dynamicCallSite(
            JavaMemberSignature caller,
            dev.archunitjava.importer.ParsedDynamicCallSite parsed,
            DeclarationLocation location,
            LineNumberTable lines) {
        JavaMethodHandle bootstrap = methodHandle(parsed.bootstrapMethod());
        int retained = Math.min(
                parsed.bootstrapArguments().size(), JavaDynamicCallSite.MAXIMUM_BOOTSTRAP_ARGUMENTS);
        List<JavaBootstrapArgument> arguments = parsed.bootstrapArguments().stream()
                .limit(retained)
                .map(value -> new JavaBootstrapArgument(
                        value.kind(),
                        value.encodedValue(),
                        value.methodHandle().map(TypeModelBuilder::methodHandle)))
                .toList();
        JvmMethodType invocationType = JvmDescriptors.parseMethod(parsed.invocationDescriptor());
        Optional<JavaMethodHandle> implementation = arguments.size() >= 2
                ? arguments.get(1).methodHandle()
                : Optional.empty();
        boolean lambdaShape = isBootstrap(
                        bootstrap,
                        "java.lang.invoke.LambdaMetafactory",
                        "metafactory",
                        "altMetafactory")
                && bootstrap.kind() == JavaMethodHandleKind.STATIC
                && arguments.size() >= 3
                && arguments.get(0).kind().equals("METHOD_TYPE")
                && implementation.isPresent()
                && arguments.get(2).kind().equals("METHOD_TYPE")
                && invocationType.returnType() instanceof JvmReferenceType;
        JavaDynamicCallSiteKind kind;
        List<JvmReferenceType> functionalInterfaces;
        if (lambdaShape) {
            kind = JavaDynamicCallSiteKind.LAMBDA_METAFACTORY;
            functionalInterfaces = List.of((JvmReferenceType) invocationType.returnType());
        } else if (isBootstrap(
                        bootstrap,
                        "java.lang.invoke.StringConcatFactory",
                        "makeConcat",
                        "makeConcatWithConstants")
                && bootstrap.kind() == JavaMethodHandleKind.STATIC) {
            kind = JavaDynamicCallSiteKind.STRING_CONCAT;
            implementation = Optional.empty();
            functionalInterfaces = List.of();
        } else {
            kind = JavaDynamicCallSiteKind.GENERIC;
            implementation = Optional.empty();
            functionalInterfaces = List.of();
        }
        return new JavaDynamicCallSite(
                caller,
                parsed.invocationName(),
                invocationType,
                bootstrap,
                arguments,
                parsed.bootstrapArguments().size(),
                parsed.bootstrapArguments().size() > retained,
                kind,
                implementation,
                functionalInterfaces,
                new BytecodeLocation(
                        location.resource(),
                        location.sourceFile(),
                        parsed.bytecodeOffset(),
                        lines.lineAt(parsed.bytecodeOffset())));
    }

    private static boolean isBootstrap(
            JavaMethodHandle handle, String owner, String... names) {
        if (!(handle.ownerType() instanceof JvmReferenceType reference)
                || !reference.binaryName().equals(owner)) return false;
        return java.util.Arrays.asList(names).contains(handle.name());
    }

    private static JavaMethodHandle methodHandle(
            dev.archunitjava.importer.ParsedMethodHandle parsed) {
        return new JavaMethodHandle(
                JavaMethodHandleKind.valueOf(parsed.kind()),
                parsed.referenceKind(),
                JvmDescriptors.parseField(parsed.ownerDescriptor()),
                parsed.name(),
                parsed.lookupDescriptor(),
                parsed.ownerInterface());
    }

    private static List<JavaAnnotationOccurrence> typeAnnotations(
            JavaTypeName owner, List<ParsedAnnotationOccurrence> annotations) {
        return annotations.stream()
                .filter(annotation -> annotation.container() == ParsedAnnotationOccurrence.Container.TYPE
                        || annotation.container() == ParsedAnnotationOccurrence.Container.RECORD_COMPONENT)
                .map(annotation -> annotationOccurrence(annotation, owner.binaryName(), null))
                .sorted()
                .toList();
    }

    private static List<JavaAnnotationOccurrence> memberAnnotations(
            JavaMemberSignature signature,
            JavaMemberKind memberKind,
            List<ParsedAnnotationOccurrence> annotations) {
        ParsedAnnotationOccurrence.Container expected = memberKind == JavaMemberKind.FIELD
                ? ParsedAnnotationOccurrence.Container.FIELD
                : ParsedAnnotationOccurrence.Container.METHOD;
        return annotations.stream()
                .filter(annotation -> annotation.container() == expected)
                .filter(annotation -> annotation.ownerName().equals(signature.name())
                        && annotation.ownerDescriptor().equals(signature.descriptor()))
                .map(annotation -> annotationOccurrence(annotation, signature.stableKey(), memberKind))
                .sorted()
                .toList();
    }

    private static Optional<JavaAnnotationValue> annotationDefault(
            ParsedMember member, List<ParsedAnnotationDefault> defaults) {
        List<JavaAnnotationValue> values = defaults.stream()
                .filter(value -> value.methodName().equals(member.name())
                        && value.methodDescriptor().equals(member.descriptor()))
                .map(value -> annotationValue(value.value()))
                .distinct()
                .toList();
        if (values.size() > 1) {
            throw new IllegalArgumentException(
                    "Conflicting annotation defaults for " + member.name() + member.descriptor());
        }
        return values.stream().findFirst();
    }

    private static JavaAnnotationOccurrence annotationOccurrence(
            ParsedAnnotationOccurrence parsed,
            String ownerKey,
            JavaMemberKind memberKind) {
        AnnotationSiteKind siteKind;
        if (parsed.site() == ParsedAnnotationOccurrence.Site.TYPE_USE) {
            siteKind = AnnotationSiteKind.TYPE_USE;
        } else if (parsed.site() == ParsedAnnotationOccurrence.Site.PARAMETER) {
            siteKind = AnnotationSiteKind.PARAMETER;
        } else {
            siteKind = switch (parsed.container()) {
                case TYPE -> AnnotationSiteKind.TYPE_DECLARATION;
                case FIELD -> AnnotationSiteKind.FIELD_DECLARATION;
                case METHOD -> memberKind == JavaMemberKind.CONSTRUCTOR
                        ? AnnotationSiteKind.CONSTRUCTOR_DECLARATION
                        : AnnotationSiteKind.METHOD_DECLARATION;
                case RECORD_COMPONENT -> AnnotationSiteKind.RECORD_COMPONENT;
            };
        }
        String actualOwner = parsed.container() == ParsedAnnotationOccurrence.Container.RECORD_COMPONENT
                ? ownerKey + "#record:" + parsed.ownerName() + parsed.ownerDescriptor()
                : ownerKey;
        Optional<JavaTypeUseTarget> target = parsed.typeUseTarget().map(value ->
                new JavaTypeUseTarget(value.targetType(), value.targetInfo(), value.path()));
        return new JavaAnnotationOccurrence(
                AnnotationVisibility.valueOf(parsed.visibility().name()),
                new AnnotationSite(siteKind, actualOwner, parsed.parameterIndex(), target),
                annotation(parsed.annotation()));
    }

    private static JavaAnnotation annotation(ParsedAnnotation parsed) {
        JvmType parsedType = JvmDescriptors.parseField(parsed.typeDescriptor());
        if (!(parsedType instanceof JvmReferenceType annotationType)) {
            throw new IllegalArgumentException("Annotation type must be a reference descriptor");
        }
        return new JavaAnnotation(
                annotationType,
                parsed.elements().stream()
                        .map(TypeModelBuilder::annotationElement)
                        .toList());
    }

    private static JavaAnnotationElement annotationElement(ParsedAnnotationElement parsed) {
        return new JavaAnnotationElement(parsed.name(), annotationValue(parsed.value()));
    }

    private static JavaAnnotationValue annotationValue(ParsedAnnotationValue parsed) {
        if (parsed instanceof ParsedAnnotationValue.ScalarValue value) {
            return new JavaAnnotationValue.ScalarValue(
                    JavaAnnotationValue.ScalarKind.valueOf(value.kind().name()), value.encodedValue());
        }
        if (parsed instanceof ParsedAnnotationValue.EnumValue value) {
            JvmType enumType = JvmDescriptors.parseField(value.typeDescriptor());
            if (!(enumType instanceof JvmReferenceType referenceType)) {
                throw new IllegalArgumentException("Annotation enum type must be a reference descriptor");
            }
            return new JavaAnnotationValue.EnumValue(referenceType, value.constantName());
        }
        if (parsed instanceof ParsedAnnotationValue.ClassValue value) {
            return new JavaAnnotationValue.ClassValue(value.descriptor());
        }
        if (parsed instanceof ParsedAnnotationValue.NestedAnnotationValue value) {
            return new JavaAnnotationValue.NestedAnnotationValue(annotation(value.annotation()));
        }
        if (parsed instanceof ParsedAnnotationValue.ArrayValue value) {
            return new JavaAnnotationValue.ArrayValue(
                    value.values().stream().map(TypeModelBuilder::annotationValue).toList());
        }
        throw new IllegalArgumentException("Unsupported parsed annotation value: " + parsed.getClass());
    }

    private static LineNumberTable lineNumbers(List<ParsedLineNumber> parsedLines) {
        return new LineNumberTable(parsedLines.stream()
                .map(line -> new LineNumberEntry(line.bytecodeOffset(), line.lineNumber()))
                .toList());
    }

    private static JavaMemberKind memberKind(ParsedMember parsed) {
        if (parsed.kind() == ParsedMember.Kind.FIELD) return JavaMemberKind.FIELD;
        return switch (parsed.name()) {
            case "<init>" -> JavaMemberKind.CONSTRUCTOR;
            case "<clinit>" -> JavaMemberKind.STATIC_INITIALIZER;
            default -> JavaMemberKind.METHOD;
        };
    }

    private static void addMemberModifiers(int flags, EnumSet<JavaMemberModifier> modifiers) {
        if (has(flags, ClassFile.ACC_PUBLIC)) modifiers.add(JavaMemberModifier.PUBLIC);
        if (has(flags, ClassFile.ACC_PROTECTED)) modifiers.add(JavaMemberModifier.PROTECTED);
        if (has(flags, ClassFile.ACC_PRIVATE)) modifiers.add(JavaMemberModifier.PRIVATE);
        if (has(flags, ClassFile.ACC_STATIC)) modifiers.add(JavaMemberModifier.STATIC);
        if (has(flags, ClassFile.ACC_FINAL)) modifiers.add(JavaMemberModifier.FINAL);
        if (has(flags, ClassFile.ACC_ABSTRACT)) modifiers.add(JavaMemberModifier.ABSTRACT);
        if (has(flags, ClassFile.ACC_SYNCHRONIZED)) modifiers.add(JavaMemberModifier.SYNCHRONIZED);
        if (has(flags, ClassFile.ACC_NATIVE)) modifiers.add(JavaMemberModifier.NATIVE);
        if (has(flags, ClassFile.ACC_STRICT)) modifiers.add(JavaMemberModifier.STRICT);
        if (has(flags, ClassFile.ACC_SYNTHETIC)) modifiers.add(JavaMemberModifier.SYNTHETIC);
        if (has(flags, ClassFile.ACC_BRIDGE)) modifiers.add(JavaMemberModifier.BRIDGE);
        if (has(flags, ClassFile.ACC_VARARGS)) modifiers.add(JavaMemberModifier.VARARGS);
        if (has(flags, ClassFile.ACC_VOLATILE)) modifiers.add(JavaMemberModifier.VOLATILE);
        if (has(flags, ClassFile.ACC_TRANSIENT)) modifiers.add(JavaMemberModifier.TRANSIENT);
        if (has(flags, ClassFile.ACC_ENUM)) modifiers.add(JavaMemberModifier.ENUM);
    }

    private static int fieldFlags() {
        return ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_PRIVATE
                | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL | ClassFile.ACC_VOLATILE
                | ClassFile.ACC_TRANSIENT | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_ENUM;
    }

    private static int methodFlags() {
        return ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_PRIVATE
                | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL | ClassFile.ACC_SYNCHRONIZED
                | ClassFile.ACC_BRIDGE | ClassFile.ACC_VARARGS | ClassFile.ACC_NATIVE
                | ClassFile.ACC_ABSTRACT | ClassFile.ACC_STRICT | ClassFile.ACC_SYNTHETIC;
    }

    private static JavaTypeKind kind(int flags, boolean recordDeclaration) {
        if (has(flags, ClassFile.ACC_ANNOTATION)) return JavaTypeKind.ANNOTATION;
        if (has(flags, ClassFile.ACC_ENUM)) return JavaTypeKind.ENUM;
        if (has(flags, ClassFile.ACC_INTERFACE)) return JavaTypeKind.INTERFACE;
        if (recordDeclaration) return JavaTypeKind.RECORD;
        return JavaTypeKind.CLASS;
    }

    private static boolean has(int flags, int expected) {
        return (flags & expected) != 0;
    }
}
