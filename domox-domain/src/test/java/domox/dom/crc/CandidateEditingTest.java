package domox.dom.crc;

import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.Editing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guardrail test: every concrete {@link Candidate} subclass that participates in the review
 * pipeline must opt into inline editing via {@code @DomainObject(editing = Editing.ENABLED)}.
 * <p>
 * The subclass set is read from {@link Candidate#SUBCLASSES} — the single source of truth also
 * used by {@code Reviews} to iterate unprocessed candidates — so this test can never drift away
 * from the set the application actually reviews.  Adding a new subclass there automatically makes
 * it covered here.
 * <p>
 * Apache Causeway defaults editing to the (non-editable) configured state, so a subclass that
 * forgets this flag renders the whole object read-only.  The concrete symptom is an inherited,
 * otherwise-editable property (e.g. {@code Diagram Hop Depth} on the {@link Candidate} base)
 * being silently disabled for exactly that one subclass — the bug fixed for {@link ActionCdd}.
 */
class CandidateEditingTest {

    private static final List<Class<? extends Candidate>> CANDIDATE_SUBCLASSES = Candidate.SUBCLASSES;

    @Test
    void everyCandidateSubclassOptsIntoInlineEditing() {
        for (final Class<? extends Candidate> type : CANDIDATE_SUBCLASSES) {
            final DomainObject annotation = type.getAnnotation(DomainObject.class);
            assertNotNull(annotation,
                    type.getSimpleName() + " must be annotated @DomainObject");
            assertEquals(
                    Editing.ENABLED, annotation.editing(),
                    type.getSimpleName()
                            + " must declare editing = Editing.ENABLED so properties inherited from "
                            + "Candidate (e.g. Diagram Hop Depth) remain editable; otherwise the whole "
                            + "object is rendered read-only");
        }
    }
}