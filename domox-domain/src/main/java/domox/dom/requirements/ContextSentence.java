package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.diagram.ContextHighlight;
import domox.dom.crc.diagram.ContextMatching;
import domox.dom.nlp.Sentence;
import jakarta.inject.Named;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.Optionality;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Title;
import org.apache.causeway.applib.annotation.Where;
import org.apache.causeway.applib.value.Markup;

import java.util.List;

/**
 * One row of a candidate's <em>Context Sentences</em> tab: a corpus sentence, with the words the
 * candidate matched on highlighted.
 *
 * <p><b>Why a view model rather than the {@link Sentence} itself.</b>  The highlight is a property of
 * the <em>pair</em> (candidate, sentence) — the same sentence is evidence for several candidates and
 * is highlighted differently in each, so it cannot be stored on, or derived from, the
 * {@link Sentence}.  The row therefore carries the sentence <em>and</em> the text as it should be
 * read for this candidate.</p>
 *
 * <p><b>Why the text is a {@link Markup}.</b>  That is what makes the viewer render the highlight
 * instead of escaping it: {@code Markup} is a Causeway value type whose
 * {@code MarkupValueSemantics.htmlPresentation} returns the HTML as-is, and the Wicket viewer has a
 * {@code MarkupAttributePanel} registered for it in <em>parented</em> contexts — a table cell
 * included.  A {@code String} property would show the literal {@code <mark>}.</p>
 *
 * <p>The text is {@link ContextHighlight#highlight(String, List) highlighted} from the surface forms
 * the match reported ({@link ContextMatching.SentenceOccurrence#terms()}), so a sentence that writes
 * {@code Pets} where the candidate's word lemmatises to {@code pet} is marked on the word as written.</p>
 *
 * <p><b>Why the sentence is not a column of the tab.</b>  The row still <em>is</em> the sentence —
 * that is its {@link #title() title}, and the row's title column is what opens the row — but the
 * {@link #getSentence() sentence} property is {@link Where#ALL_TABLES hidden} in tables: the
 * {@link #getText() text} column already is the sentence, so a second column repeating it (and
 * repeating the link the title column already offers) adds nothing to read.</p>
 */
@DomainObject(nature = Nature.VIEW_MODEL)
@Named(DomainModule.NAMESPACE + ".ContextSentence")
@DomainObjectLayout(cssClassFa = "quote-left")
@NoArgsConstructor
public class ContextSentence {

    /** The sentence text as HTML, with the matched words marked. */
    private Markup text;

    /** The sentence this row is evidence from — the row's title, and what the row's page shows. */
    private Sentence sentence;

    /**
     * The sentence's own title, so the row reads exactly as the sentence does in the sentence list.
     */
    @Title
    public String title() {
        return sentence != null ? sentence.title() : "";
    }

    /**
     * The sentence as the reviewer should read it: the matched words wrapped in {@code <mark>},
     * everything else escaped.
     */
    @Property()
    @PropertyLayout(
            named = "Text",
            describedAs = "The sentence, with the words this candidate matched on highlighted",
            sequence = "1")
    public Markup getText() {
        return text != null ? text : Markup.valueOf("");
    }

    public void setText(final Markup text) {
        this.text = text;
    }

    /**
     * The sentence this row is evidence from — the row's title, not a column of the tab.
     *
     * <p>Hidden in tables ({@link Where#ALL_TABLES}): the <em>Context Sentences</em> tab lists each
     * sentence as {@link #getText() its own text}, so a column that repeats it is not needed.  It
     * stays a property — and so stays visible on the row's own page, which the row's title column
     * opens — because the sentence is what the row <em>is</em>, for this candidate.</p>
     */
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Sentence",
            describedAs = "The corpus sentence this row is evidence from",
            sequence = "2",
            hidden = Where.ALL_TABLES)
    public Sentence getSentence() {
        return sentence;
    }

    public void setSentence(final Sentence sentence) {
        this.sentence = sentence;
    }

    /**
     * The row for {@code sentence}, highlighting {@code terms} — the surface forms the match
     * reported.
     *
     * @param sentence the corpus sentence the row is evidence from
     * @param terms    the words to highlight; empty yields the plain (escaped) sentence text
     */
    static ContextSentence of(final Sentence sentence, final List<String> terms) {
        final ContextSentence row = new ContextSentence();
        row.sentence = sentence;
        row.text = Markup.valueOf(ContextHighlight.highlight(sentence.getText(), terms));
        return row;
    }
}
