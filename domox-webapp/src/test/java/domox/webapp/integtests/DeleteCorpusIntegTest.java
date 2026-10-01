package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.xactn.TransactionService;

import domox.dom.rqm.Corpora;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.ActionCddRepository;
import domox.dom.crc.AssociationCdd;
import domox.dom.crc.AssociationCddRepository;
import domox.dom.crc.AssociationType;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.ClassCddRepository;
import domox.dom.crc.DomainModel;
import domox.dom.crc.DomainModels;
import domox.dom.crc.PropertyCdd;
import domox.dom.crc.PropertyCddRepository;
import domox.dom.rqm.Corpus;

/**
 * Regression test for {@code Corpora.deleteCorpus(Corpus)}.
 * <p>
 * A {@code Corpus} owns one shared {@link DomainModel}.  When the corpus is
 * deleted the shared model must be destroyed together with every candidate it
 * produced — class-owned and orphan (e.g. {@link ActionCdd} with a
 * {@code null} owning class) alike — without tripping the DB foreign-key
 * constraints that point back at the {@code DomainModel} (such as
 * {@code FK_ActionCdd_DOMAINMODEL_ID}) at flush time.
 */
@ContextConfiguration(initializers = DeleteCorpusIntegTest.Initializer.class)
@Transactional
class DeleteCorpusIntegTest extends ApplicationIntegTestAbstract {

    @Inject private Corpora corpora;
    @Inject private DomainModels domainModels;
    @Inject private RepositoryService repositoryService;
    @Inject private TransactionService transactionService;
    @Inject private ClassCddRepository classCddRepository;
    @Inject private ActionCddRepository actionCddRepository;
    @Inject private PropertyCddRepository propertyCddRepository;
    @Inject private AssociationCddRepository associationCddRepository;

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

        final PropertyCdd age = new PropertyCdd("age", "int");
        age.setCandidateType("PropertyCdd");
        age.domainModel = dm;
        age.classCdd = customer;
        customer.propertyList.add(age);
        dm.propertyList.add(age);

        final ActionCdd purchase = actionCdd("purchase", dm);
        purchase.classCdd = customer;
        customer.actionList.add(purchase);
        dm.actionList.add(purchase);

        // orphan action — no owning class, reachable only through the model
        final ActionCdd orphan = actionCdd("orphanAction", dm);
        dm.actionList.add(orphan);

        final AssociationCdd customerStore = new AssociationCdd("Customer_Store", customer, store);
        customerStore.setCandidateType("AssociationCdd");
        customerStore.setDomainModel(dm);
        customerStore.setType(AssociationType.ASSOCIATION);
        customer.addAssociation(customerStore);
        dm.associationList.add(customerStore);

        corpus.setDomainModel(dm);
        repositoryService.persistAndFlush(corpus);
        transactionService.flushTransaction();

        // sanity: everything is present before deletion
        assertThat(classCddRepository.findAll()).hasSize(2);
        assertThat(actionCddRepository.findAll()).hasSize(2);
        assertThat(propertyCddRepository.findAll()).hasSize(1);
        assertThat(associationCddRepository.findAll()).hasSize(1);

        // when: the corpus is deleted
        wrap(corpora).deleteCorpus(corpus);
        transactionService.flushTransaction();

        // then: every candidate of every type is gone (flush did not trip an FK)
        assertThat(classCddRepository.findAll()).isEmpty();
        assertThat(actionCddRepository.findAll()).isEmpty();
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

    private ActionCdd actionCdd(final String name, final DomainModel dm) {
        final ActionCdd a = new ActionCdd();
        a.setCandidateName(name);
        a.setCandidateType("ActionCdd");
        a.setOutputType("void");
        a.domainModel = dm;
        return a;
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            if (ApplicationIntegTestAbstract.postgres != null && ApplicationIntegTestAbstract.postgres.isRunning()) {
                TestPropertyValues.of(
                        "spring.datasource.url=" + ApplicationIntegTestAbstract.postgres.getJdbcUrl(),
                        "spring.datasource.username=" + ApplicationIntegTestAbstract.postgres.getUsername(),
                        "spring.datasource.password=" + ApplicationIntegTestAbstract.postgres.getPassword()
                ).applyTo(context.getEnvironment());
            }
        }
    }
}