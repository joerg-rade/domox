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

    private final List<TypedDependencyRule> rules;

    @Inject
    public Analysis(List<TypedDependencyRule> rules) {
        this.rules = rules;
    }

    @Action
    public List<Candidate> analyzeDocument(
            @ParameterLayout(named = "Document") final Document document) {
        log.info("Starting analysis phase for document: {}", document.getTitle());
        List<Candidate> candidates = new ArrayList<>();

        // Apply each TypedDependencyRule to each sentence
        for (Sentence sentence : document.getSentences()) {
            for (TypedDependencyRule rule : rules) {   // inject all TDR beans
                List<Candidate> found = rule.analyze(sentence);
                log.info("Rule {} on sentence {} over {} deps -> {} candidates",
                        rule.getRuleName(), sentence.getId(),
                        sentence.getTypedDependencies().size(), found.size());
                candidates.addAll(found);
            }
        }

        // Persist all candidates in a single operation
        if (!candidates.isEmpty()) {
            repositoryService.persistAndFlush(candidates);
        }

        log.info("Analysis complete. Created {} candidates", candidates.size());
        return candidates;
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
