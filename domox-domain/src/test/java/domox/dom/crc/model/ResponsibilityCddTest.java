package domox.dom.crc.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit guardrail for {@link ResponsibilityCdd#toPlantUmlString()} — the operation line of the
 * exported UML class diagram.
 *
 * <p>Every one of the 149 operation lines in the model export used to read {@code browse(: void}:
 * the closing parenthesis was only emitted by a trailing-separator strip, so an operation with no
 * parameters never got one. These cases pin the PlantUML shape (an operation is
 * {@code name(param: Type, …)} with the return type <em>omitted</em> for {@code void}) and the
 * null-tolerance of a hand-built instance.</p>
 */
class ResponsibilityCddTest {

    private static List<ResponsibilityParameter> parameters(final String... nameTypePairs) {
        final List<ResponsibilityParameter> parameters = new ArrayList<>();
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            parameters.add(new ResponsibilityParameter(nameTypePairs[i], nameTypePairs[i + 1]));
        }
        return parameters;
    }

    @Test
    void rendersAParameterlessOperationWithItsClosingParenthesis() {
        // given
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", new ArrayList<>(), "void");

        // when / then — the regression: "browse(: void"
        assertThat(browse.toPlantUmlString()).isEqualTo("browse()");
    }

    @Test
    void rendersASingleParameter() {
        final ResponsibilityCdd notify =
                new ResponsibilityCdd("notify", parameters("message", "String"), "void");

        assertThat(notify.toPlantUmlString()).isEqualTo("notify(message: String)");
    }

    @Test
    void rendersSeveralParametersCommaSeparated() {
        final ResponsibilityCdd order =
                new ResponsibilityCdd("order", parameters("pet", "Pet", "quantity", "int"), "void");

        assertThat(order.toPlantUmlString()).isEqualTo("order(pet: Pet, quantity: int)");
    }

    @Test
    void rendersANonVoidReturnType() {
        final ResponsibilityCdd total =
                new ResponsibilityCdd("calculateTotal", new ArrayList<>(), "double");

        assertThat(total.toPlantUmlString()).isEqualTo("calculateTotal(): double");
    }

    /** UML writes no return type for {@code void} — and never the literal {@code null}. */
    @Test
    void omitsTheReturnTypeWhenItIsVoidBlankOrNull() {
        assertThat(new ResponsibilityCdd("a", new ArrayList<>(), "void").toPlantUmlString())
                .isEqualTo("a()");
        assertThat(new ResponsibilityCdd("b", new ArrayList<>(), "").toPlantUmlString())
                .isEqualTo("b()");
        assertThat(new ResponsibilityCdd("c", new ArrayList<>(), null).toPlantUmlString())
                .isEqualTo("c()");
    }

    /** A null parameter list is normalised by the constructor, so the entity stays usable. */
    @Test
    void normalisesANullParameterListToAnEmptyOne() {
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", null, "void");

        assertThat(browse.inputTypeList).isEmpty();
        assertThat(browse.toPlantUmlString()).isEqualTo("browse()");
    }

    /**
     * The constructor copies the caller's list. {@code List.of()} is immutable, and a parameter is
     * appended to this collection later ({@code ResponsibilityCandidates#addParameter}) — storing the
     * caller's list verbatim made that append throw {@code UnsupportedOperationException}.
     */
    @Test
    void acceptsAnImmutableParameterList() {
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", List.of(), "void");

        browse.inputTypeList.add(new ResponsibilityParameter("pet", "Pet"));

        assertThat(browse.toPlantUmlString()).isEqualTo("browse(pet: Pet)");
    }

    /**
     * The field is public, so a detached instance can still carry {@code null} (Hibernate normally
     * materialises it as an empty bag). Rendering must not throw.
     */
    @Test
    void toleratesANullParameterListOnTheField() {
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", List.of(), "void");
        browse.inputTypeList = null;

        assertThat(browse.toPlantUmlString()).isEqualTo("browse()");
    }

    // ============ the stored return type is never null (the column is NOT NULL) ============

    /**
     * Reads the write-only {@link ResponsibilityCdd#outputType} field. There is deliberately no
     * {@code getOutputType()} — the UML rendering is the field's public contract — so the storage
     * invariant pinned below can only be read reflectively.
     */
    private static String storedOutputType(final ResponsibilityCdd operation) {
        try {
            final Field field = ResponsibilityCdd.class.getDeclaredField("outputType");
            field.setAccessible(true);
            return (String) field.get(operation);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read outputType", e);
        }
    }

    /**
     * The regression that aborted a corpus load: the {@code outputtype} column is {@code NOT NULL},
     * while the analysis path answers "no return type" with a {@code null} for every verb that is
     * neither a receive verb nor an output verb — most of them. Storing that answer straight through
     * failed the <em>whole</em> analysis phase with
     * {@code null value in column "outputtype" of relation "responsibilitycdd" violates not-null
     * constraint} (the first operation it hit was {@code groom}). So every write path — the
     * constructor and the setter — must leave the field at the stored {@code void} literal.
     */
    @Test
    void storesVoidWhenThereIsNoReturnType() {
        assertThat(storedOutputType(new ResponsibilityCdd("groom", new ArrayList<>(), null)))
                .isEqualTo("void");
        assertThat(storedOutputType(new ResponsibilityCdd("groom", new ArrayList<>(), "")))
                .isEqualTo("void");
        assertThat(storedOutputType(new ResponsibilityCdd("groom", new ArrayList<>(), "   ")))
                .isEqualTo("void");

        // the other write path — a detached instance built by hand, then set
        final ResponsibilityCdd handBuilt = new ResponsibilityCdd();
        handBuilt.setOutputType(null);
        assertThat(storedOutputType(handBuilt)).isEqualTo("void");
    }

    /** A real return type is stored verbatim, and an explicit {@code void} is left as it is. */
    @Test
    void keepsARealReturnType() {
        final ResponsibilityCdd receive = new ResponsibilityCdd("receive", new ArrayList<>(), "Payment");
        assertThat(storedOutputType(receive)).isEqualTo("Payment");
        assertThat(receive.toPlantUmlString()).isEqualTo("receive(): Payment");

        final ResponsibilityCdd explicitVoid = new ResponsibilityCdd();
        explicitVoid.setOutputType("void");
        assertThat(storedOutputType(explicitVoid)).isEqualTo("void");
    }
}
