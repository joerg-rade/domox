package domox.dom.crc;

import java.util.Comparator;

/**
 * Sorts {@link Candidate} elements by {@link Candidate#getRuleMatchCount()}
 * in <em>descending</em> order — candidates with the most evidence (rule
 * matches) appear first.
 * <p>
 * Designed for use with Causeway's {@code @CollectionLayout(sortedBy = ...)}
 * on collections whose element type extends {@link Candidate}.
 */
@SuppressWarnings("rawtypes")
public class CandidateByRuleMatchCountComparator implements Comparator {

    @Override
    public int compare(final Object a, final Object b) {
        final int countA = a instanceof Candidate ca ? ca.getRuleMatchCount() : 0;
        final int countB = b instanceof Candidate cb ? cb.getRuleMatchCount() : 0;
        // descending: most rule matches first
        return Integer.compare(countB, countA);
    }
}
