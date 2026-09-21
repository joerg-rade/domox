package domox.dom.crc;

import domox.DomainModule;
import domox.dom.rules.NlpProperties;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".ClassCandidates")
@Priority(PriorityPrecedence.EARLY)
public class ClassCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final ClassCddRepository classCddRepository;
    private final NlpProperties nlpProperties;

    @Inject
    public ClassCandidates(RepositoryService repositoryService, FactoryService factoryService, ClassCddRepository classCddRepository, NlpProperties nlpProperties) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.classCddRepository = classCddRepository;
        this.nlpProperties = nlpProperties;
    }

    @ActionLayout(sequence = "1")
    public List<ClassCdd> listAll() {
        return repositoryService.allInstances(ClassCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    @ActionLayout(sequence = "2")
    public ClassCdd findByCandidateName(String candidateName) {
        return classCddRepository.findByCandidateName(candidateName);
    }

    @ActionLayout(sequence = "3")
    public ClassCdd create(String candidateName) {
        // Auto-create a DomainModel for UI convenience
        final DomainModel domainModel = new DomainModel();
        repositoryService.persist(domainModel);
        return create(candidateName, domainModel);
    }

    @Programmatic
    public ClassCdd create(String candidateName, DomainModel domainModel) {
        final ClassCdd obj = factoryService.detachedEntity(ClassCdd.class);
        obj.setCandidateName(candidateName);
        obj.setCandidateType("ClassCdd");
        obj.domainModel = domainModel;
        if (domainModel != null) {
            domainModel.classList.add(obj);
        }
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public ClassCdd findOrCreate(final String candidateName, final DomainModel domainModel) {
        if (isBlockedUseCaseNoun(candidateName)) {
            return null;
        }
        ClassCdd candidate = findByCandidateName(candidateName);
        if (candidate == null) {
            if (domainModel == null) {
                candidate = create(candidateName);
            } else {
                candidate = create(candidateName, domainModel);
            }
        }
        return candidate;
    }

    /**
     * Returns {@code true} when {@code candidateName} is a use-case document
     * meta-noun (template header, narrative scaffolding, or generic result term)
     * that must never become a {@link ClassCdd} entity.  This is the single
     * choke point for {@code ClassCdd} creation, so blocked nouns cannot leak
     * in through {@link PropertyCandidates} owner resolution either.
     * <p>
     * The blocked nouns are configured as singular lemmas under
     * {@code domox.nlp.use-case-blocked-nouns}.  Candidate names are compared
     * case-insensitively; a simple plural fallback is applied for nouns that
     * reach Phase 2 without lemmatization.
     */
    @Programmatic
    boolean isBlockedUseCaseNoun(String candidateName) {
        if (candidateName == null) {
            return false;
        }
        final List<String> blocked = nlpProperties.getUseCaseBlockedNouns();
        if (blocked == null) {
            return false;
        }
        String lower = candidateName.toLowerCase().trim();
        if (blocked.contains(lower)) {
            return true;
        }
        // Defensive fallback for un-lemmatized plurals ("Conditions" → "condition")
        if (lower.endsWith("s") && !lower.endsWith("ss")) {
            return blocked.contains(lower.substring(0, lower.length() - 1));
        }
        return false;
    }

    @Action()
    @ActionLayout(sequence = "4", cssClassFa = "trash")
    public void deleteAll() {
        var all = listAll();
        for (ClassCdd cc : all) {
            repositoryService.remove(cc);
        }
    }
}
