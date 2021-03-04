package domox.dom.nlp;

import javax.annotation.Generated;
import javax.jdo.query.*;
import org.datanucleus.api.jdo.query.*;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QSentence extends PersistableExpressionImpl<Sentence> implements PersistableExpression<Sentence>
{
    public static final QSentence jdoCandidate = candidate("this");

    public static QSentence candidate(String name)
    {
        return new QSentence(null, name, 5);
    }

    public static QSentence candidate()
    {
        return jdoCandidate;
    }

    public static QSentence parameter(String name)
    {
        return new QSentence(Sentence.class, name, ExpressionType.PARAMETER);
    }

    public static QSentence variable(String name)
    {
        return new QSentence(Sentence.class, name, ExpressionType.VARIABLE);
    }

    public final NumericExpression<Long> id;
    public final StringExpression raw;
    public final ObjectExpression<org.apache.isis.applib.value.Clob> parsed;
    public final domox.dom.rqm.QDocument document;

    public QSentence(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.raw = new StringExpressionImpl(this, "raw");
        this.parsed = new ObjectExpressionImpl<org.apache.isis.applib.value.Clob>(this, "parsed");
        if (depth > 0)
        {
            this.document = new domox.dom.rqm.QDocument(this, "document", depth-1);
        }
        else
        {
            this.document = null;
        }
    }

    public QSentence(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.raw = new StringExpressionImpl(this, "raw");
        this.parsed = new ObjectExpressionImpl<org.apache.isis.applib.value.Clob>(this, "parsed");
        this.document = new domox.dom.rqm.QDocument(this, "document", 5);
    }
}
