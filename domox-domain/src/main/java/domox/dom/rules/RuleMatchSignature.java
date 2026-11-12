package domox.dom.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The <em>candidate + rule signature</em> that decides whether two {@link RuleMatch}es describe the
 * same finding, and the de-duplication built on it.
 * <p>
 * The signature is the tuple {@code (ruleClassName, candidateType, candidateName,
 * relatedCandidateType, relatedCandidateName, description)}.  It used to be written out twice — once
 * as the in-memory comparison on {@code Candidate} ({@code sameRuleMatchSignature}) and once as the
 * repository query behind {@code RuleMatches.findExistingMatch} — with a javadoc note in each place
 * telling the reader to keep them in step.  This class is the single source of the in-memory half;
 * {@code Candidate} delegates to it, so the two cannot drift.
 * <p>
 * Static and free of any framework dependency, so it is unit-testable on its own.
 */
public final class RuleMatchSignature {

    private RuleMatchSignature() {
    }

    /**
     * Whether {@code a} and {@code b} describe the same finding.  Two {@code null}s are <em>not</em>
     * equal — a candidate never carries a {@code null} match, so treating it as a duplicate would
     * silently swallow real ones.
     */
    public static boolean equal(final RuleMatch a, final RuleMatch b) {
        return a != null
                && b != null
                && Objects.equals(a.getRuleClassName(), b.getRuleClassName())
                && Objects.equals(a.getCandidateType(), b.getCandidateType())
                && Objects.equals(a.getCandidateName(), b.getCandidateName())
                && Objects.equals(a.getRelatedCandidateType(), b.getRelatedCandidateType())
                && Objects.equals(a.getRelatedCandidateName(), b.getRelatedCandidateName())
                && Objects.equals(a.getDescription(), b.getDescription());
    }

    /** Whether any match in {@code matches} has the same signature as {@code match}. */
    public static boolean contains(final List<RuleMatch> matches, final RuleMatch match) {
        for (final RuleMatch existing : matches) {
            if (equal(existing, match)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The matches in {@code matches} with the signature-duplicates removed — the <em>first</em>
     * occurrence of a signature is kept and every later one dropped.  Always returns a fresh,
     * mutable list; the argument is never modified.
     */
    public static List<RuleMatch> deduplicate(final List<RuleMatch> matches) {
        final List<RuleMatch> deduped = new ArrayList<>(matches.size());
        for (final RuleMatch current : matches) {
            if (!contains(deduped, current)) {
                deduped.add(current);
            }
        }
        return deduped;
    }
}
