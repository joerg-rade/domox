## Arora et al. 2016 — Generalization / Is-a Rules (MODELS'16)

### B5 — Explicit Generalization (most authority)
Patterns scanned in NL text:
- `"is a"` / `"are a"` → X is a Y → Y --|> X
- `"type of"` → X is a type of Y → Y --|> X
- `"kind of"` → X is a kind of Y → Y --|> X
- `"may be"` → X may be Y1 or Y2 → Y --|> Y1, Y --|> Y2

**Limitation (Arora):** "Rarely triggered in industrial NL requirements; authors must consciously write these patterns."

**Pipeline:** Rule-based → split the object clause by "or" → each alternative is a subclass.

### D3 — Adjectival Generalization (repurposed)
- Originally: `amod(NP, ADJ)` → attribute extraction (from Yue et al.)
- Arora repurposed: Remove leading adjective → remaining noun = parent; full NP = subclass
- Example: `amod(Device, Linked)` → Parent: Device, Subclass: Linked Device
- Caveat: Attribute vs. generalization is a user choice (by design in Arora's tool)

### Dependency Patterns for Generalization
For copula "X is a Y":
```
nsubj(Y, X)         # X is the dependent (subclass), Y is the governor (parent)
cop(Y, is)          # copula link
det(Y, a/an)        # determiner — signals "X is a [class]"
```

For "X is a kind of Y":
```
nsubj(Y, X)         # X is a type of Y
cop(Y, is)
nmod:of(kind, Y)    # "of" phrase points to parent
det(kind, a)
```

For "X may be A or B":
```
nsubj(Y, X)         # outer clause
cop(Y, may)
conj:or(Y, A)       # first alternative
conj:or(Y, B)       # second alternative
```

### Literature Citations (from Arora Table 1 and References)
- **Abbott (1983)** [1]: "is a" → class hierarchy
- **Chen (1983)** [8]: "is a" → ER generalization
- **Coad & Yourdon (1991)** [19]: "is a" → inheritance
- **Yue et al. (2011)** [31]: survey of 20+ approaches; aggregation & generalization rules across 8+ papers

### References
Arora, C., Sabetzadeh, M., Briand, L., & Zimmer, F. (2016). "Extracting domain models from natural-language requirements: approach and industrial evaluation." In *MODELS'16: ACM/IEEE 19th International Conference on Model Driven Engineering Languages and Systems* (pp. 250–260). http://dx.doi.org/10.1145/2976767.2976769


![Preview](./docs/BigPicture.png)

Domox allows the user to:
* Import documents
* Analyze requirements therein
* Extract relations
* Generate a Domain Model (UML)
* Flag relations as irrelevant (i.e. exclude them from the Domain Model)
* View the (plant)UML model and
* Export it (for subsequent processing by other tools, e.g. Apache Causeway)
* Allow to trace back from the Domain Model elements to requirements.

Design follows the outline in [1]:

![Preview](./docs/DomoxClassDiagram.png)

NLP Processing usually is as follows:

![Preview](./docs/Pipeline.png)


### Glossary
* Corpus - A list of documents
* NER - Named Entity Recognition
* POS - Part of Speech (Taggigng)
* Sofa - Subject of Analysis
* Boilerplate
* Tokenization
* Sentence Splitting
* Parsing
* Co-reference Resolution (synonyms)
* Lemmatization
* Stemming
* Annotation (refrence to text, begin/end)
* Type System - output
* AE - Analysis Engine


### References
[1] C. Arora, M. Sabetzadeh, L. Briand, and F. Zimmer (2016).
Extracting Domain Models from Natural-Language Requirements:
Approach and Industrial Evaluation.
https://people.svv.lu/sabetzadeh/pub/MODELS16.pdf

[2] M. Javed, and Y. Lin (2020). iMER: Iterative Process of Entity Relationship and Business Process Models Extraction from the Requirements.

[3] P. Coad, E. Lefebvre, and J. De Luca (1999).
Java Modeling in Color with UML.
Enterprise Components and Processes.
ISBN 0-13-011510-X

[4] Generating code via XTEXT
https://github.com/echebbi/standalone-xtext-example
https://blogs.itemis.com/en/building-domain-specific-languages-with-xtext-and-xtend

[5] R. J. Abbott (1983).
"Program design by informal English descriptions,"
Commun. ACM, vol. 26, no. 11, pp. 882–894, doi: 10.1145/182.358441.

[6] P. Coad and E. Yourdon (1991).
Object-Oriented Analysis, 2nd ed.
Yourdon Press, ISBN 0-13-629981-4.

[7] N. Kiyavitskaya, A. Krausova, and M. Avazyan (2019).
"Rule-based approach for automatic extraction of domain terms from natural language requirements,"
in Proc. CEUR Workshop, vol. 2476, pp. 55–66.

[8] L. Mich (2001).
"A use case driven requirements engineering method,"
PhD Thesis, University of Trento.

[9] H. Höhn (2003).
Natural Language to Object-Oriented Analysis: A Grammar-Based Approach.
PhD Thesis, University of Koblenz-Landau.


#### Excerpt from [1]:
Syntactic parsing (SP) is the key enabling NLP technology.
It consists of:
* Phrase structure parsing
    * noun phrases (NP)
    * verb phrases (VP) / verb (VB)
* dependency parsing (functional constituents, parse tree)
    * subject
    * object

There are four kinds of relations:
* (regular) Association
* Aggregation
* Generalization
* Attribute

A Domain Model has:
* 1..n Concepts
* 1..n Attributes
* 1..n Associations

### Various
#### Tutorials
* Tutorialspoint: https://www.tutorialspoint.
* Lucene NLP: https://fabian-kostadinov.github.io/2018/09/08/introduction-to-lucene-opennlp-part1/
* Baeldung: https://www.baeldung.com/apache-open-nlp
* https://pub.towardsai.net/natural-language-processing-nlp-with-python-tutorial-for-beginners-1f54e610a1a0

#### Ideas
* Colorize ClassCandidates by means of their properties/actions [3]
* temporal relations -> activity diagrams (HeidelTime?)
* use MaryTTS (http://mary.dfki.de:59125/) to create audio of sentences, store them as Blob and have it play (cf. https://www.w3schools.com/html/html5_audio.asp)

* Have StanfordNLP run in a docker image, using GPU:
    * https://stackoverflow.com/questions/60718574/containerization-of-a-python-code-with-stanfordnlp-that-uses-gpu
    * https://github.com/NLPbox/stanford-corenlp-docker

---
## Implementation Status (Sep 17, 2026)

### TDR38-TDR40: Generalization Rules — IMPLEMENTED ✅

Implemented three new rules for detecting generalization (is-a) relationships:

1. **TDR38** (copula-based): `nsubj(Parent, Child)` + `cop(Parent, be)` + `det(Parent, a/an)`
   - Example: "A dog is an animal" → Dog --|> Animal
   - Files: `TDR38.java` (new)

2. **TDR39** (kind-of/type-of): `nmod:of(TypeKindNoun, Parent)` where TypeKindNoun ~ "kind"/"type"/"sort"
   - Example: "SUV is a kind of car" → Suv --|> Car
   - Scans sentence for `nsubj(TypeKindNoun, ChildNoun)` to find subclass
   - Files: `TDR39.java` (new)

3. **TDR40** (adjectival classifier): `amod(Noun, Adjective)` where adjective is a classifier
   - Example: "linked device" → LinkedDevice --|> Device
   - Excludes evaluative/generic adjectives (via stop list)
   - Files: `TDR40.java` (new)

### Supporting Changes
- `TypedDependencyPredicates`: Added `cop()`, `isBeVerbDependent()`, `isIndefiniteArticleDependent()`, `isKindTypeOrSortDependent()`, `isKindTypeOrSortGovernor()` predicates
- `AssociationCandidates.create()` and `findOrCreate()` now accept optional `AssociationType` parameter
- `RuleMatches.createCandidateFromMatch()` handles `"GeneralizationCdd"` candidateType → creates `AssociationCdd` with `AssociationType.GENERALIZATION`
- 7 new tests added to `TypedDependencyRulesTest.java` (all pass)
- Documentation updated: `RULES_REGISTRY.md`, `RULES_EXAMPLES.md`, `scratch_pad.md`

---
#### Tools
* Apache OpenNLP https://opennlp.apache.org/com/opennlp/opennlp_command_line_interface.htm
* Apache UIMA
* Apache cTAKES
* BRAT https://brat.nlplab.org/index.html
* Heideltime https://github.com/HeidelTime/heideltime
* FreeLing http://nlp.lsi.upc.edu/freeling/node/1

#### Search Terms
* Onlotogy learning/extraction https://en.wikipedia.org/wiki/Ontology_learning

### Running with Docker
#### Kroki/PlantUML
Kroki is used for generating diagrams (UML, TypedDependency) - this can either be the instance the kroki team provides or one you host yourself as Docker image.

#### StanfordCoreNLP
StanfordCoreNLP can be used either from a jar library or via a service hosted by a Docker image.
https://hub.docker.com/r/alborzi/stanford-corenlp

docker pull alborzi/stanford-corenlp