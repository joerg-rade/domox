package domox.dom.nlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TdTypeTest {

    @Test
    void everyConstantRoundTripsThroughFromCode() {
        for (final TdType type : TdType.values()) {
            assertEquals(type, TdType.fromCode(type.getCode()), "failed for code '" + type.getCode() + "'");
        }
    }

    @Test
    void fromCode_resolvesRecentlyAddedUniversalRelations() {
        assertEquals(TdType.CLF, TdType.fromCode("clf"));
        assertEquals(TdType.DISCOURSE, TdType.fromCode("discourse"));
        assertEquals(TdType.DISLOCATED, TdType.fromCode("dislocated"));
        assertEquals(TdType.EXPL, TdType.fromCode("expl"));
        assertEquals(TdType.FLAT, TdType.fromCode("flat"));
        assertEquals(TdType.FLAT_NAME, TdType.fromCode("flat:name"));
        assertEquals(TdType.GOESWITH, TdType.fromCode("goeswith"));
        assertEquals(TdType.LIST, TdType.fromCode("list"));
        assertEquals(TdType.ORPHAN, TdType.fromCode("orphan"));
        assertEquals(TdType.REPARANDUM, TdType.fromCode("reparandum"));
        assertEquals(TdType.VOCATIVE, TdType.fromCode("vocative"));
    }
}