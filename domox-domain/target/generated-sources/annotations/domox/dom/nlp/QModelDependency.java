package domox.dom.nlp;

import javax.annotation.Generated;
import javax.jdo.query.*;
import org.datanucleus.api.jdo.query.*;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QModelDependency extends PersistableExpressionImpl<ModelDependency> implements PersistableExpression<ModelDependency>
{
    public static final QModelDependency jdoCandidate = candidate("this");

    public static QModelDependency candidate(String name)
    {
        return new QModelDependency(null, name, 5);
    }

    public static QModelDependency candidate()
    {
        return jdoCandidate;
    }

    public static QModelDependency parameter(String name)
    {
        return new QModelDependency(ModelDependency.class, name, ExpressionType.PARAMETER);
    }

    public static QModelDependency variable(String name)
    {
        return new QModelDependency(ModelDependency.class, name, ExpressionType.VARIABLE);
    }

    public final NumericExpression<Long> id;
    public final EnumExpression type;
    public final domox.dom.nlp.QPartOfSpeech partA;
    public final domox.dom.nlp.QPartOfSpeech partB;

    public QModelDependency(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.type = new EnumExpressionImpl(this, "type");
        if (depth > 0)
        {
            this.partA = new domox.dom.nlp.QPartOfSpeech(this, "partA", depth-1);
        }
        else
        {
            this.partA = null;
        }
        if (depth > 0)
        {
            this.partB = new domox.dom.nlp.QPartOfSpeech(this, "partB", depth-1);
        }
        else
        {
            this.partB = null;
        }
    }

    public QModelDependency(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.type = new EnumExpressionImpl(this, "type");
        this.partA = new domox.dom.nlp.QPartOfSpeech(this, "partA", 5);
        this.partB = new domox.dom.nlp.QPartOfSpeech(this, "partB", 5);
    }
}
