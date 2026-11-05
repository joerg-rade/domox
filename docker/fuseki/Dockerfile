# =============================================================================
#  Apache Jena Fuseki serving the DoMoX ontology as the dataset  /ds
# =============================================================================
#  The dataset is the OntoUML `services2015` model the application bundles as
#  `domox-domain/src/main/resources/ontology.ttl` — the same graph the ontology
#  diagram reads in local mode.  Serving it from Fuseki lets
#  `domox.rdf.endpoint-url` point at `http://localhost:3030/ds/sparql` (see
#  `domox-webapp/src/main/resources/application.yml`) without touching the classpath.
#
#  Build from the REPOSITORY ROOT so the bundled ontology is in the build context:
#
#      docker build -f docker/fuseki/Dockerfile -t domox-fuseki .
#
#  If the bundled copy is missing (or you want a newer upstream revision) the build
#  downloads it from the OntoUML repository instead — see ONTOLOGY_URL below.
#
#  Run (Fuseki UI + SPARQL endpoint on http://localhost:3030):
#
#      docker run -d --name domox-fuseki -p 3030:3030 domox-fuseki
#
#      SPARQL : http://localhost:3030/ds/sparql
#      UI     : http://localhost:3030/        (user `admin`; password in `docker logs`)
# =============================================================================
FROM stain/jena-fuseki:5.1.0

# Upstream OntoUML model — used only when the bundled copy is absent.
ARG ONTOLOGY_URL=https://raw.githubusercontent.com/OntoUML/ontouml-models/master/models/services2015/ontology.ttl

# Source ontology and the pre-built TDB2 database (published as dataset /ds, see CMD).
ENV DOMOX_ONTOLOGY=/opt/domox/ontology.ttl \
    DOMOX_DATABASE=/opt/domox/databases/ds

USER root

# The ontology: prefer the copy the build context provides (repo root); the
# `domox-domain/src/main/resources/` directory always exists in the repository,
# so this COPY never fails even when the ontology file itself is absent.
COPY domox-domain/src/main/resources/ /opt/domox/source/

# Resolve the ontology, then pre-build the TDB2 index with tdb2.tdbloader.
# NOTE: we deliberately build the database OUTSIDE /fuseki — that path is a VOLUME
# in the base image, and Docker discards build-time writes into a volume.
RUN set -eux; \
    mkdir -p /opt/domox/databases; \
    if [ -f /opt/domox/source/ontology.ttl ]; then \
        echo "Using bundled ontology from the build context"; \
        cp /opt/domox/source/ontology.ttl "${DOMOX_ONTOLOGY}"; \
    else \
        echo "Bundled ontology absent - downloading ${ONTOLOGY_URL}"; \
        curl -fsSL "${ONTOLOGY_URL}" -o "${DOMOX_ONTOLOGY}"; \
    fi; \
    rm -rf /opt/domox/source; \
    java -cp "${FUSEKI_HOME}/fuseki-server.jar" tdb2.tdbloader \
         --loc="${DOMOX_DATABASE}" "${DOMOX_ONTOLOGY}"; \
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
