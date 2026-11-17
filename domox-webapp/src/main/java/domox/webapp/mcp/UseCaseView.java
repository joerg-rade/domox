package domox.webapp.mcp;

/**
 * One use case of the corpus, as the sub-domain pass sees it.
 *
 * @param documentId             the use-case document id
 * @param title                  the document's title
 * @param fileName               the document's file name (e.g. {@code UC02_PetGroomingServices.md})
 * @param candidateCount         how many class candidates are evidenced by this use case
 * @param dominantCandidateCount how many class candidates this use case is the <em>strongest</em>
 *                               evidence for — the ones a partition would assign to it
 */
public record UseCaseView(
        long documentId,
        String title,
        String fileName,
        long candidateCount,
        long dominantCandidateCount) {
}
