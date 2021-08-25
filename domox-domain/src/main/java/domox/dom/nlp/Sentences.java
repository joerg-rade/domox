package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Sentences")
@RequiredArgsConstructor
public class Sentences {
    private final RepositoryService repositoryService;
    private final SentenceRepository repository;

    @PropertyLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
    public List<Sentence> listAll() {
        return repositoryService.allInstances(Sentence.class);
    }

    @Programmatic
    public Sentence create() {
        final Sentence obj = repositoryService.detachedEntity(Sentence.class);
        repositoryService.persist(obj);
        return obj;
    }

}