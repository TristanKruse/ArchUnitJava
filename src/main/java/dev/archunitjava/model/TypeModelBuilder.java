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
import dev.archunitjava.importer.ParsedLineNumber;

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
        JavaTypeKind typeKind = kind(flags);
        var superclass = typeKind == JavaTypeKind.INTERFACE || typeKind == JavaTypeKind.ANNOTATION
                ? java.util.Optional.<JvmReferenceType>empty()
                : parsed.superclassBinaryName().map(JvmReferenceType::new);
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
                parsed.interfaceBinaryNames().stream().map(JvmReferenceType::new).toList(),
                members(name, parsed.declaredMembers(), location)));
    }

    private static List<JavaMember> members(
            JavaTypeName owner,
            List<ParsedMember> parsedMembers,
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
            members.add(new JavaMember(
                    new JavaMemberSignature(owner, parsed.name(), parsed.descriptor()),
                    kind,
                    modifiers,
                    flags,
                    flags & ~knownFlags,
                    parsed.hasCode(),
                    location,
                    lineNumbers(parsed.lineNumbers())));
        }
        return List.copyOf(members);
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
