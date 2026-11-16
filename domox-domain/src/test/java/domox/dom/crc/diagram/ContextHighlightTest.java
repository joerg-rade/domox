package domox.dom.crc.diagram;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the highlighting rule — {@link ContextHighlight#highlight(String, List)} — with no
 * container, no repository and no NLP: it is a pure function of a sentence's text and the words the
 * match reported.
 *
 * <p>Two properties matter, and both are about what the reviewer sees: the words the sentence
 * <em>matched on</em> are marked, as they are written ({@code Pets}, not the lemma {@code pet}) and
 * only as whole words; and nothing else in the sentence — which is corpus text, not markup — can
 * change the markup, so the cell renders as HTML rather than as a broken tag.</p>
 */
class ContextHighlightTest {

    @Test
    void highlight_wrapsTheMatchedWordInMark() {
        assertThat(ContextHighlight.highlight("A pet shop.", List.of("pet")))
                .isEqualTo("A <mark>pet</mark> shop.");
    }

    @Test
    void highlight_marksTheWordAsTheSentenceWritesIt_whateverItsCase() {
        // the term is the surface form the parser saw; the text may differ in case around it
        assertThat(ContextHighlight.highlight("Pets are groomed.", List.of("pets")))
                .isEqualTo("<mark>Pets</mark> are groomed.");
        assertThat(ContextHighlight.highlight("PETS are groomed.", List.of("Pets")))
                .isEqualTo("<mark>PETS</mark> are groomed.");
    }

    @Test
    void highlight_marksEveryOccurrenceOfATerm() {
        assertThat(ContextHighlight.highlight("The pet grooms the pet.", List.of("pet")))
                .isEqualTo("The <mark>pet</mark> grooms the <mark>pet</mark>.");
    }

    @Test
    void highlight_marksEveryTermOfACompoundName() {
        assertThat(ContextHighlight.highlight("The pet shop sells pet food.", List.of("pet", "shop")))
                .isEqualTo("The <mark>pet</mark> <mark>shop</mark> sells <mark>pet</mark> food.");
    }

    @Test
    void highlight_marksOnlyWholeWords() {
        // "pet" is a prefix of "petrol" and of "petShop" — neither is the word the parser matched
        assertThat(ContextHighlight.highlight("Petrol is not a pet.", List.of("pet")))
                .isEqualTo("Petrol is not a <mark>pet</mark>.");
        assertThat(ContextHighlight.highlight("PetShop and pet.", List.of("pet")))
                .isEqualTo("PetShop and <mark>pet</mark>.");
        assertThat(ContextHighlight.highlight("petpet", List.of("pet")))
                .isEqualTo("petpet");
    }

    @Test
    void highlight_treatsDigitsAsPartOfTheWord() {
        assertThat(ContextHighlight.highlight("The uc01 use case.", List.of("uc01")))
                .isEqualTo("The <mark>uc01</mark> use case.");
        assertThat(ContextHighlight.highlight("The uc011 use case.", List.of("uc01")))
                .isEqualTo("The uc011 use case.");
    }

    @Test
    void highlight_marksAWordThatPunctuationSurrounds() {
        // the quotes of the sentence are escaped (they are data, not markup) but read the same
        assertThat(ContextHighlight.highlight("(pet), pet; \"pet\"", List.of("pet")))
                .isEqualTo("(<mark>pet</mark>), <mark>pet</mark>; &quot;<mark>pet</mark>&quot;");
    }

    @Test
    void highlight_escapesTheSentenceSoCorpusTextCannotChangeTheMarkup() {
        // a sentence is data: the tag it contains must be shown, not opened
        assertThat(ContextHighlight.highlight("A <b>bold</b> pet & a \"quoted\" one.", List.of("pet")))
                .isEqualTo("A &lt;b&gt;bold&lt;/b&gt; <mark>pet</mark> &amp; a &quot;quoted&quot; one.");
    }

    @Test
    void highlight_escapesAMatchedWordToo() {
        assertThat(ContextHighlight.highlight("The A&B shop.", List.of("A&B")))
                .isEqualTo("The <mark>A&amp;B</mark> shop.");
    }

    @Test
    void highlight_takesATermLiterally_evenWhenItIsARegularExpression() {
        // Pattern.quote — a term is a word, never a pattern
        assertThat(ContextHighlight.highlight("C++ and C#.", List.of("c++")))
                .isEqualTo("<mark>C++</mark> and C#.");
        assertThat(ContextHighlight.highlight("The a.b rule.", List.of("a.b")))
                .isEqualTo("The <mark>a.b</mark> rule.");
        assertThat(ContextHighlight.highlight("A (pet) shop.", List.of("(pet)")))
                .isEqualTo("A <mark>(pet)</mark> shop.");
    }

    @Test
    void highlight_stripsSurroundingWhitespaceOffATerm() {
        assertThat(ContextHighlight.highlight("A pet shop.", List.of(" pet ")))
                .isEqualTo("A <mark>pet</mark> shop.");
    }

    @Test
    void highlight_marksARepeatedTermOncePerOccurrence() {
        // the same term twice must not double-wrap or break the alternation
        assertThat(ContextHighlight.highlight("A pet.", List.of("pet", "pet")))
                .isEqualTo("A <mark>pet</mark>.");
    }

    @Test
    void highlight_withoutTermsIsTheEscapedSentence() {
        assertThat(ContextHighlight.highlight("A <b>pet</b>.", List.of()))
                .isEqualTo("A &lt;b&gt;pet&lt;/b&gt;.");
        assertThat(ContextHighlight.highlight("A pet.", null)).isEqualTo("A pet.");
        assertThat(ContextHighlight.highlight("A pet.", Arrays.asList("", "  ", null)))
                .as("blank and null terms have nothing to highlight")
                .isEqualTo("A pet.");
    }

    @Test
    void highlight_ofNoTextIsEmpty() {
        assertThat(ContextHighlight.highlight(null, List.of("pet"))).isEmpty();
        assertThat(ContextHighlight.highlight("", List.of("pet"))).isEmpty();
    }

    @Test
    void highlight_ofATermThatOccursNowhereIsTheEscapedSentence() {
        assertThat(ContextHighlight.highlight("A pet shop.", List.of("groomer")))
                .isEqualTo("A pet shop.");
    }
}
