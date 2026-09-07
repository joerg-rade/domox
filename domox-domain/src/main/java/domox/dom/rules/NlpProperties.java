package domox.dom.rules;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "domox.nlp")
@Getter
@Setter
public class NlpProperties {
    private List<String> basicAttributes = List.of();
    private List<String> auxiliaryVerbs = List.of();
    private List<String> modalVerbs = List.of();
    private List<String> userInputVerbs = List.of();
    private List<String> systemOutputVerbs = List.of();
    private List<String> actionVerbs = List.of();
    private List<String> inputPastVerbs = List.of();
    private List<String> outputPastVerbs = List.of();
    private List<String> receiveVerbs = List.of();
    private List<String> exceptionTerms = List.of();
    private List<String> controlFlowVerbs = List.of();
    private List<String> customerActors = List.of();
    private List<String> controlFlowTerms = List.of();
    private List<String> serviceNouns = List.of();
    private List<String> blockedVerbs = List.of();
}