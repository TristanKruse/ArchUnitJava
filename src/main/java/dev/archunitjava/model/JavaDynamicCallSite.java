package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One unresolved invokedynamic call site with narrowly classified bootstrap evidence. */
public record JavaDynamicCallSite(
        JavaMemberSignature caller,
        String invocationName,
        JvmMethodType invocationType,
        JavaMethodHandle bootstrapMethod,
        List<JavaBootstrapArgument> bootstrapArguments,
        int originalBootstrapArgumentCount,
        boolean bootstrapArgumentsTruncated,
        JavaDynamicCallSiteKind kind,
        Optional<JavaMethodHandle> lambdaImplementation,
        List<JvmReferenceType> functionalInterfaces,
        BytecodeLocation location)
        implements Comparable<JavaDynamicCallSite> {
    public static final int MAXIMUM_BOOTSTRAP_ARGUMENTS = 256;

    public JavaDynamicCallSite {
        Objects.requireNonNull(caller, "caller");
        if (invocationName == null || invocationName.isBlank()) {
            throw new IllegalArgumentException("invocationName must not be blank");
        }
        Objects.requireNonNull(invocationType, "invocationType");
        Objects.requireNonNull(bootstrapMethod, "bootstrapMethod");
        Objects.requireNonNull(bootstrapArguments, "bootstrapArguments");
        bootstrapArguments = List.copyOf(bootstrapArguments);
        bootstrapArguments.forEach(value -> Objects.requireNonNull(value, "bootstrapArgument"));
        if (bootstrapArguments.size() > MAXIMUM_BOOTSTRAP_ARGUMENTS) {
            throw new IllegalArgumentException("Too many retained bootstrap arguments");
        }
        if (originalBootstrapArgumentCount < bootstrapArguments.size()) {
            throw new IllegalArgumentException("Original argument count is too small");
        }
        if (bootstrapArgumentsTruncated
                != (originalBootstrapArgumentCount > bootstrapArguments.size())) {
            throw new IllegalArgumentException("Bootstrap truncation flag is inconsistent");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lambdaImplementation, "lambdaImplementation");
        Objects.requireNonNull(functionalInterfaces, "functionalInterfaces");
        functionalInterfaces = functionalInterfaces.stream()
                .map(value -> Objects.requireNonNull(value, "functionalInterface"))
                .distinct()
                .sorted(java.util.Comparator.comparing(JvmReferenceType::binaryName))
                .toList();
        if (kind == JavaDynamicCallSiteKind.LAMBDA_METAFACTORY
                != (lambdaImplementation.isPresent() && !functionalInterfaces.isEmpty())) {
            throw new IllegalArgumentException("Only a proven lambda has implementation/interface evidence");
        }
        Objects.requireNonNull(location, "location");
    }

    @Override
    public int compareTo(JavaDynamicCallSite other) {
        int result = caller.compareTo(other.caller);
        if (result != 0) return result;
        result = Integer.compare(location.bytecodeOffset(), other.location.bytecodeOffset());
        if (result != 0) return result;
        result = invocationName.compareTo(other.invocationName);
        return result != 0 ? result : invocationType.descriptor()
                .compareTo(other.invocationType.descriptor());
    }
}
