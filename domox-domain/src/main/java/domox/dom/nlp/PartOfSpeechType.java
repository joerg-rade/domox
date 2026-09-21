package domox.dom.nlp;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*    ADVP,
    NOUN_PHRASE,
    VERB_PHARSE,
    VERB,*/
public enum PartOfSpeechType {
    DOT("."),
    COMMA(","),
    DDOT(":"),
    CC("CC"),
    CD("CD"),
    DT("DT"),
    HYPH("HYPH"),
    IN("IN"),
    JJ("JJ"),
    LS("LS"),
    MD("MD"),
    NFP("NFP"),
    NN("NN"),
    NNP("NNP"),
    NNPS("NNPS"),
    NNS("NNS"),
    /*    NP,
        PP,*/
    PRP("PRP"),
    PRP$("PRP$"),
    RB("RB"),
    RRB("-RRB-"),
    /*    SBAR,*/
    TO("TO"),
    VB("VB"),
    VBD("VBD"),
    VBG("VBG"),
    VBN("VBN"),
    VBP("VBP"),
    VBZ("VBZ"),
    /*    VP,*/
    WDT("WDT"),
    WRB("WRB"),
    NULL("");

    @Getter
    final String code;

    PartOfSpeechType(String code) {
        this.code = code;
    }

    private static final Logger log = LoggerFactory.getLogger(PartOfSpeechType.class);

    public static PartOfSpeechType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        final String trimmed = code.trim();
        for (PartOfSpeechType type : PartOfSpeechType.values()) {
            if (type.getCode().equals(trimmed)) {
                return type;
            }
        }
        log.warn("Unknown POS code '{}' — returning null", code);
        return null;
    }

}
