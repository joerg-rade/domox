package domox.dom.rules;

import domox.dom.nlp.TypedDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TDR2Test {

    @Mock
    private TypedDependency currentTd;

    @InjectMocks
    private TDR2 tdr2;

    @BeforeEach
    void setUp() {
        tdr2.currentTd = currentTd;
    }

    @Test
    void determineClassName_capitalizesTheGovernorLemma() {
        when(currentTd.getA()).thenReturn("customer");
        assertThat(tdr2.determineClassName()).isEqualTo("Customer");
    }

    @Test
    void determineType_mapsKnownAttributeNamesToJavaTypes() {
        when(currentTd.getB()).thenReturn("name");
        assertThat(tdr2.determineType()).isEqualTo("String");

        when(currentTd.getB()).thenReturn("age");
        assertThat(tdr2.determineType()).isEqualTo("int");
    }
}