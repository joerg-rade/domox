package domox.dom.rules;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.nlp.SentenceRepository;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies the {@link RuleMatch} lifecycle owned by {@link RuleMatches}: the persistence guard
 * (blocked use-case nouns, signature de-duplication) and the Phase-2 entry points, which are thin
 * facades over {@link CandidateFactory}.
 *
 * <p>The match-to-candidate mapping itself is covered by {@link CandidateFactoryTest} — the two
 * concerns used to share one class.</p>
 */
@ExtendWith(MockitoExtension.class)
class RuleMatchesTest {

    private RuleMatches classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @Mock
    FactoryService mockFactoryService;

    @Mock
    RuleMatchRepository mockRuleMatchRepository;

    @Mock
    SentenceRepository mockSentenceRepository;

    @Mock
    NlpProperties mockNlpProperties;

    @Mock
    CandidateFactory mockCandidateFactory;

    @BeforeEach
    public void setUp() {
        classUnderTest = new RuleMatches(
                mockRepositoryService,
                mockFactoryService,
                mockRuleMatchRepository,
                mockSentenceRepository,
                mockNlpProperties,
                mockCandidateFactory);
    }

    // ============ Phase 2 entry points (delegation) ============

    @Test
    void createCandidatesFromMatches_readsAllPersistedMatchesAndDelegatesToTheFactory() {
        // given
        final RuleMatch persisted = match("ClassCdd", "Customer", null, null);
        final ClassCdd classCdd = new ClassCdd();
        when(mockRuleMatchRepository.findAll()).thenReturn(List.of(persisted));
        when(mockCandidateFactory.createCandidatesFrom(List.of(persisted))).thenReturn(List.of(classCdd));

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFromMatches();

        // then
        assertThat(result).containsExactly(classCdd);
        verify(mockCandidateFactory).createCandidatesFrom(List.of(persisted));
    }

    @Test
    void createCandidatesFrom_delegatesToTheFactory() {
        // given
        final RuleMatch persisted = match("ClassCdd", "Customer", null, null);
        final ClassCdd classCdd = new ClassCdd();
        when(mockCandidateFactory.createCandidatesFrom(List.of(persisted))).thenReturn(List.of(classCdd));

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(List.of(persisted));

        // then
        assertThat(result).containsExactly(classCdd);
    }

    @Test
    void createCandidatesFrom_withDomainModel_delegatesToTheFactory() {
        // given
        final RuleMatch persisted = match("ClassCdd", "Customer", null, null);
        final ClassCdd classCdd = new ClassCdd();
        final DomainModel model = new DomainModel();
        when(mockCandidateFactory.createCandidatesFrom(List.of(persisted), model)).thenReturn(List.of(classCdd));

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(List.of(persisted), model);

        // then
        assertThat(result).containsExactly(classCdd);
        verify(mockCandidateFactory).createCandidatesFrom(List.of(persisted), model);
    }

    // ============ the blocked-noun guard ============

    @Test
    void isBlockedUseCaseNoun_matchesSingularLemmaCaseInsensitively() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("step", "condition"));

        // expect
        assertThat(classUnderTest.isBlockedUseCaseNoun("Step")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("CONDITION")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("Customer")).isFalse();
        assertThat(classUnderTest.isBlockedUseCaseNoun(null)).isFalse();
    }

    @Test
    void create_returnsNullForBlockedUseCaseNoun() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("actor"));
        // ClassCdd with blocked name "Actor"

        // when
        final RuleMatch result = classUnderTest.create(null, "TDR11", "ClassCdd", "Actor", null, null, null);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(mockRepositoryService);
    }

    @Test
    void create_persistsUnblockedClassMatch() {
        // given
        final RuleMatch persisted = new RuleMatch();
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("actor"));
        when(mockFactoryService.detachedEntity(RuleMatch.class)).thenReturn(persisted);
        // ClassCdd with non-blocked name "Customer"

        // when
        final RuleMatch result = classUnderTest.create(null, "TDR11", "ClassCdd", "Customer", null, null, null);

        // then — passes through the guard and returns the persisted match
        assertThat(result).isNotNull();
        assertThat(result).isSameAs(persisted);
        assertThat(result.getCandidateName()).isEqualTo("Customer");
        assertThat(result.getCandidateType()).isEqualTo("ClassCdd");
        verify(mockFactoryService).detachedEntity(RuleMatch.class);
        verify(mockRepositoryService).persist(persisted);
    }

    @Test
    void create_skipsPersistingWhenDuplicateSignatureExists() {
        // given — an existing match already present for the same
        // candidate name/type, rule class, description and related candidate.
        final RuleMatch existing = new RuleMatch();
        existing.setRuleClassName("TDR11");
        existing.setCandidateType("PropertyCdd");
        existing.setCandidateName("range");
        existing.setRelatedCandidateType("ClassCdd");
        existing.setRelatedCandidateName("ClassCdd");
        existing.setDescription("Entity.add(range)");
        when(mockRuleMatchRepository
                .findByCandidateNameAndCandidateTypeAndRuleClassNameAndDescriptionAndRelatedCandidateNameAndRelatedCandidateType(
                        "range", "PropertyCdd", "TDR11", "Entity.add(range)", "ClassCdd", "ClassCdd"))
                .thenReturn(Collections.singletonList(existing));

        // when — create the same signature again
        final RuleMatch result = classUnderTest.create(
                null, "TDR11", "PropertyCdd", "range", "ClassCdd", "ClassCdd", "Entity.add(range)");

        // then — no second match is persisted; the existing one is returned
        assertThat(result).isSameAs(existing);
        verifyNoInteractions(mockFactoryService);
        verify(mockRepositoryService, never()).persist(any());
    }

    @Test
    void create_persistsWhenNoDuplicateExists() {
        // given — repository reports no existing match with this signature
        when(mockRuleMatchRepository
                .findByCandidateNameAndCandidateTypeAndRuleClassNameAndDescriptionAndRelatedCandidateNameAndRelatedCandidateType(
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        final RuleMatch persisted = new RuleMatch();
        when(mockFactoryService.detachedEntity(RuleMatch.class)).thenReturn(persisted);

        // when
        final RuleMatch result = classUnderTest.create(
                null, "TDR6", "PropertyCdd", "range", "ClassCdd", "ClassCdd", "Entity.add(range)");

        // then — a fresh match is persisted and returned
        assertThat(result).isSameAs(persisted);
        verify(mockRepositoryService).persist(persisted);
    }

    // -- helper

    private static RuleMatch match(String candidateType, String candidateName, String relatedType, String relatedName) {
        final RuleMatch match = new RuleMatch();
        match.setCandidateType(candidateType);
        match.setCandidateName(candidateName);
        match.setRelatedCandidateType(relatedType);
        match.setRelatedCandidateName(relatedName);
        return match;
    }
}
