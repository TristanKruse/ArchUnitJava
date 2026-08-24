package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class JvmDescriptorsTest {
    @Test
    void everyPrimitiveFieldTypeRoundTrips() {
        for (JvmPrimitiveType primitive : JvmPrimitiveType.values()) {
            JvmType parsed = JvmDescriptors.parseField(primitive.descriptor());
            assertEquals(primitive, parsed);
            assertEquals(primitive.descriptor(), parsed.descriptor());
        }
    }

    @Test
    void referencesRetainCanonicalBinaryNames() {
        JvmReferenceType type = assertInstanceOf(
                JvmReferenceType.class, JvmDescriptors.parseField("Ljava/util/Map$Entry;"));

        assertEquals("java.util.Map$Entry", type.binaryName());
        assertEquals("Ljava/util/Map$Entry;", type.descriptor());
    }

    @Test
    void multidimensionalArraysRoundTripWithoutNestedArrayObjects() {
        JvmArrayType primitive = assertInstanceOf(
                JvmArrayType.class, JvmDescriptors.parseField("[[[I"));
        JvmArrayType reference = assertInstanceOf(
                JvmArrayType.class, JvmDescriptors.parseField("[[Ljava/lang/String;"));

        assertEquals(3, primitive.dimensions());
        assertEquals(JvmPrimitiveType.INT, primitive.elementType());
        assertEquals("int[][][]", primitive.displayName());
        assertEquals("[[[I", primitive.descriptor());
        assertEquals(new JvmReferenceType("java.lang.String"), reference.elementType());
        assertEquals("[[Ljava/lang/String;", reference.descriptor());
    }

    @Test
    void methodParametersKeepExactOrderAndVoidIsASeparateReturnType() {
        JvmMethodType method = JvmDescriptors.parseMethod("(IJ[Ljava/lang/String;Z)V");

        assertEquals(
                List.of(
                        JvmPrimitiveType.INT,
                        JvmPrimitiveType.LONG,
                        new JvmArrayType(new JvmReferenceType("java.lang.String"), 1),
                        JvmPrimitiveType.BOOLEAN),
                method.parameterTypes());
        assertEquals(JvmVoidType.VOID, method.returnType());
        assertEquals("(IJ[Ljava/lang/String;Z)V", method.descriptor());
        assertThrows(UnsupportedOperationException.class, () -> method.parameterTypes().clear());
    }

    @Test
    void nonVoidMethodReturnsRoundTrip() {
        JvmMethodType method = JvmDescriptors.parseMethod("()[[D");

        assertEquals(new JvmArrayType(JvmPrimitiveType.DOUBLE, 2), method.returnType());
        assertEquals("()[[D", method.descriptor());
    }

    @Test
    void voidIsRejectedForFieldsParametersAndArrays() {
        assertInvalid("V", false);
        assertInvalid("(V)V", true);
        assertInvalid("[V", false);
    }

    @Test
    void malformedDescriptorsFailLocallyWithOffsets() {
        List<String> invalidFields = List.of("", "Q", "Igarbage", "Ljava/lang/String", "L/foo;", "Lfoo//Bar;");
        for (String descriptor : invalidFields) assertInvalid(descriptor, false);

        List<String> invalidMethods = List.of("", "I)V", "(I", "(I)", "(I)Vextra", "([)V");
        for (String descriptor : invalidMethods) assertInvalid(descriptor, true);

        InvalidJvmDescriptorException failure = assertThrows(
                InvalidJvmDescriptorException.class,
                () -> JvmDescriptors.parseField("Ljava.lang.String;"));
        assertEquals(1, failure.offset());
        assertEquals("Ljava.lang.String;", failure.descriptor());
    }

    @Test
    void arrayDimensionLimitIsEnforcedExactly() {
        String maximum = "[".repeat(255) + "I";
        assertEquals(maximum, JvmDescriptors.parseField(maximum).descriptor());

        InvalidJvmDescriptorException failure = assertThrows(
                InvalidJvmDescriptorException.class,
                () -> JvmDescriptors.parseField("[".repeat(256) + "I"));
        assertEquals(255, failure.offset());
    }

    @Test
    void valueConstructorsRejectNonCanonicalStates() {
        assertThrows(IllegalArgumentException.class,
                () -> new JvmArrayType(JvmVoidType.VOID, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new JvmArrayType(new JvmArrayType(JvmPrimitiveType.INT, 1), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new JvmMethodType(List.of(JvmVoidType.VOID), JvmVoidType.VOID));
        assertThrows(IllegalArgumentException.class,
                () -> new JvmReferenceType("java/lang/String"));
    }

    private static void assertInvalid(String descriptor, boolean method) {
        assertThrows(
                InvalidJvmDescriptorException.class,
                () -> {
                    if (method) JvmDescriptors.parseMethod(descriptor);
                    else JvmDescriptors.parseField(descriptor);
                });
    }
}
