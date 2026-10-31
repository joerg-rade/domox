package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.xactn.TransactionService;

import domox.dom.requirements.Corpora;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.repository.ResponsibilityCddRepository;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.repository.CollaboratorCddRepository;
import domox.dom.crc.model.AssociationType;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.repository.ClassCddRepository;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.DomainModels;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.repository.AttributeCddRepository;
import domox.dom.requirements.Corpus;

/**
 * Regression test for {@code Corpora.deleteCorpus(Corpus)}.
 * <p>
 * A {@code Corpus} owns one shared {@link DomainModel}.  When the corpus is
 * deleted the shared model must be destroyed together with every candidate it
 * produced — class-owned and orphan (e.g. {@link ResponsibilityCdd} with a
 * {@code null} owning class) alike — without tripping the DB foreign-key
 * constraints that point back at the {@code DomainModel} (such as
 * {@code FK_ResponsibilityCdd_DOMAINMODEL_ID}) at flush time.
 */
@Transactional
class DeleteCorpusIntegTest extends ApplicationIntegTestAbstract {

    @Inject private Corpora corpora;
    @Inject private DomainModels domainModels;
    @Inject private RepositoryService repositoryService;
    @Inject private TransactionService transactionService;
    @Inject private ClassCddRepository classCddRepository;
    @Inject private ResponsibilityCddRepository responsibilityCddRepository;
    @Inject private AttributeCddRepository propertyCddRepository;
    @Inject private CollaboratorCddRepository associationCddRepository;

    @Test
    void deleteCorpus_removesSharedModel_TogetherWithOrphanAndClassOwnedCandidates() {
        // given: a corpus owning a shared model with a class, an orphan action,
        //        a class-owned action/property, and an association
        final Corpus corpus = new Corpus();
        corpus.setTitle("Delete Repro");
        corpus.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));

        final DomainModel dm = domainModels.create();

        final ClassCdd customer = classCdd("Customer", dm);
        final ClassCdd store = classCdd("Store", dm);

        final AttributeCdd age = new AttributeCdd("age", "int");
        age.setCandidateType("PropertyCdd");
        age.domainModel = dm;
        age.classCdd = customer;
        customer.attributeList.add(age);
        dm.attributeList.add(age);

        final ResponsibilityCdd purchase = responsibilityCdd("purchase", dm);
        purchase.classCdd = customer;
        customer.responsibilityList.add(purchase);
        dm.responsibilityList.add(purchase);

        // orphan action — no owning class, reachable only through the model
        final ResponsibilityCdd orphan = responsibilityCdd("orphanAction", dm);
        dm.responsibilityList.add(orphan);

        final CollaboratorCdd customerStore = new CollaboratorCdd("Customer_Store", customer, store);
        customerStore.setCandidateType("AssociationCdd");
        customerStore.setDomainModel(dm);
        customerStore.setType(AssociationType.ASSOCIATION);
        customer.addCollaborator(customerStore);
        dm.collaboratorList.add(customerStore);

        corpus.setDomainModel(dm);
        repositoryService.persistAndFlush(corpus);
        transactionService.flushTransaction();

        // sanity: everything is present before deletion
        assertThat(classCddRepository.findAll()).hasSize(2);
        assertThat(responsibilityCddRepository.findAll()).hasSize(2);
        assertThat(propertyCddRepository.findAll()).hasSize(1);
        assertThat(associationCddRepository.findAll()).hasSize(1);

        // when: the corpus is deleted
        wrap(corpora).deleteCorpus(corpus);
        transactionService.flushTransaction();

        // then: every candidate of every type is gone (flush did not trip an FK)
        assertThat(classCddRepository.findAll()).isEmpty();
        assertThat(responsibilityCddRepository.findAll()).isEmpty();
        assertThat(propertyCddRepository.findAll()).isEmpty();
        assertThat(associationCddRepository.findAll()).isEmpty();
    }

    private ClassCdd classCdd(final String name, final DomainModel dm) {
        final ClassCdd c = new ClassCdd();
        c.setCandidateName(name);
        c.setCandidateType("ClassCdd");
        c.domainModel = dm;
        dm.classList.add(c);
        return c;
    }

    private ResponsibilityCdd responsibilityCdd(final String name, final DomainModel dm) {
        final ResponsibilityCdd a = new ResponsibilityCdd();
        a.setCandidateName(name);
        a.setCandidateType("ActionCdd");
        a.setOutputType("void");
        a.domainModel = dm;
        return a;
    }

    }