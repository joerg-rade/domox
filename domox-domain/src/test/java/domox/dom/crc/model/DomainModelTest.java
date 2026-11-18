package domox.dom.crc.model;

import domox.dom.AbstractEntity;
import domox.dom.requirements.Corpora;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-level guardrail for {@link DomainModel#title()} — the model's only human-readable identity.
 * <p>
 * The model has no name column: the title is derived from the owning corpus (looked up through the
 * injected {@link Corpora} service) plus the size of the model's own candidate collections.  Three
 * cases matter, because all three are reachable in the running system:
 * <ul>
 *   <li>a model analysed from a corpus — named after that corpus;</li>
 *   <li>a model created by hand from {@code DomainModels#create()} — no corpus, no candidates,
 *       previously rendered as the literal {@code "//TODO"};</li>
 *   <li>a model whose service was never injected — must still render something usable.</li>
 * </ul>
 * The end-to-end path (a real corpus and a real model in the database) is covered by the webapp
 * integration tests; here the collaborator is a mock, so the assertions are about the <em>format</em>
 * and the null-handling, not about persistence.
 */
class DomainModelTest {

    // --- the owner half of the title ----------------------------------------

    @Test
    void titleNamesTheOwningCorpus() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning("Pet Shop Use Cases"));

        assertThat(model.title()).isEqualTo("Pet Shop Use Cases (empty)");
    }

    /**
     * A hand-created model owns no corpus.  The old title said {@code "//TODO"}; it must now say what
     * the row actually is, so that a stray model is recognisable in a list.
     */
    @Test
    void titleSaysUnattachedWhenNoCorpusOwnsTheModel() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning(null));

        assertThat(model.title()).isEqualTo("Unattached domain model (empty)");
    }

    /** No service injected (plain unit test / outside Causeway) — still a usable title. */
    @Test
    void titleIsUsableWithoutAnInjectedService() {
        assertThat(new DomainModel().title()).isEqualTo("Domain model (empty)");
    }

    /** The id disambiguates two models that happen to share an owner. */
    @Test
    void titleIncludesTheIdWhenAssigned() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning("Pet Shop Use Cases"));
        setId(model, 653L);

        assertThat(model.title()).isEqualTo("Pet Shop Use Cases #653 (empty)");
    }

    // --- the size half of the title -----------------------------------------

    @Test
    void titleCountsEveryCandidateKind() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning("Pet Shop Use Cases"));
        final ClassCdd store = new ClassCdd("Store", List.of(), List.of(), List.of());
        model.classList.add(store);
        model.attributeList.add(new AttributeCdd("openingHours", "String"));
        model.responsibilityList.add(new ResponsibilityCdd("browse", List.of(), "void"));
        model.collaboratorList.add(new CollaboratorCdd("has", store, store));

        assertThat(model.title()).isEqualTo(
                "Pet Shop Use Cases (classes: 1, attributes: 1, responsibilities: 1, collaborators: 1)");
    }

    /** A model with an owner but no candidates yet still says "empty" rather than four zeroes. */
    @Test
    void titlePrefersEmptyOverZeroCounts() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning("Pet Shop Use Cases"));

        assertThat(model.title()).contains("empty").doesNotContain("classes: 0");
    }

    /**
     * {@code title()} is called on every render, including for a half-built model, so a null
     * collection must count as none instead of throwing {@code NullPointerException}.
     */
    @Test
    void titleTreatsNullCollectionsAsEmpty() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning("Pet Shop Use Cases"));
        model.classList = null;
        model.attributeList = null;
        model.responsibilityList = null;
        model.collaboratorList = null;

        assertThat(model.title()).isEqualTo("Pet Shop Use Cases (empty)");
    }

    /** Only the populated kinds are reported as non-zero; the others still appear, at zero. */
    @Test
    void titleReportsZeroForTheUnpopulatedKinds() {
        final DomainModel model = new DomainModel();
        injectCorpora(model, corporaOwning("Pet Shop Use Cases"));
        model.classList.add(new ClassCdd("Store", List.of(), List.of(), List.of()));
        model.classList.add(new ClassCdd("Pet", List.of(), List.of(), List.of()));

        assertThat(model.title()).isEqualTo(
                "Pet Shop Use Cases (classes: 2, attributes: 0, responsibilities: 0, collaborators: 0)");
    }

    // --- helpers ------------------------------------------------------------

    private static Corpora corporaOwning(final String title) {
        final Corpora corpora = mock(Corpora.class);
        when(corpora.titleOf(any(DomainModel.class))).thenReturn(title);
        return corpora;
    }

    /** Assigns the private injected {@code corpora} field (normally set by Causeway's injector). */
    private static void injectCorpora(final DomainModel model, final Corpora corpora) {
        try {
            final Field field = DomainModel.class.getDeclaredField("corpora");
            field.setAccessible(true);
            field.set(model, corpora);
        } catch (final Exception e) {
            throw new RuntimeException("failed to inject Corpora into " + model, e);
        }
    }

    /** Assigns the private JPA {@code id} field (normally set by the sequence generator). */
    private static void setId(final DomainModel model, final long id) {
        try {
            final Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(model, id);
        } catch (final Exception e) {
            throw new RuntimeException("failed to set id on " + model, e);
        }
    }
}
