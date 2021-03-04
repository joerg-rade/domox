package domox.dom.nlp;

import javax.annotation.Generated;
import javax.jdo.query.*;
import org.datanucleus.api.jdo.query.*;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QRelation extends PersistableExpressionImpl<Relation> implements PersistableExpression<Relation>
{
    public static final QRelation jdoCandidate = candidate("this");

    public static QRelation candidate(String name)
    {
        return new QRelation(null, name, 5);
    }

    public static QRelation candidate()
    {
        return jdoCandidate;
    }

    public static QRelation parameter(String name)
    {
        return new QRelation(Relation.class, name, ExpressionType.PARAMETER);
    }

    public static QRelation variable(String name)
    {
        return new QRelation(Relation.class, name, ExpressionType.VARIABLE);
    }

    public final NumericExpression<Long> id;
    public final EnumExpression type;
    public final ListExpression modelDependencies;

    public QRelation(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.type = new EnumExpressionImpl(this, "type");
        this.modelDependencies = new ListExpressionImpl(this, "modelDependencies");
    }

    public QRelation(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.type = new EnumExpressionImpl(this, "type");
        this.modelDependencies = new ListExpressionImpl(this, "modelDependencies");
    }
}
