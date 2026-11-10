package domox.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(filter.stripMarkdownRegex("**Source sentence**:")).isEqualTo("Source sentence:");
    }

    @Test
    void headingWithNumber_keepsOnlyThePlainHeadingText() {
        assertThat(filter.stripMarkdownRegex("## 1. Metadata")).isEqualTo("Metadata");
    }

    @Test
    void tableHeaderRow_isReducedToItsCellWords() {
        assertThat(filter.stripMarkdownRegex("| Field               | Value |")).isEqualTo("Field Value");
    }

    @Test
    void tableSeparatorRow_isRemovedEntirely() {
        assertThat(filter.stripMarkdownRegex("|---------------------|-------|")).isEmpty();
    }

    @Test
    void singleStarEmphasis_isStripped() {
        assertThat(filter.stripMarkdownRegex("*End of Use Case UC-01*")).isEqualTo("End of Use Case UC-01");
    }

    @Test
    void provenanceAnnotation_boldStrippedButInlineCodeKept() {
        // Inline code (backticks) is not handled by this filter, so it is preserved.
        assertThat(filter.stripMarkdownRegex("**Created from**: `PetShop_UseCases.txt` — Sentence 1"))
                .isEqualTo("Created from: `PetShop_UseCases.txt` — Sentence 1");
    }

    // -----------------------------------------------------------------
    // Core markdown constructs
    // -----------------------------------------------------------------

    @Test
    void codeBlock_isRemoved() {
        assertThat(filter.stripMarkdownRegex("```java\nint x = 1;\n```\nrest")).isEqualTo("rest");
    }

    @Test
    void inlineLink_isReplacedByItsLabel() {
        assertThat(filter.stripMarkdownRegex("[Click here](https://example.com)")).isEqualTo("Click here");
    }

    @Test
    void image_isRemovedEntirely() {
        assertThat(filter.stripMarkdownRegex("![alt text](img/petshop.png)")).isEmpty();
    }

    @Test
    void bulletListMarkers_areStrippedFromEachLine() {
        assertThat(filter.stripMarkdownRegex("* item\n- item2")).isEqualTo("item\nitem2");
    }

    @Test
    void numberedListMarkers_areStrippedFromEachLine() {
        assertThat(filter.stripMarkdownRegex("1. First\n2. Second")).isEqualTo("First\nSecond");
    }

    // -----------------------------------------------------------------
    // Whitespace normalisation
    // -----------------------------------------------------------------

    @Test
    void blankLine_isCollapsedToASingleNewline() {
        assertThat(filter.stripMarkdownRegex("line1\n\nline2")).isEqualTo("line1\nline2");
    }

    @Test
    void repeatedWhitespaceInsideLine_isCollapsed() {
        assertThat(filter.stripMarkdownRegex("a    b\t\tc")).isEqualTo("a b c");
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(filter.stripMarkdownRegex("")).isEmpty();
    }

    @Test
    void surroundingWhitespace_isTrimmed() {
        assertThat(filter.stripMarkdownRegex("   content\n")).isEqualTo("content");
    }
}