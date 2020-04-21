package org.apache.isis.domox.modules.ksimple.dom.so

import org.apache.isis.applib.annotation.*
import org.apache.isis.applib.services.repository.RepositoryService
import org.apache.isis.domox.modules.ksimple.SimpleModule
import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObject
import org.apache.isis.domox.modules.ksimple.types.Name
import org.apache.isis.persistence.jdo.applib.services.IsisJdoSupport_v3_2
import javax.jdo.JDOQLTypedQuery

@DomainService(nature = NatureOfService.VIEW, objectType = "simple.SimpleObjects")
class SimpleObjects {
    private val repositoryService: RepositoryService? = null
    private val isisJdoSupport: IsisJdoSupport_v3_2? = null

    open class ActionDomainEvent : SimpleModule.ActionDomainEvent<SimpleObjects?>()
    class CreateActionDomainEvent : ActionDomainEvent()

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT, domainEvent = CreateActionDomainEvent::class)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    fun create(
            @Name name: String?): SimpleObject {
        return repositoryService!!.persist(SimpleObject.withName(name))!!
    }

    class FindByNameActionDomainEvent : ActionDomainEvent()

    @Action(semantics = SemanticsOf.SAFE, domainEvent = FindByNameActionDomainEvent::class)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT, promptStyle = PromptStyle.DIALOG_SIDEBAR)
    fun findByName(@Name name: String?): MutableList<SimpleObject?>? {
        var q: JDOQLTypedQuery<SimpleObject?> = isisJdoSupport!!.newTypesafeQuery(SimpleObject::class.java)
        val cand: QSimpleObject = QSimpleObject.candidate()
        q = q.filter(
                QSimpleObject.name.indexOf(q.stringParameter("name")).ne(-1)
        )
        return q.setParameter("name", name)
                .executeList()
    }

    @Programmatic
    fun findByNameExact(name: String?): SimpleObject {
        var q: JDOQLTypedQuery<SimpleObject?> = isisJdoSupport!!.newTypesafeQuery(SimpleObject::class.java)
        val cand: QSimpleObject = QSimpleObject.candidate()
        val potMatch = q.stringParameter("name")
        q = q.filter(
                QSimpleObject.name.eq(potMatch)   // OR eq??
        )!!
        return q.setParameter("name", name)
                .executeUnique()!!
    }

    class ListAllActionDomainEvent : ActionDomainEvent()

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT)
    fun listAll(): MutableList<SimpleObject>? {
        return repositoryService!!.allInstances(SimpleObject::class.java)
    }

    @Programmatic
    fun ping() {
        val q: JDOQLTypedQuery<SimpleObject> = isisJdoSupport!!.newTypesafeQuery(SimpleObject::class.java)
        val candidate: QSimpleObject = QSimpleObject.candidate()
        q.range(0, 2)
        q.orderBy(QSimpleObject.name.asc())
        q.executeList()
    }

    object QSimpleObject {

        var name: String = ""

        fun candidate(): QSimpleObject {
            return this
        }
    }

}
