package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;

import java.util.List;

/**
 * Single top-level menu that groups the "List All … Candidates" actions that
 * previously lived on each of the {@code *Candidates} services.  Each action
 * delegates to the corresponding service's (internal) {@code listAll()}.
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".Candidates")
@Priority(PriorityPrecedence.EARLY)
public class Candidates {

    private final ClassCandidates classCandidates;
    private final ResponsibilityCandidates responsibilityCandidates;
    private final AttributeCandidates attributeCandidates;
    private final CollaboratorCandidates collaboratorCandidates;
    private final RuleMatches ruleMatches;

    @Inject
    public Candidates(
            ClassCandidates classCandidates,
            ResponsibilityCandidates responsibilityCandidates,
            AttributeCandidates attributeCandidates,
            CollaboratorCandidates collaboratorCandidates,
            RuleMatches ruleMatches) {
        this.classCandidates = classCandidates;
        this.responsibilityCandidates = responsibilityCandidates;
        this.attributeCandidates = attributeCandidates;
        this.collaboratorCandidates = collaboratorCandidates;
        this.ruleMatches = ruleMatches;
    }

    @ActionLayout(sequence = "1", named = "List All Class Candidates")
    public List<ClassCdd> listAllClasses() {
        return classCandidates.listAll();
    }

    @ActionLayout(sequence = "2", named = "List All Responsibility Candidates")
    public List<ResponsibilityCdd> listAllResponsibilities() {
        return responsibilityCandidates.listAll();
    }

    @ActionLayout(sequence = "3", named = "List All Attribute Candidates")
    public List<AttributeCdd> listAllAttributes() {
        return attributeCandidates.listAll();
    }

    @ActionLayout(sequence = "4", named = "List All Collaborator Candidates")
    public List<CollaboratorCdd> listAllCollaborators() {
        return collaboratorCandidates.listAll();
    }

    @ActionLayout(sequence = "4", named = "List All Synonyms")
    public List<RuleMatch> listAllSynonyms() {
        return ruleMatches.findByRuleClassName("TDR41");
    }

}