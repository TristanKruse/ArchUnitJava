package dev.archunitjava.importer;

import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.Attributes;
import java.util.ArrayList;
import java.util.List;

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
        model.fields().forEach(field -> members.add(new ParsedMember(
                ParsedMember.Kind.FIELD,
                field.fieldName().stringValue(),
                field.fieldType().stringValue(),
                field.flags().flagsMask(),
                false)));
        model.methods().forEach(method -> {
            var code = method.code();
            List<ParsedLineNumber> lines = code.stream()
                    .flatMap(value -> value.findAttributes(Attributes.lineNumberTable()).stream())
                    .flatMap(attribute -> attribute.lineNumbers().stream())
                    .map(line -> new ParsedLineNumber(line.startPc(), line.lineNumber()))
                    .sorted()
                    .toList();
            members.add(new ParsedMember(
                    ParsedMember.Kind.METHOD,
                    method.methodName().stringValue(),
                    method.methodType().stringValue(),
                    method.flags().flagsMask(),
                    code.isPresent(),
                    lines));
        });
        var sourceFile = model.findAttributes(Attributes.sourceFile()).stream()
                .map(attribute -> attribute.sourceFile().stringValue())
                .sorted()
                .findFirst();
        return new ParsedClassHeader(
                binaryName,
                model.flags().flagsMask(),
                model.majorVersion(),
                model.minorVersion(),
                model.isModuleInfo(),
                sourceFile,
                List.copyOf(members));
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
