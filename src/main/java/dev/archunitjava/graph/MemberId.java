package dev.archunitjava.graph;

import java.util.Objects;

/** A field or method identity consisting of owner, JVM unqualified name, and descriptor. */
public record MemberId(TypeId owner, String name, String descriptor) implements StableId {
    public MemberId {
        Objects.requireNonNull(owner, "owner");
        if (name == null || name.isEmpty() || hasForbiddenNameCharacter(name)) {
            throw new IllegalArgumentException("Invalid member name: " + name);
        }
        boolean method = DescriptorGrammar.isMethodDescriptor(descriptor);
        boolean field = DescriptorGrammar.isFieldDescriptor(descriptor);
        if (!method && !field) throw new IllegalArgumentException("Invalid member descriptor: " + descriptor);
        if (method && (name.indexOf('<') >= 0 || name.indexOf('>') >= 0)
                && !name.equals("<init>") && !name.equals("<clinit>")) {
            throw new IllegalArgumentException("Invalid method name: " + name);
        }
        if (name.equals("<init>") && !descriptor.endsWith(")V")) {
            throw new IllegalArgumentException("Constructor must return void");
        }
        if (name.equals("<clinit>") && !descriptor.equals("()V")) {
            throw new IllegalArgumentException("Class initializer descriptor must be ()V");
        }
    }

    public static MemberId of(TypeId owner, String name, String descriptor) {
        return new MemberId(owner, name, descriptor);
    }

    private static boolean hasForbiddenNameCharacter(String name) {
        return name.indexOf('.') >= 0 || name.indexOf(';') >= 0 || name.indexOf('[') >= 0
                || name.indexOf('/') >= 0;
    }

    @Override public String stableKey() {
        return "member:" + owner.binaryName() + "#" + name + descriptor;
    }
}
