package domox.dom.rqm;

import org.apache.isis.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class Author_Test {

    @Mock
    RepositoryService mockRepositoryService;

    Author object;

    @BeforeEach
    public void setUp() throws Exception {
        object = Author.withLastName("Foo");
    }

    @Nested
    public class updateName {

        @Test
        void happy_case() {
            // given
            assertThat(object.getLastName()).isEqualTo("Foo");

            // when
            //         object.updateName("Bar");

            // then
            assertThat(object.getLastName()).isEqualTo("Bar");
        }

    }

    @Nested
    class delete {

        @Test
        void happy_case() {

            // given
            assertThat(object).isNotNull();

            // expecting

            // when
            //          object.delete();

            // then
            verify(mockRepositoryService).removeAndFlush(object);
        }
    }
}
