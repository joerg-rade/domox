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

import domox.dom.crc.model.ClassCdd
import domox.dom.crc.model.DomainModel
import domox.dom.crc.model.ModelContent
import domox.dom.crc.model.SubDomainClassification

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
    fun buildForSubDomain(dm: DomainModel, subDomainName: String?): String {
        val wanted = subDomainName?.trim() ?: ""
        return render(dm, ModelContent.includedClasses(dm).filter { nameOf(it).equals(wanted, ignoreCase = true) })
    }

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

        val code = PumlCode()
        for ((label, index) in nodeIndex) {
            code.addLine("class \"$label\" as SD$index")
        }
        for ((pair, count) in edges) {
            val from = nodeIndex.getValue(pair.first)
            val to = nodeIndex.getValue(pair.second)
            code.addLine("SD$from --> SD$to : $count")
        }
        code.asUml()
        return code.code
    }

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
