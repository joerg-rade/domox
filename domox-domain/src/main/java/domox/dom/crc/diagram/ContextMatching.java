package domox.dom.crc.diagram;

import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The <b>context-matching rule</b>: the sentences of the corpus in which a candidate's name occurs
 * — the "where is this candidate actually talked about" question, next to the lexical graph
 * ({@link CandidateD3Graph}) that answers "which other words co-occur with it".
 * <p>
 * Both halves are pure and static — splitting a name into its words ({@link #wordParts(String)}) and
 * turning a set of looked-up typed dependencies into the sentences that matched
 * ({@link #occurrences(List, Function)}) — and free of Spring and JPA, so the rule is unit-testable
 * without a container: the caller supplies the look-up as a {@link Function}
 * ({@code TypedDependencyRepository#findByLemma}).  The thin orchestrator that owns the repositories
 * is {@code domox.dom.requirements.ContextEvidence}, which resolves the matched sentence ids to the
 * {@link Sentence} entities the candidate's <em>Context Sentences</em> tab lists.
 */
public final class ContextMatching {

    private ContextMatching() {
    }

    //region > input/output records

    /**
     * One sentence in which the candidate's name occurs.
     *
     * @param id    the sentence's id
     * @param text  the sentence text
     * @param pos   the Penn POS code of the occurrence the sentence matched on — the last matched
     *              word of the name, i.e. the head of a compound such as {@code PetShop}
     * @param count how many typed dependencies in this sentence matched
     * @param terms the <em>surface forms</em> the sentence matched on — the glosses of the matched
     *              endpoints ({@code Pets} for the lemma {@code pet}), in the order they were first
     *              matched and without repeats.  These are what the candidate's
     *              <em>Context Sentences</em> table highlights
     *              ({@link ContextHighlight#highlight(String, List)}): matching is on lemmas, but a
     *              reader looks for the word as it is <em>written</em>, which is not always the lemma
     *              ({@code pets} lemmatises to {@code pet})
     */
    public record SentenceOccurrence(long id, String text, String pos, int count, List<String> terms) {
    }

    //endregion

    //region > matching rule

    /**
     * The sentences that match <em>every</em> word of the candidate's name, in ascending sentence
     * id order.
     * <p>
     * A sentence matches when, for each part of the name, at least one typed dependency in that
     * sentence carries the part as its governor or dependent lemma — so a compound name matches
     * only sentences in which all of its words occur.  Matching on typed dependencies rather than
     * on {@code Sentence.text} containment is what makes "occurs as a word" true: the tokens were
     * lemmatised and POS-tagged by the parser, so {@code "pet"} does not match {@code "petrol"}.
     *
     * @param parts  the lower-cased words of the candidate name (see {@link #wordParts(String)})
     * @param lookup the typed dependencies carrying a given lemma — {@code TypedDependencyRepository#findByLemma}
     * @return one occurrence per matching sentence, ascending by id; empty when nothing matches
     */
    public static List<SentenceOccurrence> occurrences(final List<String> parts,
                                                      final Function<String, List<TypedDependency>> lookup) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        final Map<Long, Accumulator> bySentence = new LinkedHashMap<>();
        for (final String part : parts) {
            final List<TypedDependency> matches = lookup.apply(part);
            if (matches == null) {
                continue;
            }
            for (final TypedDependency td : matches) {
                if (td == null) {
                    continue;
                }
                final Sentence sentence = td.getSentence();
                if (sentence == null || sentence.getId() == null) {
                    continue;
                }
                bySentence.computeIfAbsent(sentence.getId(), id -> new Accumulator(sentence)).record(part, td);
            }
        }
        final List<SentenceOccurrence> result = new ArrayList<>();
        for (final Accumulator accumulator : bySentence.values()) {
            if (accumulator.matchedEveryPart(parts)) {
                result.add(accumulator.toOccurrence(parts));
            }
        }
        result.sort(Comparator.comparingLong(SentenceOccurrence::id));
        return result;
    }

    /**
     * The lower-cased words of a model name, splitting on non-alphanumeric characters and at
     * camel-case boundaries — {@code "PetShop"} → {@code [pet, shop]}, {@code "TD2-Reader"} →
     * {@code [td2, reader]}.  A run of capitals stays together ({@code "PDFViewer"} →
     * {@code [pdf, viewer]}), so an acronym is one word, not one word per letter.  Consecutive
     * separators produce no empty part.
     */
    public static List<String> wordParts(final String name) {
        final List<String> parts = new ArrayList<>();
        if (name == null) {
            return parts;
        }
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                flush(parts, current);
                continue;
            }
            if (Character.isUpperCase(c) && !current.isEmpty() && startsAWord(name, i)) {
                flush(parts, current);
            }
            current.append(Character.toLowerCase(c));
        }
        flush(parts, current);
        return parts;
    }

    /**
     * Whether the capital at {@code index} begins a new word: it is the first character (there is
     * nothing before it), it follows a non-capital ({@code "PetShop"} → the {@code S}), or it is the
     * last capital of a run and a lower-case letter follows it ({@code "PDFViewer"} → the
     * {@code V}, but not the {@code D} or {@code F}).
     */
    private static boolean startsAWord(final String name, final int index) {
        if (index == 0) {
            return true;
        }
        if (!Character.isUpperCase(name.charAt(index - 1))) {
            return true;
        }
        return index + 1 < name.length() && Character.isLowerCase(name.charAt(index + 1));
    }

    /** Adds the word being accumulated to {@code parts} (if any) and starts a new one. */
    private static void flush(final List<String> parts, final StringBuilder current) {
        if (!current.isEmpty()) {
            parts.add(current.toString());
            current.setLength(0);
        }
    }

    //endregion

    //region > accumulation

    /**
     * Accumulates, per sentence, which parts of the candidate name matched, how many dependencies
     * matched in total, and the POS code of each part — the raw material of one
     * {@link SentenceOccurrence}.
     */
    private static final class Accumulator {

        private final Sentence sentence;
        private final Set<String> partsSeen = new LinkedHashSet<>();
        private final Set<String> terms = new LinkedHashSet<>();
        private final Map<String, String> posByPart = new LinkedHashMap<>();
        private int count;

        private Accumulator(final Sentence sentence) {
            this.sentence = sentence;
        }

        private void record(final String part, final TypedDependency td) {
            partsSeen.add(part);
            count++;
            final String gloss = glossOf(part, td);
            if (!gloss.isBlank()) {
                terms.add(gloss);
            }
            if (!posByPart.containsKey(part)) {
                final String pos = posOf(part, td);
                if (!pos.isEmpty()) {
                    posByPart.put(part, pos);
                }
            }
        }

        private boolean matchedEveryPart(final List<String> parts) {
            return partsSeen.containsAll(parts);
        }

        private SentenceOccurrence toOccurrence(final List<String> parts) {
            return new SentenceOccurrence(sentence.getId(), sentence.getText(), posFor(parts), count,
                    List.copyOf(terms));
        }

        /** The POS of the last part this sentence matched — the head word of a compound name. */
        private String posFor(final List<String> parts) {
            String pos = "";
            for (final String part : parts) {
                final String matched = posByPart.get(part);
                if (matched != null) {
                    pos = matched;
                }
            }
            return pos;
        }

        /** The POS the dependency gives {@code part}, read off whichever endpoint carries that lemma. */
        private static String posOf(final String part, final TypedDependency td) {
            final PartOfSpeechType pos = isGovernor(part, td) ? td.getGovernorPos() : td.getDependentPos();
            return pos != null && pos.getCode() != null ? pos.getCode() : "";
        }

        /**
         * The <em>surface form</em> the dependency gives {@code part} — the gloss of whichever
         * endpoint carries that lemma, i.e. the word as the sentence writes it ({@code Pets} where the
         * lemma is {@code pet}); empty when the dependency carries no gloss there.
         */
        private static String glossOf(final String part, final TypedDependency td) {
            final String gloss = isGovernor(part, td) ? td.getGovernorGloss() : td.getDependentGloss();
            return gloss != null ? gloss : "";
        }

        /** Whether {@code part} is carried by the dependency's governor (rather than its dependent). */
        private static boolean isGovernor(final String part, final TypedDependency td) {
            return part.equals(DiagramLemmas.lower(td.getGovernorLemma()));
        }
    }

    //endregion
}
