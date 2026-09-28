package domox.dom.crc;

public enum AssociationType {
    ASSOCIATION("->"), //attribute
    GENERALIZATION("|>-"), // inheritance
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

