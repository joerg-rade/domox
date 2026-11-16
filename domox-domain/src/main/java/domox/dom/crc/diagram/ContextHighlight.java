package domox.dom.crc.diagram;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The <b>highlighting rule</b>: a sentence's text as HTML, with the words a candidate matched on
 * wrapped in {@code <mark>}.
 *
 * <p><b>Why it exists.</b>  A reviewer reads the candidate's <em>Context Sentences</em> tab to
 * decide whether the evidence really supports the candidate, and the question they are asking of
 * every row is "where is this candidate in that sentence?".  The answer is a few words inside a
 * long sentence — a comment can run to hundreds of characters — so those words are marked rather
 * than left for the reader to hunt for.</p>
 *
 * <p><b>What is highlighted, and why the terms are given, not derived.</b>  The rule highlights the
 * <em>surface forms</em> the sentence matched on, which the caller reads off the parser's
 * dependencies (their glosses — {@link ContextMatching.SentenceOccurrence#terms()}), not by
 * searching the text for the candidate's words.  That matters because matching is on <em>lemmas</em>:
 * the lemma {@code pet} occurs in the sentence as {@code Pets}, so a search for the lemma would
 * either miss the word or over-match ({@code pet} is a prefix of {@code petrol}).  Handing the terms
 * in keeps this rule pure and free of Spring, JPA and NLP.</p>
 *
 * <p><b>Escaping.</b>  A sentence is corpus text, not markup — it may contain {@code <}, {@code &} or
 * a quote.  Every character that is not part of a {@code <mark>} wrapper this rule itself wrote is
 * escaped, so the result is well-formed HTML whatever the sentence contains (and a term is
 * {@link Pattern#quote(String) quoted} before it reaches the regex, so a term is never a pattern
 * either).</p>
 *
 * <p>A term matches whole <em>words</em> only, ignoring case: {@code Pets} highlights
 * {@code the pets} and {@code Pets are …}, but neither {@code petrol} nor {@code petshop}.  A term
 * that occurs more than once is highlighted everywhere it occurs — every occurrence is the word.</p>
 */
public final class ContextHighlight {

    private static final String MARK_OPEN = "<mark>";
    private static final String MARK_CLOSE = "</mark>";

    /** What separates a term from a word it merely starts or ends: neither a letter nor a digit. */
    private static final String NOT_A_WORD_CHARACTER = "[\\p{L}\\p{N}]";

    private ContextHighlight() {
    }

    /**
     * {@code text} as HTML, with every whole-word occurrence of every term wrapped in
     * {@code <mark>} — the text itself escaped, so it is safe to render as markup.
     *
     * @param text  the sentence text; {@code null} yields the empty string
     * @param terms the surface forms to highlight ({@link ContextMatching.SentenceOccurrence#terms()});
     *              {@code null}, an empty list or only blank terms yields the escaped text unchanged
     */
    public static String highlight(final String text, final List<String> terms) {
        if (text == null) {
            return "";
        }
        final Pattern pattern = patternFor(terms);
        if (pattern == null) {
            return escape(text);
        }
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder html = new StringBuilder(text.length() + 32);
        int end = 0;
        while (matcher.find()) {
            appendEscaped(html, text, end, matcher.start());
            html.append(MARK_OPEN);
            appendEscaped(html, text, matcher.start(), matcher.end());
            html.append(MARK_CLOSE);
            end = matcher.end();
        }
        appendEscaped(html, text, end, text.length());
        return html.toString();
    }

    /**
     * The terms as one alternation of whole words, or {@code null} when there is nothing to highlight
     * — no term at all, or only blank ones.
     */
    private static Pattern patternFor(final List<String> terms) {
        if (terms == null) {
            return null;
        }
        final String alternatives = terms.stream()
                .filter(term -> term != null && !term.isBlank())
                .distinct()
                .map(ContextHighlight::boundedWord)
                .collect(Collectors.joining("|"));
        if (alternatives.isEmpty()) {
            return null;
        }
        return Pattern.compile(alternatives, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * {@code term} quoted, guarded on either side so it matches a whole word and not the start or the
     * end of a longer one — {@code pet} must match neither {@code petrol} nor {@code petShop}.
     * <p>
     * The guard sits on each term rather than around the alternation, so the pattern needs no group
     * (alternation binds loosest, so every branch carries its own guards).
     */
    private static String boundedWord(final String term) {
        return "(?<!" + NOT_A_WORD_CHARACTER + ")"
                + Pattern.quote(term.strip())
                + "(?!" + NOT_A_WORD_CHARACTER + ")";
    }

    /** {@code text} with the characters that would change the markup escaped. */
    private static String escape(final String text) {
        final StringBuilder html = new StringBuilder(text.length());
        appendEscaped(html, text, 0, text.length());
        return html.toString();
    }

    /**
     * Appends {@code text[from, to)} with {@code & < > "} escaped — the four characters that would
     * otherwise open a tag, close one, or be read as an entity or an attribute boundary.
     */
    private static void appendEscaped(final StringBuilder html, final String text, final int from, final int to) {
        for (int i = from; i < to; i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '&' -> html.append("&amp;");
                case '<' -> html.append("&lt;");
                case '>' -> html.append("&gt;");
                case '"' -> html.append("&quot;");
                default -> html.append(c);
            }
        }
    }
}
