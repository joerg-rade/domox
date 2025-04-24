package domox.nlp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class TokenTO {
    Long index = 0L;
    public String word = "";
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
