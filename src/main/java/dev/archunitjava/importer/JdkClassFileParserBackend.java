package dev.archunitjava.importer;

import java.lang.classfile.AttributedElement;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attribute;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** JDK Class-File API implementation. No JDK parser model escapes this class. */
final class JdkClassFileParserBackend implements ClassFileParserBackend {
    private final ClassFile classFile = ClassFile.of();

    @Override
    public ParsedClassHeader parse(byte[] bytes, TraversalObserver observer) {
        observer.phase(ClassFileTraversalPhase.PARSE_MODEL);
        ClassModel model = classFile.parse(bytes);
        observer.phase(ClassFileTraversalPhase.TRAVERSE_MODEL);
        traverse(model);
        observer.phase(ClassFileTraversalPhase.ADAPT_MODEL);
        String binaryName = model.thisClass().asInternalName().replace('/', '.');
        List<ParsedMember> members = new ArrayList<>();
        List<ParsedAnnotationOccurrence> annotations = new ArrayList<>();
        List<ParsedAnnotationDefault> annotationDefaults = new ArrayList<>();
        List<ParsedRecordComponent> recordComponents = new ArrayList<>();
        addDeclarationAnnotations(
                model, ParsedAnnotationOccurrence.Container.TYPE, "", "", annotations);
        addTypeAnnotations(
                model, ParsedAnnotationOccurrence.Container.TYPE, "", "", null, annotations);
        model.fields().forEach(field -> {
            String name = field.fieldName().stringValue();
            String descriptor = field.fieldType().stringValue();
            members.add(new ParsedMember(
                    ParsedMember.Kind.FIELD,
                    name,
                    descriptor,
                    field.flags().flagsMask(),
                    false,
                    List.of(),
                    signature(field),
                    List.of()));
            addDeclarationAnnotations(
                    field, ParsedAnnotationOccurrence.Container.FIELD, name, descriptor, annotations);
            addTypeAnnotations(
                    field,
                    ParsedAnnotationOccurrence.Container.FIELD,
                    name,
                    descriptor,
                    null,
                    annotations);
        });
        model.methods().forEach(method -> {
            String name = method.methodName().stringValue();
            String descriptor = method.methodType().stringValue();
            var code = method.code();
            List<ParsedLineNumber> lines = code.stream()
                    .flatMap(value -> value.findAttributes(Attributes.lineNumberTable()).stream())
                    .flatMap(attribute -> attribute.lineNumbers().stream())
                    .map(line -> new ParsedLineNumber(line.startPc(), line.lineNumber()))
                    .sorted()
                    .toList();
            members.add(new ParsedMember(
                    ParsedMember.Kind.METHOD,
                    name,
                    descriptor,
                    method.flags().flagsMask(),
                    code.isPresent(),
                    lines,
                    signature(method),
                    code.map(JdkClassFileParserBackend::codeAccesses).orElse(List.of())));
            addDeclarationAnnotations(
                    method, ParsedAnnotationOccurrence.Container.METHOD, name, descriptor, annotations);
            addParameterAnnotations(method, name, descriptor, annotations);
            addTypeAnnotations(
                    method,
                    ParsedAnnotationOccurrence.Container.METHOD,
                    name,
                    descriptor,
                    null,
                    annotations);
            code.ifPresent(value -> addTypeAnnotations(
                    value,
                    ParsedAnnotationOccurrence.Container.METHOD,
                    name,
                    descriptor,
                    requireCodeAttribute(value),
                    annotations));
            method.findAttributes(Attributes.annotationDefault()).stream()
                    .map(attribute -> new ParsedAnnotationDefault(
                            name, descriptor, annotationValue(attribute.defaultValue())))
                    .sorted()
                    .forEach(annotationDefaults::add);
        });
        model.findAttributes(Attributes.record()).stream()
                .flatMap(attribute -> attribute.components().stream())
                .sorted(Comparator.comparing(component -> component.name().stringValue()))
                .forEach(component -> {
                    String name = component.name().stringValue();
                    String descriptor = component.descriptor().stringValue();
                    recordComponents.add(new ParsedRecordComponent(
                            name, descriptor, signature(component)));
                    addDeclarationAnnotations(
                            component,
                            ParsedAnnotationOccurrence.Container.RECORD_COMPONENT,
                            name,
                            descriptor,
                            annotations);
                    addTypeAnnotations(
                            component,
                            ParsedAnnotationOccurrence.Container.RECORD_COMPONENT,
                            name,
                            descriptor,
                            null,
                            annotations);
                });
        var sourceFile = model.findAttributes(Attributes.sourceFile()).stream()
                .map(attribute -> attribute.sourceFile().stringValue())
                .sorted()
                .findFirst();
        var permittedSubclassAttributes = model.findAttributes(Attributes.permittedSubclasses());
        List<String> permittedSubclasses = permittedSubclassAttributes.stream()
                .flatMap(attribute -> attribute.permittedSubclasses().stream())
                .map(entry -> entry.asInternalName().replace('/', '.'))
                .sorted()
                .toList();
        List<ParsedInnerClass> innerClasses = model.findAttributes(Attributes.innerClasses()).stream()
                .flatMap(attribute -> attribute.classes().stream())
                .map(entry -> new ParsedInnerClass(
                        binaryName(entry.innerClass().asInternalName()),
                        entry.outerClass().map(value -> binaryName(value.asInternalName())),
                        entry.innerName().map(value -> value.stringValue()),
                        entry.flagsMask()))
                .sorted()
                .toList();
        List<ParsedEnclosingMethod> enclosingMethods = model
                .findAttributes(Attributes.enclosingMethod()).stream()
                .map(attribute -> new ParsedEnclosingMethod(
                        binaryName(attribute.enclosingClass().asInternalName()),
                        attribute.enclosingMethodName().map(value -> value.stringValue()),
                        attribute.enclosingMethodType().map(value -> value.stringValue())))
                .sorted()
                .toList();
        List<String> nestHosts = model.findAttributes(Attributes.nestHost()).stream()
                .map(attribute -> binaryName(attribute.nestHost().asInternalName()))
                .sorted()
                .toList();
        List<String> nestMembers = model.findAttributes(Attributes.nestMembers()).stream()
                .flatMap(attribute -> attribute.nestMembers().stream())
                .map(entry -> binaryName(entry.asInternalName()))
                .sorted()
                .toList();
        return new ParsedClassHeader(
                binaryName,
                model.flags().flagsMask(),
                model.majorVersion(),
                model.minorVersion(),
                model.isModuleInfo(),
                model.superclass().map(entry -> entry.asInternalName().replace('/', '.')),
                model.interfaces().stream()
                        .map(entry -> entry.asInternalName().replace('/', '.'))
                        .sorted()
                        .toList(),
                sourceFile,
                List.copyOf(members),
                List.copyOf(annotations),
                List.copyOf(annotationDefaults),
                signature(model),
                !model.findAttributes(Attributes.record()).isEmpty(),
                List.copyOf(recordComponents),
                !permittedSubclassAttributes.isEmpty(),
                permittedSubclasses,
                new ParsedNestingMetadata(
                        innerClasses, enclosingMethods, nestHosts, nestMembers));
    }

    private static String binaryName(String internalName) {
        return internalName.replace('/', '.');
    }

    private static Optional<String> signature(AttributedElement element) {
        return element.findAttributes(Attributes.signature()).stream()
                .map(attribute -> attribute.signature().stringValue())
                .sorted()
                .findFirst();
    }

    private static List<ParsedCodeAccess> codeAccesses(java.lang.classfile.CodeModel code) {
        List<ParsedCodeAccess> result = new ArrayList<>();
        int bytecodeOffset = 0;
        for (Object element : code) {
            if (!(element instanceof Instruction instruction)) continue;
            if (instruction instanceof FieldInstruction field) {
                result.add(fieldAccess(field, bytecodeOffset));
            } else if (instruction instanceof InvokeInstruction invocation) {
                result.add(methodAccess(invocation, bytecodeOffset));
            }
            bytecodeOffset += instruction.sizeInBytes();
        }
        return result.stream().sorted().toList();
    }

    private static ParsedCodeAccess fieldAccess(FieldInstruction field, int offset) {
        ParsedCodeAccess.Kind kind = switch (field.opcode()) {
            case GETFIELD, GETSTATIC -> ParsedCodeAccess.Kind.FIELD_READ;
            case PUTFIELD, PUTSTATIC -> ParsedCodeAccess.Kind.FIELD_WRITE;
            default -> throw new IllegalArgumentException("Unexpected field opcode: " + field.opcode());
        };
        return new ParsedCodeAccess(
                kind,
                ParsedCodeAccess.Opcode.valueOf(field.opcode().name()),
                ownerDescriptor(field.owner().asInternalName()),
                field.name().stringValue(),
                field.type().stringValue(),
                false,
                offset);
    }

    private static ParsedCodeAccess methodAccess(InvokeInstruction invocation, int offset) {
        Opcode opcode = invocation.opcode();
        ParsedCodeAccess.Kind kind = invocation.name().stringValue().equals("<init>")
                ? ParsedCodeAccess.Kind.CONSTRUCTOR_CALL
                : ParsedCodeAccess.Kind.METHOD_CALL;
        return new ParsedCodeAccess(
                kind,
                ParsedCodeAccess.Opcode.valueOf(opcode.name()),
                ownerDescriptor(invocation.owner().asInternalName()),
                invocation.name().stringValue(),
                invocation.type().stringValue(),
                invocation.isInterface(),
                offset);
    }

    private static String ownerDescriptor(String internalName) {
        return internalName.startsWith("[") ? internalName : "L" + internalName + ";";
    }

    private static void addDeclarationAnnotations(
            AttributedElement element,
            ParsedAnnotationOccurrence.Container container,
            String ownerName,
            String ownerDescriptor,
            List<ParsedAnnotationOccurrence> output) {
        element.findAttributes(Attributes.runtimeVisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .map(annotation -> declarationOccurrence(
                        ParsedAnnotationOccurrence.Visibility.RUNTIME_VISIBLE,
                        container,
                        ownerName,
                        ownerDescriptor,
                        annotation))
                .forEach(output::add);
        element.findAttributes(Attributes.runtimeInvisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .map(annotation -> declarationOccurrence(
                        ParsedAnnotationOccurrence.Visibility.RUNTIME_INVISIBLE,
                        container,
                        ownerName,
                        ownerDescriptor,
                        annotation))
                .forEach(output::add);
    }

    private static ParsedAnnotationOccurrence declarationOccurrence(
            ParsedAnnotationOccurrence.Visibility visibility,
            ParsedAnnotationOccurrence.Container container,
            String ownerName,
            String ownerDescriptor,
            Annotation annotation) {
        return new ParsedAnnotationOccurrence(
                visibility,
                container,
                ownerName,
                ownerDescriptor,
                ParsedAnnotationOccurrence.Site.DECLARATION,
                OptionalInt.empty(),
                Optional.empty(),
                annotation(annotation));
    }

    private static void addParameterAnnotations(
            AttributedElement method,
            String ownerName,
            String ownerDescriptor,
            List<ParsedAnnotationOccurrence> output) {
        method.findAttributes(Attributes.runtimeVisibleParameterAnnotations()).forEach(attribute ->
                addParameterAnnotations(
                        attribute.parameterAnnotations(),
                        ParsedAnnotationOccurrence.Visibility.RUNTIME_VISIBLE,
                        ownerName,
                        ownerDescriptor,
                        output));
        method.findAttributes(Attributes.runtimeInvisibleParameterAnnotations()).forEach(attribute ->
                addParameterAnnotations(
                        attribute.parameterAnnotations(),
                        ParsedAnnotationOccurrence.Visibility.RUNTIME_INVISIBLE,
                        ownerName,
                        ownerDescriptor,
                        output));
    }

    private static void addParameterAnnotations(
            List<List<Annotation>> parameterAnnotations,
            ParsedAnnotationOccurrence.Visibility visibility,
            String ownerName,
            String ownerDescriptor,
            List<ParsedAnnotationOccurrence> output) {
        for (int index = 0; index < parameterAnnotations.size(); index++) {
            int parameterIndex = index;
            parameterAnnotations.get(index).stream()
                    .map(annotation -> new ParsedAnnotationOccurrence(
                            visibility,
                            ParsedAnnotationOccurrence.Container.METHOD,
                            ownerName,
                            ownerDescriptor,
                            ParsedAnnotationOccurrence.Site.PARAMETER,
                            OptionalInt.of(parameterIndex),
                            Optional.empty(),
                            annotation(annotation)))
                    .forEach(output::add);
        }
    }

    private static void addTypeAnnotations(
            AttributedElement element,
            ParsedAnnotationOccurrence.Container container,
            String ownerName,
            String ownerDescriptor,
            CodeAttribute code,
            List<ParsedAnnotationOccurrence> output) {
        element.findAttributes(Attributes.runtimeVisibleTypeAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .map(annotation -> typeOccurrence(
                        ParsedAnnotationOccurrence.Visibility.RUNTIME_VISIBLE,
                        container,
                        ownerName,
                        ownerDescriptor,
                        code,
                        annotation))
                .forEach(output::add);
        element.findAttributes(Attributes.runtimeInvisibleTypeAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .map(annotation -> typeOccurrence(
                        ParsedAnnotationOccurrence.Visibility.RUNTIME_INVISIBLE,
                        container,
                        ownerName,
                        ownerDescriptor,
                        code,
                        annotation))
                .forEach(output::add);
    }

    private static ParsedAnnotationOccurrence typeOccurrence(
            ParsedAnnotationOccurrence.Visibility visibility,
            ParsedAnnotationOccurrence.Container container,
            String ownerName,
            String ownerDescriptor,
            CodeAttribute code,
            TypeAnnotation annotation) {
        return new ParsedAnnotationOccurrence(
                visibility,
                container,
                ownerName,
                ownerDescriptor,
                ParsedAnnotationOccurrence.Site.TYPE_USE,
                OptionalInt.empty(),
                Optional.of(typeUseTarget(annotation, code)),
                annotation(annotation.annotation()));
    }

    private static ParsedAnnotationOccurrence.TypeUseTarget typeUseTarget(
            TypeAnnotation annotation, CodeAttribute code) {
        TypeAnnotation.TargetInfo target = annotation.targetInfo();
        String info;
        if (target instanceof TypeAnnotation.TypeParameterTarget value) {
            info = "typeParameter=" + value.typeParameterIndex();
        } else if (target instanceof TypeAnnotation.SupertypeTarget value) {
            info = "supertype=" + value.supertypeIndex();
        } else if (target instanceof TypeAnnotation.TypeParameterBoundTarget value) {
            info = "typeParameter=" + value.typeParameterIndex() + ",bound=" + value.boundIndex();
        } else if (target instanceof TypeAnnotation.FormalParameterTarget value) {
            info = "parameter=" + value.formalParameterIndex();
        } else if (target instanceof TypeAnnotation.ThrowsTarget value) {
            info = "throws=" + value.throwsTargetIndex();
        } else if (target instanceof TypeAnnotation.LocalVarTarget value) {
            CodeAttribute requiredCode = requireCodeAttribute(code);
            info = value.table().stream()
                    .map(entry -> requiredCode.labelToBci(entry.startLabel()) + "-"
                            + requiredCode.labelToBci(entry.endLabel()) + "@" + entry.index())
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
        } else if (target instanceof TypeAnnotation.CatchTarget value) {
            info = "exception=" + value.exceptionTableIndex();
        } else if (target instanceof TypeAnnotation.TypeArgumentTarget value) {
            info = "offset=" + requireCodeAttribute(code).labelToBci(value.target())
                    + ",argument=" + value.typeArgumentIndex();
        } else if (target instanceof TypeAnnotation.OffsetTarget value) {
            info = "offset=" + requireCodeAttribute(code).labelToBci(value.target());
        } else {
            info = "";
        }
        List<String> path = annotation.targetPath().stream()
                .map(component -> component.typePathKind().name() + ":" + component.typeArgumentIndex())
                .toList();
        return new ParsedAnnotationOccurrence.TypeUseTarget(
                target.targetType().name(), info, path);
    }

    private static CodeAttribute requireCodeAttribute(Object code) {
        if (code instanceof CodeAttribute attribute) return attribute;
        throw new IllegalArgumentException("Code type annotation requires a concrete Code attribute");
    }

    private static ParsedAnnotation annotation(Annotation annotation) {
        return new ParsedAnnotation(
                annotation.className().stringValue(),
                annotation.elements().stream()
                        .map(element -> new ParsedAnnotationElement(
                                element.name().stringValue(), annotationValue(element.value())))
                        .toList());
    }

    private static ParsedAnnotationValue annotationValue(AnnotationValue value) {
        if (value instanceof AnnotationValue.OfBoolean scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.BOOLEAN, Boolean.toString(scalar.booleanValue()));
        }
        if (value instanceof AnnotationValue.OfByte scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.BYTE, Byte.toString(scalar.byteValue()));
        }
        if (value instanceof AnnotationValue.OfChar scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.CHAR, Integer.toString(scalar.charValue()));
        }
        if (value instanceof AnnotationValue.OfShort scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.SHORT, Short.toString(scalar.shortValue()));
        }
        if (value instanceof AnnotationValue.OfInt scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.INT, Integer.toString(scalar.intValue()));
        }
        if (value instanceof AnnotationValue.OfLong scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.LONG, Long.toString(scalar.longValue()));
        }
        if (value instanceof AnnotationValue.OfFloat scalar) {
            return scalar(
                    ParsedAnnotationValue.ScalarKind.FLOAT_RAW_BITS,
                    Integer.toUnsignedString(Float.floatToRawIntBits(scalar.floatValue())));
        }
        if (value instanceof AnnotationValue.OfDouble scalar) {
            return scalar(
                    ParsedAnnotationValue.ScalarKind.DOUBLE_RAW_BITS,
                    Long.toUnsignedString(Double.doubleToRawLongBits(scalar.doubleValue())));
        }
        if (value instanceof AnnotationValue.OfString scalar) {
            return scalar(ParsedAnnotationValue.ScalarKind.STRING, scalar.stringValue());
        }
        if (value instanceof AnnotationValue.OfEnum enumValue) {
            return new ParsedAnnotationValue.EnumValue(
                    enumValue.className().stringValue(), enumValue.constantName().stringValue());
        }
        if (value instanceof AnnotationValue.OfClass classValue) {
            return new ParsedAnnotationValue.ClassValue(classValue.className().stringValue());
        }
        if (value instanceof AnnotationValue.OfAnnotation nested) {
            return new ParsedAnnotationValue.NestedAnnotationValue(annotation(nested.annotation()));
        }
        if (value instanceof AnnotationValue.OfArray array) {
            return new ParsedAnnotationValue.ArrayValue(
                    array.values().stream().map(JdkClassFileParserBackend::annotationValue).toList());
        }
        throw new IllegalArgumentException("Unsupported annotation value tag: " + value.tag());
    }

    private static ParsedAnnotationValue scalar(
            ParsedAnnotationValue.ScalarKind kind, String value) {
        return new ParsedAnnotationValue.ScalarValue(kind, value);
    }

    private static void traverse(CompoundElement<?> compound) {
        for (Object candidate : compound) {
            ClassFileElement element = (ClassFileElement) candidate;
            touchAttributes(element);
            if (element instanceof Attribute<?> attribute) {
                attribute.attributeName().stringValue();
            }
            if (element instanceof CompoundElement<?> nested) {
                traverse(nested);
            }
        }
    }

    private static void touchAttributes(ClassFileElement element) {
        if (element instanceof AttributedElement attributed) {
            for (Attribute<?> attribute : attributed.attributes()) {
                attribute.attributeName().stringValue();
            }
        }
    }
}
