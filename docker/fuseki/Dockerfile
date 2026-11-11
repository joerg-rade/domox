# =============================================================================
#  Apache Jena Fuseki serving reference ontologies as the dataset  /ds
# =============================================================================
#  The dataset holds five supplementary reference vocabularies — Schema.org, BFO,
#  gist and BPMN (BBO + sBPMN) — each in its OWN named graph, so it stays individually
#  addressable with `GRAPH <iri> { ... }`:
#
#      <https://schema.org/>                        Schema.org (Turtle)
#      <http://purl.obolibrary.org/obo/bfo.owl>     BFO 2019-08-26 (RDF/XML)
#      <https://w3id.org/semanticarts/ontology/gistCore>   gist 14.1.0 — minimalist enterprise upper ontology (Turtle)
#      <http://BPMNbasedOntology>                   BBO — BPMN 2.0 Based Ontology (RDF/XML)
#      <https://ntimes404.github.io/sBPMN/>         sBPMN — semantic BPMN 2.0 (Turtle)
#
#  The DoMoX OntoUML model (`domox-domain/src/main/resources/ontology.ttl`) is NOT
#  imported — this image serves the reference vocabularies only.
#
#  Build from the REPOSITORY ROOT (the build context is pruned by `.dockerignore`):
#
#      docker build -f docker/fuseki/Dockerfile -t domox-fuseki .
#
#  Force a FRESH download + re-import.  The URLs below track "latest" revisions and
#  Docker caches the RUN layer whenever the Dockerfile is unchanged, so pass
#  --no-cache to bypass the cache and re-fetch every ontology:
#
#      docker build --no-cache -f docker/fuseki/Dockerfile -t domox-fuseki .
#
#  Each download is best-effort: a flaky upstream URL logs a WARNING and is skipped
#  rather than failing the whole image build.  Override any URL with --build-arg.
#
#  Run (Fuseki UI + SPARQL endpoint on http://localhost:3030):
#
#      docker run -d --name domox-fuseki -p 3030:3030 domox-fuseki
#
#      SPARQL : http://localhost:3030/ds/sparql
#      UI     : http://localhost:3030/        (user `admin`; password in `docker logs`)
# =============================================================================
FROM stain/jena-fuseki:5.1.0

# Pre-built TDB2 database (published as dataset /ds, see CMD) and where the
# downloaded ontologies are staged.
ENV DOMOX_DATABASE=/opt/domox/databases/ds \
    DOMOX_ONTOLOGY_DIR=/opt/domox/ontologies

USER root

# -----------------------------------------------------------------------------
#  Reference ontologies (Schema.org, BFO, gist, BPMN) — downloaded and imported.
# -----------------------------------------------------------------------------
#  Each is loaded into its OWN named graph (never the default graph) so it stays
#  individually addressable with `GRAPH <iri> { ... }`:
#
#      <https://schema.org/>                        Schema.org (Turtle)
#      <http://purl.obolibrary.org/obo/bfo.owl>     BFO 2019-08-26 (RDF/XML)
#      <https://w3id.org/semanticarts/ontology/gistCore>   gist 14.1.0 (Turtle)
#      <http://BPMNbasedOntology>                   BBO — BPMN 2.0 Based Ontology (RDF/XML)
#      <https://ntimes404.github.io/sBPMN/>         sBPMN — semantic BPMN 2.0 (Turtle)
#
#  Each download is best-effort (curl --retry): a flaky third-party URL logs a
#  WARNING and is skipped rather than failing the whole image build.  Override any
#  URL with --build-arg, e.g.
#      docker build -f docker/fuseki/Dockerfile \
#        --build-arg SCHEMA_ORG_URL=https://example.org/schemaorg.ttl -t domox-fuseki .
ARG SCHEMA_ORG_URL=https://schema.org/version/latest/schemaorg-current-https.ttl
ARG BFO_URL=http://purl.obolibrary.org/obo/bfo.owl
ARG GIST_URL=https://raw.githubusercontent.com/semanticarts/gist/develop/ontologies/gistCore.ttl
ARG BBO_URL=https://raw.githubusercontent.com/AminaANNANE/BBO_BPMNbasedOntology/master/BBO.owl
ARG SBPMN_URL=https://raw.githubusercontent.com/ntimes404/sBPMN/main/ontology.ttl

# Download each reference ontology and bulk-load it into a named graph of the TDB2
# database.  Repeated tdb2.tdbloader runs against the same --loc APPEND, so all five
# named graphs coexist in /ds.  NOTE: the database is built OUTSIDE /fuseki — that
# path is a VOLUME in the base image, and Docker discards build-time writes into it.
RUN set -eux; \
    mkdir -p "${DOMOX_ONTOLOGY_DIR}" "${DOMOX_DATABASE}"; \
    fetch_and_load() { \
        name="$1"; url="$2"; file="$3"; graph="$4"; \
        echo "==> Importing ${name} into graph <${graph}> from ${url}"; \
        if curl -fsSL --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 300 \
                "${url}" -o "${DOMOX_ONTOLOGY_DIR}/${file}"; then \
            java -cp "${FUSEKI_HOME}/fuseki-server.jar" tdb2.tdbloader \
                 --loc="${DOMOX_DATABASE}" --graph="${graph}" \
                 "${DOMOX_ONTOLOGY_DIR}/${file}"; \
        else \
            echo "WARNING: could not download ${name} from ${url} - skipping"; \
        fi; \
    }; \
    fetch_and_load "Schema.org" "${SCHEMA_ORG_URL}" "schemaorg.ttl" "https://schema.org/"; \
    fetch_and_load "BFO"        "${BFO_URL}"        "bfo.owl"        "http://purl.obolibrary.org/obo/bfo.owl"; \
    fetch_and_load "gist"       "${GIST_URL}"       "gistCore.ttl"   "https://w3id.org/semanticarts/ontology/gistCore"; \
    fetch_and_load "BBO (BPMN)" "${BBO_URL}"        "bbo.owl"        "http://BPMNbasedOntology"; \
    fetch_and_load "sBPMN"      "${SBPMN_URL}"      "sbpmn.ttl"      "https://ntimes404.github.io/sBPMN/"; \
    chown -R fuseki:fuseki /opt/domox

# Back to the image's unprivileged user.
USER fuseki

# Serve the pre-built database as dataset /ds with SPARQL Update enabled, so the
# dataset can be extended at runtime.  `--tdb2` is the default but stated for clarity.
# The binary must be spelled out: the base image's entrypoint execs this CMD as-is.
CMD ["/jena-fuseki/fuseki-server", \
     "--tdb2", \
     "--update", \
     "--loc=/opt/domox/databases/ds", \
     "/ds"]
