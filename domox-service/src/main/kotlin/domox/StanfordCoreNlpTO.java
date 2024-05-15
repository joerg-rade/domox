package domox;

import lombok.Data;

import java.util.List;

@Data
class StanfordCoreNlpTO {
    List<SentenceTO> sentences = null;
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
    List<String> entitymentions = null;
    List<TokenTO> tokens = null;
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
}
