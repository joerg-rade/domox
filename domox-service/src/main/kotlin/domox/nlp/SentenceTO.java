package domox.nlp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class SentenceTO {
    Long index = 0L;
    String parse = "";
    List<BasicDependencyTO> basicDependencies = null;
    List<BasicDependencyTO> enhancedDependencies = null;
    List<BasicDependencyTO> enhancedPlusPlusDependencies = null;
    String sentimentValue = "";
    String sentiment = "";
    List<Long> sentimentDistribution = null;
    String sentimentTree = "";
    @JsonIgnore
    List<EntityMentionTO> entitymentions = null;
    public List<TokenTO> tokens = null;
}
