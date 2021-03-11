package domox.dom.rqm;

import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jpa.applib.services.JpaSupportService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Authors_Test {

    @Mock
    RepositoryService mockRepositoryService;
    @Mock
    JpaSupportService mockJpaSupportService;
    @Mock
    AuthorRepository mockAuthorRepository;

    Authors authors;

    @BeforeEach
    public void setUp() {
        authors = new Authors(mockRepositoryService, mockJpaSupportService, mockAuthorRepository);
    }

    @Nested
    class create {

        @Test
        void happyCase() {

            // given
            final String someName = "Foobar";

            // expect
            when(mockRepositoryService.persist(
                    argThat((ArgumentMatcher<Author>) author -> Objects.equals(author.getLastName(), someName)))
            ).then((Answer<Author>) invocation -> invocation.getArgument(0));

            // when
            final Author obj = authors.create(someName);

            // then
            assertThat(obj).isNotNull();
            assertThat(obj.getLastName()).isEqualTo(someName);
        }
    }

    @Nested
    class ListAll {

        @Test
        void happyCase() {

            // given
            final List<Author> all = new ArrayList<>();

            // expecting
            when(mockRepositoryService.allInstances(Author.class))
                    .thenReturn(all);

            // when
            final List<Author> list = authors.listAll();

            // then
            Assertions.assertThat(list).isEqualTo(all);
        }
    }
}
