package domox.webapp;

import org.apache.causeway.core.config.presets.CausewayPresets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({AppManifest.class})
public class Application /*extends SpringBootServletInitializer*/ {

    /**
     * @implNote this is to support the <em>Spring Boot Maven Plugin</em>, which auto-detects an
     * entry point by searching for classes having a {@code main(...)}
     */
    public static void main(String[] args) {
        CausewayPresets.prototyping();
        SpringApplication.run(Application.class, args);
    }

}
