package domox.dom.uml;

import lombok.Data;

@Data
public class Cardinality {
    private Integer min;
    private Integer max;
    private boolean hasLimit;

}
