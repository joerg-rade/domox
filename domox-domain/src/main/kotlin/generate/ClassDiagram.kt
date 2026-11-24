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
 * Assembles the model's PlantUML class diagrams from the **reviewed** candidates.
 *
 * <p><b>Review-aware, not a dump.</b>  The export used to render every class and every member the
 * analysis produced, whatever the review said — which is why the assembled model claimed 168
 * operation lines when only 85 were approved, and 88 attribute lines when only 9 were
 * ({@code MODEL_UML_READINESS_REVIEW.md} §2.1).  Every diagram built here contains only what the
 * review approved, and only classes that have something to say (see
 * {@link ModelContent#includedClasses(DomainModel)}).</p>
 *
 * <p><b>Three views.</b></p>
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
 *       the per-sub-domain diagrams say what is inside each.</li>
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
     * The names of the sub-domains that would produce a non-empty diagram — the choices for the
     * {@code generateSubDomainUmlDiagram} action, and the "one diagram per sub-domain" list.
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
     * <p>{@link #build} and its siblings turn this into PlantUML (Kroki → SVG → PDF → the pdf.js
     * panel); {@code UmlDiagramLayout#render} turns it into the {@code {"layout":"uml",…}} payload the
     * D3 panel draws statically.  Both read the <em>same</em> class list and the same collaboration
     * list, so the two pictures of one diagram cannot disagree about what the model contains — which is
     * the whole point of assembling the content here rather than in either renderer.</p>
     */
    data class Graph(
        val title: String,
        val nodes: List<UmlDiagramLayout.Node>,
        val edges: List<UmlDiagramLayout.Edge>,
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
     */
    fun graphOverview(dm: DomainModel): Graph {
        val overview = overviewOf(dm)
        val nodes = overview.nodeIndex.keys.map { UmlDiagramLayout.Node(it, it, null) }
        val edges = overview.edges.entries.map { entry ->
            UmlDiagramLayout.Edge(
                entry.key.first,
                entry.key.second,
                UmlDiagramLayout.ASSOCIATION,
                entry.value.toString(),
            )
        }
        return Graph(titleOf(dm, "sub-domain overview"), nodes, edges)
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
     * The overview's content, before either renderer draws it: node label → position, and undirected
     * edge → the number of collaborations it stands for.
     *
     * <p>Shared by {@link #buildSubDomainOverview} (PlantUML) and {@link #graphOverview} (D3), so the
     * overview cannot aggregate its edges one way on paper and another on screen.</p>
     */
    private class Overview(
        val nodeIndex: LinkedHashMap<String, Int>,
        val edges: LinkedHashMap<Pair<String, String>, Int>,
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
        val edges = LinkedHashMap<Pair<String, String>, Int>()
        for (asc in ModelContent.materialCollaborations(dm)) {
            val source = asc.source ?: continue
            val target = asc.target ?: continue
            if (!inModel.contains(source) || !inModel.contains(target)) continue
            val from = labelOf(source)
            val to = labelOf(target)
            if (from == to) continue
            val key = if (nodeIndex.getValue(from) <= nodeIndex.getValue(to)) from to to else to to from
            edges.merge(key, 1) { a, b -> a + b }
        }

        return Overview(nodeIndex, edges)
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
                )
            }

    /**
     * The connectors of a class diagram: the model's material collaborations whose two ends are both
     * drawn here.
     *
     * <p>An edge to a class outside the diagram — a sub-domain boundary, or a class the review did not
     * include — is dropped rather than drawn into empty space, exactly as {@link #render} drops it.</p>
     */
    private fun edgesOf(dm: DomainModel, inModel: Set<ClassCdd>): List<UmlDiagramLayout.Edge> =
        ModelContent.materialCollaborations(dm).mapNotNull { edgeOf(it, inModel) }

    /** One material collaboration as an edge, or {@code null} when it is not wholly inside the diagram. */
    private fun edgeOf(asc: CollaboratorCdd, inModel: Set<ClassCdd>): UmlDiagramLayout.Edge? {
        val source = asc.source ?: return null
        val target = asc.target ?: return null
        if (!inModel.contains(source) || !inModel.contains(target)) return null
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
     * this diagram (a sub-domain boundary, or a class the review did not include) is dropped.</p>
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
            val source = asc.source
            val target = asc.target
            if (source == null || target == null) continue
            if (!inModel.contains(source) || !inModel.contains(target)) continue
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
