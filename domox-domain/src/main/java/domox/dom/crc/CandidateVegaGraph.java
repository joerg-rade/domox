package domox.dom.crc;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import domox.dom.rules.RuleMatch;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.valuetypes.vega.applib.value.Vega;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a <b>Vega-Lite</b> bar chart for a single {@link Candidate}, parallel to
 * {@link CandidateDiagrams#renderDiagram(Candidate)} which produces a PDF.
 * <p>
 * The Vega graph provides an interactive, browser-native chart of the candidate's
 * rule-match profile — a horizontal bar chart showing how many rule matches exist per
 * {@code ruleClassName}, stacked above a small panel showing review status counts when
 * reviews exist.
 * <p>
 * The graph is regenerated on <em>every</em> call — it is never cached — because its
 * content reflects the current candidate set, which changes as candidates are reviewed,
 * approved, or rejected.
 */
@Named(DomainModule.NAMESPACE + ".CandidateVegaGraph")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateVegaGraph {

    private static final Logger log = LoggerFactory.getLogger(CandidateVegaGraph.class);

    @Programmatic
    public Vega renderGraph(final Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        final long id = candidate.getId() != null ? candidate.getId() : -1L;
        final String name = candidate.getCandidateName();
        log.debug("Rendering Vega graph for candidate #{} '{}'", id, name);
        try {
            final String vegaJsonSpec = buildVegaLiteSpec(candidate);
            log.debug("Vega JSON spec ({} chars): {}", vegaJsonSpec.length(), vegaJsonSpec);
            return Vega.valueOf(vegaJsonSpec);
        } catch (Exception e) {
            log.warn("Failed to render Vega graph for candidate #{} '{}'", id, name, e);
            return null;
        }
    }

    // -- helpers

    /**
     * Builds a Vega-Lite v6 {@code "vconcat"} chart with two stacked, independently
     * scaled panels:
     * <ul>
     *   <li><b>Panel 1 (top)</b> — A horizontal bar chart of rule-match counts grouped
     *       by {@code ruleClassName}, with count labels at bar ends.</li>
     *   <li><b>Panel 2 (bottom)</b> — A small bar chart of review status counts
     *       (APPROVED / REJECTED), visible only when reviews exist.</li>
     * </ul>
     * <p>
     * {@code vconcat} (rather than {@code layer}) is essential here: {@code layer}
     * would merge both charts onto one shared plot with a unified y-domain, mixing the
     * rule-class bars and the review-status bar onto the same axis.
     */
    private static String buildVegaLiteSpec(final Candidate candidate) {
        final List<Map<String, Object>> ruleMatchData = buildRuleMatchData(candidate);
        final List<Map<String, Object>> reviewData = buildReviewData(candidate);

        final List<Map<String, Object>> panels = new ArrayList<>();
        panels.add(buildRulePanel(ruleMatchData));

        if (!reviewData.isEmpty()) {
            panels.add(buildReviewPanel(reviewData));
        }

        final Map<String, Object> spec = new LinkedHashMap<>();
        // IMPORTANT: must be a schema version the Causeway Vega.Schema enum recognizes
        // (https://vega.github.io/schema/vega-lite/v6.json). Any other value — e.g. a
        // v5 URL — is classified Schema.NONE, which makes the Wicket panel render
        // nothing and load no Vega JS at all.
        spec.put("$schema", "https://vega.github.io/schema/vega-lite/v6.json");
        spec.put("title", candidate.getCandidateType() + ": " + candidate.getCandidateName());
        spec.put("width", 400);
        spec.put("autosize", Map.of("type", "fit", "contains", "padding"));
        spec.put("vconcat", panels);

        return toJson(spec);
    }

    /**
     * Groups rule matches by {@code ruleClassName} and returns a list of
     * {@code {"rule": "...", "count": N}} data points, sorted descending by count.
     */
    private static List<Map<String, Object>> buildRuleMatchData(final Candidate candidate) {
        final List<RuleMatch> matches = candidate.getRuleMatches();
        if (matches == null) {
            return List.of();
        }
        final Map<String, Long> grouped = matches.stream()
                .filter(rm -> rm.getRuleClassName() != null)
                .collect(Collectors.groupingBy(
                        RuleMatch::getRuleClassName,
                        Collectors.counting()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    final Map<String, Object> point = new LinkedHashMap<>();
                    point.put("rule", e.getKey());
                    point.put("count", e.getValue());
                    return point;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns review status counts as {@code {"status": "...", "count": N}} data
     * points, or an empty list when no reviews exist.
     */
    private static List<Map<String, Object>> buildReviewData(final Candidate candidate) {
        final List<Review> reviews = candidate.getReviews();
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        final Map<ReviewStatus, Long> grouped = reviews.stream()
                .filter(r -> r.getStatus() != null)
                .collect(Collectors.groupingBy(Review::getStatus, Collectors.counting()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<ReviewStatus, Long>comparingByValue().reversed())
                .map(e -> {
                    final Map<String, Object> point = new LinkedHashMap<>();
                    point.put("status", e.getKey().name());
                    point.put("count", e.getValue());
                    return point;
                })
                .collect(Collectors.toList());
    }

    // -- panel builders

    /**
     * Top panel: horizontal bar chart of rule-match counts — rule class name (nominal)
     * on Y, count (quantitative) on X, with count labels at bar ends.
     * <p>
     * Height scales with the number of distinct rule classes (28px per band, min 120px).
     */
    private static Map<String, Object> buildRulePanel(final List<Map<String, Object>> data) {
        final Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("height", Math.max(120, data.size() * 28));
        panel.put("data", Map.of("values", data));

        // Main bar mark
        final Map<String, Object> bar = new LinkedHashMap<>();
        bar.put("mark", Map.of(
                "type", "bar",
                "tooltip", true,
                "cursor", "pointer"));

        final Map<String, Object> barEncoding = new LinkedHashMap<>();
        barEncoding.put("y", Map.of(
                "field", "rule",
                "type", "nominal",
                "title", "Rule Class",
                "sort", "-x",
                "axis", Map.of("labelLimit", 300, "labelFontSize", 11)));
        barEncoding.put("x", Map.of(
                "field", "count",
                "type", "quantitative",
                "title", "Rule Matches",
                "axis", Map.of("grid", true)));
        barEncoding.put("color", Map.of("value", "#4c78a8"));
        bar.put("encoding", barEncoding);

        // Text labels at bar ends
        final Map<String, Object> text = new LinkedHashMap<>();
        text.put("mark", Map.of(
                "type", "text",
                "align", "left",
                "dx", 3,
                "fontSize", 10));
        final Map<String, Object> textEncoding = new LinkedHashMap<>();
        textEncoding.put("y", Map.of("field", "rule", "type", "nominal", "sort", "-x"));
        textEncoding.put("x", Map.of("field", "count", "type", "quantitative"));
        textEncoding.put("text", Map.of("field", "count", "type", "quantitative"));
        text.put("encoding", textEncoding);

        panel.put("layer", List.of(bar, text));
        return panel;
    }

    /**
     * Bottom panel: small horizontal bar chart of review status counts — status
     * (nominal) on Y, count (quantitative) on X, colour-coded green (APPROVED) /
     * red (REJECTED).
     * <p>
     * The colour legend is deliberately disabled ({@code "legend": null}): the y-axis
     * labels already name the statuses, so a separate legend would only duplicate them.
     */
    private static Map<String, Object> buildReviewPanel(final List<Map<String, Object>> data) {
        final Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("height", 80);
        panel.put("data", Map.of("values", data));

        final Map<String, Object> encoding = new LinkedHashMap<>();
        encoding.put("y", Map.of(
                "field", "status",
                "type", "nominal",
                "title", "Status",
                "sort", "-x",
                "axis", Map.of("labelFontSize", 11)));
        encoding.put("x", Map.of(
                "field", "count",
                "type", "quantitative",
                "title", "Reviews",
                "axis", Map.of("grid", true)));
        final Map<String, Object> colorEncoding = new LinkedHashMap<>();
        colorEncoding.put("field", "status");
        colorEncoding.put("type", "nominal");
        colorEncoding.put("scale", Map.of(
                "domain", List.of("APPROVED", "REJECTED"),
                "range", List.of("#2ca02c", "#d62728")));
        colorEncoding.put("legend", null);   // redundant — statuses are already on the y-axis
        encoding.put("color", colorEncoding);

        panel.put("mark", Map.of(
                "type", "bar",
                "tooltip", true));
        panel.put("encoding", encoding);

        return panel;
    }

    // -- JSON serialisation (lightweight, no external dep)

    /**
     * Converts a {@code Map<String, Object>} to a compact JSON string.
     * <p>
     * Handles strings, numbers, booleans, nulls, lists, and nested maps.
     * Strings are escaped minimally (backslash and double-quote only).
     */
    private static String toJson(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + escapeJson(s) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            final StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(toJson(entry.getKey().toString()));
                sb.append(":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            final StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (final Object item : list) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private static String escapeJson(final String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
