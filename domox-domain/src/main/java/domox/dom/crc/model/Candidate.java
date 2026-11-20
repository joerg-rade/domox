package domox.dom.crc.model;
import domox.dom.AbstractEntity;
import domox.DomainModule;
import domox.dom.crc.diagram.CandidateD3Graph;
import domox.dom.crc.service.CandidateReviewWorkflow;
import domox.dom.nlp.Sentence;
import domox.dom.requirements.ContextEvidence;
import domox.dom.requirements.ContextSentence;
import domox.dom.requirements.OntologyDiagrams;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatchSignature;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base class for all UML class-diagram candidate entities.
 * <p>
 * Holds the sentence / typed-dependency provenance of a candidate, and passes on
 * the {@link AbstractEntity} {@code id}/{@code version} mapping to its concrete
 * {@code @Entity} subclasses via {@link InheritanceType#TABLE_PER_CLASS}.
 * <p>
 * Because {@code Candidate} is itself an {@code @Entity} (for relationship support
 * with {@link Review}), concrete subclasses produce their own full table via
 * TABLE_PER_CLASS inheritance — no {@code CANDIDATE} base table is created.
 */
@Entity
@Table(schema = DomainModule.SCHEMA, name = "Candidate")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Candidate")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Candidate extends AbstractEntity {

    /**
     * The concrete {@link Candidate} subclasses that participate in the review pipeline.
     * <p>
     * Single source of truth shared by {@code Reviews} (which iterates every type to find
     * unprocessed candidates) and {@code CandidateEditingTest} (which asserts each subclass
     * opts into inline editing).  When a new subclass is added it must be registered here —
     * nowhere else — so the review pipeline and the guardrail test always see the same set.
     */
    public static final List<Class<? extends Candidate>> SUBCLASSES = List.of(
            ClassCdd.class,
            ResponsibilityCdd.class,
            AttributeCdd.class,
            CollaboratorCdd.class,
            ResponsibilityParameter.class);

    @Inject
    @Transient
    private CandidateReviewWorkflow candidateReviewWorkflow;

    // region lexical graph
    /**
     * Injected {@link CandidateD3Graph} service used to render an interactive
     * <b>D3 force-directed node-link graph</b> of the candidate's dependency neighbourhood
     * on demand.
     * <p>
     * The graph is deliberately <em>not</em> persisted — it is regenerated on every access
     * because its content depends on the current candidate set, which changes as candidates are
     * reviewed, approved, or rejected.
     */
    @Inject
    @Transient
    private CandidateD3Graph candidateD3GraphService;

    /**
     * How many hops the candidate-centric lexical diagram reaches out from the candidate:
     * {@code 1} (the default) draws the candidate plus every candidate directly connected to it.
     * Larger values deepen the neighbourhood — e.g. {@code 2} additionally draws every candidate
     * connected to those direct neighbours.
     * <p>
     * Changing this value triggers the {@link #getD3Graph()} dependency diagram} to be rebuilt at
     * the new depth: the diagram is derived again on every access, so the next render reflects the
     * updated hop depth.
     */
    @Getter
    @Column(nullable = false)
    @PropertyLayout(
            named = "Lexical Graph Hop Depth",
            describedAs = "How far the dependency diagram reaches out (in hops) from this candidate (1–7)",
            fieldSetId = "general",
            sequence = "4")
    private Integer hopDepth = 1;

    /**
     * Sets the hop depth, clamped to a minimum of {@code 1}.  Because {@link #getD3Graph()} is
     * regenerated on every access, an actual change causes the dependency diagram to be rebuilt at
     * the new depth on its next render.
     */
    public void setHopDepth(final Integer hopDepth) {
        final int clamped = Math.max(1, hopDepth != null ? hopDepth : 1);
        if (!this.hopDepth.equals(clamped)) {
            this.hopDepth = clamped;
            // The dependency diagram is derived on access, so the next getDiagram() rebuilds it.
        }
    }

    /**
     * Renders the hop depth as a dropdown restricted to 1–7.
     */
    public List<Integer> choicesHopDepth() {
        return List.of(1, 2, 3, 4, 5, 6, 7);
    }

    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Lexical Graph",
            describedAs = "Interactive D3 force-directed node-link graph of this candidate's dependency neighbourhood",
            fieldSetId = "lexical",
            sequence = "5")
    public String getD3Graph() {
        return candidateD3GraphService != null ? candidateD3GraphService.renderGraph(this) : null;
    }
    // endregion lexical graph
    @Inject
    @Transient
    private OntologyDiagrams ontologyDiagrams;

    /**
     * The candidate's name resolved against the reference ontologies, rendered as an interactive
     * D3 force-layout graph.
     * <p>
     * Hidden in tables ({@link Where#ALL_TABLES}): a list of candidates is a table, and
     * {@code TableDecorator.DatatablesNet} relocates cell DOM for sorting and paging, which would
     * fight the D3 canvas — the same reason {@code Corpus#getOntologyDiagram()} is hidden there.
     * The graph is shown on the candidate's own page instead.
     */
    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(
            named = "Ontology Graph",
            describedAs = "Interactive D3 force-directed node-link graph of this candidate's ontologies",
            fieldSetId = "ontology",
            sequence = "5.1",
            hidden = Where.ALL_TABLES)
    public String getOntologyGraph() {
        return ontologyDiagrams != null ? ontologyDiagrams.ontologyAsD3Graph(this.candidateName) : null;
    }

    @Inject
    @Transient
    private ContextEvidence contextEvidence;

    /**
     * The corpus sentences in which this candidate's name occurs, one row per sentence — the
     * evidence a reviewer reads when judging whether the candidate is really talked about.
     * <p>
     * The sentences are matched on the parser's <em>lemmas</em> ({@code ContextEvidence#sentencesFor}),
     * not on {@code Sentence.text} containment, so {@code "pet"} does not match {@code "petrol"}; a
     * compound name matches only a sentence carrying <em>every</em> one of its words.  Each row is a
     * {@link ContextSentence}: the sentence in ascending sentence-id order, its text with the words
     * this candidate matched on highlighted, and the sentence itself — so opening it shows the syntax
     * diagram and the typed dependencies.
     * <p>
     * The rows are view models rather than the {@link Sentence} entities, because the highlight
     * belongs to the <em>pair</em> (candidate, sentence): one sentence is evidence for several
     * candidates and is marked up differently in each.
     * <p>
     * This replaces the former {@code contextGraph} property of this tab: rendered as a D3 graph,
     * the very same evidence was one truncated label per sentence — a form from which the sentence
     * could not be read, which is the whole point of the tab.
     */
    @Collection
    @CollectionLayout(
            named = "Sentences",
            describedAs = "The corpus sentences in which this candidate's name occurs",
            sequence = "3")
    public List<ContextSentence> getContextSentences() {
        return contextEvidence != null ? contextEvidence.sentencesFor(this) : List.of();
    }

    /**
     * The Causeway object type of this candidate — e.g. {@code domox.ResponsibilityCdd}.
     * <p>
     * Read from the {@link Named} annotation rather than derived from
     * {@link Class#getSimpleName()}, because it is the framework's own object type and must
     * match exactly: the diagram JSON exposes this value so the client can build a
     * {@code /wicket/object/<objectType>:<id>} click-through link.  Since the 2026-11
     * persisted-name migration (TODO-15) the annotation, the table name, the entity name and
     * the persisted {@code candidateType} strings all carry the CRC vocabulary, so this
     * coincides with the simple class name — but the annotation remains the source of truth,
     * not the convention.
     * <p>
     * Marked {@link Programmatic} so Causeway does not expose it as a metamodel property.
     */
    @Programmatic
    public String getObjectType() {
        for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
            final Named named = type.getAnnotation(Named.class);
            if (named != null && !named.value().isEmpty()) {
                return named.value();
            }
        }
        return DomainModule.NAMESPACE + "." + getClass().getSimpleName();
    }

    @Title
    public String title() {
        return getClass().getSimpleName() + ": " + candidateName;
    }

    @Column(nullable = false)
    @Getter
    @Setter
    @PropertyLayout(sequence = "1")
    private String candidateName;

    @Column(nullable = false)
    @Getter
    @Setter
    @PropertyLayout(sequence = "2")
    private String candidateType;

    /**
     * Why the last change to this candidate was made, in the words of whoever made it.
     * <p>
     * Supplied by the MCP client: every write tool of the review and sub-domain passes takes a
     * required {@code reason} and records it here, so a decision can be read back together with
     * its justification instead of only as a status.  Note the division of labour with
     * {@code Review.rationale}, which is the <em>categorical</em> reason
     * ({@code DUPLICATE}, {@code NOT_RELEVANT}, …) — this is the free text behind it.
     * <p>
     * A blank value is stored as {@code null} and a longer one is truncated to
     * {@link RationaleText#MAX_LENGTH} characters (see {@link #setRationale(String)}), so a
     * verbose agent cannot fail the write with a database error.
     * <p>
     * Hidden in tables: it is free text of up to 2000 characters and would swamp a list cell.
     */
    @Column(length = RationaleText.MAX_LENGTH)
    @Getter
    @PropertyLayout(
            named = "Rationale",
            describedAs = "Why the last change to this candidate was made",
            fieldSetId = "general",
            sequence = "3",
            hidden = Where.ALL_TABLES)
    private String rationale;

    /**
     * Stores the reason for the last change through {@link RationaleText#normalise(String)}:
     * trimmed, blank becomes {@code null}, and an over-long value is truncated rather than
     * rejected — the same clamp-don't-throw convention {@link #setHopDepth(Integer)} follows, so
     * a long rationale degrades instead of aborting the caller's transaction.
     */
    public void setRationale(final String rationale) {
        this.rationale = RationaleText.normalise(rationale);
    }

    @ManyToMany
    @JoinTable(schema = DomainModule.SCHEMA)
    @Getter
    @Setter
    @CollectionLayout(sequence = "1")
    private List<RuleMatch> ruleMatches = new ArrayList<>();

    @PropertyLayout(sequence = "8")
    public int getRuleMatchCount() {
        return ruleMatches.size();
    }

    @PropertyLayout(sequence = "9", hidden = Where.ALL_EXCEPT_STANDALONE_TABLES)
    public String getRuleNames() {
        return ruleMatches.stream()
                .map(RuleMatch::getRuleClassName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter
    @Setter
    @CollectionLayout(sequence = "2")
    private List<Review> reviews = new ArrayList<>();

    @PropertyLayout(sequence = "4")
    public int getReviewCount() {
        return reviews.size();
    }

    @PropertyLayout(sequence = "5")
    public int getApprovalCount() {
        final List<Review> approvals = reviews.stream()
                .filter(r -> r.getStatus() == ReviewStatus.APPROVED)
                .toList();
        return approvals.size();
    }

    /**
     * Adds a matching rule to this candidate.
     * <p>
     * The {@code ruleMatches} list is de-duplicated by the same candidate + rule
     * signature used when {@code RuleMatches} persists matches ({@code ruleClassName},
     * {@code candidateType}, {@code candidateName}, {@code relatedCandidateType},
     * {@code relatedCandidateName}, {@code description}), so a candidate can never
     * expose two equivalent matches — regardless of candidate type (ClassCdd,
     * ResponsibilityCdd, AttributeCdd, CollaboratorCdd).  The persisted
     * {@code candidateType} strings carry the same CRC vocabulary as the classes (the 2026-11
     * persisted-name migration, TODO-15).  This also collapses duplicate M:N
     * association rows left over from runs that predate RuleMatch de-duplication.
     *
     * @param match The rule match to add.
     */
    @Programmatic
    public void addMatchingRule(RuleMatch match) {
        if (match == null) {
            return;
        }
        // First collapse any pre-existing signature-duplicates already attached to
        // this candidate, then only append if no equivalent match is present.
        deduplicateRuleMatches();
        if (RuleMatchSignature.contains(ruleMatches, match)) {
            return;
        }
        ruleMatches.add(match);
    }

    /**
     * Removes any {@code RuleMatch} entries sharing the same candidate + rule
     * signature (keeping the first occurrence), so the {@code ruleMatches} list
     * holds no duplicates.  Signature match is defined exactly as in
     * {@code RuleMatches.create(...)}; see {@link RuleMatchSignature}.
     */
    @Programmatic
    public void deduplicateRuleMatches() {
        if (ruleMatches.size() < 2) {
            return;
        }
        final List<RuleMatch> deduped = RuleMatchSignature.deduplicate(ruleMatches);
        if (deduped.size() != ruleMatches.size()) {
            ruleMatches.clear();
            ruleMatches.addAll(deduped);
        }
    }

    @Programmatic
    public void addReview(Review review) {
        if (review != null && !reviews.contains(review)) {
            reviews.add(review);
            review.setCandidate(this);
        }
    }

    @Programmatic
    public void removeReview(Review review) {
        if (review != null && reviews.remove(review)) {
            review.setCandidate(null);
        }
    }

    @Action
    @ActionLayout(
            sequence = "6",
            position = ActionLayout.Position.PANEL,
            describedAs = "Create a new review for this candidate")
    public Review createReview() {
        return candidateReviewWorkflow.createReview(this);
    }

    // --- Review workflow actions ---

    @Action(
            semantics = SemanticsOf.NON_IDEMPOTENT,
            commandPublishing = Publishing.ENABLED)
    @ActionLayout(
            sequence = "7",
            position = ActionLayout.Position.PANEL,
            cssClassFa = "check-circle",
            describedAs = "Approve this candidate and go to the next unprocessed one of the same type")
    public Candidate approveAndGoToNext() {
        return candidateReviewWorkflow.approveAndNext(this); // Causeway navigates to the returned entity (or stays if null)
    }

    @Action(
            semantics = SemanticsOf.NON_IDEMPOTENT,
            commandPublishing = Publishing.ENABLED)
    @ActionLayout(
            sequence = "8",
            position = ActionLayout.Position.PANEL,
            cssClassFa = "times-circle",
            describedAs = "Reject this candidate with a rationale and go to the next unprocessed one of the same type")
    public Candidate rejectAndGoToNext(final ReviewRationale rationale) {
        return candidateReviewWorkflow.rejectAndNext(this, rationale);
    }

    @MemberSupport
    public List<ReviewRationale> choices0RejectAndGoToNext() {
        return List.of(ReviewRationale.values());
    }

    /**
     * Returns an existing pending review for this candidate, or creates a new one.
     * Delegates to {@link CandidateReviewWorkflow}.
     */
    @Programmatic
    public Review findOrCreatePendingReview() {
        return candidateReviewWorkflow.pendingOrNew(this);
    }
}