package domox.webapp.integtests.smoke;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@ContextConfiguration(initializers = Smoke_IntegTest.Initializer.class)
@Transactional
class Smoke_IntegTest extends ApplicationIntegTestAbstract {

    /*@Autowired
    Authors authors;
    @Inject
    TransactionService transactionService;

    @Test
    void happy_case() {

        // when
        List<Author> all = wrap(authors).listAll();

        // then
        assertThat(all).isEmpty();


        // when
        final Author fred = wrap(authors).create("Fred");
        transactionService.flushTransaction();

        // then
        all = wrap(authors).listAll();
        assertThat(all).hasSize(1);
        assertThat(all).contains(fred);


        // when
        final Author bill = wrap(authors).create("Bill");
        transactionService.flushTransaction();

        // then
        all = wrap(authors).listAll();
        assertThat(all).hasSize(2);
        assertThat(all).contains(fred, bill);


        // when
//        wrap(fred).updateName("Freddy");
        transactionService.flushTransaction();

        // then
        assertThat(wrap(fred).getLastName()).isEqualTo("Freddy");


        // when
//       wrap(fred).setNotes("These are some notes");
        transactionService.flushTransaction();

        // then
//        assertThat(wrap(fred).getNotes()).isEqualTo("These are some notes");


        // when
        Assertions.assertThrows(InvalidException.class, () -> {
  //          wrap(fred).updateName("New name !!!");
            transactionService.flushTransaction();
        }, "Exclamation mark is not allowed");

        // then
     //   assertThat(wrap(fred).getNotes()).isEqualTo("These are some notes");


        // when
//        wrap(fred).delete();
        transactionService.flushTransaction();

        // then
        all = wrap(authors).listAll();
        assertThat(all).hasSize(1);
    }
*/

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
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
