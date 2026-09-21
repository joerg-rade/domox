package domox.dom.nlp;

import lombok.Getter;

public enum TdType {
    ROOT("ROOT"),
    ACL("acl"),
    ACL_TO("acl:to"),
    ACL_RELCL("acl:relcl"),
    ADVCL("advcl"),
    ADVCL_BEFORE("advcl:before"),
    ADVCL_BASED_ON("advcl:based_on"),
    ADVCL_ON("advcl:on"),
    ADVCL_TO("advcl:to"),
    ADVMOD("advmod"),
    AMOD("amod"),
    APPOS("appos"),
    AUX("aux"),
    AUX_PASS("aux:pass"),
    CASE("case"),
    CC("cc"),
    CCOMP("ccomp"),
    COMPOUND("compound"),
    CONJ("conj"),
    CONJ_AND("conj:and"),
    CONJ_AMP_AND("conj:&"),
    CONJ_OR("conj:or"),
    COP("cop"),
    DEP("dep"),
    DET("det"),
    DET_QMOD("det:qmod"),
    FIXED("fixed"),
    IOBJ("iobj"),
    MARK("mark"),
    NMOD("nmod"),
    NMOD_AT("nmod:at"),
    NMOD_ACROSS("nmod:across"),
    NMOD_FROM("nmod:from"),
    NMOD_FOR("nmod:for"),
    NMOD_IN("nmod:in"),
    NMOD_INCLUDING("nmod:including"),
    NMOD_INSTEAD_OF("nmod:instead_of"),
    NMOD_OF("nmod:of"),
    NMOD_ON("nmod:on"),
    NMOD_OUT_OF("nmod:out_of"),
    NMOD_POSS("nmod:poss"),
    NMOD_SUCH_AS("nmod:such_as"),
    NMOD_WITH("nmod:with"),
    NSUBJ("nsubj"),
    NSUBJ_PASS("nsubj:pass"),
    NSUBJPASS("nsubjpass"), //TODO
    NSUBJ_XSUBJ("nsubj:xsubj"),
    NUMMOD("nummod"),
    OBJ("obj"),
    OBL("obl"),
    OBL_ACROSS("obl:across"),
    OBL_AT("obl:at"),
    OBL_BY("obl:by"),
    OBL_DURING("obl:during"),
    OBL_FOR("obl:for"),
    OBL_FROM("obl:from"),
    OBL_IN("obl:in"),
    OBL_INTO("obl:into"),
    OBL_NPMOD("obl:npmod"),
    OBL_OF("obl:of"),
    OBL_ON("obl:on"),
    OBL_TO("obl:to"),
    OBL_WITH("obl:with"),
    OBL_WITHIN("obl:within"),
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
