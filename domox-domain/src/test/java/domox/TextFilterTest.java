package domox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TextFilter#stripMarkdownRegex(String)}.
 *
 * <p>The test cases mirror real markdown scaffolding that shows up in the
 * PetShop use-case corpus: bold topic labels, auto-generated headings, table
 * header/separator rows, bullet/numbered list markers, and provenance
 * annotations that reference a source file.</p>
 */
class TextFilterTest {

    private final TextFilter filter = new TextFilter();

    // -----------------------------------------------------------------
    // The exact scaffolding strings observed in the corpus
    // -----------------------------------------------------------------

    @Test
    void boldTopicLabel_hasMarkersStripped() {
        assertEquals("Source sentence:", filter.stripMarkdownRegex("**Source sentence**:"));
    }

    @Test
    void headingWithNumber_keepsOnlyThePlainHeadingText() {
        assertEquals("Metadata", filter.stripMarkdownRegex("## 1. Metadata"));
    }

    @Test
    void tableHeaderRow_isReducedToItsCellWords() {
        assertEquals("Field Value", filter.stripMarkdownRegex("| Field               | Value |"));
    }

    @Test
    void tableSeparatorRow_isRemovedEntirely() {
        assertEquals("", filter.stripMarkdownRegex("|---------------------|-------|"));
    }

    @Test
    void singleStarEmphasis_isStripped() {
        assertEquals("End of Use Case UC-01", filter.stripMarkdownRegex("*End of Use Case UC-01*"));
    }

    @Test
    void provenanceAnnotation_boldStrippedButInlineCodeKept() {
        // Inline code (backticks) is not handled by this filter, so it is preserved.
        assertEquals("Created from: `PetShop_UseCases.txt` — Sentence 1",
                filter.stripMarkdownRegex("**Created from**: `PetShop_UseCases.txt` — Sentence 1"));
    }

    // -----------------------------------------------------------------
    // Core markdown constructs
    // -----------------------------------------------------------------

    @Test
    void codeBlock_isRemoved() {
        assertEquals("rest", filter.stripMarkdownRegex("```java\nint x = 1;\n```\nrest"));
    }

    @Test
    void inlineLink_isReplacedByItsLabel() {
        assertEquals("Click here", filter.stripMarkdownRegex("[Click here](https://example.com)"));
    }

    @Test
    void image_isRemovedEntirely() {
        assertEquals("", filter.stripMarkdownRegex("![alt text](img/petshop.png)"));
    }

    @Test
    void bulletListMarkers_areStrippedFromEachLine() {
        assertEquals("item\nitem2", filter.stripMarkdownRegex("* item\n- item2"));
    }

    @Test
    void numberedListMarkers_areStrippedFromEachLine() {
        assertEquals("First\nSecond", filter.stripMarkdownRegex("1. First\n2. Second"));
    }

    // -----------------------------------------------------------------
    // Whitespace normalisation
    // -----------------------------------------------------------------

    @Test
    void blankLine_isCollapsedToASingleNewline() {
        assertEquals("line1\nline2", filter.stripMarkdownRegex("line1\n\nline2"));
    }

    @Test
    void repeatedWhitespaceInsideLine_isCollapsed() {
        assertEquals("a b c", filter.stripMarkdownRegex("a    b\t\tc"));
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertEquals("", filter.stripMarkdownRegex(""));
    }

    @Test
    void surroundingWhitespace_isTrimmed() {
        assertEquals("content", filter.stripMarkdownRegex("   content\n"));
    }
}