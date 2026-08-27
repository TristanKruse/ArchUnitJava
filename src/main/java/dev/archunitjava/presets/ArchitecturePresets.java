package dev.archunitjava.presets;

import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.selector.TypeSelector;
import java.util.List;
import java.util.Objects;

/** Explicit-selector factories for common architecture dependency directions. */
public final class ArchitecturePresets {
    private ArchitecturePresets() {}

    public static ArchitecturePreset clean(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector entities,
            TypeSelector useCases,
            TypeSelector interfaceAdapters,
            TypeSelector frameworks) {
        return ArchitecturePreset.create(
                "clean",
                model,
                graph,
                List.of(
                        layer("entities", entities),
                        layer("use-cases", useCases),
                        layer("interface-adapters", interfaceAdapters),
                        layer("frameworks", frameworks)),
                inwardRules(List.of(
                        "entities", "use-cases", "interface-adapters", "frameworks")));
    }

    public static ArchitecturePreset onion(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector domain,
            TypeSelector application,
            TypeSelector infrastructure,
            TypeSelector presentation) {
        return ArchitecturePreset.create(
                "onion",
                model,
                graph,
                List.of(
                        layer("domain", domain),
                        layer("application", application),
                        layer("infrastructure", infrastructure),
                        layer("presentation", presentation)),
                inwardRules(List.of(
                        "domain", "application", "infrastructure", "presentation")));
    }

    public static ArchitecturePreset hexagonal(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector domain,
            TypeSelector application,
            TypeSelector inboundAdapters,
            TypeSelector outboundAdapters) {
        return ArchitecturePreset.create(
                "hexagonal",
                model,
                graph,
                List.of(
                        layer("domain", domain),
                        layer("application", application),
                        layer("inbound-adapters", inboundAdapters),
                        layer("outbound-adapters", outboundAdapters)),
                List.of(
                        PresetRule.exactlyOneLayer("coverage"),
                        PresetRule.mayOnlyAccess("domain-dependencies", "domain", List.of()),
                        PresetRule.mayOnlyAccess(
                                "application-dependencies", "application", List.of("domain")),
                        PresetRule.mayOnlyAccess(
                                "inbound-adapter-dependencies",
                                "inbound-adapters",
                                List.of("application", "domain")),
                        PresetRule.mayOnlyAccess(
                                "outbound-adapter-dependencies",
                                "outbound-adapters",
                                List.of("application", "domain"))));
    }

    public static ArchitecturePreset providerSdk(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector consumerApi,
            TypeSelector providerSpi,
            TypeSelector internals,
            TypeSelector providerImplementations) {
        return ArchitecturePreset.create(
                "provider-sdk",
                model,
                graph,
                List.of(
                        layer("consumer-api", consumerApi),
                        layer("provider-spi", providerSpi),
                        layer("internals", internals),
                        layer("provider-implementations", providerImplementations)),
                List.of(
                        PresetRule.exactlyOneLayer("coverage"),
                        PresetRule.mayOnlyAccess(
                                "consumer-api-dependencies", "consumer-api", List.of()),
                        PresetRule.mayOnlyAccess(
                                "provider-spi-dependencies",
                                "provider-spi",
                                List.of("consumer-api")),
                        PresetRule.mayOnlyAccess(
                                "internal-dependencies",
                                "internals",
                                List.of("consumer-api", "provider-spi")),
                        PresetRule.mayOnlyAccess(
                                "provider-implementation-dependencies",
                                "provider-implementations",
                                List.of("consumer-api", "provider-spi")),
                        PresetRule.publicInterface(
                                "provider-public-interface",
                                List.of("provider-implementations"),
                                List.of("consumer-api", "provider-spi"))));
    }

    private static List<PresetRule> inwardRules(List<String> insideToOutside) {
        java.util.ArrayList<PresetRule> rules = new java.util.ArrayList<>();
        rules.add(PresetRule.exactlyOneLayer("coverage"));
        for (int index = 0; index < insideToOutside.size(); index++) {
            String subject = insideToOutside.get(index);
            rules.add(PresetRule.mayOnlyAccess(
                    subject + "-dependencies", subject, insideToOutside.subList(0, index)));
        }
        return List.copyOf(rules);
    }

    private static PresetLayer layer(String name, TypeSelector selector) {
        return PresetLayer.named(name, Objects.requireNonNull(selector, name));
    }
}
