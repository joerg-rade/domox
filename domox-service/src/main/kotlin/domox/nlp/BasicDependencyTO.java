package domox.nlp;

import lombok.Data;

@Data
public class BasicDependencyTO {
    String dep = "";
    Long governor = 0L;
    String governorGloss = "";
    Long dependent = 0L;
    String dependentGloss = "";
}
