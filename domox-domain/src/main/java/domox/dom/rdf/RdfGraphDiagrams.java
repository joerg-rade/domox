package domox.dom.rdf;

import domox.port.RdfGraphTO;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

/**
 * Turns an RDF/OWL graph (as delivered by {@link domox.port.RdfPort}) into the JSON structure the
 * D3 force-layout renderer consumes: <code>{nodes, links, legend}</code>.
 *
 * <p>The shape is the same one {@code DomainModelDiagramsD3} / {@code CandidateD3Graph} produce, so
 * the existing client renderer (`d3-integration.mjs`) draws an ontology without any change:</p>
 *
 * <pre>
 *   "nodes": [ {"id","name","subdomain","pos","color","r","approved","isCandidateColumn"} ]
 *   "links": [ {"source","target","label"} ]
 *   "legend":[ {"color","label","description"} ]
 *   "reSearch": true          (enables the client's node-click drill-down)
 * </pre>
 *
 * <p><b>RDF → diagram mapping</b> (every RDF statement becomes an edge):</p>
 * <ul>
 *   <li><b>node id</b> — the IRI (blank nodes keep their <code>_:label</code>)</li>
 *   <li><b>name</b> — <code>rdfs:label</code> when present, else <code>ontouml:name</code>, else
 *       <code>skos:prefLabel</code>, else the IRI's local name.  A node <em>without</em> a name whose
 *       fallback local name is an opaque tool id (e.g. <code>ykj3OGFS_j2pB_5</code>) is dropped; a node
 *       carrying a real name is always kept, however machine-like that name happens to look — see
 *       {@link RdfIriNames#looksLikeOpaqueId(String)}</li>
 *   <li><b>subdomain</b> — the cluster group (the client clusters nodes by this field):
 *       a <b>class</b> by its super-class' name (empty for a root class), an <b>individual</b> by its
 *       <code>rdf:type</code>, a <b>literal</b> by its predicate</li>
 *   <li><b>pos</b> — the node kind shown in <code>&lt;&lt;…&gt;&gt;</code>:
 *       {@code Class}, {@code Individual} or {@code Literal}</li>
 *   <li><b>links</b> — one per triple: subject → object, labelled with the predicate's local name
 *       (e.g. {@code subClassOf}, {@code owns}, {@code isVaccinated})</li>
 *   <li><b>ontology</b> — the reference ontology a node came from ({@code gist}, {@code BFO},
 *       {@code Schema.org}, …), shown as a fourth label line.  Empty when the source has no named
 *       graphs — see {@link RdfIriNames#ontologyLabel(String)}</li>
 * </ul>
 *
 * <p>This service is now only the entry point: the node indexing lives in {@link RdfGraphIndex}, the
 * JSON emission in {@link RdfDiagramJson}, and the IRI naming rules in {@link RdfIriNames} (shared
 * with {@link DiagramIndex} and {@link OntologyTerms}).</p>
 */
@DomainService
@Named("domox.dom.rdf.RdfGraphDiagrams")
@Priority(PriorityPrecedence.LATE)
public class RdfGraphDiagrams {

    /**
     * Renders the graph as D3 JSON (nodes/links/legend).
     *
     * @param graph the RDF graph, e.g. {@code rdfPort.load("ontology.ttl")}
     */
    @Programmatic
    public String renderDiagram(final RdfGraphTO graph) {
        return RdfDiagramJson.write(graph, RdfGraphIndex.index(graph));
    }
}
