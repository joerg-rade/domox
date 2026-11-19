package domox.dom.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The <b>edge label</b> of an association — the verb the association was extracted from.
 *
 * <p><b>Why it exists.</b> {@code CollaboratorCdd} stores only the two ends, so the exported diagram
 * labelled every edge with the candidate's own name, i.e. {@code source_target}
 * ({@code Customer_Premium}) — a name, not a relationship.  The verb survives in the rule match the
 * association was created from, because the association rules (TDR14–TDR23) write their own
 * explanation in the spec's shape:</p>
 *
 * <pre>
 * relationship.add(inventory (has) product)      →  has
 * relationship.add(customer (enroll in) class)   →  enroll in
 * relationship.add(pet (take) list)              →  take
 * </pre>
 *
 * <p><b>Why it is derived rather than stored.</b> The label <em>is</em> the evidence — storing a copy
 * on the entity would duplicate what the rule match already says and would need a schema change
 * ({@code UML_GAP_FIX_PLAN.md} §10).  Nothing is persisted, so a re-analysis updates the label with the
 * evidence.</p>
 *
 * <p><b>Why a pure collaborator.</b> It is a string shape with a fallback, it runs on every export, and
 * it is testable without a database or a model — the same shape as {@link MultiplicityEvidence}.</p>
 */
public final class AssociationLabel {

    /** The parenthesised verb group of {@code relationship.add(<source> (<verb>) <target>)}. */
    private static final Pattern VERB = Pattern.compile("\\(([A-Za-z][A-Za-z ]*)\\)");

    private AssociationLabel() {
    }

    /**
     * The label to draw on the association's edge.
     *
     * @param ruleMatches the association's own matches, in the order they were recorded
     * @param fallback    the candidate's name — used when no match records a verb, so the edge is never
     *                    left unlabelled
     * @return the recorded verb (e.g. {@code "has"}, {@code "enroll in"}), or {@code fallback}
     */
    public static String of(final List<RuleMatch> ruleMatches, final String fallback) {
        if (ruleMatches != null) {
            for (final RuleMatch match : ruleMatches) {
                final String verb = verbIn(match);
                if (verb != null) {
                    return verb;
                }
            }
        }
        return fallback;
    }

    /** The verb {@code match} recorded, or {@code null} when its description carries none. */
    private static String verbIn(final RuleMatch match) {
        if (match == null || match.getDescription() == null) {
            return null;
        }
        final Matcher matcher = VERB.matcher(match.getDescription());
        if (!matcher.find()) {
            return null;
        }
        final String verb = matcher.group(1).trim();
        return verb.isEmpty() ? null : verb;
    }
}
