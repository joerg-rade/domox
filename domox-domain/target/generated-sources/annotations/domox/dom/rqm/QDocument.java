package domox.dom.rqm;

import javax.annotation.Generated;
import javax.jdo.query.*;
import org.datanucleus.api.jdo.query.*;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QDocument extends PersistableExpressionImpl<Document> implements PersistableExpression<Document>
{
    public static final QDocument jdoCandidate = candidate("this");

    public static QDocument candidate(String name)
    {
        return new QDocument(null, name, 5);
    }

    public static QDocument candidate()
    {
        return jdoCandidate;
    }

    public static QDocument parameter(String name)
    {
        return new QDocument(Document.class, name, ExpressionType.PARAMETER);
    }

    public static QDocument variable(String name)
    {
        return new QDocument(Document.class, name, ExpressionType.VARIABLE);
    }

    public final NumericExpression<Long> id;
    public final StringExpression title;
    public final StringExpression docVersion;
    public final StringExpression url;
    public final ObjectExpression<org.apache.isis.applib.value.Clob> content;
    public final ObjectExpression<java.sql.Timestamp> createdAt;
    public final ObjectExpression<java.sql.Timestamp> updatedAt;

    public QDocument(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.title = new StringExpressionImpl(this, "title");
        this.docVersion = new StringExpressionImpl(this, "docVersion");
        this.url = new StringExpressionImpl(this, "url");
        this.content = new ObjectExpressionImpl<org.apache.isis.applib.value.Clob>(this, "content");
        this.createdAt = new ObjectExpressionImpl<java.sql.Timestamp>(this, "createdAt");
        this.updatedAt = new ObjectExpressionImpl<java.sql.Timestamp>(this, "updatedAt");
    }

    public QDocument(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.id = new NumericExpressionImpl<Long>(this, "id");
        this.title = new StringExpressionImpl(this, "title");
        this.docVersion = new StringExpressionImpl(this, "docVersion");
        this.url = new StringExpressionImpl(this, "url");
        this.content = new ObjectExpressionImpl<org.apache.isis.applib.value.Clob>(this, "content");
        this.createdAt = new ObjectExpressionImpl<java.sql.Timestamp>(this, "createdAt");
        this.updatedAt = new ObjectExpressionImpl<java.sql.Timestamp>(this, "updatedAt");
    }
}
