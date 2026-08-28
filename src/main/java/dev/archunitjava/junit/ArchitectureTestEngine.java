package dev.archunitjava.junit;

import dev.archunitjava.rules.ArchitectureRule;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;

/**
 * Optional JUnit Platform engine for {@link ArchitectureTest} methods.
 *
 * <p>Discovery deliberately accepts only explicit class, method, and unique-ID selectors. It does
 * not scan packages or class-path roots because doing so would load unrelated analyzed classes.
 */
public final class ArchitectureTestEngine implements TestEngine {
    public static final String ENGINE_ID = "archunitjava";
    public static final String CACHE_ENABLED = "archunitjava.junit.cache.enabled";
    public static final String PARALLEL_ENABLED = "archunitjava.junit.parallel.enabled";

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        Objects.requireNonNull(request, "request");
        EngineDescriptor root = new EngineDescriptor(uniqueId, "ArchUnitJava");
        Set<Class<?>> candidates = candidates(request);
        request.getSelectorsByType(MethodSelector.class).stream()
                .map(MethodSelector::getJavaMethod)
                .filter(method -> method.isAnnotationPresent(ArchitectureTest.class))
                .forEach(method -> candidates.add(method.getDeclaringClass()));
        for (Class<?> candidate : candidates.stream()
                .filter(type -> included(request, type.getName())).sorted(
                        java.util.Comparator.comparing(Class::getName)).toList()) {
            addClass(root, candidate, selectedMethods(request, candidate));
        }
        return root;
    }

    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor root = request.getRootTestDescriptor();
        EngineExecutionListener listener = request.getEngineExecutionListener();
        ConfigurationParameters parameters = request.getConfigurationParameters();
        boolean cacheEnabled = parameters.getBoolean(CACHE_ENABLED).orElse(true);
        boolean parallel = parameters.getBoolean(PARALLEL_ENABLED).orElse(false);
        ArchitectureEngineImports.ExecutionCache cache =
                new ArchitectureEngineImports.ExecutionCache(cacheEnabled);
        listener.executionStarted(root);
        try {
            for (TestDescriptor container : root.getChildren()) {
                executeContainer(container, listener, cache, parallel);
            }
            listener.executionFinished(root, TestExecutionResult.successful());
        } catch (Throwable failure) {
            listener.executionFinished(root, TestExecutionResult.failed(failure));
        }
    }

    private static Set<Class<?>> candidates(EngineDiscoveryRequest request) {
        Set<Class<?>> candidates = new LinkedHashSet<>();
        request.getSelectorsByType(ClassSelector.class).stream()
                .map(ClassSelector::getJavaClass).forEach(candidates::add);
        for (UniqueIdSelector selector : request.getSelectorsByType(UniqueIdSelector.class)) {
            selector.getUniqueId().getSegments().stream()
                    .filter(segment -> segment.getType().equals("class"))
                    .map(UniqueId.Segment::getValue)
                    .map(ReflectionSupport::tryToLoadClass)
                    .forEach(loaded -> loaded.toOptional().ifPresent(candidates::add));
        }
        return candidates;
    }

    private static boolean included(EngineDiscoveryRequest request, String className) {
        return request.getFiltersByType(ClassNameFilter.class).stream()
                .allMatch(filter -> filter.apply(className).included());
    }

    private static Set<String> selectedMethods(
            EngineDiscoveryRequest request, Class<?> candidate) {
        Set<String> selected = new LinkedHashSet<>();
        request.getSelectorsByType(MethodSelector.class).stream()
                .filter(selector -> selector.getJavaClass().equals(candidate))
                .map(MethodSelector::getMethodName).forEach(selected::add);
        request.getSelectorsByType(UniqueIdSelector.class).stream()
                .flatMap(selector -> selector.getUniqueId().getSegments().stream())
                .filter(segment -> segment.getType().equals("method"))
                .map(UniqueId.Segment::getValue).forEach(selected::add);
        return Set.copyOf(selected);
    }

    private static void addClass(
            EngineDescriptor root, Class<?> candidate, Set<String> selectedMethods) {
        List<Method> methods = List.of(candidate.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(ArchitectureTest.class))
                .filter(method -> selectedMethods.isEmpty() || selectedMethods.contains(method.getName()))
                .sorted(java.util.Comparator.comparing(Method::getName))
                .toList();
        if (methods.isEmpty()) return;
        UniqueId classId = root.getUniqueId().append("class", candidate.getName());
        ArchitectureClassDescriptor container = new ArchitectureClassDescriptor(classId, candidate);
        methods.forEach(method -> container.addChild(new ArchitectureMethodDescriptor(
                classId.append("method", method.getName()), method)));
        root.addChild(container);
    }

    private static void executeContainer(
            TestDescriptor container,
            EngineExecutionListener listener,
            ArchitectureEngineImports.ExecutionCache cache,
            boolean parallel) throws Exception {
        listener.executionStarted(container);
        List<TestDescriptor> tests = List.copyOf(container.getChildren());
        if (!parallel || tests.size() < 2) {
            for (TestDescriptor test : tests) executeTest(test, listener, cache);
        } else {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Callable<Void>> tasks = tests.stream().<Callable<Void>>map(test -> () -> {
                    executeTest(test, listener, cache);
                    return null;
                }).toList();
                for (Future<Void> future : executor.invokeAll(tasks)) future.get();
            }
        }
        listener.executionFinished(container, TestExecutionResult.successful());
    }

    private static void executeTest(
            TestDescriptor descriptor,
            EngineExecutionListener listener,
            ArchitectureEngineImports.ExecutionCache cache) {
        listener.executionStarted(descriptor);
        try {
            ArchitectureMethodDescriptor method = (ArchitectureMethodDescriptor) descriptor;
            ArchitectureRule rule = ArchitectureEngineImports.withCache(cache, method::rule);
            ArchitectureAssertions.assertPasses(rule);
            listener.executionFinished(descriptor, TestExecutionResult.successful());
        } catch (Throwable failure) {
            listener.executionFinished(descriptor, TestExecutionResult.failed(failure));
        }
    }

    private static final class ArchitectureClassDescriptor extends AbstractTestDescriptor {
        private ArchitectureClassDescriptor(UniqueId uniqueId, Class<?> testClass) {
            super(uniqueId, testClass.getSimpleName(), ClassSource.from(testClass));
        }

        @Override
        public Type getType() {
            return Type.CONTAINER;
        }
    }

    private static final class ArchitectureMethodDescriptor extends AbstractTestDescriptor {
        private final Method method;

        private ArchitectureMethodDescriptor(UniqueId uniqueId, Method method) {
            super(uniqueId, displayName(method), MethodSource.from(method));
            this.method = method;
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }

        @Override
        public Set<TestTag> getTags() {
            return List.of(method.getAnnotation(ArchitectureTest.class).tags()).stream()
                    .map(TestTag::create)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private ArchitectureRule rule() {
            validate(method);
            try {
                if (!method.trySetAccessible()) {
                    throw new IllegalStateException("Architecture test method is not accessible");
                }
                return (ArchitectureRule) Objects.requireNonNull(
                        method.invoke(null), "architecture rule");
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Architecture test method failed", cause);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not invoke architecture test method", failure);
            }
        }

        private static void validate(Method method) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || !ArchitectureRule.class.isAssignableFrom(method.getReturnType())) {
                throw new IllegalStateException(
                        "@ArchitectureTest method must be static, no-argument, and return ArchitectureRule: "
                                + method.toGenericString());
            }
        }

        private static String displayName(Method method) {
            String configured = method.getAnnotation(ArchitectureTest.class).value();
            return configured.isBlank() ? method.getName() : configured;
        }
    }
}
