package domox.dom.rules;

import domox.dom.nlp.TypedDependency;
import org.apache.causeway.commons.internal.assertions._Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void testDetermineClassName() {
        when(currentTd.getA()).thenReturn("customer");
        _Assert.assertEquals("Customer", tdr2.determineClassName());
    }

    @Test
    void testDetermineType() {
        when(currentTd.getB()).thenReturn("name");
        _Assert.assertEquals("String", tdr2.determineType());

        when(currentTd.getB()).thenReturn("age");
        _Assert.assertEquals("int", tdr2.determineType());
    }
}