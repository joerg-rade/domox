# domox
Domain Model Extractor: automatically extract a (first iteration) domain model from requirements.
By means of Natural Language Processing (NLP), it will try to identify candidates for:

* Classes
* Properties
* Actions and
* Associations

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

