package domox.dom;

import domox.HtmlDocumentReader;
import domox.dom.rqm.Document;
import domox.dom.rqm.Documents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.value.Clob;

import javax.inject.Inject;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW,
        objectType = "domox.dom.Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Slf4j
public class Analysis {
    private final Documents documents;
    final HtmlDocumentReader reader = new HtmlDocumentReader();

    @Action()
    @ActionLayout(cssClassFa = "play")
    public Document init() {
        //https://www.reqview.com/papers/ReqView-Example_Software_Requirements_Specification_SRS_Document.pdf
        final String url = "https://web.cse.ohio-state.edu/~bair.41/616/Project/Example_Document/Req_Doc_Example.html";
        final String title = "Requirements Document Example";
        final String txtContent = reader.extractContentFromUrl(url);
        final Clob content = new Clob("", "text/xml", txtContent);
        final Document document = documents.create(title, url, content, null);
        final List<String> rawList = reader.split(document);
        for (String r :rawList) {
            documents.addSentenceTo(r, document);
        }
        return document;
    }

}