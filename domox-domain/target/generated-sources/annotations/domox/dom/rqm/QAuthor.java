package domox.dom.rqm;

import javax.annotation.Generated;
import javax.jdo.query.*;
import org.datanucleus.api.jdo.query.*;

@Generated(value="org.datanucleus.jdo.query.JDOQueryProcessor")
public class QAuthor extends PersistableExpressionImpl<Author> implements PersistableExpression<Author>
{
    public static final QAuthor jdoCandidate = candidate("this");

    public static QAuthor candidate(String name)
    {
        return new QAuthor(null, name, 5);
    }

    public static QAuthor candidate()
    {
        return jdoCandidate;
    }

    public static QAuthor parameter(String name)
    {
        return new QAuthor(Author.class, name, ExpressionType.PARAMETER);
    }

    public static QAuthor variable(String name)
    {
        return new QAuthor(Author.class, name, ExpressionType.VARIABLE);
    }

    public final ObjectExpression<org.apache.isis.applib.services.repository.RepositoryService> repositoryService;
    public final ObjectExpression<org.apache.isis.applib.services.title.TitleService> titleService;
    public final ObjectExpression<org.apache.isis.applib.services.message.MessageService> messageService;
    public final StringExpression firstName;
    public final StringExpression middleInitial;
    public final StringExpression lastName;
    public final StringExpression eMail;

    public QAuthor(PersistableExpression parent, String name, int depth)
    {
        super(parent, name);
        this.repositoryService = new ObjectExpressionImpl<org.apache.isis.applib.services.repository.RepositoryService>(this, "repositoryService");
        this.titleService = new ObjectExpressionImpl<org.apache.isis.applib.services.title.TitleService>(this, "titleService");
        this.messageService = new ObjectExpressionImpl<org.apache.isis.applib.services.message.MessageService>(this, "messageService");
        this.firstName = new StringExpressionImpl(this, "firstName");
        this.middleInitial = new StringExpressionImpl(this, "middleInitial");
        this.lastName = new StringExpressionImpl(this, "lastName");
        this.eMail = new StringExpressionImpl(this, "eMail");
    }

    public QAuthor(Class type, String name, ExpressionType exprType)
    {
        super(type, name, exprType);
        this.repositoryService = new ObjectExpressionImpl<org.apache.isis.applib.services.repository.RepositoryService>(this, "repositoryService");
        this.titleService = new ObjectExpressionImpl<org.apache.isis.applib.services.title.TitleService>(this, "titleService");
        this.messageService = new ObjectExpressionImpl<org.apache.isis.applib.services.message.MessageService>(this, "messageService");
        this.firstName = new StringExpressionImpl(this, "firstName");
        this.middleInitial = new StringExpressionImpl(this, "middleInitial");
        this.lastName = new StringExpressionImpl(this, "lastName");
        this.eMail = new StringExpressionImpl(this, "eMail");
    }
}
