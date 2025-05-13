package domox.dom.nlp;

import lombok.Getter;

public enum TdType {
    ROOT("ROOT"),
    ACL("acl"),
    ACL_RELCL("acl:relcl"),
    ADVCL("advcl"),
    ADVCL_BASED_ON("advcl:based_on"),
    ADVCL_ON("advcl:on"),
    ADVCL_TO("advcl:to"),
    ADVMOD("advmod"),
    AMOD("amod"),
    AUX("aux"),
    CASE("case"),
    CC("cc"),
    CCOMP("ccomp"),
    COMPOUND("compound"),
    CONJ("conj"),
    CONJ_AND("conj:and"),
    CONJ_OR("conj:or"),
    COP("cop"),
    DEP("dep"),
    DET("det"),
    DET_QMOD("det:qmod"),
    FIXED("fixed"),
    MARK("mark"),
    NMOD("nmod"),
    NMOD_FOR("nmod:for"),
    NMOD_INCLUDING("nmod:including"),
    NMOD_OF("nmod:of"),
    NMOD_POSS("nmod:poss"),
    NMOD_SUCH_AS("nmod:such_as"),
    NMOD_WITH("nmod:with"),
    NSUBJ("nsubj"),
    NSUBJ_XSUBJ("nsubj:xsubj"),
    NUMMOD("nummod"),
    OBJ("obj"),
    OBL("obl"),
    OBL_BY("obl:by"),
    OBL_DURING("obl:during"),
    OBL_FOR("obl:for"),
    OBL_IN("obl:in"),
    OBL_NPMOD("obl:npmod"),
    OBL_ON("obl:on"),
    OBL_TO("obl:to"),
    OBL_WIN("obl:with"),
    PARATAXIS("parataxis"),
    PUNCT("punct"),
    REF("ref"),
    XCOMP("xcomp");

    @Getter
    final String code;

    TdType(String code) {
        this.code = code;
    }

    public static TdType fromCode(String code) {
        for (TdType type : TdType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant found for code: " + code);
    }

}
