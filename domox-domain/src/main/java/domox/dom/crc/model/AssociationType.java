package domox.dom.crc.model;

/**
 * The association kinds a {@link CollaboratorCdd} can have, and the PlantUML arrow each is drawn with.
 *
 * <p><b>Every symbol must be one PlantUML actually parses.</b>  The export's output is rendered by
 * Kroki (see {@code DomainModels} / {@code UmlClassDiagram}), and PlantUML rejects the whole diagram
 * with {@code Error 400: Syntax Error?} when a single relation uses an arrow it does not know — which
 * is how {@code GENERALIZATION} came to be spelled {@code --|>} rather than the {@code |>-} that read
 * naturally from the notation: {@code |>-} is not a PlantUML arrow, and the 970-class whole-model
 * diagram failed to render because of it alone.</p>
 *
 * <p>The direction is the one the notation reads in: {@code source --|> target} means the
 * <em>source</em> inherits the <em>target</em> (the head points at the super-class), which is what
 * {@code CandidateFactory} creates — {@code findOrCreate(assocName, child, parent, …)}.</p>
 */
public enum AssociationType {
    ASSOCIATION("->"), //attribute
    GENERALIZATION("--|>"), // inheritance
    IMPLEMENTATION("..|>"), //REALIZATION
    DEPENDENCY(".>"),
    AGGREGATION("*->"),
    COMPOSITION("+->"), // existence
    SYNONYM("=="); // semantic equivalence (TDR41)

    final String symbol;

    AssociationType(String symbol) {
        this.symbol = symbol;
    }
}

