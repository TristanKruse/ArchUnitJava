package dev.archunitjava.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectUniqueId;
import static org.junit.platform.launcher.TagFilter.includeTags;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.rules.ArchitectureRule;
import dev.archunitjava.rules.ArchitectureRules;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.testkit.engine.EngineTestKit;

final class ArchitectureTestEngineTest {
    @Test
    void serviceRegistrationDiscoveryTagsUniqueIdsAndFailuresFollowPlatformContracts() {
        assertTrue(ServiceLoader.load(TestEngine.class).stream()
                .anyMatch(provider -> provider.type().equals(ArchitectureTestEngine.class)));

        var results = EngineTestKit.engine(ArchitectureTestEngine.ENGINE_ID)
                .selectors(selectClass(PassingAndFailingFixture.class))
                .execute();

        results.testEvents().assertStatistics(statistics ->
                statistics.started(2).succeeded(1).failed(1));
        var successful = results.testEvents().succeeded().list().getFirst().getTestDescriptor();
        assertEquals("passing architecture", successful.getDisplayName());
        assertEquals(
                "[engine:archunitjava]/[class:"
                        + PassingAndFailingFixture.class.getName() + "]/[method:passing]",
                successful.getUniqueId().toString());

        var tagged = EngineTestKit.engine(ArchitectureTestEngine.ENGINE_ID)
                .selectors(selectClass(PassingAndFailingFixture.class))
                .filters(includeTags("fast"))
                .execute();
        tagged.testEvents().assertStatistics(statistics ->
                statistics.started(1).succeeded(1).failed(0));
    }

    @Test
    void methodAndUniqueIdSelectorsNarrowDiscoveryDeterministically() {
        EngineTestKit.engine(new ArchitectureTestEngine())
                .selectors(selectMethod(PassingAndFailingFixture.class, "passing"))
                .execute().testEvents()
                .assertStatistics(statistics -> statistics.started(1).succeeded(1));

        UniqueId id = UniqueId.forEngine(ArchitectureTestEngine.ENGINE_ID)
                .append("class", PassingAndFailingFixture.class.getName())
                .append("method", "passing");
        EngineTestKit.engine(new ArchitectureTestEngine())
                .selectors(selectUniqueId(id))
                .execute().testEvents()
                .assertStatistics(statistics -> statistics.started(1).succeeded(1));
    }

    @Test
    @Timeout(10)
    void executionScopedImportCacheIsParallelSafeCanBeDisabledAndDoesNotLeak() {
        ParallelCacheFixture.reset();
        EngineTestKit.engine(new ArchitectureTestEngine())
                .selectors(selectClass(ParallelCacheFixture.class))
                .configurationParameter(ArchitectureTestEngine.PARALLEL_ENABLED, "true")
                .execute().testEvents()
                .assertStatistics(statistics -> statistics.started(2).succeeded(2));
        assertEquals(1, ParallelCacheFixture.IMPORTS.get());

        ParallelCacheFixture.reset();
        EngineTestKit.engine(new ArchitectureTestEngine())
                .selectors(selectClass(ParallelCacheFixture.class))
                .configurationParameter(ArchitectureTestEngine.CACHE_ENABLED, "false")
                .configurationParameter(ArchitectureTestEngine.PARALLEL_ENABLED, "true")
                .execute().testEvents()
                .assertStatistics(statistics -> statistics.started(2).succeeded(2));
        assertEquals(2, ParallelCacheFixture.IMPORTS.get());

        ParallelCacheFixture.reset();
        EngineTestKit.engine(new ArchitectureTestEngine())
                .selectors(selectClass(ParallelCacheFixture.class))
                .configurationParameter(ArchitectureTestEngine.PARALLEL_ENABLED, "true")
                .execute();
        EngineTestKit.engine(new ArchitectureTestEngine())
                .selectors(selectClass(ParallelCacheFixture.class))
                .configurationParameter(ArchitectureTestEngine.PARALLEL_ENABLED, "true")
                .execute();
        assertEquals(2, ParallelCacheFixture.IMPORTS.get());
        assertThrows(IllegalStateException.class,
                () -> ArchitectureEngineImports.cached("outside", Object::new));
    }

    public static final class PassingAndFailingFixture {
        @ArchitectureTest(value = "passing architecture", tags = {"fast", "architecture"})
        public static ArchitectureRule passing() {
            return ArchitectureRules.define(
                    "engine.pass", "passing architecture",
                    (metadata, options) -> RuleResult.passed(metadata));
        }

        @ArchitectureTest(value = "failing architecture", tags = "slow")
        public static ArchitectureRule failing() {
            Violation violation = new Violation(
                    new ViolationId("engine.failure"), "dependency.forbidden", Severity.ERROR,
                    List.of(new ViolationSubject(
                            "origin", TypeId.ofBinaryName("com.example.Bad"))),
                    List.of(DependencyEvidence.at(
                            LocationId.ofResourcePath("classes/com/example/Bad.class"))),
                    Map.of());
            return ArchitectureRules.define(
                    "engine.fail", "failing architecture",
                    (metadata, options) -> RuleResult.failed(
                            metadata, List.of(violation), List.of()));
        }
    }

    public static final class ParallelCacheFixture {
        private static final AtomicInteger IMPORTS = new AtomicInteger();
        private static volatile CountDownLatch concurrentMethods = new CountDownLatch(2);

        static void reset() {
            IMPORTS.set(0);
            concurrentMethods = new CountDownLatch(2);
        }

        @ArchitectureTest("parallel one")
        public static ArchitectureRule one() {
            return cachedPassing("parallel.one");
        }

        @ArchitectureTest("parallel two")
        public static ArchitectureRule two() {
            return cachedPassing("parallel.two");
        }

        private static ArchitectureRule cachedPassing(String id) {
            ArchitectureEngineImports.cached("shared-model", () -> {
                IMPORTS.incrementAndGet();
                return "immutable-model";
            });
            concurrentMethods.countDown();
            try {
                if (!concurrentMethods.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Architecture methods did not execute in parallel");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while verifying parallel execution", failure);
            }
            return ArchitectureRules.define(
                    id, id, (metadata, options) -> RuleResult.passed(metadata));
        }
    }
}
