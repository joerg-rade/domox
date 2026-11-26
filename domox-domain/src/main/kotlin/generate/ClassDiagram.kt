/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package generate

import domox.dom.crc.model.AssociationType
import domox.dom.crc.model.ClassCdd
import domox.dom.crc.model.CollaboratorCdd
import domox.dom.crc.model.DomainModel
import domox.dom.crc.model.ModelContent
import domox.dom.crc.model.SubDomainClassification
import domox.dom.rdf.UmlDiagramLayout

/**
 * Assembles the model's class diagrams — as PlantUML text and as the graph the D3 panel draws — from the
 * **reviewed** candidates.
 *
 * <p><b>Review-aware, not a dump.</b>  The export used to render every class and every member the
 * analysis produced, whatever the review said — which is why the assembled model claimed 168
 * operation lines when only 85 were approved, and 88 attribute lines when only 9 were
 * ({@code MODEL_UML_READINESS_REVIEW.md} §2.1).  Every diagram assembled here contains only what the
 * review approved, and only classes that have something to say (see
 * {@link ModelContent#includedClasses(DomainModel)}).</p>
 *
 * <p><b>Three views, two forms.</b>  Each of the three views below exists as PlantUML text
 * ([build], [buildForSubDomain], [buildSubDomainOverview] — served as a download by
 * {@code DomainModels#downloadPlantUml}) and as a [Graph] ([graph], [graphForSubDomain],
 * [graphOverview] — laid out by {@code UmlDiagramLayout#render} and drawn by the browser).  Both forms
 * read the same class list and the same collaboration list, so they cannot disagree about what the model
 * contains.</p>
 *
 * <ul>
 *   <li>[build] — the whole model, flat: every included class and every material edge between two of
 *       them.</li>
 *   <li>[buildForSubDomain] — one diagram per sub-domain: the included classes assigned to it and the
 *       edges <em>wholly inside</em> it.  Edges that leave the sub-domain are deliberately dropped —
 *       they are what the overview is for, and drawing them here would need a boundary stub for every
 *       neighbour.</li>
 *   <li>[buildSubDomainOverview] — one node per sub-domain (plus an {@code Unassigned} node when
 *       classes have no sub-domain) and one edge per distinct cross-sub-domain relation, labelled with
 *       how many collaborations it aggregates.  This is the map that says how the sub-domains relate;
 *       the per-sub-domain diagrams say what is inside each.  Its D3 form ([graphOverview]) draws each
 *       node as a UML <b>package</b> and lists inside it the classes of that sub-domain which relate to a
 *       class outside it — the classes the connectors between packages belong to.</li>
 * </ul>
 *
 * <p><b>What "included" means.</b>  {@link ModelContent#includedClasses(DomainModel)} answers it and
 * this class only renders the answer: a class is in the diagram when it is <em>approved</em> and
 * either carries approved content of its own or is an end of a material collaboration.  The second
 * half is the "no empty boxes" rule of the playbook (§4.6) — an approved class with neither draws as
 * an empty rectangle, so it is left out of the diagram and rejected in the review
 * ({@code DomainModels#rejectEmptyClasses}).</p>
 *
 * <p><b>Identity.</b>  Membership of a class in the diagram is tested by object identity
 * ({@link java.util.Set} over the entity, which defines no {@code equals}) — the classes and the
 * collaborators' endpoints all come from the model's own lists, so the same row is the same
 * instance.</p>
 */
class ClassDiagram {

    /** The whole model: every included class, and every material edge between two included classes. */
    fun build(dm: DomainModel): String = render(dm, ModelContent.includedClasses(dm))

    /**
     * One sub-domain's diagram: its included classes and the material edges wholly inside it.
     *
     * @param subDomainName the sub-domain's name, matched case-insensitively; blank selects nothing
     *                      (rather than everything, which a blank parameter would otherwise mean)
     */
    fun buildForSubDomain(dm: DomainModel, subDomainName: String?): String =
        render(dm, subDomainClasses(dm, subDomainName))

    /**
     * The names of the sub-domains that would produce a non-empty diagram — the sub-domain tabs of
     * {@code UmlDiagramSet}, i.e. the "one diagram per sub-domain" list.
     *
     * <p>Classes with no sub-domain are not a diagram of their own; they appear as the
     * {@code Unassigned} node of the overview instead.</p>
     */
    fun subDomainNames(dm: DomainModel): List<String> =
        ModelContent.includedClasses(dm)
            .map { nameOf(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    // -- the same three diagrams, as a graph the D3 panel draws ------------------

    /**
     * One diagram as a graph — its boxes and the connectors between them — <em>before</em> either
     * renderer draws it.
     *
     * <p>{@link #build} and its siblings turn the same content into PlantUML text (served as a
     * download); {@code UmlDiagramLayout#render} turns this graph into the
     * {@code {"layout":"uml","force":true,…}} payload the D3 panel draws and the reader can drag.  Both
     * read the <em>same</em> class list and the same collaboration list, so the picture and the text
     * cannot disagree about what the model contains — which is the whole point of assembling the content
     * here rather than in either renderer.</p>
     */
    data class Graph @JvmOverloads constructor(
        val title: String,
        val nodes: List<UmlDiagramLayout.Node>,
        val edges: List<UmlDiagramLayout.Edge>,
        /**
         * Whether each node is a *sub-domain* rather than a class — which is what tells the picture to draw
         * a **package** symbol instead of a class box, and to read the node's [attributes] as the list of
         * its classes rather than as a class's own members.
         *
         * <p>Carried on the graph, not inferred from the title, because the two diagrams share every other
         * field: a caller that had to match on a title would break the moment the wording changed.</p>
         */
        val packages: Boolean = false,
    )

    /** The whole model as a graph — what {@link #build} renders, which has no stored geometry of its own. */
    fun graph(dm: DomainModel): Graph {
        val included = ModelContent.includedClasses(dm)
        return Graph(titleOf(dm, "whole model"), nodesOf(included), edgesOf(dm, included.toSet()))
    }

    /** One sub-domain as a graph — what {@link #buildForSubDomain} renders. */
    fun graphForSubDomain(dm: DomainModel, subDomainName: String?): Graph {
        val included = subDomainClasses(dm, subDomainName)
        val wanted = subDomainName?.trim() ?: ""
        return Graph(
            titleOf(dm, if (wanted.isEmpty()) "sub-domain (unnamed)" else "sub-domain $wanted"),
            nodesOf(included),
            edgesOf(dm, included.toSet()),
        )
    }

    /**
     * The sub-domain overview as a graph — what {@link #buildSubDomainOverview} renders: one node per
     * sub-domain, and one edge per distinct cross-sub-domain relation, labelled with the number of
     * collaborations it aggregates.
     *
     * <p>Every node is in one band: an overview node <em>is</em> a sub-domain, so grouping the nodes
     * would turn the diagram's own content into whitespace.</p>
     *
     * <p>The graph is marked [Graph.getPackages()], so the picture draws each node as a UML <b>package</b>
     * rather than as a class box, and each node lists the classes of its sub-domain that take part in a
     * relation leaving it — the classes the connectors between packages belong to.  Each of those lines
     * also states the class row it stands for, so the picture can make a name in the package body a
     * click-through to that class's own page: a package is a list of rows, and the reader who clicks one
     * of them means the class, not the sub-domain.</p>
     */
    fun graphOverview(dm: DomainModel): Graph {
        val overview = overviewOf(dm)
        val nodes = overview.nodeIndex.keys.map { label ->
            // The node's `attributes` slot carries the package's **contents**: the classes of this
            // sub-domain that take part in a relation leaving it.  Those are the classes the connectors
            // between packages actually belong to, so the reader can see what an edge stands for without
            // opening the sub-domain's own diagram.
            //
            // The same lines are listed again as `contents`, each with its own `objectType` / `id` — the
            // pair the client links on.  The *package* itself carries none: it is a sub-domain, and a
            // click on the body means the class whose name was clicked.
            val contents = overview.members[label].orEmpty().map { cls ->
                UmlDiagramLayout.Node.Content(cls.candidateName, cls.objectType, cls.id)
            }
            UmlDiagramLayout.Node(
                label,
                label,
                null,
                contents.map { it.name() },
                emptyList(),
                null,
                null,
                null,
                null,
                contents,
            )
        }
        val edges = overview.edges.entries.map { entry ->
            UmlDiagramLayout.Edge(
                entry.key.first,
                entry.key.second,
                UmlDiagramLayout.ASSOCIATION,
                entry.value.toString(),
                // The count is the *label*, so what it counts has to travel with the edge: the reader who
                // wants to know which classes two sub-domains lean on through points at the line and reads
                // these (see `overviewOf`).  A count and its explanation are built in the same pass, so they
                // cannot disagree about how many collaborations there are.
                overview.edgeMembers[entry.key].orEmpty().toList(),
            )
        }
        return Graph(titleOf(dm, "sub-domain overview"), nodes, edges, packages = true)
    }

    /**
     * The sub-domain overview: one node per sub-domain, one edge per distinct cross-sub-domain
     * relation.
     *
     * <p>Every material collaboration whose two ends sit in <em>different</em> sub-domains is
     * aggregated into a single edge, labelled with the number of collaborations it stands for — a
     * count rather than the verb, because one overview edge usually stands for several different
     * verbs and a multi-line label would defeat the point of an overview.  Edges inside one
     * sub-domain are omitted: they belong to that sub-domain's own diagram.</p>
     *
     * <p>A class with no sub-domain is drawn as one {@code Unassigned} node rather than dropped: after
     * the sub-domain pass there should be none, so the node's presence is itself the signal that the
     * partition is incomplete.</p>
     */
    fun buildSubDomainOverview(dm: DomainModel): String {
        val overview = overviewOf(dm)

        val code = PumlCode()
        for ((label, index) in overview.nodeIndex) {
            code.addLine("class \"$label\" as SD$index")
        }
        for ((pair, count) in overview.edges) {
            val from = overview.nodeIndex.getValue(pair.first)
            val to = overview.nodeIndex.getValue(pair.second)
            code.addLine("SD$from --> SD$to : $count")
        }
        code.asUml()
        return code.code
    }

    /**
     * The overview's content, before either renderer draws it: node label → position, undirected
     * edge → the number of collaborations it stands for, node label → the classes of that
     * sub-domain that take part in a relation leaving it, and undirected edge → the collaborations
     * themselves, one line each.
     *
     * <p>Shared by {@link #buildSubDomainOverview} (PlantUML) and {@link #graphOverview} (D3), so the
     * overview cannot aggregate its edges one way on paper and another on screen.</p>
     *
     * <p>A member is the {@link ClassCdd} row itself, not only its name: the D3 picture lists those
     * classes inside the package and makes each one a click-through to its own page, which needs the
     * row's id and Causeway object type.  The PlantUML form draws the names alone.</p>
     *
     * <p>{@code edgeMembers} is keyed the same way {@code edges} is, and is the answer to the question the
     * count raises but cannot answer: <em>which</em> collaborations is the edge standing for.  It is filled
     * in the same pass as the count, so the two always agree — one line per collaboration counted.</p>
     */
    private class Overview(
        val nodeIndex: LinkedHashMap<String, Int>,
        val edges: LinkedHashMap<Pair<String, String>, Int>,
        val members: LinkedHashMap<String, List<ClassCdd>>,
        val edgeMembers: LinkedHashMap<Pair<String, String>, List<String>>,
    )

    private fun overviewOf(dm: DomainModel): Overview {
        val included = ModelContent.includedClasses(dm)
        val inModel = included.toSet()

        // Node label -> its position, in first-appearance order, so the diagram is deterministic.
        val nodeIndex = LinkedHashMap<String, Int>()
        for (cls in included) {
            val label = labelOf(cls)
            if (!nodeIndex.containsKey(label)) {
                nodeIndex[label] = nodeIndex.size
            }
        }

        // Undirected edge (an association has no direction in the model) -> how many it stands for.
        // The pair is ordered by node position, so an edge is always drawn from the earlier node.
        // The same pass records each node's *outward* classes: a class on one end of a relation that
        // leaves its sub-domain is one of the classes the reader has to know about, and a class that
        // relates only to its own neighbours says nothing about how the packages fit together.
        val edges = LinkedHashMap<Pair<String, String>, Int>()
        val members = LinkedHashMap<String, LinkedHashMap<String, ClassCdd>>()
        // Undirected edge -> what the count on that edge stands for, one line per collaboration counted.
        // A reader who sees "7" between two packages cannot tell which seven relations it means; this is
        // the list the connector's infobox prints, built in the same pass as the count so the two agree.
        val edgeMembers = LinkedHashMap<Pair<String, String>, MutableList<String>>()
        for (asc in ModelContent.materialCollaborations(dm)) {
            val (source, target) = endsWithin(asc, inModel) ?: continue
            val from = labelOf(source)
            val to = labelOf(target)
            if (from == to) continue
            val key = if (nodeIndex.getValue(from) <= nodeIndex.getValue(to)) from to to else to to from
            edges.merge(key, 1) { a, b -> a + b }
            // The same key the count is filed under, so the line and its explanation are one pass and
            // cannot drift apart.  The list is built here rather than derived later because only here is
            // the collaboration's own row (`asc`) in hand.
            edgeMembers.getOrPut(key) { mutableListOf() } += collaborationLine(asc, source, target)
            addMember(members, from, source)
            addMember(members, to, target)
        }

        // Sorted, so the list a package draws does not follow the model's collaboration order.
        val memberList = LinkedHashMap<String, List<ClassCdd>>()
        for ((label, rows) in members) {
            memberList[label] = rows.values.sortedBy { it.candidateName }
        }
        // Sorted too, for the same reason: the order the model happens to list its collaborations in is
        // not something the reader can see, and an infobox that reorders between runs reads as a bug.
        val edgeMemberList = LinkedHashMap<Pair<String, String>, List<String>>()
        for ((key, lines) in edgeMembers) {
            edgeMemberList[key] = lines.sorted()
        }
        return Overview(nodeIndex, edges, memberList, edgeMemberList)
    }

    /**
     * Records that {@code cls} is one of {@code node}'s outward classes; a nameless class is skipped, and
     * a class that reaches out along two relations is recorded once.
     */
    private fun addMember(
        members: MutableMap<String, LinkedHashMap<String, ClassCdd>>,
        node: String,
        cls: ClassCdd,
    ) {
        val name = cls.candidateName
        if (name.isNullOrEmpty()) return
        members.getOrPut(node) { LinkedHashMap() }.putIfAbsent(name, cls)
    }

    // -- assembling a class diagram's graph --------------------------------------

    /** One sub-domain's included classes, matched case-insensitively on the sub-domain's name. */
    private fun subDomainClasses(dm: DomainModel, subDomainName: String?): List<ClassCdd> {
        val wanted = subDomainName?.trim() ?: ""
        return ModelContent.includedClasses(dm).filter { nameOf(it).equals(wanted, ignoreCase = true) }
    }

    /**
     * The boxes of a class diagram.
     *
     * <p>Ordered by sub-domain and then by name: the layout engine starts a new band whenever a node's
     * group changes (see {@code UmlDiagramLayout#place}), so this ordering is what turns a model's
     * sub-domains into visible blocks — and, being a total order on the model alone, it is also what
     * makes the picture reproducible.</p>
     *
     * <p>A class with no name is dropped rather than drawn as an unnamed box: {@link #render} skips it
     * for the same reason, and an edge could not be attached to it anyway.</p>
     *
     * <p>Each box also states the row it stands for (its {@code id} and its Causeway {@code objectType}),
     * so the picture can make the class name a click-through to that class's own page — the
     * {@code ClassCdd} the reviewer works with, not just a name in a drawing.</p>
     */
    private fun nodesOf(included: List<ClassCdd>): List<UmlDiagramLayout.Node> =
        included
            .filter { !it.candidateName.isNullOrEmpty() }
            .sortedWith(compareBy({ nameOf(it) }, { it.candidateName }))
            .map {
                UmlDiagramLayout.Node(
                    it.candidateName,
                    it.candidateName,
                    nameOf(it),
                    // The compartments and the archetype are what the D3 picture draws and the PDF always
                    // did; the lines are the export's own rendering, so the two cannot disagree about
                    // what a class contains.
                    it.approvedAttributeLines(),
                    it.approvedResponsibilityLines(),
                    it.classType.label,
                    it.classType.cssColor,
                    // The row this box *is*: the picture makes a class name a click-through to the
                    // class's own page.  `objectType` is read back from `@Named` (not derived from the
                    // class name), because it is what the `/wicket/object/<objectType>:<id>` URL needs.
                    it.id,
                    it.objectType,
                )
            }

    /**
     * The two ends of a collaboration that is drawn in a diagram, or {@code null} when it is not drawn
     * there: <b>both</b> of its ends must be classes the diagram contains.
     *
     * <p>An edge to a class outside it — a sub-domain boundary, or a class the review did not include — is
     * dropped rather than drawn into empty space, and a collaboration with no stated end is not drawn at
     * all.  One rule for all three diagrams: "wholly inside" is the same question in the picture
     * ([edgesOf]), in the text ([render]) and in the overview ([overviewOf]).</p>
     */
    private fun endsWithin(asc: CollaboratorCdd, inModel: Set<ClassCdd>): Pair<ClassCdd, ClassCdd>? {
        val source = asc.source ?: return null
        val target = asc.target ?: return null
        return if (inModel.contains(source) && inModel.contains(target)) source to target else null
    }

    /**
     * One counted collaboration as a single line — {@code "Source owns Target"}, the ends named exactly as
     * the boxes and package content lines are, so a reader can match the line to what they see.
     *
     * <p>{@code asc.label()} is the model's own verb ("owns", "is a") and is absent for some rows, in which
     * case the relation is stated with {@code "->"} rather than invented.  The ends are taken from the
     * collaboration's own rows, not from the two labels the edge is keyed by, so a line can never read
     * {@code "A -> A"} because two sub-domains happen to name a class the same way.</p>
     */
    private fun collaborationLine(asc: CollaboratorCdd, source: ClassCdd, target: ClassCdd): String {
        val verb = asc.label()?.takeIf { it.isNotBlank() } ?: "->"
        return "${source.candidateName} $verb ${target.candidateName}"
    }

    /**
     * The connectors of a class diagram: the model's material collaborations wholly inside it, in the order
     * the model lists them.
     */
    private fun edgesOf(dm: DomainModel, inModel: Set<ClassCdd>): List<UmlDiagramLayout.Edge> =
        ModelContent.materialCollaborations(dm).mapNotNull { edgeOf(it, inModel) }

    /** One material collaboration as an edge, or {@code null} when it is not wholly inside the diagram. */
    private fun edgeOf(asc: CollaboratorCdd, inModel: Set<ClassCdd>): UmlDiagramLayout.Edge? {
        val (source, target) = endsWithin(asc, inModel) ?: return null
        val from = source.candidateName ?: return null
        val to = target.candidateName ?: return null
        if (from.isEmpty() || to.isEmpty()) return null
        // The model states a generalization from the sub-class to the super-class, while
        // UmlDiagramLayout reads an edge the way its arrow reads: from the super-class.  The swap is
        // what lets PortLayout merge the group into one trunk on the super-class's own edge.
        //
        // An implementation — `implements`, PlantUML's dashed realization — is an is-a edge too and is read
        // the same way, so the D3 picture seeds the implementing class below the interface and draws the
        // arrowhead, where a plain association drew a grey line that said nothing about hierarchy.  The PDF
        // keeps the dashed distinction: it renders from `asc.toPlantUmlString()`, not from this mapping.
        return if (asc.type == AssociationType.GENERALIZATION || asc.type == AssociationType.IMPLEMENTATION) {
            UmlDiagramLayout.Edge(to, from, UmlDiagramLayout.GENERALIZATION, asc.label())
        } else {
            UmlDiagramLayout.Edge(from, to, UmlDiagramLayout.ASSOCIATION, asc.label())
        }
    }

    /** The title of a diagram: the model it was drawn from, and which view of it this is. */
    private fun titleOf(dm: DomainModel, view: String): String = "${dm.title()} — $view"

    /**
     * Renders one diagram: the given classes, then the model's material edges between two of them.
     *
     * <p>Edges are read from the model's collaboration list rather than from the classes, so an
     * association is drawn exactly once whichever end owns it — and an edge to a class that is not in
     * this diagram is dropped ([endsWithin]), the same rule the picture applies.</p>
     */
    private fun render(dm: DomainModel, included: List<ClassCdd>): String {
        val code = PumlCode()
        val inModel = included.toSet()
        for (cls in included) {
            val rendered: String = cls.toPlantUmlString() ?: ""
            if (rendered.isNotEmpty()) {
                code.add(rendered)
            }
        }
        for (asc in ModelContent.materialCollaborations(dm)) {
            if (endsWithin(asc, inModel) == null) continue
            val rendered: String = asc.toPlantUmlString() ?: ""
            if (rendered.isNotEmpty()) {
                code.addCollaborator(rendered)
            }
        }
        code.asUml()
        return code.code
    }

    /** The class's sub-domain name, or {@code ""} when it has none (or the sub-domain is unnamed). */
    private fun nameOf(cls: ClassCdd): String {
        val subDomain = cls.subDomain ?: return ""
        return subDomain.name ?: ""
    }

    /**
     * The overview node a class contributes to: its sub-domain — with the strategic classification
     * when one has been decided — or the single {@code Unassigned} node.
     */
    private fun labelOf(cls: ClassCdd): String {
        val name = nameOf(cls)
        if (name.isEmpty()) return UNASSIGNED
        val classification = cls.subDomain?.classification
        return if (classification == null || classification == SubDomainClassification.UNCLASSIFIED) {
            name
        } else {
            "$name ($classification)"
        }
    }

    private companion object {
        /** Label of the overview node that collects classes with no sub-domain. */
        const val UNASSIGNED = "Unassigned"
    }
}
