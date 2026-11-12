package domox.dom.crc.diagram;

import domox.dom.nlp.PartOfSpeechType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lemma, part-of-speech and candidate-vocabulary helpers shared by every lexical diagram renderer
 * ({@link CandidateD3Graph}, {@link CandidateVegaGraph}, {@link DomainModelDiagramsD3}).
 * <p>
 * A diagram keys its nodes by the <em>lower-cased lemma</em> of a candidate word, so the same
 * normalisation, POS accumulation and candidate-type labelling must be used everywhere — otherwise
 * two renderers of the same model would disagree on which node a link attaches to.
 */
public final class DiagramLemmas {

    private DiagramLemmas() {
    }

    /** Lower-cases a lemma, mapping {@code null} to the empty string. */
    public static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Records a POS code for a lemma, skipping a {@code null} POS or a {@code null}/empty code. */
    public static void accumulatePos(final Map<String, Set<String>> posByLemma,
                                     final String lemma,
                                     final PartOfSpeechType pos) {
        if (pos == null) return;
        final String code = pos.getCode();
        if (code != null && !code.isEmpty()) {
            posByLemma.computeIfAbsent(lemma, k -> new LinkedHashSet<>()).add(code);
        }
    }

    /** First POS code from the set, or the empty string when the set is {@code null}/empty. */
    public static String firstPos(final Set<String> posSet) {
        if (posSet == null || posSet.isEmpty()) return "";
        return posSet.iterator().next();
    }

    /** Friendly label of a candidate type, e.g. {@code ClassCdd} → {@code Class}. */
    public static String typeLabel(final String candidateType) {
        if (candidateType == null || candidateType.isEmpty()) {
            return "Domain";
        }
        return candidateType.endsWith("Cdd")
                ? candidateType.substring(0, candidateType.length() - 3)
                : candidateType;
    }
}
