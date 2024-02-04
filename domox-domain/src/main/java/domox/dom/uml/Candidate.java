package domox.dom.uml;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public abstract class Candidate {

    @Getter
    @Setter
    public String name;
}
