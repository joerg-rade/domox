package domox.dom.uml;

import java.util.List;

import lombok.Data;

@Data
public class ActionCdc
        extends Candidate {

    private List<Object> inputTypes;
    private Object outputType;
}
