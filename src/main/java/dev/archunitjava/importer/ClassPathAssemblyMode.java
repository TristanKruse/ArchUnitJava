package dev.archunitjava.importer;

/**
 * Resource lookup namespace.
 *
 * <p>{@link #CLASSPATH} uses one global binary-name namespace and the earliest input wins.
 * {@link #MODULE_PATH} keeps each input in a separate namespace; cross-module name conflicts are
 * therefore not silently resolved using classpath order.
 */
public enum ClassPathAssemblyMode {
    CLASSPATH,
    MODULE_PATH
}
