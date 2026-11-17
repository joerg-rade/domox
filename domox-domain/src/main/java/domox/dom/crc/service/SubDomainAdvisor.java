package domox.dom.crc.service;

import domox.DomainModule;
import domox.dom.crc.model.SubDomainClassification;
import domox.dom.requirements.UseCaseProvenance;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proposes a sub-domain partition from the corpus evidence, as an <b>advisory</b> starting point.
 *
 * <p>Pure and side-effect free, like {@link ReviewAdvisor}: it is given each unassigned class
 * candidate together with the use cases its rule-match evidence comes from
 * ({@code UseCases#provenanceFor}), and it returns a partition — the clusters, the candidates
 * that belong to no single area, and the reasons behind each call.  It decides nothing and writes
 * nothing.</p>
 *
 * <p><b>What it can and cannot derive.</b>  The <em>partition</em> follows from the evidence: a
 * candidate whose evidence comes from one use case is owned by that area, one whose evidence is
 * spread across many use cases is a shared, cross-cutting concept.  The <em>name</em> is derived
 * from the use-case titles.  The <em>classification</em> is business judgement the corpus does not
 * record, so it stays {@link SubDomainClassification#UNCLASSIFIED} unless a cluster is demonstrably
 * broad — spanning several use cases is the "solved the same way everywhere" signal for
 * {@link SubDomainClassification#GENERIC}.  Core and supporting are never guessed.</p>
 *
 * <p><b>Why it is conservative about merging.</b>  Two use cases are merged only when the candidates
 * they <em>share</em> overlap heavily — at or above {@link #MERGE_SIMILARITY}, on at least
 * {@link #MERGE_MIN_SHARED} shared candidates.  Shared concepts are excluded first, so the overlap
 * measured is between the distinctive concepts of the two areas — without that, every pair of use
 * cases would look alike, because they all talk about customers and pets.</p>
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".SubDomainAdvisor")
@Priority(PriorityPrecedence.LATE)
public class SubDomainAdvisor {

    /**
     * A candidate is treated as cross-cutting — owned by no single area — once its evidence spans
     * this many use cases.
     */
    public static final int CROSS_CUTTING_USE_CASE_THRESHOLD = 5;

    /** Two use cases are merged into one sub-domain at or above this Jaccard similarity of the candidates they share. */
    public static final double MERGE_SIMILARITY = 0.5;

    /**
     * How many candidates two use cases must share before a merge is even considered.
     * <p>
     * With small sets the Jaccard is 1.0 for any pair that happens to share a single concept, which
     * would collapse unrelated areas into one cluster; two shared candidates is the smallest overlap
     * that means anything.
     */
    public static final int MERGE_MIN_SHARED = 2;

    /** A cluster spanning this many use cases is broad enough to be proposed as {@code GENERIC}. */
    public static final int GENERIC_USE_CASE_SPAN = 3;

    /** One candidate and the use cases its evidence comes from. */
    public record Profile(long candidateId, String candidateName, List<UseCaseProvenance> useCases) {
        public Profile {
            useCases = List.copyOf(useCases == null ? List.of() : useCases);
        }
    }

    /**
     * A proposed sub-domain.
     *
     * @param proposedName   a name derived from the use-case titles; rename it if the business calls
     *                       the area something else
     * @param classification the proposed classification (see the class javadoc for how little is claimed)
     * @param candidateIds   the candidates this cluster owns
     * @param reasons        the evidence behind the cluster, in the order it was weighed
     * @param confidence     how much weight to give the cluster
     */
    public record Cluster(
            String proposedName,
            SubDomainClassification classification,
            List<Long> candidateIds,
            List<String> reasons,
            Confidence confidence) {

        public Cluster {
            candidateIds = List.copyOf(candidateIds == null ? List.of() : candidateIds);
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }
    }

    /**
     * The proposed partition.
     *
     * @param clusters                  the proposed sub-domains, largest first
     * @param crossCuttingCandidateIds  candidates whose evidence spans several use cases — shared
     *                                  concepts, deliberately left out of every cluster
     * @param unevidencedCandidateIds   candidates with no rule-match evidence at all, so no use case
     *                                  can be attributed
     * @param notes                     observations that did not fit a single cluster
     */
    public record Proposal(
            List<Cluster> clusters,
            List<Long> crossCuttingCandidateIds,
            List<Long> unevidencedCandidateIds,
            List<String> notes) {

        public Proposal {
            clusters = List.copyOf(clusters == null ? List.of() : clusters);
            crossCuttingCandidateIds = List.copyOf(crossCuttingCandidateIds == null ? List.of() : crossCuttingCandidateIds);
            unevidencedCandidateIds = List.copyOf(unevidencedCandidateIds == null ? List.of() : unevidencedCandidateIds);
            notes = List.copyOf(notes == null ? List.of() : notes);
        }
    }

    @Programmatic
    public Proposal propose(final List<Profile> profiles) {
        final List<String> notes = new ArrayList<>();
        if (profiles == null || profiles.isEmpty()) {
            notes.add("No unassigned class candidates: nothing to partition.");
            return new Proposal(List.of(), List.of(), List.of(), notes);
        }

        final List<Profile> owned = new ArrayList<>();
        final List<Long> crossCutting = new ArrayList<>();
        final List<String> crossCuttingNames = new ArrayList<>();
        final List<Long> unevidenced = new ArrayList<>();
        final List<String> unevidencedNames = new ArrayList<>();

        for (final Profile profile : profiles) {
            if (profile.useCases().isEmpty()) {
                unevidenced.add(profile.candidateId());
                unevidencedNames.add(profile.candidateName());
            } else if (profile.useCases().size() >= CROSS_CUTTING_USE_CASE_THRESHOLD) {
                crossCutting.add(profile.candidateId());
                crossCuttingNames.add(profile.candidateName());
            } else {
                owned.add(profile);
            }
        }

        if (!crossCuttingNames.isEmpty()) {
            notes.add("Cross-cutting (" + crossCuttingNames.size() + ") - evidenced by "
                    + CROSS_CUTTING_USE_CASE_THRESHOLD + " or more use cases, so no single area owns them: "
                    + String.join(", ", crossCuttingNames) + ".");
        }
        if (!unevidencedNames.isEmpty()) {
            notes.add("Unevidenced (" + unevidencedNames.size() + ") - no rule match, so no use case can be "
                    + "attributed; decide these by hand: " + String.join(", ", unevidencedNames) + ".");
        }
        if (owned.isEmpty()) {
            notes.add("Every candidate is cross-cutting or unevidenced: no cluster could be formed.");
            return new Proposal(List.of(), crossCutting, unevidenced, notes);
        }

        // group the owned candidates by the use case their evidence is strongest in, and record which
        // non-cross-cutting candidates each use case is evidenced by — the merge similarity measure
        final Map<Long, List<Profile>> byDominantDocument = new LinkedHashMap<>();
        final Map<Long, UseCaseProvenance> documentById = new LinkedHashMap<>();
        final Map<Long, Set<Long>> evidencedByDocument = new LinkedHashMap<>();
        for (final Profile profile : owned) {
            final UseCaseProvenance dominant = profile.useCases().getFirst();
            byDominantDocument.computeIfAbsent(dominant.documentId(), key -> new ArrayList<>()).add(profile);
            for (final UseCaseProvenance useCase : profile.useCases()) {
                documentById.putIfAbsent(useCase.documentId(), useCase);
                evidencedByDocument
                        .computeIfAbsent(useCase.documentId(), key -> new HashSet<>())
                        .add(profile.candidateId());
            }
        }

        // merge areas whose shared (non-cross-cutting) candidates overlap heavily, transitively.
        // NOTE: the candidates each area OWNS are disjoint by construction — a candidate has exactly
        // one dominant area — so the overlap has to be measured over what each area is evidenced by.
        final List<Long> documentIds = new ArrayList<>(byDominantDocument.keySet());
        final UnionFind merged = new UnionFind(documentIds.size());
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                if (shouldMerge(
                        evidencedByDocument.getOrDefault(documentIds.get(i), Set.of()),
                        evidencedByDocument.getOrDefault(documentIds.get(j), Set.of()))) {
                    merged.union(i, j);
                }
            }
        }

        final Map<Integer, List<Long>> groups = new LinkedHashMap<>();
        for (int i = 0; i < documentIds.size(); i++) {
            groups.computeIfAbsent(merged.find(i), key -> new ArrayList<>()).add(documentIds.get(i));
        }

        final List<Cluster> clusters = new ArrayList<>();
        for (final List<Long> members : groups.values()) {
            clusters.add(clusterFor(members, byDominantDocument, documentById));
        }
        clusters.sort(Comparator.comparingInt((Cluster cluster) -> cluster.candidateIds().size()).reversed()
                .thenComparing(Cluster::proposedName));

        return new Proposal(clusters, crossCutting, unevidenced, notes);
    }

    private Cluster clusterFor(
            final List<Long> documentIds,
            final Map<Long, List<Profile>> byDominantDocument,
            final Map<Long, UseCaseProvenance> documentById) {

        final List<Long> bySize = new ArrayList<>(documentIds);
        bySize.sort(Comparator
                .comparingInt((Long id) -> byDominantDocument.get(id).size()).reversed()
                .thenComparing(Comparator.naturalOrder()));
        final UseCaseProvenance primary = documentById.get(bySize.getFirst());

        final List<String> reasons = new ArrayList<>();
        reasons.add("Named after '" + primary.fileName() + "' (" + primary.documentTitle()
                + "), the largest area in this cluster.");
        if (documentIds.size() > 1) {
            final List<String> others = new ArrayList<>();
            for (int i = 1; i < bySize.size(); i++) {
                others.add(documentById.get(bySize.get(i)).fileName());
            }
            reasons.add("Merged with " + String.join(", ", others) + ": their shared candidates overlap at "
                    + "or above " + MERGE_SIMILARITY + " (Jaccard), on at least " + MERGE_MIN_SHARED
                    + " shared candidates.");
        }

        final List<Long> candidateIds = new ArrayList<>();
        final List<String> mixed = new ArrayList<>();
        for (final Long documentId : bySize) {
            for (final Profile member : byDominantDocument.get(documentId)) {
                candidateIds.add(member.candidateId());
                if (member.useCases().size() > 1) {
                    mixed.add(member.candidateName() + " (" + member.useCases().size() + " use cases)");
                }
            }
        }
        if (!mixed.isEmpty()) {
            reasons.add("Assigned on dominant evidence; these also appear elsewhere: "
                    + String.join(", ", mixed) + ".");
        }

        final SubDomainClassification classification = documentIds.size() >= GENERIC_USE_CASE_SPAN
                ? SubDomainClassification.GENERIC
                : SubDomainClassification.UNCLASSIFIED;
        if (classification == SubDomainClassification.GENERIC) {
            reasons.add("Proposed GENERIC: the cluster spans " + documentIds.size()
                    + " use cases, the 'solved the same way everywhere' signal.");
        } else {
            reasons.add("Classification left UNCLASSIFIED: core vs supporting is business judgement "
                    + "the corpus does not record.");
        }

        final Confidence confidence = documentIds.size() == 1 && candidateIds.size() >= 3
                ? Confidence.MEDIUM
                : Confidence.LOW;

        return new Cluster(humanise(primary.fileName()), classification, candidateIds, reasons, confidence);
    }

    /**
     * Whether two use cases belong to the same sub-domain: they must share at least
     * {@link #MERGE_MIN_SHARED} candidates <em>and</em> those shared candidates must be at or above
     * {@link #MERGE_SIMILARITY} of everything either of them is evidenced by (Jaccard).
     */
    private static boolean shouldMerge(final Set<Long> a, final Set<Long> b) {
        final Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.size() < MERGE_MIN_SHARED) {
            return false;
        }
        final Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / (double) union.size() >= MERGE_SIMILARITY;
    }

    /**
     * Turns a use-case file name into a readable area name:
     * {@code UC02_PetGroomingServices.md} becomes {@code Pet Grooming Services}.
     */
    static String humanise(final String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String name = fileName.trim();
        final int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceFirst("^UC\\d+_", "").replace('_', ' ').replace('-', ' ');
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(name.charAt(i - 1))
                    && name.charAt(i - 1) != ' ') {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    /** Minimal union-find over cluster indices, so merges compose transitively. */
    private static final class UnionFind {
        private final int[] parent;

        UnionFind(final int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(final int i) {
            return parent[i] == i ? i : (parent[i] = find(parent[i]));
        }

        void union(final int a, final int b) {
            final int rootA = find(a);
            final int rootB = find(b);
            if (rootA != rootB) {
                parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
            }
        }
    }

    /** How much weight a proposed cluster deserves. */
    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }
}
