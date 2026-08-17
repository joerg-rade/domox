package domox.dom.uml;

import lombok.Data;

import java.io.Serializable;

@Data
public class Cardinality implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer min;
    private Integer max;
    private boolean hasLimit;

}
