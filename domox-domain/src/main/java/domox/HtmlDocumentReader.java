package domox;

import domox.dom.rqm.Document;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class HtmlDocumentReader {
    public String extractContentFromUrl(String url) {
        final String rawContent = readStringFromURL(url);
        final String txtContent = html2text(rawContent);
        return txtContent;
    }

    private String readStringFromURL(String requestURL) {
        InputStream is = null;
        try {
            is = new URL(requestURL).openStream();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        final String csName = StandardCharsets.UTF_8.toString();
        try (Scanner scanner = new Scanner(is, csName)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public List<String> split(Document doc) {
        final List<String> lines = new ArrayList<>();
        final Properties props = new Properties();
        props.setProperty("annotators", "tokenize,cleanxml,ssplit");
        props.setProperty("coref.algorithm", "neural");
        final StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        final String text = doc.getContent().getChars().toString();
        final CoreDocument cd = new CoreDocument(text);
        pipeline.annotate(cd);

        final List<CoreSentence> coreSentences = cd.sentences();
        for (CoreSentence cs : coreSentences) {
            String t = cs.text().trim();
            if (!t.endsWith(":")) { //|| (!t.matches("\\d.$")))
                if (t.contains(":")) {
                    final int pos = t.lastIndexOf(":");
                    t = t.substring(pos + 1).trim();
                }
                lines.add(t);
            }
        }
        return lines;
    }

    private String html2text(String html) {
        String out = html;
        out = out.replaceAll("<h2>Table of Contents[\\s\\S]*?Introduction</h1></a>", "");
        out = out.replaceAll("<a [\\s\\S]*?</a>", "");
        // out = out.replaceAll("<li>Section[\\s\\S]*?<br>", "");
        out = out.replaceAll("<h1>[\\s\\S]*?</h1>", "");
        out = out.replaceAll("<h2>[\\s\\S]*?</h2>", "");
        out = out.replaceAll("<h3>[\\s\\S]*?</h3>", "");
        out = out.replaceAll("<dt>[\\s\\S]*?</dt>", "");
        out = out.replaceAll("<title>[\\s\\S]*?</title>", "");
        //       out = out.replaceAll("<[^>]+>", "");
        return out.replaceAll("&nbsp;", " ");
    }

}
