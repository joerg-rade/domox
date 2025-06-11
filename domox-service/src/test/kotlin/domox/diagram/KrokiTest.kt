package domox.diagram;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KrokiTest {

    private static final DockerImageName KROKI_IMAGE = DockerImageName.parse("yuzutech/kroki");

    @Test
    void testKrokiContainer() throws Exception {
        try (GenericContainer<?> kroki = new GenericContainer<>(KROKI_IMAGE)
                .withExposedPorts(8000)) {

            kroki.start();
            String krokiUrl = "http://" + kroki.getHost() + ":" + kroki.getMappedPort(8000) + "/plantuml/svg/";

            // Example PlantUML diagram
            String plantUmlCode = "@startuml\nAlice -> Bob: Hello\n@enduml";
            byte[] postData = plantUmlCode.getBytes(StandardCharsets.UTF_8);

            // Send request to Kroki
            HttpURLConnection connection = (HttpURLConnection) new URL(krokiUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.getOutputStream().write(postData);

            // Validate response
            assertEquals(200, connection.getResponseCode());
        }
    }
}
