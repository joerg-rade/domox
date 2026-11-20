package domox.dom.crc.service;
import domox.dom.AbstractEntity;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import domox.dom.crc.repository.SubDomainRepository;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Guardrail for {@link SubDomains} — the sub-domain reference data and the programmatic half of the
 * sub-domain pass ({@code createSubDomain}, {@code assignCandidatesToSubDomain},
 * {@code clearCandidateSubDomain} on the MCP side).
 *
 * <p>The behaviour pinned here is mostly <em>idempotence</em>: the pass is re-run over a corpus after
 * the clustering has been adjusted, so {@code findOrCreate} must not create a second sub-domain for a
 * name it already has, and {@code assignAll} must be safe to repeat.  The other half is the
 * <em>reason</em> every write now carries — it is recorded on the row the change was made to, so the
 * justification survives the transcript.</p>
 *
 * <p>Written after a mutation run showed the whole class at 0 % (44 mutants, none covered): the only
 * thing that touched it was a webapp integration test, which pitest cannot see.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubDomainsTest {

    private SubDomains classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @Mock
    SubDomainRepository mockSubDomainRepository;

    @BeforeEach
    public void setUp() {
        classUnderTest = new SubDomains(mockRepositoryService, mockSubDomainRepository);
    }

    // -- create / createClassified

    @Test
    void create_defaultsTheClassificationWhenNoneIsGiven() {
        // when — a reviewer who does not know yet must not have to guess
        final SubDomain result = classUnderTest.create("Checkout", null);

        // then
        assertThat(result.getClassification()).isEqualTo(SubDomainClassification.UNCLASSIFIED);
        assertThat(result.getName()).isEqualTo("Checkout");
        verify(mockRepositoryService).persist(result);
    }

    @Test
    void create_keepsTheClassificationItWasGiven() {
        // when
        final SubDomain result = classUnderTest.create("Checkout", SubDomainClassification.CORE);

        // then
        assertThat(result.getClassification()).isEqualTo(SubDomainClassification.CORE);
    }

    @Test
    void createClassified_trimsTheName() {
        // when
        final SubDomain result = classUnderTest.createClassified("  Checkout  ", SubDomainClassification.SUPPORTING);

        // then
        assertThat(result.getName()).isEqualTo("Checkout");
    }

    // -- findByName / findByNameIgnoreCase

    @Test
    void findByName_isNullSafeAndDoesNotHitTheRepository() {
        // when / then
        assertThat(classUnderTest.findByName(null)).isNull();
        verifyNoInteractions(mockSubDomainRepository);
    }

    @Test
    void findByName_delegatesToTheRepository() {
        // given
        final SubDomain existing = subDomain("Checkout", SubDomainClassification.CORE);
        when(mockSubDomainRepository.findByName("Checkout")).thenReturn(existing);

        // when / then
        assertThat(classUnderTest.findByName("Checkout")).isSameAs(existing);
    }

    @Test
    void findByNameIgnoreCase_returnsNullWhenThereIsNoMatch() {
        // given
        when(mockSubDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc("checkout")).thenReturn(List.of());

        // when / then
        assertThat(classUnderTest.findByNameIgnoreCase("checkout")).isNull();
    }

    @Test
    void findByNameIgnoreCase_isNullSafeAndTrimsTheName() {
        // given
        when(mockSubDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc("Checkout")).thenReturn(List.of());

        // when / then — a null name must not reach the repository at all
        assertThat(classUnderTest.findByNameIgnoreCase(null)).isNull();
        assertThat(classUnderTest.findByNameIgnoreCase("  Checkout  ")).isNull();
        verify(mockSubDomainRepository).findAllByNameIgnoreCaseOrderByIdAsc("Checkout");
    }

    @Test
    void findByNameIgnoreCase_takesTheFirstOfSeveralMatches() {
        // given — nothing constrains the name to be unique, so a duplicate must not blow up
        final SubDomain first = subDomain("Checkout", SubDomainClassification.CORE);
        final SubDomain second = subDomain("checkout", SubDomainClassification.GENERIC);
        when(mockSubDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc("Checkout"))
                .thenReturn(List.of(first, second));

        // when / then
        assertThat(classUnderTest.findByNameIgnoreCase("Checkout")).isSameAs(first);
    }

    // -- findOrCreate (idempotent: the pass is re-run over a corpus)

    @Test
    void findOrCreate_createsTheSubDomainAndRecordsTheReason() {
        // given
        when(mockSubDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc("Checkout")).thenReturn(List.of());

        // when
        final SubDomain result = classUnderTest.findOrCreate(
                "Checkout", SubDomainClassification.CORE, "core: the ordering capability");

        // then
        assertThat(result.getName()).isEqualTo("Checkout");
        assertThat(result.getClassification()).isEqualTo(SubDomainClassification.CORE);
        assertThat(result.getRationale()).as("the write must carry its justification")
                .isEqualTo("core: the ordering capability");
        verify(mockRepositoryService, atLeastOnce()).persist(result);
    }

    @Test
    void findOrCreate_reusesAnExistingSubDomainCaseInsensitively() {
        // given
        final SubDomain existing = subDomain("Checkout", SubDomainClassification.CORE);
        when(mockSubDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc("checkout"))
                .thenReturn(List.of(existing));

        // when
        final SubDomain result = classUnderTest.findOrCreate(
                "checkout", SubDomainClassification.GENERIC, "re-run of the pass");

        // then — the same row, not a second one, and the area keeps the classification it already has
        assertThat(result).as("a re-run must not create a second sub-domain").isSameAs(existing);
        assertThat(result.getClassification()).as("an existing area keeps its classification")
                .isEqualTo(SubDomainClassification.CORE);
        assertThat(result.getRationale()).as("the re-run still records why it was touched")
                .isEqualTo("re-run of the pass");
        verify(mockRepositoryService, atLeastOnce()).persist(existing);
    }

    @Test
    void findOrCreate_normalisesABlankReasonToNull() {
        // given
        when(mockSubDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc("Checkout")).thenReturn(List.of());

        // when — the "must not be blank" rule lives at the MCP boundary, not here
        final SubDomain result = classUnderTest.findOrCreate("Checkout", SubDomainClassification.CORE, "   ");

        // then
        assertThat(result.getRationale()).isNull();
        verify(mockRepositoryService, atLeastOnce()).persist(result);
    }

    // -- classify

    @Test
    void classify_setsTheClassificationAndRecordsTheReason() {
        // given
        final SubDomain subDomain = subDomain("Checkout", SubDomainClassification.UNCLASSIFIED);

        // when
        final SubDomain result = classUnderTest.classify(
                subDomain, SubDomainClassification.CORE, "core: the ordering capability");

        // then
        assertThat(result).isSameAs(subDomain);
        assertThat(result.getClassification()).isEqualTo(SubDomainClassification.CORE);
        assertThat(result.getRationale()).as("a re-classification must carry its justification")
                .isEqualTo("core: the ordering capability");
        verify(mockRepositoryService).persist(subDomain);
    }

    @Test
    void classify_defaultsToUnclassifiedWhenNoneIsGiven() {
        // given
        final SubDomain subDomain = subDomain("Checkout", SubDomainClassification.CORE);

        // when
        final SubDomain result = classUnderTest.classify(subDomain, null, "reviewer undecided");

        // then — the area goes back to visibly undecided rather than keeping a stale guess
        assertThat(result.getClassification()).isEqualTo(SubDomainClassification.UNCLASSIFIED);
    }

    // -- assignAll / clearSubDomain

    @Test
    void assignAll_assignsEveryCandidateAndRecordsTheReasonOnEach() {
        // given
        final SubDomain subDomain = subDomain("Checkout", SubDomainClassification.CORE);
        final ClassCdd first = classCandidate("Order");
        final ClassCdd second = classCandidate("Payment");

        // when
        final List<ClassCdd> assigned =
                classUnderTest.assignAll(List.of(first, second), subDomain, "cluster 3: ordering");

        // then — the candidates come back in the order given, each carrying the reason
        assertThat(assigned).containsExactly(first, second);
        assertThat(first.getSubDomain()).isSameAs(subDomain);
        assertThat(second.getSubDomain()).isSameAs(subDomain);
        assertThat(first.getRationale()).isEqualTo("cluster 3: ordering");
        assertThat(second.getRationale()).isEqualTo("cluster 3: ordering");
        verify(mockRepositoryService).persist(first);
        verify(mockRepositoryService).persist(second);
    }

    @Test
    void assignAll_skipsNullsAndToleratesANullList() {
        // given
        final SubDomain subDomain = subDomain("Checkout", SubDomainClassification.CORE);
        final ClassCdd first = classCandidate("Order");

        // when
        final List<ClassCdd> withNull =
                classUnderTest.assignAll(Arrays.asList(first, null), subDomain, "cluster 3");

        // then — the null slot is dropped, not dereferenced
        assertThat(withNull).containsExactly(first);
        assertThat(classUnderTest.assignAll(null, subDomain, "cluster 3")).isEmpty();
    }

    @Test
    void clearSubDomain_removesTheAssignmentAndRecordsTheReason() {
        // given
        final ClassCdd candidate = classCandidate("Order");
        candidate.setSubDomain(subDomain("Checkout", SubDomainClassification.CORE));

        // when
        final ClassCdd result = classUnderTest.clearSubDomain(candidate, "not a domain concept after all");

        // then
        assertThat(result).isSameAs(candidate);
        assertThat(result.getSubDomain()).isNull();
        assertThat(result.getRationale()).as("a removal must carry its justification")
                .isEqualTo("not a domain concept after all");
        verify(mockRepositoryService).persist(candidate);
    }

    // -- listAll / listAllClasses / listUnassigned / listAssignedTo

    @Test
    void listAll_returnsEverySubDomain() {
        // given
        final SubDomain checkout = subDomain("Checkout", SubDomainClassification.CORE);
        when(mockRepositoryService.allInstances(SubDomain.class)).thenReturn(List.of(checkout));

        // when / then
        assertThat(classUnderTest.listAll()).containsExactly(checkout);
    }

    @Test
    void listAllClasses_ordersById() {
        // given
        final ClassCdd later = classCandidate("Payment");
        setId(later, 20L);
        final ClassCdd earlier = classCandidate("Order");
        setId(earlier, 5L);
        when(mockRepositoryService.allInstances(ClassCdd.class)).thenReturn(List.of(later, earlier));

        // when / then
        assertThat(classUnderTest.listAllClasses()).containsExactly(earlier, later);
    }

    @Test
    void listUnassigned_returnsOnlyTheCandidatesWithoutASubDomain() {
        // given
        final SubDomain subDomain = subDomain("Checkout", SubDomainClassification.CORE);
        setId(subDomain, 1L);
        final ClassCdd assigned = classCandidate("Order");
        setId(assigned, 10L);
        assigned.setSubDomain(subDomain);
        final ClassCdd unassigned = classCandidate("Payment");
        setId(unassigned, 11L);
        when(mockRepositoryService.allInstances(ClassCdd.class)).thenReturn(List.of(assigned, unassigned));

        // when / then
        assertThat(classUnderTest.listUnassigned()).containsExactly(unassigned);
    }

    @Test
    void listAssignedTo_returnsOnlyTheCandidatesOfThatSubDomain() {
        // given
        final SubDomain checkout = subDomain("Checkout", SubDomainClassification.CORE);
        setId(checkout, 1L);
        final SubDomain inventory = subDomain("Inventory", SubDomainClassification.SUPPORTING);
        setId(inventory, 2L);
        final ClassCdd order = classCandidate("Order");
        setId(order, 10L);
        order.setSubDomain(checkout);
        final ClassCdd stock = classCandidate("Stock");
        setId(stock, 11L);
        stock.setSubDomain(inventory);
        final ClassCdd unassigned = classCandidate("Payment");
        setId(unassigned, 12L);
        when(mockRepositoryService.allInstances(ClassCdd.class)).thenReturn(List.of(order, stock, unassigned));

        // when / then — matched by id, so a second instance of the same area would still count
        assertThat(classUnderTest.listAssignedTo(checkout)).containsExactly(order);
    }

    @Test
    void listAssignedTo_returnsNothingForANullSubDomain() {
        // when / then — nothing to match on, and the corpus is not even read
        assertThat(classUnderTest.listAssignedTo(null)).isEmpty();
        verifyNoInteractions(mockRepositoryService);
    }

    /** A class candidate with no sub-domain, as the corpus ingestion leaves it. */
    private static ClassCdd classCandidate(final String name) {
        return new ClassCdd(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** A sub-domain as the repository would return it — name and classification already set. */
    private static SubDomain subDomain(final String name, final SubDomainClassification classification) {
        final SubDomain subDomain = new SubDomain();
        subDomain.setName(name);
        subDomain.setClassification(classification);
        return subDomain;
    }

    /** Assigns the private JPA {@code id} field (normally set by the sequence generator). */
    private static void setId(final AbstractEntity entity, final long id) {
        try {
            final Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (final Exception e) {
            throw new RuntimeException("failed to set id on " + entity, e);
        }
    }
}
