package domainapp.modules.simple.dom.so;

import javax.annotation.Generated;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QSimpleObject extends PersistableExpressionImpl<SimpleObject> implements PersistableExpression<SimpleObject>
{
    public static final QSimpleObject jdoCandidate = candidate("this");

    public static QSimpleObject candidate(String name)
    {
        return new QSimpleObject(null, name, 5);
    }

    public static QSimpleObject candidate()
    {
        return jdoCandidate;
    }

    public static QSimpleObject parameter(String name)
    {
        return new QSimpleObject(SimpleObject.class, name, ExpressionType.PARAMETER);
    }

    public static QSimpleObject variable(String name)
    {
        return new QSimpleObject(SimpleObject.class, name, ExpressionType.VARIABLE);
    }

    public final ObjectExpression<org.apache.isis.applib.services.repository.RepositoryService> repositoryService;
    public final ObjectExpression<org.apache.isis.applib.services.title.TitleService> titleService;
    public final ObjectExpression<org.apache.isis.applib.services.message.MessageService> messageService;
    public final StringExpression name;
    public final StringExpression notes;

    public QSimpleObject(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.repositoryService = new ObjectExpressionImpl<org.apache.isis.applib.services.repository.RepositoryService>(this, "repositoryService");
        this.titleService = new ObjectExpressionImpl<org.apache.isis.applib.services.title.TitleService>(this, "titleService");
        this.messageService = new ObjectExpressionImpl<org.apache.isis.applib.services.message.MessageService>(this, "messageService");
        this.name = new StringExpressionImpl(this, "name");
        this.notes = new StringExpressionImpl(this, "notes");
    }

    public QSimpleObject(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.repositoryService = new ObjectExpressionImpl<org.apache.isis.applib.services.repository.RepositoryService>(this, "repositoryService");
        this.titleService = new ObjectExpressionImpl<org.apache.isis.applib.services.title.TitleService>(this, "titleService");
        this.messageService = new ObjectExpressionImpl<org.apache.isis.applib.services.message.MessageService>(this, "messageService");
        this.name = new StringExpressionImpl(this, "name");
        this.notes = new StringExpressionImpl(this, "notes");
    }
}
