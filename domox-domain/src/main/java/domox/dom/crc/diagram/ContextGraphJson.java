package domox.dom.crc.diagram;

import domox.DomainModule;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Renders a candidate's <em>context</em> as a D3 force-layout graph: one hub node carrying the
 * candidate's name and one node per {@link Sentence} in which that word actually occurs — the
 * "which sentences is this candidate talked about in" question, next to the lexical graph
 * ({@link CandidateD3Graph}) that answers "which other words co-occur with it".
 * <p>
 * The two pure halves of that feature live together here: the matching rule that turns a set of
 * looked-up typed dependencies into the sentences that matched ({@link #occurrences}) and the D3
 * JSON those occurrences are rendered into ({@link #render}).  Both are static and free of Spring
 * and JPA, so the whole rule is unit-testable without a container; the caller supplies the
 * look-up as a {@link Function} ({@code TypedDependencyRepository#findByLemma}).  The thin
 * orchestrator that owns the repository is {@code domox.dom.requirements.ContextGraphs}.
 * <p>
 * The payload uses the same node/link/legend vocabulary as the other D3 renderers, so the shared
 * client ({@code d3-integration.mjs}) renders it unchanged — including the click-through: a node
 * carrying {@code candidateId} + {@code objectType} opens that object's Causeway page.
 */
public final class ContextGraphJson {

    /** The hub node's id — the one fixed id in the payload; every other node is {@code sentence:<id>}. */
    public static final String HUB_NODE_ID = "candidate";

    /** Prefix of a sentence node's id, followed by the sentence's own id. */
    public static final String SENTENCE_NODE_ID_PREFIX = "sentence:";

    /** The hub is the largest node the shared sizing produces ({@code NODE_BASE_RADIUS * MAX_SCALE}). */
    public static final int HUB_RADIUS =
            (int) Math.round(DiagramNodeSizing.NODE_BASE_RADIUS * DiagramNodeSizing.MAX_SCALE);

    /** A sentence label longer than this is cut at the last blank that still fits. */
    public static final int LABEL_MAX_CHARS = 120;

    private static final String ELLIPSIS = "…";

    private ContextGraphJson() {
    }

    //region > input/output records

    /**
     * The hub node — the candidate whose context is rendered.
     *
     * @param name       the candidate name, shown as the node label
     * @param id         the candidate's id (omitted from the payload when {@code null}, which also
     *                   disables the client's click-through for the hub)
     * @param objectType the candidate's Causeway object type (see {@link #objectTypeOf(Class)})
     * @param approved   whether the candidate is already approved, which the client renders as a
     *                   thicker node border (the shared client's only use of this flag)
     */
    public record Center(String name, Long id, String objectType, boolean approved) {
    }

    /**
     * One sentence in which the candidate's name occurs.
     *
     * @param id    the sentence's id
     * @param text  the sentence text (the label is truncated for display)
     * @param pos   the Penn POS code of the occurrence the sentence matched on — the last matched
     *              word of the name, i.e. the head of a compound such as {@code PetShop}
     * @param count how many typed dependencies in this sentence matched, which sizes the node
     */
    public record SentenceOccurrence(long id, String text, String pos, int count) {
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

    /**
     * A sentence text cut down to a node label: stripped, and cut at the last blank that fits
     * within {@link #LABEL_MAX_CHARS} when it is longer (appending an ellipsis).  Sentences are
     * often long, and a node label is drawn on the canvas, not wrapped.
     */
    public static String truncate(final String text) {
        if (text == null) {
            return "";
        }
        final String stripped = text.strip();
        if (stripped.length() <= LABEL_MAX_CHARS) {
            return stripped;
        }
        final String cut = stripped.substring(0, LABEL_MAX_CHARS);
        final int lastBlank = cut.lastIndexOf(' ');
        return (lastBlank > 0 ? cut.substring(0, lastBlank) : cut) + ELLIPSIS;
    }

    /**
     * The Causeway object type of an entity class — its {@link Named} value, or the
     * {@code domox.} default when it carries none.  Mirrors {@code Candidate#getObjectType()},
     * which cannot be reused here because this also has to answer for {@link Sentence}.
     */
    public static String objectTypeOf(final Class<?> type) {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            final Named named = t.getAnnotation(Named.class);
            if (named != null && !named.value().isEmpty()) {
                return named.value();
            }
        }
        return DomainModule.NAMESPACE + "." + type.getSimpleName();
    }

    //endregion

    //region > rendering

    /**
     * The D3 payload: the hub node, one node per occurrence, an edge from the hub to each of them,
     * and the POS legend restricted to the codes that actually occur.
     *
     * @param center             the hub; a {@code null} center yields {@code null} (no payload)
     * @param sentenceObjectType the object type of a sentence node (see {@link #objectTypeOf(Class)})
     * @param occurrences        the matching sentences; {@code null} is treated as none
     */
    public static String render(final Center center, final String sentenceObjectType,
                                final List<SentenceOccurrence> occurrences) {
        if (center == null) {
            return null;
        }
        final List<SentenceOccurrence> sentences = occurrences != null ? occurrences : List.of();
        final String hubPos = dominantPos(sentences);
        final int maxCount = sentences.stream()
                .mapToInt(SentenceOccurrence::count)
                .max()
                .orElse(DiagramNodeSizing.MIN_MATCH_COUNT);

        final StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"nodes\":[");
        appendNode(json, HUB_NODE_ID, center.name(), hubPos, HUB_RADIUS, center.approved(),
                center.id(), center.objectType());
        for (final SentenceOccurrence occurrence : sentences) {
            json.append(',');
            appendNode(json, SENTENCE_NODE_ID_PREFIX + occurrence.id(), truncate(occurrence.text()),
                    occurrence.pos(), DiagramNodeSizing.radiusFor(occurrence.count(), maxCount), false,
                    occurrence.id(), sentenceObjectType);
        }
        json.append("],\"links\":[");
        appendLinks(json, sentences);
        json.append("],");
        PosLegend.appendLegendJson(json, usedPosCodes(hubPos, sentences));
        json.append('}');
        return json.toString();
    }

    private static void appendNode(final StringBuilder json, final String id, final String name,
                                   final String pos, final int radius, final boolean approved,
                                   final Long objectId, final String objectType) {
        final String posCode = pos != null ? pos : "";
        final boolean linkable = objectId != null && objectType != null;
        json.append('{');
        json.append("\"id\":\"").append(DiagramJson.escape(id)).append('"');
        json.append(",\"name\":\"").append(DiagramJson.escape(name)).append('"');
        json.append(",\"pos\":\"").append(DiagramJson.escape(posCode)).append('"');
        json.append(",\"color\":\"").append(PosPalette.color(posCode)).append('"');
        json.append(",\"r\":").append(radius);
        json.append(",\"approved\":").append(approved);
        json.append(",\"isCandidateColumn\":").append(linkable);
        if (linkable) {
            json.append(",\"candidateId\":").append(objectId);
            json.append(",\"objectType\":\"").append(DiagramJson.escape(objectType)).append('"');
        }
        json.append('}');
    }

    /** One edge per sentence, from the hub to that sentence. */
    private static void appendLinks(final StringBuilder json, final List<SentenceOccurrence> sentences) {
        for (int i = 0; i < sentences.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"source\":\"").append(HUB_NODE_ID).append("\",\"target\":\"")
                    .append(SENTENCE_NODE_ID_PREFIX).append(sentences.get(i).id()).append("\"}");
        }
    }

    /**
     * The POS code the hub is labelled with: the code the most sentences matched on (the first one
     * in encounter order wins a tie), or blank when no sentence carried a POS code at all.
     */
    private static String dominantPos(final List<SentenceOccurrence> sentences) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final SentenceOccurrence occurrence : sentences) {
            if (occurrence.pos() != null && !occurrence.pos().isEmpty()) {
                counts.merge(occurrence.pos(), 1, Integer::sum);
            }
        }
        String dominant = "";
        int best = 0;
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                dominant = entry.getKey();
                best = entry.getValue();
            }
        }
        return dominant;
    }

    /** The POS codes the payload actually uses, so the client legend lists nothing irrelevant. */
    private static Set<String> usedPosCodes(final String hubPos, final List<SentenceOccurrence> sentences) {
        final Set<String> used = new LinkedHashSet<>();
        if (hubPos != null && !hubPos.isEmpty()) {
            used.add(hubPos);
        }
        for (final SentenceOccurrence occurrence : sentences) {
            if (occurrence.pos() != null && !occurrence.pos().isEmpty()) {
                used.add(occurrence.pos());
            }
        }
        return used;
    }

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
        private final Map<String, String> posByPart = new LinkedHashMap<>();
        private int count;

        private Accumulator(final Sentence sentence) {
            this.sentence = sentence;
        }

        private void record(final String part, final TypedDependency td) {
            partsSeen.add(part);
            count++;
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
            return new SentenceOccurrence(sentence.getId(), sentence.getText(), posFor(parts), count);
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
            final PartOfSpeechType pos = part.equals(DiagramLemmas.lower(td.getGovernorLemma()))
                    ? td.getGovernorPos()
                    : td.getDependentPos();
            return pos != null && pos.getCode() != null ? pos.getCode() : "";
        }
    }

    //endregion
}


