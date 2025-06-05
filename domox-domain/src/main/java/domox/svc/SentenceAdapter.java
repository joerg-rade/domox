package domox.svc;

import domox.HttpRequest;
import domox.diagram.TdDiagram;
import domox.nlp.BasicDependencyTO;
import domox.nlp.SentenceTO;
import domox.nlp.TokenTO;
import lombok.RequiredArgsConstructor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        final String png = new HttpRequest().invokePlantUML(pumlCode);
        writeToFile(png);
        return png.getBytes();
    }

    private void writeToFile(String png) {
        try {
            InputStream is = new FileInputStream(png);
            FileOutputStream fos = new FileOutputStream("/sample.png");

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
