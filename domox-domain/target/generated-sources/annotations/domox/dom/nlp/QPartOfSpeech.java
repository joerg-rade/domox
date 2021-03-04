package domox.dom.nlp;

import javax.annotation.Generated;
import javax.jdo.query.*;
import org.datanucleus.api.jdo.query.*;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QPartOfSpeech extends PersistableExpressionImpl<PartOfSpeech> implements PersistableExpression<PartOfSpeech>
{
    public static final QPartOfSpeech jdoCandidate = candidate("this");

    public static QPartOfSpeech candidate(String name)
    {
        return new QPartOfSpeech(null, name, 5);
    }

    public static QPartOfSpeech candidate()
    {
        return jdoCandidate;
    }

    public static QPartOfSpeech parameter(String name)
    {
        return new QPartOfSpeech(PartOfSpeech.class, name, ExpressionType.PARAMETER);
    }

    public static QPartOfSpeech variable(String name)
    {
        return new QPartOfSpeech(PartOfSpeech.class, name, ExpressionType.VARIABLE);
    }

    public final NumericExpression<Long> id;
    public final StringExpression content;
    public final EnumExpression type;

    public QPartOfSpeech(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.content = new StringExpressionImpl(this, "content");
        this.type = new EnumExpressionImpl(this, "type");
    }

    public QPartOfSpeech(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.content = new StringExpressionImpl(this, "content");
        this.type = new EnumExpressionImpl(this, "type");
    }
}
