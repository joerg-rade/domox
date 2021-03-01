package domox.integtests.tests;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.services.wrapper.DisabledException;
import org.apache.isis.applib.services.wrapper.InvalidException;
import org.apache.isis.persistence.jdo.datanucleus5.jdosupport.mixins.Persistable_datanucleusIdLong;

import lombok.Getter;

import domox.dom.rqm.Author;
import domox.fixture.SimpleObject_persona;
import domox.integtests.SimpleModuleIntegTestAbstract;

@Transactional
public class Author_IntegTest extends SimpleModuleIntegTestAbstract {

    Author author;

    @BeforeEach
    public void setUp() {
        // given
        author = fixtureScripts.runPersona(SimpleObject_persona.FOO);
    }

    public static class name extends Author_IntegTest {

        @Test
        public void accessible() {
            // when
            final String name = wrap(author).getLastName();

            // then
            assertThat(name).isEqualTo(author.getLastName());
        }

        @Test
        public void not_editable() {

            // expect
            assertThrows(DisabledException.class, ()->{

                // when
                wrap(author).setLastName("new name");
            });
        }

    }

    public static class updateName extends Author_IntegTest {

        @DomainService
        public static class UpdateNameListener {

     //       @Getter
   //         List<Author.UpdateNameActionDomainEvent> events = new ArrayList<>();

       //     @EventListener(Author.UpdateNameActionDomainEvent.class)
         //   public void on(Author.UpdateNameActionDomainEvent ev) {
           //     events.add(ev);
            }
        }

  //      @Inject
    //    UpdateNameListener updateNameListener;


        @Test
        public void can_be_updated_directly() {

            // given
 //           updateNameListener.getEvents().clear();

            // when
 //           wrap(author).updateName("new name");
            transactionService.flushTransaction();

            // then
            assertThat(wrap(author).getLastName()).isEqualTo("new name");
 //           assertThat(updateNameListener.getEvents()).hasSize(5);
        }

        @Test
        public void fails_validation() {

            // expect
            InvalidException cause = assertThrows(InvalidException.class, ()->{

                // when
//                wrap(author).updateName("new name!");
            });

            // then
            assertThat(cause.getMessage()).contains("Character '!' is not allowed");
        }
    }

 /*   public static class dataNucleusId extends Author_IntegTest {

        @Test
        public void should_be_populated() {
            // when
            final Long id = mixin(Persistable_datanucleusIdLong.class, author).prop();

            // then
            assertThat(id).isGreaterThanOrEqualTo(0);
        }
    }

}*/
