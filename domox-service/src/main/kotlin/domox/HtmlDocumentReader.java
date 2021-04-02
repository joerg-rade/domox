package domox;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class HtmlDocumentReader {
    public String extractContentFromUrl(String url) {
        final String rawContent = readStringFromURL(url);
        return html2text(rawContent);
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
