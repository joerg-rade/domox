package domox.webapp.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import domox.dom.crc.service.Candidates;
import domox.dom.crc.service.Reviews;
import domox.dom.crc.service.SubDomainAdvisor;
import domox.dom.crc.service.SubDomains;
import domox.dom.requirements.ContextGraphs;
import domox.dom.requirements.Document;
import domox.dom.requirements.Documents;
import domox.dom.requirements.UseCaseProvenance;

/**
 * Exposes the sub-domain pass to LLM clients over MCP (Model Context Protocol).
 *
 * <p><b>What it does.</b>  It turns the corpus into sub-domains: the use cases a candidate's
 * rule-match evidence comes from ({@link ContextGraphs}) are the cohesion evidence, the advisor
 * ({@link SubDomainAdvisor}) proposes a partition over them, and {@link SubDomains} writes the
 * result — creating the sub-domain reference rows and setting {@code ClassCdd.subDomain}.</p>
 *
 * <p><b>Evidence first, then the write.</b>  The read tools ({@code listUseCases},
 * {@code listUnassignedClassCandidates}, {@code subDomainEvidenceForCandidate},
 * {@code suggestSubDomains}) come first and are safe to call at will; only
 * {@code createSubDomain}, {@code assignCandidatesToSubDomain} and {@code clearCandidateSubDomain}
 * change anything.  {@code suggestSubDomains} is <em>advisory</em> — it decides nothing, and its
 * classification is deliberately left {@code UNCLASSIFIED} except where the corpus itself shows an
 * area is broad.</p>
 *
 * <p><b>Idempotence.</b>  Every write is repeatable: a sub-domain is matched case-insensitively and
 * only created when missing, and assigning a candidate that already has that sub-domain is a no-op.
 * Re-running the pass after adjusting the clustering is therefore safe.</p>
 *
 * <p><b>Transaction discipline.</b>  The MCP annotation scanner invokes the annotated methods
 * reflectively on the raw bean (bypassing the AOP proxy), so a transaction is opened explicitly with
 * a {@link TransactionTemplate} instead of {@code @Transactional} — the same convention
 * {@link ReviewMcpTools} follows.</p>
 */
@Component
public class SubDomainMcpTools {

    private final Candidates candidates;
    private final ContextGraphs contextGraphs;
    private final SubDomains subDomains;
    private final SubDomainAdvisor subDomainAdvisor;
    private final Reviews reviews;
    private final Documents documents;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public SubDomainMcpTools(
            final Candidates candidates,
            final ContextGraphs contextGraphs,
            final SubDomains subDomains,
            final SubDomainAdvisor subDomainAdvisor,
            final Reviews reviews,
            final Documents documents,
            final PlatformTransactionManager transactionManager) {
        this.candidates = candidates;
        this.contextGraphs = contextGraphs;
        this.subDomains = subDomains;
        this.subDomainAdvisor = subDomainAdvisor;
        this.reviews = reviews;
        this.documents = documents;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @McpTool(
            name = "listUseCases",
            description = """
                    List the use cases of the corpus together with how much of the class model each one
                    evidences: how many class candidates its sentences back, and how many of those it is the
                    strongest evidence for. Call this first to see the business areas the corpus describes -
                    they are the raw material of a sub-domain partition. NOTE: a use case is not a sub-domain;
                    several use cases can belong to one sub-domain, and a concept evidenced by many use cases
                    belongs to none of them in particular.""")
    public List<UseCaseView> listUseCases() {
        return transactionTemplate.execute(status -> {
            final Map<Long, List<UseCaseProvenance>> provenance =
                    contextGraphs.provenanceForAll(candidates.listAllClasses());
            final Map<Long, Long> candidateCountByDocument = new HashMap<>();
            final Map<Long, Long> dominantCountByDocument = new HashMap<>();
            for (final List<UseCaseProvenance> useCases : provenance.values()) {
                if (useCases.isEmpty()) {
                    continue;
                }
                for (final UseCaseProvenance useCase : useCases) {
                    candidateCountByDocument.merge(useCase.documentId(), 1L, Long::sum);
                }
                dominantCountByDocument.merge(useCases.get(0).documentId(), 1L, Long::sum);
            }
            return documents.listAll().stream()
                    .sorted(Comparator.comparingLong(Document::getId))
                    .map(document -> new UseCaseView(
                            document.getId(),
                            document.getTitle(),
                            document.getFileName(),
                            candidateCountByDocument.getOrDefault(document.getId(), 0L),
                            dominantCountByDocument.getOrDefault(document.getId(), 0L)))
                    .toList();
        });
    }

    @McpTool(
            name = "listSubDomains",
            description = """
                    List the sub-domains that exist, with their classification and how many class candidates
                    each one already owns. Call this to see the current state of the sub-domain table before
                    adding to it, and again afterwards to verify a pass.""")
    public List<SubDomainView> listSubDomains() {
        return transactionTemplate.execute(status -> {
            final Map<Long, Long> countsBySubDomainId = assignedCountsBySubDomainId();
            return subDomains.listAll().stream()
                    .sorted(Comparator.comparingLong(SubDomain::getId))
                    .map(subDomain -> SubDomainView.of(subDomain,
                            countsBySubDomainId.getOrDefault(subDomain.getId(), 0L)))
                    .toList();
        });
    }

    @McpTool(
            name = "listUnassignedClassCandidates",
            description = """
                    List the approved class candidates that have no sub-domain yet - the input to a
                    sub-domain pass. Lean by design: call subDomainEvidenceForCandidate for one candidate's
                    full evidence. NOTE: candidates with no approval are omitted, because a rejected or
                    undecided candidate should not be given a sub-domain.""")
    public List<UnassignedCandidateView> listUnassignedClassCandidates() {
        return transactionTemplate.execute(status -> subDomains.listUnassigned().stream()
                .filter(candidate -> candidate.getApprovalCount() > 0)
                .map(candidate -> new UnassignedCandidateView(
                        candidate.getId(),
                        candidate.getCandidateName(),
                        candidate.getCandidateType(),
                        candidate.getApprovalCount()))
                .toList());
    }

    @McpTool(
            name = "subDomainEvidenceForCandidate",
            description = """
                    The use-case evidence behind one class candidate: which use cases its rule-match evidence
                    comes from and how many sentences of each back it, most-evidenced first, plus whether it is
                    approved and which sub-domain it is already assigned to. Use this to decide a single
                    candidate's sub-domain, or to understand one that suggestSubDomains left out.""")
    public CandidateSubDomainEvidence subDomainEvidenceForCandidate(
            @McpToolParam(description = "The numeric id of the class candidate (as returned by listUnassignedClassCandidates).")
            final long candidateId) {
        return transactionTemplate.execute(status -> {
            final Candidate candidate = requireCandidate(candidateId);
            final List<UseCaseProvenance> useCases = contextGraphs.provenanceFor(candidate);
            final List<String> notes = new ArrayList<>();
            if (useCases.isEmpty()) {
                notes.add("No rule match, so no use case can be attributed to this candidate.");
            } else if (useCases.size() >= SubDomainAdvisor.CROSS_CUTTING_USE_CASE_THRESHOLD) {
                notes.add("Cross-cutting: evidenced by " + useCases.size()
                        + " use cases, so no single area owns it.");
            } else if (useCases.size() > 1) {
                notes.add("Evidenced by " + useCases.size()
                        + " use cases; the first is the strongest.");
            }
            if (candidate.getApprovalCount() == 0) {
                notes.add("Not approved by any review; a sub-domain should normally be assigned to an "
                        + "approved candidate.");
            }
            final SubDomain subDomain = candidate instanceof ClassCdd classCdd ? classCdd.getSubDomain() : null;
            return new CandidateSubDomainEvidence(
                    candidate.getId(),
                    candidate.getCandidateName(),
                    candidate.getCandidateType(),
                    candidate.getApprovalCount() > 0,
                    subDomain != null ? subDomain.getName() : null,
                    useCases,
                    notes);
        });
    }

    @McpTool(
            name = "suggestSubDomains",
            description = """
                    Propose a sub-domain partition over the unassigned, approved class candidates, derived from
                    the use cases their evidence comes from: candidates whose evidence is concentrated in one
                    use case are clustered into the area that owns them, candidates whose evidence spans many
                    use cases are reported as cross-cutting and left out of every cluster, and use cases whose
                    owned candidates overlap heavily are merged. This is ADVISORY - it writes nothing. Rename
                    the clusters to the words the business actually uses and decide core / supporting / generic
                    yourself; the proposed classification is UNCLASSIFIED unless the cluster is demonstrably
                    broad. Then call createSubDomain and assignCandidatesToSubDomain.""")
    public SubDomainPartitionSuggestion suggestSubDomains() {
        return transactionTemplate.execute(status -> {
            final List<ClassCdd> unassigned = subDomains.listUnassigned().stream()
                    .filter(candidate -> candidate.getApprovalCount() > 0)
                    .toList();
            final Map<Long, List<UseCaseProvenance>> provenance = contextGraphs.provenanceForAll(unassigned);
            final List<SubDomainAdvisor.Profile> profiles = unassigned.stream()
                    .map(candidate -> new SubDomainAdvisor.Profile(
                            candidate.getId(),
                            candidate.getCandidateName(),
                            provenance.getOrDefault(candidate.getId(), List.of())))
                    .toList();
            final Map<Long, String> namesById = unassigned.stream()
                    .collect(Collectors.toMap(
                            ClassCdd::getId,
                            candidate -> String.valueOf(candidate.getCandidateName()),
                            (first, second) -> first,
                            LinkedHashMap::new));
            return SubDomainPartitionSuggestion.of(subDomainAdvisor.propose(profiles), namesById);
        });
    }

    @McpTool(
            name = "createSubDomain",
            description = """
                    Create a sub-domain, or update the classification of one that already exists (the name is
                    matched case-insensitively). Use the names suggestSubDomains proposed, renamed to the words
                    the business actually uses. Idempotent: calling it twice with the same name does not create
                    a second row.""")
    public SubDomainView createSubDomain(
            @McpToolParam(description = "The sub-domain name, e.g. 'Checkout'.")
            final String name,
            @McpToolParam(description = "CORE, SUPPORTING, GENERIC or UNCLASSIFIED. Blank leaves an existing sub-domain's classification untouched.")
            final String classification) {
        return transactionTemplate.execute(status -> {
            final SubDomain subDomain = subDomains.findOrCreate(name, parseClassification(classification));
            if (classification != null && !classification.isBlank()) {
                subDomains.classify(subDomain, parseClassification(classification));
            }
            return SubDomainView.of(subDomain, (long) subDomains.listAssignedTo(subDomain).size());
        });
    }

    @McpTool(
            name = "assignCandidatesToSubDomain",
            description = """
                    Assign class candidates to a sub-domain, creating the sub-domain if it does not exist yet.
                    Pass one cluster from suggestSubDomains per call. Idempotent: re-assigning a candidate that is
                    already in that sub-domain changes nothing, so a pass can be re-run safely. Candidates that
                    are not class candidates, and ids that match nothing, are reported back instead of failing
                    the whole call.""")
    public AssignmentResult assignCandidatesToSubDomain(
            @McpToolParam(description = "The numeric ids of the class candidates to assign.")
            final List<Long> candidateIds,
            @McpToolParam(description = "The sub-domain name; created when it does not exist yet.")
            final String subDomainName,
            @McpToolParam(description = "CORE, SUPPORTING, GENERIC or UNCLASSIFIED - applied only when this call creates the sub-domain. Blank leaves an existing classification untouched.")
            final String classification) {
        return transactionTemplate.execute(status -> {
            final List<String> messages = new ArrayList<>();
            final SubDomain existing = subDomains.findByNameIgnoreCase(subDomainName);
            final SubDomain subDomain;
            if (existing != null) {
                subDomain = existing;
            } else {
                subDomain = subDomains.findOrCreate(subDomainName, parseClassification(classification));
                messages.add("Created sub-domain '" + subDomain.getName() + "' ("
                        + subDomain.getClassification() + ").");
            }

            final List<ClassCdd> toAssign = new ArrayList<>();
            final List<Long> notFound = new ArrayList<>();
            for (final Long candidateId : candidateIds == null ? List.<Long>of() : candidateIds) {
                if (candidateId == null) {
                    continue;
                }
                final Candidate candidate = reviews.findByUniqueId(candidateId);
                if (candidate == null) {
                    notFound.add(candidateId);
                } else if (!(candidate instanceof ClassCdd classCdd)) {
                    messages.add("Candidate " + candidateId + " is a " + candidate.getCandidateType()
                            + ", not a class candidate; skipped.");
                } else if (classCdd.getSubDomain() != null && subDomain.getId() != null
                        && subDomain.getId().equals(classCdd.getSubDomain().getId())) {
                    messages.add("Candidate " + candidateId + " ('" + classCdd.getCandidateName()
                            + "') is already in '" + subDomain.getName() + "'; left as it is.");
                } else {
                    if (classCdd.getApprovalCount() == 0) {
                        messages.add("Candidate " + candidateId + " ('" + classCdd.getCandidateName()
                                + "') is not approved by any review; assigned anyway, but a sub-domain "
                                + "normally follows a decision.");
                    }
                    toAssign.add(classCdd);
                }
            }

            final List<Long> assignedIds = subDomains.assignAll(toAssign, subDomain).stream()
                    .map(ClassCdd::getId)
                    .toList();
            return new AssignmentResult(
                    subDomain.getName(),
                    subDomain.getClassification() != null ? subDomain.getClassification().name() : null,
                    assignedIds,
                    notFound,
                    messages);
        });
    }

    @McpTool(
            name = "clearCandidateSubDomain",
            description = """
                    Remove one class candidate's sub-domain assignment, leaving the sub-domain itself in place.
                    Use this to undo a wrong assignment without re-running a whole cluster.""")
    public AssignmentResult clearCandidateSubDomain(
            @McpToolParam(description = "The numeric id of the class candidate whose sub-domain assignment should be removed.")
            final long candidateId) {
        return transactionTemplate.execute(status -> {
            final Candidate candidate = requireCandidate(candidateId);
            if (!(candidate instanceof ClassCdd classCdd)) {
                throw new IllegalArgumentException("Candidate " + candidateId + " is a "
                        + candidate.getCandidateType() + ", not a class candidate");
            }
            final SubDomain previous = classCdd.getSubDomain();
            subDomains.clearSubDomain(classCdd);
            final List<String> messages = new ArrayList<>();
            messages.add(previous != null
                    ? "Removed the assignment to '" + previous.getName() + "'."
                    : "Candidate had no sub-domain; nothing changed.");
            return new AssignmentResult(null, null, List.of(), List.of(), messages);
        });
    }

    // --- helpers ---

    /** How many class candidates each sub-domain currently owns, keyed by sub-domain id. */
    private Map<Long, Long> assignedCountsBySubDomainId() {
        final Map<Long, Long> counts = new HashMap<>();
        for (final ClassCdd candidate : subDomains.listAllClasses()) {
            final SubDomain subDomain = candidate.getSubDomain();
            if (subDomain != null && subDomain.getId() != null) {
                counts.merge(subDomain.getId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private Candidate requireCandidate(final long candidateId) {
        final Candidate candidate = reviews.findByUniqueId(candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("No candidate found with id " + candidateId);
        }
        return candidate;
    }

    private static SubDomainClassification parseClassification(final String classification) {
        if (classification == null || classification.isBlank()) {
            return SubDomains.DEFAULT_CLASSIFICATION;
        }
        try {
            return SubDomainClassification.valueOf(classification.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown classification '" + classification
                    + "'. Must be one of " + Arrays.toString(SubDomainClassification.values()), e);
        }
    }
}
