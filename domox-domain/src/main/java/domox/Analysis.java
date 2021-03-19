package domox;

import domox.dom.rqm.Author;
import domox.dom.rqm.Document;
import domox.dom.rqm.Documents;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.value.Clob;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@DomainService(nature = NatureOfService.VIEW,
        objectType = "domox.Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Slf4j
public class Analysis {
    private final Documents documents;

    @Action()
    @ActionLayout(cssClassFa = "play")
    public List<Document> init() {
        setUpFixtures();
        return documents.listAll();
    }

    @Programmatic
    private void setUpFixtures() {
        final Author holland = Author.withLastName("Holland");
        holland.setFirstName("James");
        holland.setMiddleInitial("G.");
        holland.setEMail("james.g.holland@apa.division25.org");
        final Author skinner = Author.withLastName("Skinner");
        skinner.setFirstName("Burrhus");
        skinner.setMiddleInitial("F.");
        skinner.setEMail("burrhus.f.skinner@apa.division25.org");
        final Set<Author> authors = new HashSet<>();
        authors.add(holland);
        authors.add(skinner);
        final String title = "Analysis of Behaviour";
        final String mimeTypeBase = "text/xml";

        //https://www.reqview.com/papers/ReqView-Example_Software_Requirements_Specification_SRS_Document.pdf
        final String url = "https://web.cse.ohio-state.edu/~bair.41/616/Project/Example_Document/Req_Doc_Example.html";
        final String urlContent = readStringFromURL(url);
        final String txtContent = html2text(urlContent);
        final Clob content = new Clob("", mimeTypeBase, txtContent);
        // when
        final Document document = documents.create(title, url, content, authors);
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

    @Action()
    @ActionLayout(cssClassFa = "indent")
    public void splitIntoSentences() {
        final Document document = documents.listAll().get(0);
        if (document != null) split(document);
    }

    private void split(Document doc) {
        final Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit");
        props.setProperty("coref.algorithm", "neural");
        final StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        final String text = doc.getContent().getChars().toString();
        final CoreDocument cd = new CoreDocument(text);
        pipeline.annotate(cd);

        final List<CoreSentence> coreSentences = cd.sentences();
        for (CoreSentence cs : coreSentences) {
            String t = cs.text().trim();
            if (!t.endsWith(":")) { //) && (!t.matches("\\d.$"))
                if (t.contains(":")) {
                    final int pos = t.lastIndexOf(":");
                    t = t.substring(pos + 1).trim();
                }
                documents.addSentenceTo(t, doc);
            }
        }
        System.out.println();
    }

    private String html2text(String html) {
        String out = html.replaceAll("\"<a [\\s\\S]*?</a>", "");
        out = out.replaceAll("<h2>[\\s\\S]*?</h2>", "");
        out = out.replaceAll("<h3>[\\s\\S]*?</h3>", "");
        out = out.replaceAll("<dt>[\\s\\S]*?</dt>", "");
        out = out.replaceAll("<title>[\\s\\S]*?</title>", "");
        out = out.replaceAll("<[^>]+>", "");
        return out.replaceAll("&nbsp;", " ");
    }

}