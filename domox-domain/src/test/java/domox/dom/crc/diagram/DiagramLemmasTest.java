package domox.dom.crc.diagram;

import domox.dom.nlp.PartOfSpeechType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the lemma / POS / candidate-type helpers shared by the lexical diagram renderers.
 *
 * <p>A diagram keys its nodes by the lower-cased lemma of a candidate word, so two renderers of the
 * same model must normalise identically — otherwise a link would attach to a node the other
 * renderer does not have.  These helpers used to be private duplicates in each renderer.</p>
 */
class DiagramLemmasTest {

    // ---------------------------------------------------------------- lower

    @Test
    void lower_maps_null_to_the_empty_string() {
        assertThat(DiagramLemmas.lower(null)).isEmpty();
    }

    @Test
    void lower_lowercases_the_lemma() {
        assertThat(DiagramLemmas.lower("Pet")).isEqualTo("pet");
        assertThat(DiagramLemmas.lower("PET")).isEqualTo("pet");
        assertThat(DiagramLemmas.lower("pet")).isEqualTo("pet");
    }

    @Test
    void lower_leaves_already_lower_case_text_alone() {
        assertThat(DiagramLemmas.lower("pet shop")).isEqualTo("pet shop");
    }

    @Test
    void lower_is_locale_independent_for_the_turkish_dotted_capital_i() {
        // Locale.ROOT lower-cases "I" to "i"; a Turkish default locale would yield a dotless "ı"
        assertThat(DiagramLemmas.lower("I")).isEqualTo("i");
    }

    @Test
    void lower_preserves_non_letter_characters() {
        assertThat(DiagramLemmas.lower("A_B-1")).isEqualTo("a_b-1");
    }

    // ---------------------------------------------------------------- accumulatePos

    @Test
    void accumulatePos_a_null_pos_is_ignored() {
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        DiagramLemmas.accumulatePos(posByLemma, "pet", null);
        assertThat(posByLemma).isEmpty();
    }

    @Test
    void accumulatePos_an_empty_pos_code_is_skipped() {
        // PartOfSpeechType.NULL carries the empty code
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        DiagramLemmas.accumulatePos(posByLemma, "pet", PartOfSpeechType.NULL);
        assertThat(posByLemma).isEmpty();
    }

    @Test
    void accumulatePos_records_the_code_under_the_lemma() {
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        DiagramLemmas.accumulatePos(posByLemma, "pet", PartOfSpeechType.NN);
        assertThat(posByLemma).containsOnlyKeys("pet");
        assertThat(posByLemma.get("pet")).containsExactly("NN");
    }

    @Test
    void accumulatePos_accumulates_distinct_codes_in_insertion_order() {
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        DiagramLemmas.accumulatePos(posByLemma, "pet", PartOfSpeechType.NN);
        DiagramLemmas.accumulatePos(posByLemma, "pet", PartOfSpeechType.NNS);
        DiagramLemmas.accumulatePos(posByLemma, "pet", PartOfSpeechType.NN); // duplicate
        assertThat(posByLemma.get("pet")).containsExactly("NN", "NNS");
    }

    @Test
    void accumulatePos_keeps_different_lemmas_apart() {
        final Map<String, Set<String>> posByLemma = new LinkedHashMap<>();
        DiagramLemmas.accumulatePos(posByLemma, "pet", PartOfSpeechType.NN);
        DiagramLemmas.accumulatePos(posByLemma, "shop", PartOfSpeechType.VB);
        assertThat(posByLemma).containsOnlyKeys("pet", "shop");
        assertThat(posByLemma.get("pet")).containsExactly("NN");
        assertThat(posByLemma.get("shop")).containsExactly("VB");
    }

    // ---------------------------------------------------------------- firstPos

    @Test
    void firstPos_a_null_set_is_the_empty_string() {
        assertThat(DiagramLemmas.firstPos(null)).isEmpty();
    }

    @Test
    void firstPos_an_empty_set_is_the_empty_string() {
        assertThat(DiagramLemmas.firstPos(new LinkedHashSet<>())).isEmpty();
    }

    @Test
    void firstPos_returns_the_first_inserted_code() {
        final Set<String> posSet = new LinkedHashSet<>();
        posSet.add("VB");
        posSet.add("NN");
        assertThat(DiagramLemmas.firstPos(posSet)).isEqualTo("VB");
    }

    // ---------------------------------------------------------------- typeLabel

    @Test
    void typeLabel_a_null_type_is_Domain() {
        assertThat(DiagramLemmas.typeLabel(null)).isEqualTo("Domain");
    }

    @Test
    void typeLabel_an_empty_type_is_Domain() {
        assertThat(DiagramLemmas.typeLabel("")).isEqualTo("Domain");
    }

    @Test
    void typeLabel_strips_the_trailing_Cdd_suffix() {
        assertThat(DiagramLemmas.typeLabel("ClassCdd")).isEqualTo("Class");
        assertThat(DiagramLemmas.typeLabel("PropertyCdd")).isEqualTo("Property");
        assertThat(DiagramLemmas.typeLabel("AssociationCdd")).isEqualTo("Association");
    }

    @Test
    void typeLabel_leaves_a_type_without_the_suffix_unchanged() {
        assertThat(DiagramLemmas.typeLabel("Domain")).isEqualTo("Domain");
        assertThat(DiagramLemmas.typeLabel("CddX")).isEqualTo("CddX");
    }

    @Test
    void typeLabel_only_strips_a_trailing_occurrence_of_the_suffix() {
        assertThat(DiagramLemmas.typeLabel("CddClass")).isEqualTo("CddClass");
    }
}
