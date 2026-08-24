package dev.archunitjava.model;

import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ParsedClassFile;
import java.lang.classfile.ClassFile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        EnumSet<JavaModifier> modifiers = EnumSet.noneOf(JavaModifier.class);
        if (has(flags, ClassFile.ACC_PUBLIC)) modifiers.add(JavaModifier.PUBLIC);
        if (has(flags, ClassFile.ACC_ABSTRACT)) modifiers.add(JavaModifier.ABSTRACT);
        if (has(flags, ClassFile.ACC_FINAL)) modifiers.add(JavaModifier.FINAL);
        if (has(flags, ClassFile.ACC_SYNTHETIC)) modifiers.add(JavaModifier.SYNTHETIC);
        types.add(new JavaType(
                name,
                kind(flags),
                modifiers,
                flags,
                flags & ~KNOWN_CLASS_FLAGS,
                new ClassFileVersion(parsed.majorVersion(), parsed.minorVersion()),
                parsed.resourceName(),
                parsed.origin(),
                parsed.precedence()));
    }

    private static JavaTypeKind kind(int flags) {
        if (has(flags, ClassFile.ACC_ANNOTATION)) return JavaTypeKind.ANNOTATION;
        if (has(flags, ClassFile.ACC_ENUM)) return JavaTypeKind.ENUM;
        if (has(flags, ClassFile.ACC_INTERFACE)) return JavaTypeKind.INTERFACE;
        return JavaTypeKind.CLASS;
    }

    private static boolean has(int flags, int expected) {
        return (flags & expected) != 0;
    }
}
