package domox;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StanfordCoreNlpTO {
    List<SentenceTO> sentences = null;
    @JsonIgnore
    Map<String, List<CorefMentionTO>> corefs;
}

@Data
class SentenceTO {
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
    List<TokenTO> tokens = null;
}

@Data
class EntityMentionTO {
    Long characterOffsetBegin = 0L;
    Long characterOffsetEnd = 0L;
    String tokenEnd = "";
    String ner = "";
    String text = "";
    String normalizedNER = null;
    String docTokenBegin = null;
    String tokenBegin = null;
    String docTokenEnd = null;
    @JsonIgnore
    NerConfidenceTO nerConfidences = null;
}

@Data
class NerConfidenceTO {
    Long NUMBER = -1L;
}

@Data
class CorefMentionTO {
    String number;
    int startIndex;
    String gender;
    int sentNum;
    int endIndex;
    boolean isRepresentativeMention;
    String animacy;
    int id;
    String text;
    List<Integer> position;
    String type;
    int headIndex;
}

@Data
class BasicDependencyTO {
    String dep = "";
    Long governor = 0L;
    String governorGloss = "";
    Long dependent = 0L;
    String dependentGloss = "";
}

@Data
class TokenTO {
    Long index = 0L;
    String word = "";
    String originalText = "";
    String lemma = "";
    Long characterOffsetBegin = 0L;
    Long characterOffsetEnd = 0L;
    String pos = "";
    String ner = "";
    String before = "";
    String after = "";
    String speaker = "";
    String normalizedNER = null;
    @JsonIgnore
    TimexTO timex = null;
}

@Data
class TimexTO {
    String altValue = "";
    String type = "";
    String tid = "";
}
