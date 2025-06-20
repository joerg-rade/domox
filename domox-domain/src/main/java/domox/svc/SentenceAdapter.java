package domox.svc;

import domox.HttpRequest;
import domox.diagram.TdDiagram;
import domox.nlp.BasicDependencyTO;
import domox.nlp.SentenceTO;
import domox.nlp.TokenTO;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SentenceAdapter {
    private final SentenceTO sentence;

    public String transferObjectAsString() {
        final StringBuilder sb = new StringBuilder();
        final List<TokenTO> tokens = sentence.getTokens();
        for (TokenTO tt : tokens) {
            sb.append(tt.getWord()).append(" ");
        }
        return sb.toString().trim();
    }

    public byte[] buildTypedDependencyDiagram(SentenceTO sentenceTO) {
        final String pumlCode = TdDiagram.INSTANCE.build(sentenceTO);
        final String diagram = new HttpRequest().invokePlantUML(pumlCode);
        return diagram.getBytes();
    }

    public String getWord(int i) {
        return sentence.getTokens().get(i - 1).getWord();
    }

    public String getPartOfSpeech(int i) {
        return sentence.getTokens().get(i - 1).getPos();
    }

    public String getDependency(int i) {
        final List<BasicDependencyTO> depList = sentence.getEnhancedPlusPlusDependencies();
        for (BasicDependencyTO d : depList) {
            if (d.getDependent() == i) return d.getDep();
        }
        return null;
    }

}
