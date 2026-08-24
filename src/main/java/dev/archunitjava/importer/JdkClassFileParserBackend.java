package dev.archunitjava.importer;

import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CompoundElement;

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
        return new ParsedClassHeader(
                binaryName,
                model.flags().flagsMask(),
                model.majorVersion(),
                model.minorVersion(),
                model.isModuleInfo());
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
