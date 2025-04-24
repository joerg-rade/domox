package domox.nlp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DocumentTO {
    List<SentenceTO> sentences = null;
    @JsonIgnore
    Map<String, List<CorefMentionTO>> corefs;
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
class TimexTO {
    String altValue = "";
    String type = "";
    String tid = "";
}
