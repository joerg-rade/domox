package domox.dom;

import domox.DomainModule;
import domox.FileUtil;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.dom.nlp.TypedDependencies;
import domox.dom.nlp.TypedDependency;
import domox.dom.rqm.Author;
import domox.dom.rqm.Documents;
import domox.dom.rules.TypedDependencyRule;
import domox.dom.rqm.Document;
import domox.dom.uml.Candidate;
import domox.nlp.DocumentTO;
import domox.svc.DocumentAdapter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".Analysis")
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@NoArgsConstructor
public class Analysis {
    private static final Logger log = LoggerFactory.getLogger(Analysis.class);

    @Inject
    private RepositoryService repositoryService;

    @Inject
    private Sentences sentences;

    @Inject
    private TypedDependencies typedDependencies;

    @Inject
    private Documents documents;

    @Action
    public List<Candidate> analyzeDocument(
            @ParameterLayout(named = "Document") final Document document) {
        log.info("Starting analysis phase for document: {}", document.getTitle());
        List<Candidate> candidates = new ArrayList<>();
        final List<Sentence> docSentences = sentences.findByDocument(document);

        // Apply each TypedDependencyRule to each sentence
        for (Sentence sentence : docSentences) {
            List<TypedDependency> typedDependenciesList = typedDependencies.listBySentence(sentence);

            // Apply all rules
            for (TypedDependency dependency : typedDependenciesList) {
                for (TypedDependencyRule rule : getAllRules()) {
                    if (rule.appliesTo(dependency)) {
                        // Create or update candidate(s) from this rule match
                        Candidate candidate = rule.createCandidate(dependency, sentence, candidates);

                        if (candidate != null && !candidates.contains(candidate)) {
                            candidates.add(candidate);
                            log.debug("Created candidate: {}", candidate);
                        }
                    }
                }
            }
        }

        // Persist all candidates in a single operation
        if (!candidates.isEmpty()) {
            repositoryService.persistAndFlush(candidates);
        }

        log.info("Analysis complete. Created {} candidates", candidates.size());
        return candidates;
    }

    private List<TypedDependencyRule> getAllRules() {
        return repositoryService.allInstances(TypedDependencyRule.class);
    }

    @Action()
    @ActionLayout(sequence = "5", cssClassFa = "play")
    public Document loadFileSample() {
        final String title = "Pet Shop Use Cases";
        final String filename = "PetShop_UseCases.txt";
        final String txtContent = new FileUtil().readFileFromResources(filename);
        final Clob content = new Clob("", "text/xml", txtContent);
        final Author author = new Author();
        final List<Author> authors = new ArrayList<>();
        authors.add(author);
        return build(title, filename, content, authors);
    }

    private Document build(String title, String url, Clob content, List<Author> authors) {
        final Document document = documents.create(title, url, content, authors);
        final String rawText = document.getContent();
        final DocumentTO documentTO = new DocumentAdapter().parseTextAndAmend(rawText);
        repositoryService.persistAndFlush(document);
        List<Sentence> sentences = documents.createSentences(document, documentTO);
        document.setSentences(sentences);
        return document;
    }

}
