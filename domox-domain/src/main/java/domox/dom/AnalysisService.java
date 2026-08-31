package domox.dom;

import domox.dom.nlp.Sentence;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.TypedDependencyRule;
import domox.dom.rqm.Document;
import jakarta.inject.Inject;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    @Inject
    private RepositoryService repositoryService;

    @Inject
    private MessageService messageService;

    public List<RuleMatch> analyzeDocument(Document document) {
        log.info("Starting analysis for: {}", document.getTitle());
        
        List<RuleMatch> ruleMatches = new ArrayList<>();
        int rulesToApply = countAllRules();
        int processedRules = 0;
        
        for (Sentence sentence : document.getSentences()) {
            for (TypedDependencyRule rule : getAllRules()) {
                processedRules++;
                
                // Show progress in UI
                if (processedRules % 10 == 0) {
                    messageService.informUser(
                        String.format("Processing... %d/%d rules applied", 
                                    processedRules, rulesToApply)
                    );
                }
                
                // Apply rule logic here
                ruleMatches = rule.analyzeAndMatch(sentence);

                //TODO iterate over matches and create Candidates
            }
        }
        
        messageService.informUser(
            String.format("Analysis complete! %d rules matched", ruleMatches.size())
        );
        
        return ruleMatches;
    }

    private List<TypedDependencyRule> getAllRules() {
        return repositoryService.allInstances(TypedDependencyRule.class);
    }

    private int countAllRules() {
        return getAllRules().size();
    }
}
