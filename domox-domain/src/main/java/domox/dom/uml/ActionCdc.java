package domox.dom.uml;

import lombok.Data;

import java.util.List;

@Data
public class ActionCdc
        extends Candidate {

    private List<Object> inputTypes;
    private Object outputType;
}
