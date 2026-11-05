Angelo -> https://github.com/angelo-v/opencode-playground/blob/main/.opencode/skills/wikidata-query/SKILL.md

Sample files for PetShop/Store.

## Which file is which

All of these live in `domox-domain/src/main/resources/`:

| File | Provenance | Vocabulary |
| :--- | :--- | :--- |
| `PetStore-ontology.ttl` | the DBpedia export below | `dbo:`/`dbr:` — individuals only, no classes |
| `pet_store_wikidata.ttl` | the Wikidata CONSTRUCT below | `wdt:`/`wd:` |
| `ontology.ttl` | the **OntoUML `services2015` model** (link at the bottom) | `ontouml:` — a UML class model |
| `ontouml-models-latest.ttl` | the whole OntoUML model collection (~74 MB — it is on the classpath, so every build and boot carries it) | `ontouml:` |

The ontology diagram reads from the SPARQL endpoint in `domox.rdf.endpoint-url` — by default the
local **Fuseki** server (`http://localhost:3030/ds/sparql`; see `docker/fuseki/`).  When that is
blank it falls back to the **bundled default** `ontology.ttl`
(`Corpora.DEFAULT_ONTOLOGY_RESOURCE`); swap that per deployment with `domox.rdf.local-resource`
(a classpath name or a file path).

The diagram understands **both class vocabularies**: a plain OWL/RDFS ontology
(`owl:Class` + `rdfs:subClassOf` + `rdfs:label`) and the OntoUML one (`ontouml:Class`,
`ontouml:name`, and generalization reified as `ontouml:Generalization` /
`ontouml:general` + `ontouml:specific`) — see `RdfGraphDiagrams` and `Corpora.focusQuery`.

A node whose display name is an **opaque tool id** (e.g. `ykj3OGFS_j2pB_5` — the OntoUML export's
diagram *layout* resources carry no `ontouml:name`, so they fall back to the IRI's local name) is
dropped from the diagram, together with the edges that pointed at it; a class whose super-class is
known only by such an id loses its cluster label instead.  See `RdfGraphDiagrams.looksLikeOpaqueId`.

```bash
# DBpedia export -> PetStore-ontology.ttl
curl -L -H "Accept: text/turtle" http://dbpedia.org/data/Pet_store.ttl -o PetStore-ontology.ttl

# Wikidata CONSTRUCT -> pet_store_wikidata.ttl
curl -G "https://query.wikidata.org/sparql" \
  -H "Accept: text/turtle" \
  -H "User-Agent: MyDataExtractor/1.0 (contact@example.com)" \
  --data-urlencode "query=
    CONSTRUCT {
      ?item ?p ?o .
    }
    WHERE {
      SERVICE wikibase:mwapi {
        bd:serviceParam wikibase:endpoint 'www.wikidata.org' ;
                        wikibase:api 'EntitySearch' ;
                        mwapi:search 'Pet Store' ;
                        mwapi:language 'en' .
        ?item wikibase:apiOutputItem mwapi:item .
      }
      ?item ?p ?o .
    }
    LIMIT 200
  " \
  -o pet_store_wikidata.ttl
```

https://github.com/OntoUML/ontouml-models/blob/master/models/services2015/ontology.ttl
