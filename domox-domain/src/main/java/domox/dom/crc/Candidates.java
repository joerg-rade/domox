package domox.dom.crc;

import domox.DomainModule;
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
    private final ActionCandidates actionCandidates;
    private final PropertyCandidates propertyCandidates;
    private final AssociationCandidates associationCandidates;

    @Inject
    public Candidates(
            ClassCandidates classCandidates,
            ActionCandidates actionCandidates,
            PropertyCandidates propertyCandidates,
            AssociationCandidates associationCandidates) {
        this.classCandidates = classCandidates;
        this.actionCandidates = actionCandidates;
        this.propertyCandidates = propertyCandidates;
        this.associationCandidates = associationCandidates;
    }

    @ActionLayout(sequence = "1", named = "List All Class Candidates")
    public List<ClassCdd> listAllClasses() {
        return classCandidates.listAll();
    }

    @ActionLayout(sequence = "2", named = "List All Action Candidates")
    public List<ActionCdd> listAllActions() {
        return actionCandidates.listAll();
    }

    @ActionLayout(sequence = "3", named = "List All Property Candidates")
    public List<PropertyCdd> listAllProperties() {
        return propertyCandidates.listAll();
    }

    @ActionLayout(sequence = "4", named = "List All Association Candidates")
    public List<AssociationCdd> listAllAssociations() {
        return associationCandidates.listAll();
    }

}