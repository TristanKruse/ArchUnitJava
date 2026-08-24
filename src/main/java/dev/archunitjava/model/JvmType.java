package dev.archunitjava.model;

/** Lossless library-owned representation of a JVM field or return type. */
public sealed interface JvmType
        permits JvmArrayType, JvmPrimitiveType, JvmReferenceType, JvmVoidType {
    String descriptor();

    String displayName();
}
