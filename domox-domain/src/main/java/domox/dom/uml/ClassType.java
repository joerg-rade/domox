package domox.dom.uml;

public enum ClassType {
    /**
     * Does it represent a moment or interval of time
     * that we need to remember and work with for legal or business reasons?
     * Examples in business systems generally model activities involving
     * people, places and things such as
     * a sale, an order, a rental, an employment, making a journey, etc.
     */
    MOMENT_INTERVAL("pink", "pink"),
    /**
     * Is it a way of participating in an activity (by either a person,place,or thing)?
     * A person playing the role of:
     * an employee in an employment,
     * a thing playing the role of a product in a sale,
     * a location playing the role of a classroom for a training course,
     * are examples of roles.+
     */
    ROLE("yellow", "gold"),
    /**
     * Is it simply a catalog-entry-like description which classifies or 'labels' an object?
     * For example, the make and model of a car categorises or describes a number of physical vehicles.
     * The relationship between the blue description and green party, place or thing
     * is a type-instance relationship based on differences in the values of data items held in the 'type' object.+
     */
    DESCRIPTION("blue", "lightblue"),
    /**
     * Something tangible, uniquely identifiable. Typically the role-players in a system.
     * People are green. Organizations are green. The physical objects involved in a rental such as the physical DVDs are green.
     * Normally, if you get through the above three questions and end up here, your class is a "green."
     */
    PARTY_PLACE_THING("green", "lightgreen");

    final String colorName;
    final String colorCode;

    ClassType(String colorName, String colorCode) {
        this.colorName = colorName;
        this.colorCode = colorCode;
    }
}
