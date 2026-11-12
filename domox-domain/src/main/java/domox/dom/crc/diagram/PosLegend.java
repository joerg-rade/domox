package domox.dom.crc.diagram;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The part-of-speech <em>legend</em> shared by the lexical diagram renderers
 * ({@link CandidateD3Graph}, {@link CandidateVegaGraph}, {@link DomainModelDiagramsD3}): the ordered
 * categories (fill colour, label, description, and the Penn POS codes that map to each), the
 * code/label lookups over them, and the JSON emission of the {@code "legend"} array.
 * <p>
 * The three renderers used to carry their own copies of this table — two byte-identical
 * {@code LEGEND_ENTRIES} lists plus a third, description-less {@code CATEGORIES} variant — which
 * meant a new POS category (or a recoloured one) had to be applied in three places, and could drift
 * silently between them.  {@link CandidateVegaGraph} in particular did <em>not</em> use
 * {@link PosPalette} and derived both its node fills and its legend from its own table; keeping a
 * single source here makes the two renderers agree by construction.
 * <p>
 * A code with no specific entry falls through to the trailing catch-all {@code "Other"} entry — the
 * same fall-through that {@link CandidateVegaGraph} applied, so its node fill and its legend always
 * agree.  (The D3 renderers colour their <em>nodes</em> from {@link PosPalette}, which is finer
 * grained — e.g. {@code CD}/{@code CC} get their own colours there — and use this class only for the
 * legend, exactly as before.)
 */
public final class PosLegend {

    private PosLegend() {
    }

    /** A single legend row: fill colour, short label, description, and the POS codes that map to it. */
    public record Entry(String color, String label, String description, Set<String> posCodes) {}

    /**
     * All possible legend entries, ordered by semantic category.  The first matching entry wins.
     * The POS codes here must stay in sync with {@link PosPalette#color(String)}.
     */
    public static final List<Entry> ENTRIES = List.of(
            new Entry("#3498DB", "Noun", "Entities and objects (NN, NNS)", Set.of("NN", "NNS")),
            new Entry("#85C1E9", "Proper noun", "Named entities (NNP, NNPS)", Set.of("NNP", "NNPS")),
            new Entry("#E74C3C", "Verb", "Actions and predicates (VB, VBZ)", Set.of("VB", "VBZ", "VBD", "VBG", "VBN", "VBP")),
            new Entry("#2ECC71", "Adjective", "Descriptors and modifiers (JJ, JJR, JJS)", Set.of("JJ", "JJR", "JJS")),
            new Entry("#F39C12", "Determiner", "Articles and determiners (DT)", Set.of("DT")),
            new Entry("#9B59B6", "Pronoun", "Pronouns (PRP, PRP$)", Set.of("PRP", "PRP$", "WDT", "WP", "WP$", "WRB")),
            new Entry("#17A2B8", "Preposition", "Prepositions (IN)", Set.of("IN")),
            new Entry("#FFBB28", "Adverb", "Adverbs (RB)", Set.of("RB")),
            new Entry("#AAB7B8", "Other", "Other parts of speech", Set.of())
    );

    /** The trailing catch-all entry a code with no specific category falls through to. */
    private static final Entry OTHER = ENTRIES.getLast();

    /**
     * The legend category label for a POS code — e.g. {@code "NN"} → {@code "Noun"},
     * {@code "CD"} → {@code "Other"} (the catch-all).
     */
    public static String label(final String pos) {
        return entryFor(pos).label();
    }

    /** The colour of the entry carrying {@code label}, or the catch-all colour when none does. */
    public static String colorForLabel(final String label) {
        for (final Entry entry : ENTRIES) {
            if (entry.label().equals(label)) {
                return entry.color();
            }
        }
        return OTHER.color();
    }

    /**
     * Appends the {@code "legend"} JSON array for the POS codes that actually appear in a diagram:
     * a swatch for each specific category that is used, plus the catch-all {@code "Other"} entry when
     * there is at least one used code that no specific category covers.
     */
    public static void appendLegendJson(final StringBuilder json, final Set<String> usedPosCodes) {
        json.append("\"legend\":[");
        boolean first = true;
        for (final Entry entry : ENTRIES) {
            // The catch-all "Other" entry is emitted only when a used code is not covered elsewhere.
            final boolean include = entry.posCodes().isEmpty()
                    ? hasOther(usedPosCodes)
                    : hasAny(usedPosCodes, entry.posCodes());
            if (include) {
                if (!first) {
                    json.append(',');
                }
                json.append("{\"color\":\"").append(entry.color()).append('"')
                        .append(",\"label\":\"").append(entry.label()).append('"')
                        .append(",\"description\":\"").append(entry.description()).append("\"}");
                first = false;
            }
        }
        json.append(']');
    }

    /** True when at least one of {@code used} appears in {@code posCodes}. */
    private static boolean hasAny(final Set<String> used, final Set<String> posCodes) {
        for (final String code : posCodes) {
            if (used.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** True when at least one code in {@code used} is not covered by any specific (non-catch-all) entry. */
    private static boolean hasOther(final Set<String> used) {
        final Set<String> covered = new HashSet<>();
        for (final Entry e : ENTRIES) {
            if (!e.posCodes().isEmpty()) {
                covered.addAll(e.posCodes());
            }
        }
        for (final String code : used) {
            if (!covered.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** The first entry whose POS codes contain {@code pos}, else the trailing catch-all entry. */
    private static Entry entryFor(final String pos) {
        for (final Entry entry : ENTRIES) {
            if (entry.posCodes().contains(pos)) {
                return entry;
            }
        }
        return OTHER;
    }
}
