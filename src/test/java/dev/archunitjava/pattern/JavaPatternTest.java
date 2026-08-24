package dev.archunitjava.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class JavaPatternTest {
    @Test
    void globStarsRespectTheDomainSeparator() {
        JavaPattern oneQualifiedSegment = JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "com.*.Api");
        JavaPattern anyQualifiedDepth = JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "com.**.Api");
        JavaPattern onePathSegment = JavaPattern.glob(PatternDomain.RESOURCE_PATH, "classes/*/Api.class");
        JavaPattern anyPathDepth = JavaPattern.glob(PatternDomain.RESOURCE_PATH, "classes/**/Api.class");

        assertTrue(oneQualifiedSegment.matches("com.example.Api"));
        assertFalse(oneQualifiedSegment.matches("com.example.internal.Api"));
        assertTrue(anyQualifiedDepth.matches("com.example.internal.Api"));
        assertTrue(onePathSegment.matches("classes/example/Api.class"));
        assertFalse(onePathSegment.matches("classes/example/internal/Api.class"));
        assertTrue(anyPathDepth.matches("classes/example/internal/Api.class"));
        assertTrue(anyPathDepth.matches("classes/example\ninternal/Api.class"));
    }

    @Test
    void binaryAndSourceStyleTypeNamesHaveExplicitMatchingSemantics() {
        JavaPattern binaryNestedType = JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "com.example.Outer$*");
        JavaPattern sourceNestedType = JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "com.example.Outer.*");

        assertTrue(binaryNestedType.matches("com.example.Outer$Inner"));
        assertFalse(binaryNestedType.matches("com.example.Outer.Inner"));
        assertTrue(sourceNestedType.matches("com.example.Outer.Inner"));
        assertFalse(sourceNestedType.matches("com.example.Outer.Deep.Inner"));
    }

    @Test
    void exactGlobAndRegexMatchersExposeImmutableDescriptions() {
        JavaPattern exact = JavaPattern.exact(PatternDomain.QUALIFIED_NAME, "com.example.Api");
        JavaPattern glob = JavaPattern.glob(PatternDomain.RESOURCE_PATH, "classes/**");
        JavaPattern regex = JavaPattern.regex(PatternDomain.QUALIFIED_NAME, "com\\..+\\.Api");

        assertEquals(new PatternDescription(
                PatternSyntax.EXACT, PatternDomain.QUALIFIED_NAME, "com.example.Api"), exact.description());
        assertEquals(PatternSyntax.GLOB, glob.description().syntax());
        assertEquals(PatternSyntax.REGEX, regex.description().syntax());
        assertTrue(exact.matches("com.example.Api"));
        assertFalse(exact.matches("com.example.ApiExtra"));
        assertTrue(regex.matches("com.example.Api"));
        assertFalse(regex.matches("prefix.com.example.Api"));
        assertEquals(exact, JavaPattern.exact(PatternDomain.QUALIFIED_NAME, "com.example.Api"));
        assertNotEquals(exact, JavaPattern.exact(PatternDomain.RESOURCE_PATH, "com.example.Api"));

        List<PatternDescription> sorted = List.of(regex.description(), glob.description(), exact.description())
                .stream().sorted().toList();
        assertEquals(List.of(glob.description(), exact.description(), regex.description()), sorted);
    }

    @Test
    void globQuestionMarkAndEscapingAreDeterministic() {
        JavaPattern oneCharacter = JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "com.?.Type");
        JavaPattern literalStar = JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "com.example.Literal\\*");

        assertTrue(oneCharacter.matches("com.x.Type"));
        assertFalse(oneCharacter.matches("com.xy.Type"));
        assertFalse(oneCharacter.matches("com...Type"));
        assertTrue(literalStar.matches("com.example.Literal*"));
    }

    @Test
    void malformedPatternsFailDuringConstructionWithUserFacingErrors() {
        InvalidPatternException regexError = assertThrows(
                InvalidPatternException.class,
                () -> JavaPattern.regex(PatternDomain.QUALIFIED_NAME, "[unterminated"));
        assertTrue(regexError.getCause() instanceof java.util.regex.PatternSyntaxException);
        assertThrows(InvalidPatternException.class,
                () -> JavaPattern.glob(PatternDomain.RESOURCE_PATH, "classes/escape\\"));
        assertThrows(InvalidPatternException.class,
                () -> JavaPattern.exact(PatternDomain.QUALIFIED_NAME, " "));
        assertThrows(InvalidPatternException.class,
                () -> JavaPattern.exact(null, "com.example.Api"));
    }
}
