package dev.archunitjava.model;

import java.util.Objects;

/** Exact declaration that owns dependency evidence. */
public sealed interface DeclarationDependencyOwner extends Comparable<DeclarationDependencyOwner>
        permits DeclarationDependencyOwner.MemberOwner,
                DeclarationDependencyOwner.RecordComponentOwner,
                DeclarationDependencyOwner.TypeOwner {
    JavaTypeName type();

    String stableKey();

    @Override
    default int compareTo(DeclarationDependencyOwner other) {
        return stableKey().compareTo(other.stableKey());
    }

    record TypeOwner(JavaTypeName type) implements DeclarationDependencyOwner {
        public TypeOwner {
            Objects.requireNonNull(type, "type");
        }

        @Override
        public String stableKey() {
            return "type:" + type.binaryName();
        }
    }

    record MemberOwner(JavaMemberSignature member) implements DeclarationDependencyOwner {
        public MemberOwner {
            Objects.requireNonNull(member, "member");
        }

        @Override
        public JavaTypeName type() {
            return member.owner();
        }

        @Override
        public String stableKey() {
            return "member:" + member.stableKey();
        }
    }

    record RecordComponentOwner(JavaTypeName type, String name, String descriptor)
            implements DeclarationDependencyOwner {
        public RecordComponentOwner {
            Objects.requireNonNull(type, "type");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (descriptor == null || descriptor.isBlank()) {
                throw new IllegalArgumentException("descriptor must not be blank");
            }
            JvmDescriptors.parseField(descriptor);
        }

        @Override
        public String stableKey() {
            return "record-component:" + type.binaryName() + "#" + name + descriptor;
        }
    }
}
