package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * <b>Serialises an indexed RDF graph to the D3 payload</b> the client renders:
 * <code>{nodes, links, legend}</code>, plus the {@code reSearch} flag that enables node-click
 * drill-down.
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
 *   <li><b>links</b> — one per triple: subject → object, labelled with the predicate's local name
 *       (e.g. {@code subClassOf}, {@code owns}, {@code isVaccinated})</li>
 * </ul>
 *
 * <p>Extracted from {@code RdfGraphDiagrams}; it is handed the nodes {@link RdfGraphIndex} produced
 * and does nothing but turn them into JSON.</p>
 */
final class RdfDiagramJson {

    private RdfDiagramJson() {
    }

    /**
     * Renders the graph as D3 JSON (nodes/links/legend).
     *
     * @param graph the RDF graph, e.g. {@code rdfPort.load("ontology.ttl")}
     * @param nodes the nodes {@link RdfGraphIndex#index} produced
     */
    static String write(final RdfGraphTO graph, final Map<String, RdfDiagramNode> nodes) {
        final StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendNodes(json, nodes);
        json.append(',');
        appendLinks(json, graph, nodes);
        json.append(',');
        appendLegend(json, nodes);
        // The ontology payload supports drilling down: the client re-runs the ontology search for a
        // clicked node's label and redraws the graph.  Flagged here so the shared renderer enables that
        // only for ontology graphs — a CRC candidate diagram's synthetic nodes must not trigger it.  An
        // empty graph has nothing to click, so it keeps the plain {nodes,links,legend} shape.
        if (!nodes.isEmpty()) {
            json.append(",\"reSearch\":true");
        }
        json.append('}');
        return json.toString();
    }

    // -- links -----------------------------------------------------------------

    private static void appendLinks(
            final StringBuilder json, final RdfGraphTO graph, final Map<String, RdfDiagramNode> nodes) {
        json.append("\"links\":[");
        boolean first = true;
        final Set<String> seen = new LinkedHashSet<>();
        for (final RdfTripleTO triple : graph.triples()) {
            final String target = triple.literal() ? RdfGraphIndex.literalId(triple.objectValue()) : triple.objectValue();
            if (!nodes.containsKey(triple.subject()) || !nodes.containsKey(target)) {
                continue;
            }
            if (!seen.add(triple.subject() + "->" + target + ":" + triple.predicate())) {
                continue;   // duplicate statements in the source graph
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"source\":\"").append(RdfJson.escape(triple.subject()))
                    .append("\",\"target\":\"").append(RdfJson.escape(target))
                    .append("\",\"label\":\"").append(RdfJson.escape(RdfIriNames.localName(triple.predicate())))
                    .append("\"}");
        }
        json.append(']');
    }

    // -- nodes / legend JSON ---------------------------------------------------

    private static void appendNodes(final StringBuilder json, final Map<String, RdfDiagramNode> nodes) {
        json.append("\"nodes\":[");
        boolean first = true;
        for (final RdfDiagramNode node : nodes.values()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":\"").append(RdfJson.escape(node.id()))
                    .append("\",\"name\":\"").append(RdfJson.escape(node.name()))
                    .append("\",\"subdomain\":\"").append(RdfJson.escape(node.typeLocalName()))
                    .append("\",\"pos\":\"").append(node.kind().label)
                    .append("\",\"color\":\"").append(node.kind().color)
                    .append("\",\"r\":").append(node.kind().radius)
                    .append(",\"ontology\":\"").append(RdfJson.escape(node.ontology()))
                    .append("\",\"approved\":true")
                    .append(",\"isCandidateColumn\":false}");
        }
        json.append(']');
    }

    /** One legend entry per node kind that actually occurs in the graph. */
    private static void appendLegend(final StringBuilder json, final Map<String, RdfDiagramNode> nodes) {
        json.append("\"legend\":[");
        boolean first = true;
        for (final RdfNodeKind kind : RdfNodeKind.values()) {
            if (nodes.values().stream().noneMatch(node -> node.kind() == kind)) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"color\":\"").append(kind.color)
                    .append("\",\"label\":\"").append(kind.label)
                    .append("\",\"description\":\"").append(kind.description).append("\"}");
        }
        json.append(']');
    }
}
