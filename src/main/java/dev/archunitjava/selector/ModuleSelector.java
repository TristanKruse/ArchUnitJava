package dev.archunitjava.selector;

import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaModuleKind;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable selection of explicit, automatic, or unnamed JPMS module identities. */
public final class ModuleSelector {
    private final SelectorDescription description;
    private final Matcher matcher;

    private ModuleSelector(SelectorDescription description, Matcher matcher) {
        this.description = Objects.requireNonNull(description, "description");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public static ModuleSelector all() {
        return new ModuleSelector(new SelectorDescription("all modules"), module -> true);
    }

    public static ModuleSelector none() {
        return new ModuleSelector(new SelectorDescription("no modules"), module -> false);
    }

    public static ModuleSelector kind(JavaModuleKind kind) {
        JavaModuleKind value = Objects.requireNonNull(kind, "kind");
        return new ModuleSelector(
                new SelectorDescription("modules of kind " + value.name()),
                module -> module.identity().kind() == value);
    }

    public static ModuleSelector name(JavaPattern pattern) {
        JavaPattern value = Objects.requireNonNull(pattern, "pattern");
        if (value.description().domain() != PatternDomain.QUALIFIED_NAME) {
            throw new IllegalArgumentException("Module name requires QUALIFIED_NAME pattern domain");
        }
        return new ModuleSelector(
                new SelectorDescription("module name matches " + value.description()),
                module -> module.identity().name().filter(value::matches).isPresent());
    }

    public static ModuleSelector allOf(ModuleSelector... selectors) {
        List<ModuleSelector> values = List.of(Objects.requireNonNull(selectors, "selectors").clone())
                .stream().sorted(java.util.Comparator.comparing(value -> value.description.text()))
                .toList();
        if (values.isEmpty()) throw new IllegalArgumentException("AND group must not be empty");
        return new ModuleSelector(
                new SelectorDescription("AND " + values.stream()
                        .map(value -> value.description.text()).toList()),
                module -> values.stream().allMatch(value -> value.matcher.matches(module)));
    }

    public SelectorDescription description() {
        return description;
    }

    public ModuleSelection selectFrom(TypeModelResult model) {
        Objects.requireNonNull(model, "model");
        return select(model.modules(), model);
    }

    private ModuleSelection select(Collection<JavaModule> modules, TypeModelResult model) {
        List<JavaModule> candidates = modules.stream().distinct().sorted().toList();
        return new ModuleSelection(
                description,
                candidates.size(),
                candidates.stream().filter(matcher::matches).toList(),
                model.classFileDiagnostics(),
                model.diagnostics());
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(JavaModule module);
    }
}
