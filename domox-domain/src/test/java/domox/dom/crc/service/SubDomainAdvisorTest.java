package domox.dom.crc.service;

import domox.dom.crc.model.SubDomainClassification;
import domox.dom.requirements.UseCaseProvenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrail for {@link SubDomainAdvisor} — the advisory partition behind the {@code suggestSubDomains}
 * MCP tool.
 * <p>
 * The point of these tests is that the advice stays <em>conservative</em>: a concept evidenced by
 * most of the corpus must not be handed to one area, two areas must not be merged on a single shared
 * candidate, and core / supporting must never be guessed — the corpus cannot know them.
 */
class SubDomainAdvisorTest {

    private final SubDomainAdvisor advisor = new SubDomainAdvisor();

    // ============ naming ============

    @ParameterizedTest
    @CsvSource({
            "UC01_SellingPetProducts.md,   Selling Pet Products",
            "UC02_PetGroomingServices.md,  Pet Grooming Services",
            "UC08_OnlinePetStore.md,       Online Pet Store",
            "UC14_PetInsurance.md,         Pet Insurance",
            "noPrefix.md,                  no Prefix",
            "UC03_PetTraining,             Pet Training"
    })
    void humanise_turnsAUseCaseFileNameIntoAReadableAreaName(final String fileName, final String expected) {
        assertThat(SubDomainAdvisor.humanise(fileName)).isEqualTo(expected);
    }

    @Test
    void humanise_ofNoFileName_isEmpty() {
        assertThat(SubDomainAdvisor.humanise(null)).isEmpty();
        assertThat(SubDomainAdvisor.humanise("  ")).isEmpty();
    }

    // ============ partitioning ============

    @Test
    void propose_ofNoProfiles_proposesNothingAndSaysSo() {
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of());

        assertThat(proposal.clusters()).isEmpty();
        assertThat(proposal.crossCuttingCandidateIds()).isEmpty();
        assertThat(proposal.unevidencedCandidateIds()).isEmpty();
        assertThat(proposal.notes()).anySatisfy(note -> assertThat(note).contains("nothing to partition"));
    }

    @Test
    void propose_clustersCandidatesUnderTheUseCaseTheirEvidenceIsStrongestIn() {
        // given: 'Pet' is best evidenced by the selling use case, 'Groomer' by the grooming one
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Pet", useCase(2, "UC01_SellingPetProducts.md", 3),
                        useCase(1019, "UC02_PetGroomingServices.md", 1)),
                profile(2, "Toy", useCase(2, "UC01_SellingPetProducts.md", 2)),
                profile(3, "Groomer", useCase(1019, "UC02_PetGroomingServices.md", 2))));

        // then: two clusters, largest first, named after their use case
        assertThat(proposal.clusters()).hasSize(2);
        assertThat(proposal.clusters().get(0).proposedName()).isEqualTo("Selling Pet Products");
        assertThat(proposal.clusters().get(0).candidateIds()).containsExactly(1L, 2L);
        assertThat(proposal.clusters().get(1).proposedName()).isEqualTo("Pet Grooming Services");
        assertThat(proposal.clusters().get(1).candidateIds()).containsExactly(3L);
    }

    @Test
    void propose_reportsCandidatesEvidencedByManyUseCasesAsCrossCuttingAndClustersThemNowhere() {
        // given: 'Customer' is evidenced by five use cases, 'Toy' by one
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Customer",
                        useCase(2, "UC01.md", 1), useCase(3, "UC02.md", 1), useCase(4, "UC03.md", 1),
                        useCase(5, "UC04.md", 1), useCase(6, "UC05.md", 1)),
                profile(2, "Toy", useCase(2, "UC01.md", 2))));

        assertThat(proposal.crossCuttingCandidateIds()).containsExactly(1L);
        assertThat(proposal.clusters()).hasSize(1);
        assertThat(proposal.clusters().get(0).candidateIds()).containsExactly(2L);
        assertThat(proposal.notes()).anySatisfy(note -> assertThat(note).contains("Customer"));
    }

    @Test
    void propose_reportsCandidatesWithoutEvidenceAsUnevidenced() {
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Mystery"),
                profile(2, "Toy", useCase(2, "UC01.md", 2))));

        assertThat(proposal.unevidencedCandidateIds()).containsExactly(1L);
        assertThat(proposal.clusters()).hasSize(1);
        assertThat(proposal.clusters().get(0).candidateIds()).containsExactly(2L);
        assertThat(proposal.notes()).anySatisfy(note -> assertThat(note).contains("Mystery"));
    }

    @Test
    void propose_saysWhenACandidateWasAssignedOnDominantEvidenceOnly() {
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Pet", useCase(2, "UC01_SellingPetProducts.md", 3),
                        useCase(1019, "UC02_PetGroomingServices.md", 1))));

        assertThat(proposal.clusters()).hasSize(1);
        assertThat(proposal.clusters().get(0).reasons())
                .anySatisfy(reason -> assertThat(reason)
                        .contains("also appear elsewhere")
                        .contains("Pet (2 use cases)"));
    }

    @Test
    void propose_mergesTwoUseCasesWhoseSharedCandidatesOverlapHeavily() {
        // given: grooming and boarding share two non-cross-cutting candidates and own one each
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Groomer", useCase(10, "UC02_PetGroomingServices.md", 3),
                        useCase(11, "UC06_PetBoarding.md", 1)),
                profile(2, "GroomingKit", useCase(10, "UC02_PetGroomingServices.md", 2),
                        useCase(11, "UC06_PetBoarding.md", 1)),
                profile(3, "Kennel", useCase(11, "UC06_PetBoarding.md", 3),
                        useCase(10, "UC02_PetGroomingServices.md", 1))));

        // then: one cluster, named after the larger area, with the merge spelled out
        assertThat(proposal.clusters()).hasSize(1);
        assertThat(proposal.clusters().get(0).candidateIds()).containsExactly(1L, 2L, 3L);
        assertThat(proposal.clusters().get(0).proposedName()).isEqualTo("Pet Grooming Services");
        assertThat(proposal.clusters().get(0).reasons())
                .anySatisfy(reason -> assertThat(reason).contains("UC06_PetBoarding.md"));
    }

    @Test
    void propose_doesNotMergeTwoUseCasesOnASingleSharedCandidate() {
        // given: only ONE candidate is evidenced by both use cases
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Pet", useCase(2, "UC01_SellingPetProducts.md", 3),
                        useCase(1019, "UC02_PetGroomingServices.md", 1)),
                profile(2, "Toy", useCase(2, "UC01_SellingPetProducts.md", 2)),
                profile(3, "Groomer", useCase(1019, "UC02_PetGroomingServices.md", 2))));

        // then: still two areas — one shared concept is not a shared sub-domain
        assertThat(proposal.clusters()).hasSize(2);
    }

    // ============ classification ============

    @Test
    void propose_proposesGenericForAClusterSpanningEnoughUseCases() {
        // given: three use cases with identical evidence, so they merge into one cluster
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Appointment", useCase(10, "UC02.md", 3), useCase(11, "UC06.md", 2),
                        useCase(12, "UC07.md", 2)),
                profile(2, "Schedule", useCase(11, "UC06.md", 3), useCase(10, "UC02.md", 2),
                        useCase(12, "UC07.md", 2)),
                profile(3, "Booking", useCase(12, "UC07.md", 3), useCase(10, "UC02.md", 2),
                        useCase(11, "UC06.md", 2))));

        assertThat(proposal.clusters()).hasSize(1);
        assertThat(proposal.clusters().get(0).classification()).isEqualTo(SubDomainClassification.GENERIC);
        assertThat(proposal.clusters().get(0).reasons())
                .anySatisfy(reason -> assertThat(reason).contains("GENERIC"));
    }

    @Test
    void propose_leavesClassificationUnclassifiedForANarrowClusterAndSaysWhy() {
        final SubDomainAdvisor.Proposal proposal = advisor.propose(List.of(
                profile(1, "Toy", useCase(2, "UC01_SellingPetProducts.md", 2)),
                profile(2, "PetProduct", useCase(2, "UC01_SellingPetProducts.md", 2)),
                profile(3, "PetFood", useCase(2, "UC01_SellingPetProducts.md", 2))));

        final SubDomainAdvisor.Cluster cluster = proposal.clusters().get(0);
        assertThat(cluster.classification()).isEqualTo(SubDomainClassification.UNCLASSIFIED);
        assertThat(cluster.reasons()).anySatisfy(reason -> assertThat(reason).contains("UNCLASSIFIED"));
        assertThat(cluster.confidence()).isEqualTo(SubDomainAdvisor.Confidence.MEDIUM);
    }

    // ============ fixtures ============

    private static UseCaseProvenance useCase(final long documentId, final String fileName,
                                            final long sentenceCount) {
        return new UseCaseProvenance(documentId, fileName, fileName, sentenceCount);
    }

    /** A candidate profile; the FIRST use case is the dominant one. */
    private static SubDomainAdvisor.Profile profile(final long id, final String name,
                                                   final UseCaseProvenance... useCases) {
        return new SubDomainAdvisor.Profile(id, name, List.of(useCases));
    }
}
