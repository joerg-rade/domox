package domox.dom.requirements;

/**
 * One use case a candidate's evidence comes from: a {@link Document} the corpus was ingested from,
 * and how many distinct sentences of that document back the candidate.
 *
 * <p>This is the <b>provenance</b> of a candidate — <em>which part of the business talks about it</em>
 * — as opposed to {@code ContextSentence} (the sentences themselves) and the lexical graph (the
 * words that co-occur with it).  It is the evidence a sub-domain partition is built on: a candidate
 * whose evidence comes from one use case is owned by that area, while one whose evidence is spread
 * over most use cases is a shared, cross-cutting concept rather than any one area's property.</p>
 *
 * @param documentId    the id of the use-case document
 * @param documentTitle the document's title
 * @param fileName      the document's file name (e.g. {@code UC02_PetGroomingServices.md}), from
 *                      which a human-readable area name can be derived
 * @param sentenceCount how many distinct sentences of that document back the candidate
 */
public record UseCaseProvenance(
        long documentId,
        String documentTitle,
        String fileName,
        long sentenceCount) {
}
