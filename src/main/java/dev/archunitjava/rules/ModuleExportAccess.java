package dev.archunitjava.rules;

/** JPMS export classification for a resolved cross-boundary access. */
public enum ModuleExportAccess {
    SAME_MODULE,
    UNNAMED_OR_UNKNOWN_MODULE,
    AUTOMATIC_MODULE,
    UNQUALIFIED_EXPORT,
    QUALIFIED_EXPORT_TO_CALLER,
    PACKAGE_NOT_EXPORTED,
    QUALIFIED_EXPORT_TO_OTHER_MODULES
}
