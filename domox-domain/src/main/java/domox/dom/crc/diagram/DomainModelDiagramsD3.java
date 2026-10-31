package domox.dom.crc.diagram;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Renders a {@link DomainModel}-wide <em>lexical dependency</em> summary as a D3 force-directed
 * graph JSON seeded from the model's <em>approved</em> candidates, comparable to the per-candidate
 * {@link CandidateDiagrams#renderDiagram(Candidate)} but scoped to the whole analysis run.
 * <p>
 * The graph is built by a breadth-first expansion of the candidate-connection graph starting
 * from every approved candidate of the model (a candidate is approved when it carries at least
 * one {@link Review} whose status is {@link ReviewStatus#APPROVED}), up to
 * {@link DomainModel#getHopDepth()} hops deep (default {@code 1}).  Seeding from the approved set
 * keeps the starting graph deliberately small so it renders reliably; the depth is increased by
 * the user when a wider view of the model is wanted.  From each frontier candidate's own
 * {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent lemma both
 * correspond to a candidate word are kept, so the retained edges draw the approved candidates
 * together with every candidate directly (or, at higher depths, transitively) connected to them.
 * <p>
 * The result is a {@link String} containing a JSON object with the following structure:
 * <pre>{@code {
 *   "nodes": [
 *     {"id":"store","name":"Store","subdomain":"Inventory","pos":"NN","color":"#3498DB",
 *      "r":12,"approved":true}
 *   ],
 *   "links": [
 *     {"source":"store","target":"customer","label":"nsubj"}
 *   ],
 *   "legend": [
 *     {"color":"#3498DB","label":"Noun","description":"Entities and objects"},
 *     ...
 *   ]
 * }}</pre>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set and their reviews, both of which change as candidates are
 * reviewed, approved, or rejected.
 */
@Named(DomainModule.NAMESPACE + ".DomainModelDiagramsD3")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class DomainModelDiagramsD3 {

    private static final Logger log = LoggerFactory.getLogger(DomainModelDiagramsD3.class);
/** Base node radius (px) enlarged proportionally to rule-match count. */
    private static final double NODE_BASE_RADIUS = 8.0;

    /** Rule-match count that maps to scale 1 (no enlargement). */
    private static final int MIN_MATCH_COUNT = 1;

    /** Rule-match count of the largest node maps to scale 4. */
    private static final double MAX_SCALE = 4.0;

    @Programmatic
    public String renderDiagram(final DomainModel model) {
        if (model == null) {
            return null;
        }
        final long id = model.getId() != null ? model.getId() : -1L;
        log.debug("Rendering domain-model D3 diagram for analysis #{}", id);
        try {
            final List<Candidate> allCandidates = candidatesOf(model);
            if (allCandidates.isEmpty()) {
                log.warn("Domain model #{} has no candidates on its lists — nothing to diagram", id);
                return null;
            }
            log.debug("Domain model #{}: {} total candidates", id, allCandidates.size());
            final List<Candidate> approved = approvedCandidates(allCandidates);
            if (approved.isEmpty()) {
                log.warn("Domain model #{}: {} candidates found but NONE are approved (no Review with APPROVED status) — nothing to diagram",
                        id, allCandidates.size());
                return null;
            }
            log.debug("Domain model #{}: {} approved candidates", id, approved.size());
            final Set<String> candidateLemmas = candidateLemmas(allCandidates);
            final Map<String, List<Candidate>> candidatesByLemma = candidatesByLemma(allCandidates);
            final List<TypedDependency> dependencies = collectDependencies(
                    approved, candidateLemmas, candidatesByLemma, 1);

            // Remove association-artifact candidate nodes — synthetic Association-typed compounds
            // of the form "X_Y" derived from existing candidate lemmas (e.g. "Coverage_Age",
            // "OnlineStore_Store"), regardless of which rule created them (synonym TDR41,
            // generalization, association rule).  The participant lemmas and their typed
            // dependency link are kept; only the synthetic compound node (with its empty <<>> POS)
            // is removed from the lexical diagram.
            final Set<String> artifactLemmas = associationArtifactLemmas(allCandidates, candidateLemmas);
            dependencies.removeIf(td -> td != null && (
                    artifactLemmas.contains(lower(td.getGovernorLemma())) ||
                    artifactLemmas.contains(lower(td.getDependentLemma()))));

            // Build per-lemma metadata: POS tags and rule-match counts.
            final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
            final Map<String, Integer> countByLemma = new HashMap<>();
            for (final TypedDependency td : dependencies) {
                if (td == null) continue;
                accumulatePos(posByLemma, lower(td.getGovernorLemma()), td.getGovernorPos());
                accumulatePos(posByLemma, lower(td.getDependentLemma()), td.getDependentPos());
                final int matches = td.getRuleMatches() != null ? td.getRuleMatches().size() : 0;
                countByLemma.merge(lower(td.getGovernorLemma()), matches, Integer::sum);
                countByLemma.merge(lower(td.getDependentLemma()), matches, Integer::sum);
            }

            // Every lemma that becomes a node: approved seeds plus endpoints of dependencies.
            final Set<String> nodeLemmas = new LinkedHashSet<>();
            for (final Candidate a : approved) {
                nodeLemmas.add(lower(a.getCandidateName()));
            }
            for (final TypedDependency td : dependencies) {
                if (td == null) continue;
                nodeLemmas.add(lower(td.getGovernorLemma()));
                nodeLemmas.add(lower(td.getDependentLemma()));
            }
            // Association-artifact nodes are excluded from the diagram entirely.
            nodeLemmas.removeAll(artifactLemmas);

            // Approved lemma set (bold border).
            final Set<String> approvedLemmas = approvedLemmasSet(allCandidates);

            // Maximum count for scaling.
            final int maxCount = countByLemma.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(MIN_MATCH_COUNT);

            // Map each lemma to its display name (original casing).
            final Map<String, String> displayName = new HashMap<>();
            for (final String lemma : nodeLemmas) {
                final List<Candidate> cs = candidatesByLemma.get(lemma);
                displayName.put(lemma, cs != null && !cs.isEmpty()
                        ? cs.getFirst().getCandidateName() : lemma);
            }

            // Map each lemma to its ClassCdd subDomain name (set during review), if any.
            final Map<String, String> classSubdomainByLemma = new HashMap<>();
            for (final Candidate c : allCandidates) {
                if (c instanceof ClassCdd cc && cc.getSubDomain() != null
                        && cc.getSubDomain().getName() != null) {
                    classSubdomainByLemma.put(lower(c.getCandidateName()),
                            cc.getSubDomain().getName());
                }
            }

            // Map each lemma to its first label line (the "subdomain" line of the node):
            // ClassCdd candidates show their assigned subDomain name; all others show blank.
            final Map<String, String> subdomainByLemma = new HashMap<>();
            for (final String lemma : nodeLemmas) {
                final String sdName = classSubdomainByLemma.get(lemma);
                subdomainByLemma.put(lemma, sdName != null ? sdName : "");
            }

            // Map each lemma to the first candidate's database ID and object type
            // (e.g. "domox.ClassCdd", read from the pinned @Named) for click-through links.
            final Map<String, Long> candidateIdByLemma = new HashMap<>();
            final Map<String, String> objectTypeByLemma = new HashMap<>();
            for (final String lemma : nodeLemmas) {
                final List<Candidate> cs = candidatesByLemma.get(lemma);
                if (cs != null && !cs.isEmpty()) {
                    final Candidate c = cs.getFirst();
                    candidateIdByLemma.put(lemma, c.getId());
                    objectTypeByLemma.put(lemma, c.getObjectType());
                }
            }

            // Build enriched JSON: nodes, links, legend.
            final StringBuilder json = new StringBuilder();
            json.append('{');
            appendNodes(json, nodeLemmas, posByLemma, countByLemma, maxCount,
                    approvedLemmas, displayName, subdomainByLemma,
                    candidateIdByLemma, objectTypeByLemma);
            json.append(',');
            appendLinks(json, dependencies);
            json.append(',');
            final Set<String> usedPosCodes = new HashSet<>();
            for (final Set<String> posSet : posByLemma.values()) {
                if (posSet != null) {
                    usedPosCodes.addAll(posSet);
                }
            }
            appendLegend(json, usedPosCodes);
            json.append('}');

            final String result = json.toString();
            log.debug("D3 JSON for analysis #{}: {}", id, result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to render domain-model D3 diagram for analysis #{}: {}", id, e.getMessage());
            log.debug("Failure rendering domain-model D3 diagram for analysis #{}", id, e);
            return null;
        }
    }

    // ============ JSON generation helpers ============

    /**
     * Appends the {@code "nodes"} array. Each node: id, name, subdomain, pos, color, r, approved.
     */
    private static void appendNodes(final StringBuilder json,
                                    final Set<String> nodeLemmas,
                                    final Map<String, Set<String>> posByLemma,
                                    final Map<String, Integer> countByLemma,
                                    final int maxCount,
                                    final Set<String> approvedLemmas,
                                    final Map<String, String> displayName,
                                    final Map<String, String> subdomainByLemma,
                                    final Map<String, Long> candidateIdByLemma,
                                    final Map<String, String> objectTypeByLemma) {
        json.append("\"nodes\":[");
        boolean first = true;
        for (final String lemma : nodeLemmas) {
            if (!first) json.append(',');
            final String pos = firstPos(posByLemma.get(lemma));
            final int count = countByLemma.getOrDefault(lemma, MIN_MATCH_COUNT);
            final double scale = scaleFor(count, maxCount);
            final int radius = (int) Math.round(scale * NODE_BASE_RADIUS);
            final boolean approved = approvedLemmas.contains(lemma);
            json.append('{');
            json.append("\"id\":\"").append(escapeJson(lemma)).append('"');
            json.append(",\"name\":\"").append(escapeJson(displayName.getOrDefault(lemma, lemma))).append('"');
            final String sd = subdomainByLemma.getOrDefault(lemma, "");
            if (!sd.isEmpty()) {
                json.append(",\"subdomain\":\"").append(escapeJson(sd)).append('"');
            }
            json.append(",\"pos\":\"").append(escapeJson(pos)).append('"');
            json.append(",\"color\":\"").append(posColor(pos)).append('"');
            json.append(",\"r\":").append(radius);
            json.append(",\"approved\":").append(approved);
            final Long candId = candidateIdByLemma.get(lemma);
            final String objType = objectTypeByLemma.get(lemma);
            if (candId != null && objType != null) {
                json.append(",\"candidateId\":").append(candId);
                json.append(",\"objectType\":\"").append(escapeJson(objType)).append('"');
            }
            json.append('}');
            first = false;
        }
        json.append(']');
    }

    /**
     * Appends the {@code "links"} array. Each link: source, target, optional label (TdType code).
     */
    private static void appendLinks(final StringBuilder json,
                                    final List<TypedDependency> dependencies) {
        json.append("\"links\":[");
        boolean first = true;
        for (final TypedDependency td : dependencies) {
            if (!first) json.append(',');
            final String src = lower(td.getGovernorLemma());
            final String tgt = lower(td.getDependentLemma());
            json.append('{');
            json.append("\"source\":\"").append(escapeJson(src)).append('"');
            json.append(",\"target\":\"").append(escapeJson(tgt)).append('"');
            if (td.getType() != null) {
                json.append(",\"label\":\"").append(escapeJson(td.getType().getCode())).append('"');
            }
            json.append('}');
            first = false;
        }
        json.append(']');
    }

    /**
     * Appends the {@code "legend"} array: colour swatches for each POS-based fill colour that
     * actually appears in the diagram nodes, plus a catch-all "Other" entry when there are used
     * POS codes that do not match any specific category.
     *
     * @param json         the JSON buffer being built
     * @param usedPosCodes the set of POS codes that appear in at least one diagram node
     */
    private static void appendLegend(final StringBuilder json, final Set<String> usedPosCodes) {
        json.append("\"legend\":[");
        boolean first = true;
        for (final LegendEntry entry : LEGEND_ENTRIES) {
            // "Other" entry is shown only when there are POS codes not matched by any specific category.
            if (entry.posCodes.isEmpty()) {
                if (hasOtherPos(usedPosCodes)) {
                    appendLegendEntry(json, entry, first);
                    first = false;
                }
                continue;
            }
            if (hasAny(usedPosCodes, entry.posCodes)) {
                appendLegendEntry(json, entry, first);
                first = false;
            }
        }
        json.append(']');
    }

    /** True when at least one {@code usedPosCodes} element is not covered by any specific legend entry. */
    private static boolean hasOtherPos(final Set<String> usedPosCodes) {
        final Set<String> covered = new HashSet<>();
        for (final LegendEntry e : LEGEND_ENTRIES) {
            if (!e.posCodes.isEmpty()) {
                covered.addAll(e.posCodes);
            }
        }
        for (final String code : usedPosCodes) {
            if (!covered.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** True when the intersection of {@code used} and {@code candidates} is non-empty. */
    private static boolean hasAny(final Set<String> used, final Set<String> candidates) {
        for (final String c : candidates) {
            if (used.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** Appends a single JSON legend entry object, preceded by a comma when not the first entry. */
    private static void appendLegendEntry(final StringBuilder json, final LegendEntry entry, final boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append("{\"color\":\"").append(entry.color).append('"')
                .append(",\"label\":\"").append(entry.label).append('"')
                .append(",\"description\":\"").append(entry.description).append("\"}");
    }

    /** A single legend row: fill colour, short label, description, and the POS codes that map to it. */
    private record LegendEntry(String color, String label, String description, Set<String> posCodes) {}

    /**
     * All possible legend entries, ordered by semantic category.  The first matching entry wins.
     * The POS codes here must stay in sync with {@link #posColor(String)}.
     */
    private static final List<LegendEntry> LEGEND_ENTRIES = List.of(
            new LegendEntry("#3498DB", "Noun", "Entities and objects (NN, NNS)", Set.of("NN", "NNS")),
            new LegendEntry("#85C1E9", "Proper noun", "Named entities (NNP, NNPS)", Set.of("NNP", "NNPS")),
            new LegendEntry("#E74C3C", "Verb", "Actions and predicates (VB, VBZ)", Set.of("VB", "VBZ", "VBD", "VBG", "VBN", "VBP")),
            new LegendEntry("#2ECC71", "Adjective", "Descriptors and modifiers (JJ, JJR, JJS)", Set.of("JJ", "JJR", "JJS")),
            new LegendEntry("#F39C12", "Determiner", "Articles and determiners (DT)", Set.of("DT")),
            new LegendEntry("#9B59B6", "Pronoun", "Pronouns (PRP, PRP$)", Set.of("PRP", "PRP$", "WDT", "WP", "WP$", "WRB")),
            new LegendEntry("#17A2B8", "Preposition", "Prepositions (IN)", Set.of("IN")),
            new LegendEntry("#FFBB28", "Adverb", "Adverbs (RB)", Set.of("RB")),
            new LegendEntry("#AAB7B8", "Other", "Other parts of speech", Set.of())
    );

    // ============ Lemma metadata helpers ============

    /** Records a PartOfSpeechType code for a lemma, skipping null. */
    private static void accumulatePos(final Map<String, Set<String>> posByLemma,
                                       final String lemma,
                                       final domox.dom.nlp.PartOfSpeechType pos) {
        if (pos == null) return;
        final String code = pos.getCode();
        if (code != null && !code.isEmpty()) {
            posByLemma.computeIfAbsent(lemma, k -> new LinkedHashSet<>()).add(code);
        }
    }

    /** Returns the first POS code from the set, or empty string if null/empty. */
    private static String firstPos(final Set<String> posSet) {
        if (posSet == null || posSet.isEmpty()) return "";
        return posSet.iterator().next();
    }

    /** Lower-cased lemmas of approved candidates (bold border in D3). */
    private static Set<String> approvedLemmasSet(final List<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate c : candidates) {
            if (c != null && c.getReviews() != null
                    && c.getReviews().stream().anyMatch(r -> r != null && r.getStatus() == ReviewStatus.APPROVED)) {
                lemmas.add(lower(c.getCandidateName()));
            }
        }
        return lemmas;
    }

    /**
     * Lower-cased lemmas of <em>association-artifact</em> candidates — synthetic
     * {@code Association}-typed nodes whose name is a compound of two existing candidate lemmas
     * joined by {@code _} (e.g. {@code Coverage_Age}, {@code OnlineStore_Store}).  Such
     * candidates are products of the candidate-extraction rules (synonym TDR41, generalization,
     * or association rules) and are excluded from the D3 diagram because their derived lemma is
     * uninformative and their POS label is always empty ({@code <<>>}).
     * <p>
     * The <em>participant</em> lemmas (e.g. {@code coverage}, {@code age}) are deliberately
     * <strong>kept</strong> in the diagram together with their typed-dependency link; only the
     * synthetic association node is removed.  The originating rule is deliberately not consulted
     * — the compound-name shape is the criterion, so the filter applies uniformly to every rule
     * family (synonym, generalization, association rule).
     * <p>
     * <strong>Presentational scope only:</strong> this filter affects the <em>lexical</em> D3
     * rendering.  The association candidates themselves remain in the model — they are required
     * later for UML class-diagram construction, where generalizations and associations are
     * first-class citizens.
     */
    private static Set<String> associationArtifactLemmas(final List<Candidate> candidates,
                                                         final Set<String> candidateLemmas) {
        final Set<String> artifactLemmas = new HashSet<>();
        for (final Candidate c : candidates) {
            if (c == null || c.getCandidateName() == null) continue;
            // Only association-typed candidates are considered synthetic compounds.
            if (!"Association".equals(typeLabel(c.getCandidateType()))) continue;
            final String lemma = lower(c.getCandidateName());
            // Compound of the form "X_Y" — split on the (single) underscore separator.
            final int sep = lemma.indexOf('_');
            if (sep <= 0 || sep >= lemma.length() - 1) continue;
            final String left = lemma.substring(0, sep);
            final String right = lemma.substring(sep + 1);
            // Both halves must be real candidate lemmas for this to be a derived compound.
            if (candidateLemmas.contains(left) && candidateLemmas.contains(right)) {
                artifactLemmas.add(lemma);
            }
        }
        return artifactLemmas;
    }

    // ============ Node sizing and colour ============

    /** Linear scale: count 1 → 1.0, maxCount → MAX_SCALE. Clamped below 1. */
    private static double scaleFor(final int count, final int maxCount) {
        if (maxCount <= MIN_MATCH_COUNT) return 1.0;
        final int n = Math.max(count, MIN_MATCH_COUNT);
        return 1.0 + (MAX_SCALE - 1.0) * (n - MIN_MATCH_COUNT) / (maxCount - MIN_MATCH_COUNT);
    }

    /**
     * Penn POS code → fill colour. Mirrors {@code LexicalGraphGenerator.posColor}.
     */
    private static String posColor(final String pos) {
        return switch (pos) {
            case "NN", "NNS" -> "#3498DB";
            case "VB", "VBZ", "VBD", "VBG", "VBN", "VBP" -> "#E74C3C";
            case "JJ", "JJR", "JJS" -> "#2ECC71";
            case "DT" -> "#F39C12";
            case "PRP", "PRP$" -> "#9B59B6";
            case "IN" -> "#17A2B8";
            case "RB" -> "#FFBB28";
            case "CD" -> "#F1C40F";
            case "CC" -> "#00C49F";
            case "WDT", "WP", "WP$", "WRB" -> "#9B59B6";
            case "NNP", "NNPS" -> "#85C1E9";
            case ",", ".", "!", "?", ";", ":" -> "#FFFFFF";
            default -> "#AAB7B8";
        };
    }

    /**
     * Every candidate owned by the model, in a single snapshot.  Mirrors the per-type candidate.
     */
    private static List<Candidate> candidatesOf(final DomainModel model) {
        final List<Candidate> all = new ArrayList<>();
        if (model.classList != null) {
            all.addAll(model.classList);
        }
        if (model.responsibilityList != null) {
            all.addAll(model.responsibilityList);
        }
        if (model.attributeList != null) {
            all.addAll(model.attributeList);
        }
        if (model.collaboratorList != null) {
            all.addAll(model.collaboratorList);
        }
        return all;
    }

    /**
     * The approved subset of the candidate set — those carrying at least one {@link Review} whose
     * status is {@link ReviewStatus#APPROVED}.  These are the seeds the diagram expands outward
     * from, kept in their original order.
     */
    private static List<Candidate> approvedCandidates(final List<Candidate> candidates) {
        return candidates.stream()
                .filter(DomainModelDiagramsD3::isApproved)
                .toList();
    }

    private static boolean isApproved(final Candidate candidate) {
        return candidate.getReviews() != null
                && candidate.getReviews().stream()
                        .anyMatch(review -> review != null && review.getStatus() == ReviewStatus.APPROVED);
    }

    /**
     * The lower-cased candidate names of the snapshot.  The whitelist spans <em>every</em>
     * candidate of the model (not just the approved seeds) so the expansion can travel through any
     * candidate word and draw approved candidates together with their un-approved neighbours.
     */
    private static Set<String> candidateLemmas(final List<Candidate> allCandidates) {
        final Set<String> lemmas = new HashSet<>();
        allCandidates.forEach(c -> lemmas.add(lower(c.getCandidateName())));
        return lemmas;
    }

    /**
     * Lower-case candidate name → every candidate carrying that name.  A single word can exist
     * more than once (e.g. the same name across candidate types), and each such candidate
     * contributes its own {@link RuleMatch}es to the expansion.
     */
    private static Map<String, List<Candidate>> candidatesByLemma(final List<Candidate> allCandidates) {
        final Map<String, List<Candidate>> byLemma = new HashMap<>();
        allCandidates.forEach(c ->
                byLemma.computeIfAbsent(lower(c.getCandidateName()), k -> new ArrayList<>()).add(c));
        return byLemma;
    }

    /**
     * Breadth-first traversal of the candidate-connection graph starting from the {@code seeds}
     * (the model's approved candidates), up to {@code maxDepth} hops, returning the <em>distinct</em>
     * candidate-restricted dependencies.
     * <p>
     * Round 0 expands the approved seeds themselves (their direct connections); round 1 expands
     * each directly-connected candidate (their connections); and so on.  Every dependency kept is
     * restricted to candidate words, and candidates already expanded in an earlier round are never
     * re-expanded, which bounds the traversal even when the candidate graph contains cycles.
     */
    private static List<TypedDependency> collectDependencies(final List<Candidate> seeds,
                                                             final Set<String> candidateLemmas,
                                                             final Map<String, List<Candidate>> candidatesByLemma,
                                                             final int maxDepth) {
        final List<TypedDependency> dependencies = new ArrayList<>();
        final Set<TypedDependency> seen = new HashSet<>();
        final Set<Candidate> visited = new HashSet<>();
        Set<Candidate> frontier = new LinkedHashSet<>(seeds);
        for (int hop = 0; hop < maxDepth && !frontier.isEmpty(); hop++) {
            final Set<Candidate> nextFrontier = new LinkedHashSet<>();
            for (final Candidate candidate : frontier) {
                visited.add(candidate);
                for (final RuleMatch ruleMatch : candidate.getRuleMatches()) {
                    final TypedDependency td = ruleMatch.getTypedDependency();
                    if (td == null || !bothEndpointsAreCandidates(td, candidateLemmas)) {
                        continue;
                    }
                    if (!seen.add(td)) {
                        continue;
                    }
                    dependencies.add(td);
                    // The counterpart candidates become the next round's frontier.
                    addCandidatesForLemma(td.getGovernorLemma(), candidatesByLemma, visited, nextFrontier);
                    addCandidatesForLemma(td.getDependentLemma(), candidatesByLemma, visited, nextFrontier);
                }
            }
            frontier = nextFrontier;
        }
        return dependencies;
    }

    /** Adds every candidate carrying {@code lemma} that has not yet been expanded to the next frontier. */
    private static void addCandidatesForLemma(final String lemma,
                                              final Map<String, List<Candidate>> candidatesByLemma,
                                              final Set<Candidate> visited,
                                              final Set<Candidate> nextFrontier) {
        for (final Candidate candidate : candidatesByLemma.getOrDefault(lower(lemma), List.of())) {
            if (!visited.contains(candidate)) {
                nextFrontier.add(candidate);
            }
        }
    }

    /**
     * Keeps only those typed dependencies whose governor <em>and</em> dependent lemma both
     * correspond to a candidate word — mirroring the candidate-word restriction applied by
     * {@link domox.dom.requirements.Documents#renderDiagram} and {@link CandidateDiagrams}.
     */
    private static boolean bothEndpointsAreCandidates(final TypedDependency td, final Set<String> candidateLemmas) {
        return candidateLemmas.contains(lower(td.getGovernorLemma()))
                && candidateLemmas.contains(lower(td.getDependentLemma()));
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** Friendly label of a candidate type, e.g. {@code ClassCdd} → {@code Class}. */
    private static String typeLabel(final String candidateType) {
        if (candidateType == null || candidateType.isEmpty()) {
            return "Domain";
        }
        return candidateType.endsWith("Cdd")
                ? candidateType.substring(0, candidateType.length() - 3)
                : candidateType;
    }

    /**
     * Minimal JSON string escaping: backslash, double-quote, and control characters.
     */
    private static String escapeJson(final String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' ->  out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}