package domox.dom.crc;

public enum AssociationType {
    ASSOCIATION("->"), //attribute
    GENERALIZATION("|>-"), // inheritance
    IMPLEMENTATION(""), //REALIZATION
    DEPENDENCY(".>"),
    AGGREGATION("*->"),
    COMPOSITION("+->"); // existence

    final String symbol;

    AssociationType(String symbol) {
        this.symbol = symbol;
    }
}

