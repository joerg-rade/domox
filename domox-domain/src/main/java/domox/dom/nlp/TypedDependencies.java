package domox.dom.nlp;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".TypedDependencies")
@Priority(PriorityPrecedence.EARLY)
public class TypedDependencies {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final TypedDependencyRepository typedDependencyRepository;
    private final SentenceRepository sentenceRepository;

    @Inject
    public TypedDependencies(
            RepositoryService repositoryService,
            FactoryService factoryService,
            TypedDependencyRepository typedDependencyRepository,
            SentenceRepository sentenceRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.typedDependencyRepository = typedDependencyRepository;
        this.sentenceRepository = sentenceRepository;
    }

    @ActionLayout(sequence = "1")
    public List<TypedDependency> listAll() {
        return repositoryService.allInstances(TypedDependency.class);
    }

    @Action
    @ActionLayout(sequence = "2", named = "List By Sentence")
    public List<TypedDependency> listBySentence(
            @ParameterLayout(named = "Sentence")
            final Sentence sentence) {
        return typedDependencyRepository.findBySentence(sentence);
    }

    // Add choices for the 'sentence' parameter
    @MemberSupport
    public List<Sentence> choices0ListBySentence() {
        return sentenceRepository.findAll();
    }

    @ActionLayout(sequence = "3")
    public TypedDependency create() {
        final TypedDependency obj = factoryService.detachedEntity(TypedDependency.class);
        repositoryService.persist(obj);
        return obj;
    }
    
}