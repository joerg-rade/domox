package domox.dom.uml;

import lombok.Data;

enum AssociationType {
    AGGREGATION,
    GENERALIZATION,
    ATTRIBUTE;
}

@Data
public class AssociationCdc
        extends Candidate {
    private AssociationType type;
    private Integer sourceCardinality;
    private Integer targetCardinality;
}
