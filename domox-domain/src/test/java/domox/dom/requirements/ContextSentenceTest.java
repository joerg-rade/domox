package domox.dom.requirements;

import domox.dom.nlp.Sentence;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Where;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the row a candidate's <em>Context Sentences</em> tab renders — {@link ContextSentence}:
 * that the text it exposes is the sentence with the words the match reported marked, that it carries
 * the sentence itself (as the row's title, not as a second column of the tab), and that the defaults
 * the viewer reads hold before anything is set.
 * <p>
 * The row is the <em>pair</em> (candidate, sentence): one sentence is evidence for several candidates
 * and is highlighted differently in each, which is why it is a view model rather than the
 * {@link Sentence}.  The mapping that builds the rows is pinned by {@code ContextEvidenceTest}.
 */
class ContextSentenceTest {

    @Test
    void of_highlightsTheWordsTheSentenceMatchedOn_andCarriesTheSentence() {
        // given: the sentence writes the word as "Pets", while the matched lemma was "pet"
        final Sentence sentence = new Sentence();
        sentence.setText("Pets are groomed by a pet shop.");

        // when
        final ContextSentence row = ContextSentence.of(sentence, List.of("Pets"));

        // then
        assertThat(row.getText().html()).isEqualTo("<mark>Pets</mark> are groomed by a pet shop.");
        assertThat(row.getSentence()).isSameAs(sentence);
    }

    @Test
    void of_withoutTerms_showsTheSentenceUnmarked() {
        final Sentence sentence = new Sentence();
        sentence.setText("A pet shop.");

        assertThat(ContextSentence.of(sentence, List.of()).getText().html()).isEqualTo("A pet shop.");
    }

    @Test
    void title_isTheSentencesOwnTitle() {
        // the row reads exactly as the sentence does in the sentence list
        final Sentence sentence = new Sentence();
        sentence.setText("Source sentence : \" A pet shop . \"");

        assertThat(ContextSentence.of(sentence, List.of()).title()).isEqualTo("Source sentence");
    }

    @Test
    void title_ofARowWithoutASentenceIsEmpty() {
        // the viewer reads the title of a row it has just built — an unset row must not throw
        assertThat(new ContextSentence().title()).isEmpty();
    }

    @Test
    void text_ofARowWithoutTextIsEmptyMarkup() {
        // likewise the text cell: an unset row renders empty rather than null
        assertThat(new ContextSentence().getText().html()).isEmpty();
    }

    @Test
    void sentence_isNotAColumnOfTheTab() {
        // the tab lists each sentence as its own (highlighted) text, so the sentence is not shown as a
        // column of that table; it stays a property — the row's title column is what opens the row
        final PropertyLayout layout = sentenceLayout();

        assertThat(layout).isNotNull();
        assertThat(layout.hidden()).isEqualTo(Where.ALL_TABLES);
    }

    /** The {@link PropertyLayout} of the {@code sentence} property, as the metamodel reads it. */
    private static PropertyLayout sentenceLayout() {
        try {
            return ContextSentence.class.getMethod("getSentence").getAnnotation(PropertyLayout.class);
        } catch (final NoSuchMethodException e) {
            throw new AssertionError("ContextSentence#getSentence is gone", e);
        }
    }
}
