package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.PartOfSpeeches")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class PartOfSpeeches {
    private final RepositoryService repositoryService;
    private final PartOfSpeechRepository repository;

    @MemberOrder(sequence = "1")
    public List<PartOfSpeech> listAll() {
        return repositoryService.allInstances(PartOfSpeech.class);
    }

    @MemberOrder(sequence = "2")
    public PartOfSpeech create() {
        final PartOfSpeech obj = repositoryService.detachedEntity(PartOfSpeech.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @MemberOrder(sequence = "3")
    public List<PartOfSpeech> findByType(final PosType type) {
        return repository.findByType(type);
    }

}