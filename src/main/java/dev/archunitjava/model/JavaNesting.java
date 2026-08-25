package dev.archunitjava.model;

import dev.archunitjava.importer.ParsedNestingMetadata;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Lexical and JVM nestmate evidence kept as separate, immutable concepts. */
public record JavaNesting(
        JavaNestingKind kind,
        Optional<JavaTypeName> lexicalOwner,
        Optional<String> simpleName,
        Optional<JavaEnclosingDeclaration> enclosingDeclaration,
        List<JavaInnerClassEvidence> innerClassTable,
        JavaTypeName nestHost,
        List<JavaTypeName> declaredNestMembers,
        List<NestingDiagnostic> diagnostics,
        boolean diagnosticsTruncated) {
    public static final int MAXIMUM_DIAGNOSTICS = 256;

    public JavaNesting {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lexicalOwner, "lexicalOwner");
        Objects.requireNonNull(simpleName, "simpleName");
        simpleName = simpleName.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("simpleName must not be blank");
            return value;
        });
        Objects.requireNonNull(enclosingDeclaration, "enclosingDeclaration");
        Objects.requireNonNull(innerClassTable, "innerClassTable");
        innerClassTable = innerClassTable.stream()
                .map(value -> Objects.requireNonNull(value, "innerClassEvidence"))
                .sorted()
                .toList();
        Objects.requireNonNull(nestHost, "nestHost");
        Objects.requireNonNull(declaredNestMembers, "declaredNestMembers");
        declaredNestMembers = declaredNestMembers.stream()
                .map(value -> Objects.requireNonNull(value, "declaredNestMember"))
                .sorted()
                .toList();
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream()
                .map(value -> Objects.requireNonNull(value, "diagnostic"))
                .sorted()
                .toList();
        if (diagnostics.size() > MAXIMUM_DIAGNOSTICS) {
            throw new IllegalArgumentException("Too many nesting diagnostics");
        }
    }

    static JavaNesting from(JavaTypeName subject, ParsedNestingMetadata parsed) {
        List<JavaInnerClassEvidence> table = parsed.innerClasses().stream()
                .map(value -> new JavaInnerClassEvidence(
                        new JavaTypeName(value.innerBinaryName()),
                        value.outerBinaryName().map(JavaTypeName::new),
                        value.innerSimpleName(),
                        value.accessFlags()))
                .sorted()
                .toList();
        List<JavaInnerClassEvidence> selfEntries = table.stream()
                .filter(value -> value.innerType().equals(subject))
                .distinct()
                .toList();
        List<JavaEnclosingDeclaration> enclosing = parsed.enclosingMethods().stream()
                .map(value -> new JavaEnclosingDeclaration(
                        new JavaTypeName(value.enclosingClassBinaryName()),
                        value.methodName(),
                        value.methodDescriptor()))
                .distinct()
                .sorted()
                .toList();
        TreeSet<NestingDiagnostic> diagnosticSet = new TreeSet<>();
        if (selfEntries.size() > 1) {
            diagnosticSet.add(diagnostic(
                    NestingDiagnosticCode.CONFLICTING_INNER_CLASS_EVIDENCE,
                    Integer.toString(selfEntries.size())));
        }
        if (enclosing.size() > 1) {
            diagnosticSet.add(diagnostic(
                    NestingDiagnosticCode.CONFLICTING_ENCLOSING_METHOD_EVIDENCE,
                    Integer.toString(enclosing.size())));
        }

        Optional<JavaInnerClassEvidence> self = selfEntries.size() == 1
                ? Optional.of(selfEntries.getFirst())
                : Optional.empty();
        Optional<JavaEnclosingDeclaration> enclosingDeclaration = enclosing.size() == 1
                ? Optional.of(enclosing.getFirst())
                : Optional.empty();
        Optional<JavaTypeName> innerOwner = self.flatMap(JavaInnerClassEvidence::outerType);
        Optional<JavaTypeName> enclosingOwner = enclosingDeclaration.map(JavaEnclosingDeclaration::owner);
        Optional<JavaTypeName> lexicalOwner;
        if (innerOwner.isPresent() && enclosingOwner.isPresent()
                && !innerOwner.equals(enclosingOwner)) {
            diagnosticSet.add(diagnostic(
                    NestingDiagnosticCode.CONFLICTING_LEXICAL_OWNER_EVIDENCE,
                    innerOwner.orElseThrow().binaryName() + "|" + enclosingOwner.orElseThrow().binaryName()));
            lexicalOwner = Optional.empty();
        } else {
            lexicalOwner = enclosingOwner.or(() -> innerOwner);
        }

        JavaNestingKind kind;
        if (selfEntries.size() > 1 || enclosing.size() > 1) {
            kind = JavaNestingKind.UNKNOWN;
        } else if (self.isEmpty() && enclosingDeclaration.isEmpty()) {
            kind = JavaNestingKind.TOP_LEVEL;
        } else if (self.isEmpty()) {
            kind = JavaNestingKind.UNKNOWN;
            diagnosticSet.add(diagnostic(NestingDiagnosticCode.INCOMPLETE_LEXICAL_EVIDENCE,
                    "missing-inner-class-entry"));
        } else if (self.orElseThrow().simpleName().isEmpty()) {
            kind = JavaNestingKind.ANONYMOUS;
            if (enclosingDeclaration.isEmpty()) {
                diagnosticSet.add(diagnostic(NestingDiagnosticCode.INCOMPLETE_LEXICAL_EVIDENCE,
                        "anonymous-without-enclosing-method"));
            }
        } else if (enclosingDeclaration.isPresent()) {
            kind = JavaNestingKind.LOCAL;
        } else if (innerOwner.isPresent()) {
            kind = JavaNestingKind.MEMBER;
        } else {
            kind = JavaNestingKind.UNKNOWN;
            diagnosticSet.add(diagnostic(NestingDiagnosticCode.INCOMPLETE_LEXICAL_EVIDENCE,
                    "named-inner-without-owner"));
        }

        List<JavaTypeName> hosts = parsed.nestHostBinaryNames().stream()
                .map(JavaTypeName::new)
                .distinct()
                .sorted()
                .toList();
        JavaTypeName nestHost = hosts.isEmpty() ? subject : hosts.getFirst();
        if (hosts.size() > 1) {
            diagnosticSet.add(diagnostic(
                    NestingDiagnosticCode.CONFLICTING_NEST_HOST_EVIDENCE,
                    Integer.toString(hosts.size())));
        }
        if (!hosts.isEmpty() && nestHost.equals(subject)) {
            diagnosticSet.add(diagnostic(NestingDiagnosticCode.SELF_NEST_HOST, subject.binaryName()));
        }
        List<JavaTypeName> rawMembers = parsed.nestMemberBinaryNames().stream()
                .map(JavaTypeName::new)
                .sorted()
                .toList();
        LinkedHashSet<JavaTypeName> uniqueMembers = new LinkedHashSet<>();
        for (JavaTypeName member : rawMembers) {
            if (!uniqueMembers.add(member)) {
                diagnosticSet.add(diagnostic(
                        NestingDiagnosticCode.DUPLICATE_NEST_MEMBER, member.binaryName()));
            }
        }
        if (!hosts.isEmpty() && !uniqueMembers.isEmpty()) {
            diagnosticSet.add(diagnostic(
                    NestingDiagnosticCode.NEST_HOST_AND_MEMBERS_DECLARED, subject.binaryName()));
        }
        boolean truncated = diagnosticSet.size() > MAXIMUM_DIAGNOSTICS;
        List<NestingDiagnostic> diagnostics = diagnosticSet.stream()
                .limit(MAXIMUM_DIAGNOSTICS)
                .toList();
        return new JavaNesting(
                kind,
                lexicalOwner,
                self.flatMap(JavaInnerClassEvidence::simpleName),
                enclosingDeclaration,
                table,
                nestHost,
                List.copyOf(uniqueMembers),
                diagnostics,
                truncated);
    }

    private static NestingDiagnostic diagnostic(NestingDiagnosticCode code, String evidence) {
        return new NestingDiagnostic(code, evidence);
    }
}
