package domox.dom.nlp;

import lombok.Getter;

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
    NNS("NNS"),
    /*    NP,
        PP,*/
    PRP("PRP"),
    PRP$("PRP$"),
    RB("RB"),
    /*    SBAR,*/
    TO("TO"),
    VB("VB"),
    /*    VBD,*/
    VBG("VBG"),
    VBN("VBN"),
    VBP("VBP"),
    VBZ("VBZ"),
    /*    VP,*/
    WDT("WDT"),
    WRB("WRB");

    @Getter
    final String code;

    PartOfSpeechType(String code) {
        this.code = code;
    }

    public static PartOfSpeechType fromCode(String code) {
        for (PartOfSpeechType type : PartOfSpeechType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant found for code: " + code);
    }

}
