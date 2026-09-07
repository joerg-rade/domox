package domox.dom.rules;


import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ActionCatalog {

    private final Set<String> nonDomainVerbs = new HashSet<>();

    public ActionCatalog(NlpProperties nlpProperties) {
        addAll(nlpProperties.getAuxiliaryVerbs());
        addAll(nlpProperties.getModalVerbs());
        addAll(nlpProperties.getUserInputVerbs());
        addAll(nlpProperties.getSystemOutputVerbs());
        addAll(nlpProperties.getActionVerbs());
        addAll(nlpProperties.getInputPastVerbs());
        addAll(nlpProperties.getOutputPastVerbs());
        addAll(nlpProperties.getReceiveVerbs());
        addAll(nlpProperties.getControlFlowVerbs());
    }

    private void addAll(List<String> verbs) {
        if (verbs != null) {
            for (String verb : verbs) {
                nonDomainVerbs.add(verb.toLowerCase(Locale.ROOT));
            }
        }
    }

    //FIXME should be used in ACTION related rules
    public boolean isDomainAction(@NonNull String verb) {
        final String v = verb.toLowerCase(Locale.ROOT);
        return !nonDomainVerbs.contains(v);
    }
}