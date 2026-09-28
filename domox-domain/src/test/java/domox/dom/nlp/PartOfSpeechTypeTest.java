package domox.dom.nlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PartOfSpeechTypeTest {

    @Test
    void fromCode_resolvesAllKnownPennTreebankTags() {
        assertEquals(PartOfSpeechType.FW, PartOfSpeechType.fromCode("FW"));
        assertEquals(PartOfSpeechType.LQUOTE, PartOfSpeechType.fromCode("``"));
        assertEquals(PartOfSpeechType.RQUOTE, PartOfSpeechType.fromCode("''"));
        assertEquals(PartOfSpeechType.SYM, PartOfSpeechType.fromCode("SYM"));
        assertEquals(PartOfSpeechType.LRB, PartOfSpeechType.fromCode("-LRB-"));
        assertEquals(PartOfSpeechType.RRB, PartOfSpeechType.fromCode("-RRB-"));
        assertEquals(PartOfSpeechType.RP, PartOfSpeechType.fromCode("RP"));
        assertEquals(PartOfSpeechType.RBS, PartOfSpeechType.fromCode("RBS"));
        assertEquals(PartOfSpeechType.POS, PartOfSpeechType.fromCode("POS"));
    }

    @Test
    void fromCode_trimsWhitespace() {
        assertEquals(PartOfSpeechType.NN, PartOfSpeechType.fromCode("  NN  "));
    }

    @Test
    void fromCode_returnsNullForNullAndBlank() {
        assertNull(PartOfSpeechType.fromCode(null));
        assertNull(PartOfSpeechType.fromCode(""));
        assertNull(PartOfSpeechType.fromCode("  "));
    }

    @Test
    void fromCode_returnsNullForUnknown() {
        assertNull(PartOfSpeechType.fromCode("NOT_A_TAG"));
    }
}