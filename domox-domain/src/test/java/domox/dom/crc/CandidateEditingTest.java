package domox.dom.crc;

import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.Editing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guardrail test: every concrete {@link Candidate} subclass must opt into inline editing via
 * {@code @DomainObject(editing = Editing.ENABLED)}.
 * <p>
 * Apache Causeway defaults editing to the (non-editable) configured state, so a subclass that
 * forgets this flag renders the whole object read-only.  The concrete symptom is an inherited,
 * otherwise-editable property (e.g. {@code Diagram Hop Depth} on the {@link Candidate} base)
 * being silently disabled for exactly that one subclass — the bug fixed for {@link ActionCdd}.
 * <p>
 * Kept in sync with the set in {@code Reviews.CANDIDATE_SUBCLASSES}.
 */
class CandidateEditingTest {

    private static final List<Class<? extends Candidate>> CANDIDATE_SUBCLASSES = List.of(
            ClassCdd.class,
            ActionCdd.class,
            PropertyCdd.class,
            AssociationCdd.class,
            PackageCdd.class,
            ParameterCdd.class);

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