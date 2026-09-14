package domox.dom;

import domox.DomainModule;
import domox.FileUtil;
import domox.dom.crc.Candidate;
import domox.dom.crc.DomainModel;
import domox.dom.crc.DomainModels;
import domox.dom.nlp.Sentence;
import domox.dom.rqm.Author;
import domox.dom.rqm.Document;
import domox.dom.rqm.Documents;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.TypedDependencyRule;
import domox.nlp.DocumentTO;
import domox.svc.DocumentAdapter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
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

    private final RepositoryService repositoryService;
    private final Documents documents;
    private final RuleMatches ruleMatches;
    private final List<TypedDependencyRule> rules;
    private final DomainModels domainModels;

    @Inject
    public Analysis(RepositoryService repositoryService,
                    Documents documents,
                    RuleMatches ruleMatches,
                    List<TypedDependencyRule> rules,
                    DomainModels domainModels) {
        this.repositoryService = repositoryService;
        this.documents = documents;
        this.ruleMatches = ruleMatches;
        this.rules = rules;
        this.domainModels = domainModels;
    }

    @Action()
    @ActionLayout(sequence = "6", cssClassFa = "rupee")
    public void analyzeDocument(
            @ParameterLayout(named = "Document") final Document document) {
        log.info("Starting analysis phase for document: {}", document.getTitle());

        // Create a DomainModel to own all candidates created in this analysis
        final DomainModel domainModel = domainModels.create();
        document.setDomainModel(domainModel);

        // Apply each TypedDependencyRule to each sentence
        for (Sentence sentence : document.getSentences()) {
            for (TypedDependencyRule rule : rules) {   // inject all TDR beans
                rule.analyzeAndMatch(sentence);
            }
        }

        // Phase 2: Create Candidate objects from all RuleMatches
        final List<Candidate> candidates = ruleMatches.createCandidatesFrom(ruleMatches.listAll(), domainModel);
        log.info("Created {} candidates from rule matches", candidates.size());
    }

    @Action()
    @ActionLayout(sequence = "5", cssClassFa = "play")
    public List<RuleMatch> loadFileSample() {
        final String title = "Pet Shop Use Cases";
        final String filename = "PetShop_UseCases.txt";
        final String txtContent = new FileUtil().readFileFromResources(filename);
        final Clob content = new Clob("", "text/xml", txtContent);
        final Author author = new Author();
        final List<Author> authors = new ArrayList<>();
        authors.add(author);
        final Document document = build(title, filename, content, authors);
        analyzeDocument(document);
        return ruleMatches.listAll();
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
