package domox.nlp;

import lombok.Data;

@Data
public class BasicDependencyTO {
    public String dep = "";
    public Long governor = 0L;
    String governorGloss = "";
    public Long dependent = 0L;
    String dependentGloss = "";
}
