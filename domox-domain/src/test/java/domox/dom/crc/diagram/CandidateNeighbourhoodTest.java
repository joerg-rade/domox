package domox.dom.crc.diagram;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the candidate-neighbourhood algorithm shared by the three lexical diagram renderers.
 *
 * <p>The three used to carry their own copies of this code; these tests pin the behaviour they all
 * relied on — the breadth-first expansion (including its depth bound and cycle safety), the
 * candidate-word restriction, the association-artifact filter, and the per-lemma metadata.</p>
 */
class CandidateNeighbourhoodTest {

    // ---------------------------------------------------------------- addAll

    @Test
    void addAll_appends_a_non_null_source_in_order() {
        final List<Candidate> target = new ArrayList<>();

        CandidateNeighbourhood.addAll(target, List.of(classCandidate("Pet"), classCandidate("Shop")));

        assertThat(target).extracting(Candidate::getCandidateName).containsExactly("Pet", "Shop");
    }

    @Test
    void addAll_ignores_a_null_source() {
        final List<Candidate> target = new ArrayList<>();

        CandidateNeighbourhood.addAll(target, null);

        assertThat(target).isEmpty();
    }

    // ---------------------------------------------------------------- candidateLemmas

    @Test
    void candidateLemmas_lower_cases_and_deduplicates() {
        final Set<String> lemmas = CandidateNeighbourhood.candidateLemmas(
                List.of(classCandidate("Pet"), classCandidate("pet"), classCandidate("Shop")));

        assertThat(lemmas).containsExactlyInAnyOrder("pet", "shop");
    }

    @Test
    void candidateLemmas_of_an_empty_snapshot_is_empty() {
        assertThat(CandidateNeighbourhood.candidateLemmas(List.of())).isEmpty();
    }

    // ---------------------------------------------------------------- candidatesByLemma

    @Test
    void candidatesByLemma_groups_by_lower_cased_name_keeping_order() {
        final ClassCdd pet = classCandidate("Pet");
        final ClassCdd shop = classCandidate("Shop");
        final ClassCdd petAgain = classCandidate("pet");

        final Map<String, List<Candidate>> byLemma =
                CandidateNeighbourhood.candidatesByLemma(List.of(pet, shop, petAgain));

        assertThat(byLemma).containsOnlyKeys("pet", "shop");
        assertThat(byLemma.get("pet")).containsExactly(pet, petAgain);
        assertThat(byLemma.get("shop")).containsExactly(shop);
    }

    // ---------------------------------------------------------------- approvedLemmas

    @Test
    void approvedLemmas_keeps_only_candidates_with_an_approved_review() {
        final ClassCdd approved = classCandidate("Pet");
        approved.getReviews().add(review(ReviewStatus.APPROVED));
        final ClassCdd rejected = classCandidate("Shop");
        rejected.getReviews().add(review(ReviewStatus.REJECTED));
        final ClassCdd unreviewed = classCandidate("Cart");

        assertThat(CandidateNeighbourhood.approvedLemmas(List.of(approved, rejected, unreviewed)))
                .containsExactly("pet");
    }

    @Test
    void approvedLemmas_skips_a_null_candidate_and_a_null_review_entry() {
        final ClassCdd candidate = classCandidate("Pet");
        candidate.getReviews().add(null);
        final List<Candidate> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(candidate);

        assertThat(CandidateNeighbourhood.approvedLemmas(candidates)).isEmpty();
    }

    @Test
    void approvedLemmas_ignores_a_candidate_whose_reviews_list_is_null() {
        final ClassCdd candidate = classCandidate("Pet");
        candidate.setReviews(null);

        assertThat(CandidateNeighbourhood.approvedLemmas(List.of(candidate))).isEmpty();
    }

    // ---------------------------------------------------------------- associationArtifactLemmas

    @Test
    void associationArtifactLemmas_flags_a_compound_of_two_candidate_lemmas() {
        final ClassCdd coverage = classCandidate("Coverage");
        final ClassCdd age = classCandidate("Age");
        final ClassCdd compound = associationCandidate("Coverage_Age");
        final List<Candidate> all = List.of(coverage, age, compound);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .containsExactly("coverage_age");
    }

    @Test
    void associationArtifactLemmas_ignores_a_non_association_candidate_type() {
        final ClassCdd coverage = classCandidate("Coverage");
        final ClassCdd age = classCandidate("Age");
        final ClassCdd compound = classCandidate("Coverage_Age"); // ClassCdd, not Association
        final List<Candidate> all = List.of(coverage, age, compound);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .isEmpty();
    }

    @Test
    void associationArtifactLemmas_requires_both_halves_to_be_candidate_words() {
        final ClassCdd compound = associationCandidate("Coverage_Age");
        final List<Candidate> all = List.of(compound);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .isEmpty();
    }

    @Test
    void associationArtifactLemmas_ignores_a_name_without_a_separator() {
        final ClassCdd compound = associationCandidate("Coverage");
        final List<Candidate> all = List.of(compound);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .isEmpty();
    }

    @Test
    void associationArtifactLemmas_ignores_a_leading_or_trailing_separator() {
        final ClassCdd leading = associationCandidate("_Age");
        final ClassCdd trailing = associationCandidate("Coverage_");
        final List<Candidate> all = List.of(leading, trailing);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .isEmpty();
    }

    @Test
    void associationArtifactLemmas_requires_the_separator_to_sit_strictly_inside_the_name() {
        // Both halves of each compound ARE candidate words (including the empty lemma), so the
        // only reason neither is an artifact is that the separator sits at an end of the name.
        final ClassCdd unnamed = classCandidate("");
        final ClassCdd coverage = classCandidate("Coverage");
        final ClassCdd age = classCandidate("Age");
        final ClassCdd leading = associationCandidate("_Age");
        final ClassCdd trailing = associationCandidate("Coverage_");
        final List<Candidate> all = List.of(unnamed, coverage, age, leading, trailing);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .isEmpty();
    }

    @Test
    void associationArtifactLemmas_skips_a_null_candidate_name() {
        final ClassCdd unnamed = associationCandidate(null);
        final List<Candidate> all = List.of(unnamed);

        assertThat(CandidateNeighbourhood.associationArtifactLemmas(all,
                CandidateNeighbourhood.candidateLemmas(all)))
                .isEmpty();
    }

    // ---------------------------------------------------------------- collectDependencies

    @Test
    void collectDependencies_returns_nothing_at_depth_zero() {
        final ClassCdd pet = classCandidate("Pet");
        pet.getRuleMatches().add(match(dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN)));
        final List<Candidate> all = List.of(pet);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 0))
                .isEmpty();
    }

    @Test
    void collectDependencies_keeps_a_dependency_between_two_candidate_words() {
        final ClassCdd pet = classCandidate("Pet");
        final ClassCdd shop = classCandidate("Shop");
        final TypedDependency petShop = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        pet.getRuleMatches().add(match(petShop));
        final List<Candidate> all = List.of(pet, shop);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 1))
                .containsExactly(petShop);
    }

    @Test
    void collectDependencies_drops_a_dependency_to_a_non_candidate_word() {
        final ClassCdd pet = classCandidate("Pet");
        pet.getRuleMatches().add(match(dep("pet", PartOfSpeechType.NN, "the", PartOfSpeechType.DT)));
        final List<Candidate> all = List.of(pet);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 1))
                .isEmpty();
    }

    @Test
    void collectDependencies_keeps_a_repeated_dependency_once() {
        final ClassCdd pet = classCandidate("Pet");
        final ClassCdd shop = classCandidate("Shop");
        final TypedDependency petShop = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        pet.getRuleMatches().add(match(petShop));
        pet.getRuleMatches().add(match(petShop));
        final List<Candidate> all = List.of(pet, shop);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 1))
                .containsExactly(petShop);
    }

    @Test
    void collectDependencies_skips_a_rule_match_without_a_typed_dependency() {
        final ClassCdd pet = classCandidate("Pet");
        pet.getRuleMatches().add(new RuleMatch());
        final List<Candidate> all = List.of(pet);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 1))
                .isEmpty();
    }

    @Test
    void collectDependencies_skips_a_candidate_with_a_null_match_list() {
        final ClassCdd pet = classCandidate("Pet");
        pet.setRuleMatches(null);
        final List<Candidate> all = List.of(pet);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 1))
                .isEmpty();
    }

    @Test
    void collectDependencies_stops_at_the_hop_depth() {
        final ClassCdd pet = classCandidate("Pet");
        final ClassCdd shop = classCandidate("Shop");
        final ClassCdd cart = classCandidate("Cart");
        final TypedDependency petShop = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        final TypedDependency shopCart = dep("shop", PartOfSpeechType.NN, "cart", PartOfSpeechType.NN);
        pet.getRuleMatches().add(match(petShop));
        shop.getRuleMatches().add(match(shopCart));
        final List<Candidate> all = List.of(pet, shop, cart);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 1))
                .containsExactly(petShop);
    }

    @Test
    void collectDependencies_expands_to_the_next_frontier_at_depth_two() {
        final ClassCdd pet = classCandidate("Pet");
        final ClassCdd shop = classCandidate("Shop");
        final ClassCdd cart = classCandidate("Cart");
        final TypedDependency petShop = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        final TypedDependency shopCart = dep("shop", PartOfSpeechType.NN, "cart", PartOfSpeechType.NN);
        pet.getRuleMatches().add(match(petShop));
        shop.getRuleMatches().add(match(shopCart));
        final List<Candidate> all = List.of(pet, shop, cart);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 2))
                .containsExactly(petShop, shopCart);
    }

    @Test
    void collectDependencies_terminates_on_a_cycle() {
        final ClassCdd pet = classCandidate("Pet");
        final ClassCdd shop = classCandidate("Shop");
        final TypedDependency petShop = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        final TypedDependency shopPet = dep("shop", PartOfSpeechType.NN, "pet", PartOfSpeechType.NN);
        pet.getRuleMatches().add(match(petShop));
        shop.getRuleMatches().add(match(shopPet));
        final List<Candidate> all = List.of(pet, shop);

        assertThat(CandidateNeighbourhood.collectDependencies(List.of(pet),
                CandidateNeighbourhood.candidateLemmas(all),
                CandidateNeighbourhood.candidatesByLemma(all), 3))
                .containsExactlyInAnyOrder(petShop, shopPet);
    }

    // ---------------------------------------------------------------- removeArtifactEdges

    @Test
    void removeArtifactEdges_drops_every_edge_touching_an_artifact() {
        final TypedDependency keep = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        final TypedDependency asGovernor = dep("coverage_age", PartOfSpeechType.NN, "age", PartOfSpeechType.NN);
        final TypedDependency asDependent = dep("coverage", PartOfSpeechType.NN, "coverage_age", PartOfSpeechType.NN);
        final List<TypedDependency> dependencies = new ArrayList<>(List.of(keep, asGovernor, asDependent));

        CandidateNeighbourhood.removeArtifactEdges(dependencies, Set.of("coverage_age"));

        assertThat(dependencies).containsExactly(keep);
    }

    @Test
    void removeArtifactEdges_leaves_a_null_dependency_untouched() {
        final List<TypedDependency> dependencies = new ArrayList<>();
        dependencies.add(null);

        CandidateNeighbourhood.removeArtifactEdges(dependencies, Set.of("coverage_age"));

        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.getFirst()).isNull();
    }

    // ---------------------------------------------------------------- lemmaMetrics

    @Test
    void lemmaMetrics_accumulates_the_pos_codes_of_both_endpoints() {
        final TypedDependency td = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.VBZ);

        final CandidateNeighbourhood.LemmaMetrics metrics =
                CandidateNeighbourhood.lemmaMetrics(List.of(td));

        assertThat(metrics.posByLemma().get("pet")).containsExactly("NN");
        assertThat(metrics.posByLemma().get("shop")).containsExactly("VBZ");
    }

    @Test
    void lemmaMetrics_sums_the_rule_match_count_per_lemma() {
        final TypedDependency td = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        td.setRuleMatches(List.of(new RuleMatch(), new RuleMatch()));

        final CandidateNeighbourhood.LemmaMetrics metrics =
                CandidateNeighbourhood.lemmaMetrics(List.of(td));

        assertThat(metrics.matchCountByLemma()).containsEntry("pet", 2).containsEntry("shop", 2);
    }

    @Test
    void lemmaMetrics_accumulates_a_lemma_reached_by_several_dependencies() {
        final TypedDependency first = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        final TypedDependency second = dep("shop", PartOfSpeechType.NN, "cart", PartOfSpeechType.NN);
        second.setRuleMatches(List.of(new RuleMatch()));

        final CandidateNeighbourhood.LemmaMetrics metrics =
                CandidateNeighbourhood.lemmaMetrics(List.of(first, second));

        assertThat(metrics.matchCountByLemma()).containsEntry("shop", 1).containsEntry("cart", 1);
        assertThat(metrics.posByLemma().get("shop")).containsExactly("NN");
    }

    @Test
    void lemmaMetrics_treats_a_dependency_without_matches_as_zero() {
        final TypedDependency td = dep("pet", PartOfSpeechType.NN, "shop", PartOfSpeechType.NN);
        td.setRuleMatches(null);

        final CandidateNeighbourhood.LemmaMetrics metrics =
                CandidateNeighbourhood.lemmaMetrics(List.of(td));

        assertThat(metrics.matchCountByLemma()).containsEntry("pet", 0).containsEntry("shop", 0);
    }

    @Test
    void lemmaMetrics_skips_a_null_dependency() {
        final List<TypedDependency> dependencies = new ArrayList<>();
        dependencies.add(null);

        final CandidateNeighbourhood.LemmaMetrics metrics =
                CandidateNeighbourhood.lemmaMetrics(dependencies);

        assertThat(metrics.posByLemma()).isEmpty();
        assertThat(metrics.matchCountByLemma()).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private static ClassCdd classCandidate(final String name) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.setCandidateType("ClassCdd");
        return candidate;
    }

    private static ClassCdd associationCandidate(final String name) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.setCandidateType("AssociationCdd");
        return candidate;
    }

    private static Review review(final ReviewStatus status) {
        final Review review = new Review();
        review.setStatus(status);
        return review;
    }

    private static RuleMatch match(final TypedDependency typedDependency) {
        final RuleMatch match = new RuleMatch();
        match.setTypedDependency(typedDependency);
        return match;
    }

    private static TypedDependency dep(final String governorLemma,
                                       final PartOfSpeechType governorPos,
                                       final String dependentLemma,
                                       final PartOfSpeechType dependentPos) {
        final TypedDependency td = new TypedDependency();
        td.setType(TdType.NSUBJ);
        td.setGovernorIndex(1);
        td.setGovernorLemma(governorLemma);
        td.setGovernorPos(governorPos);
        td.setDependentIndex(2);
        td.setDependentLemma(dependentLemma);
        td.setDependentPos(dependentPos);
        return td;
    }
}
