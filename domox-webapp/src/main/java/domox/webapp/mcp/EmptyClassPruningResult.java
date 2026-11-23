package domox.webapp.mcp;

import domox.dom.crc.model.ClassCdd;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of the {@code rejectEmptyClasses} MCP tool: what the "no empty boxes" sweep rejected.
 *
 * <p>The sweep is the last step of the assembly pass: a class the reviewer approved but which carries
 * no approved attribute, responsibility or relation draws as an empty rectangle, so the export omits
 * it — and leaving the review saying {@code APPROVED} would make the review disagree with the model
 * it produced.  See {@code DomainModels#rejectEmptyClasses}.</p>
 *
 * @param rejectedCount how many approved classes were rejected
 * @param candidateIds  their ids, so the caller can read each decision back
 * @param message       a one-line summary, or the "nothing to do" note
 */
public record EmptyClassPruningResult(
        int rejectedCount,
        List<Long> candidateIds,
        String message) {

    /** No approved class was empty — the sweep is idempotent, so this is the re-run result too. */
    public static final String NOTHING_TO_REJECT_MESSAGE =
            "No approved class is empty: every approved class carries at least one approved "
                    + "attribute, responsibility or relation. Nothing to reject.";

    static EmptyClassPruningResult of(final List<ClassCdd> rejected) {
        if (rejected == null || rejected.isEmpty()) {
            return new EmptyClassPruningResult(0, List.of(), NOTHING_TO_REJECT_MESSAGE);
        }
        return new EmptyClassPruningResult(
                rejected.size(),
                rejected.stream().map(ClassCdd::getId).toList(),
                "Rejected " + rejected.size()
                        + " approved class(es) with no approved content (rationale INSUFFICIENT_INFORMATION): "
                        + names(rejected));
    }

    private static String names(final List<ClassCdd> rejected) {
        return rejected.stream()
                .map(ClassCdd::getCandidateName)
                .collect(Collectors.joining(", "));
    }
}
