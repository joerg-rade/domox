package org.apache.isis.domox.modules.ksimple.dom.so

import org.apache.isis.applib.annotation.*
import org.apache.isis.applib.annotation.SemanticsOf.IDEMPOTENT
import org.apache.isis.applib.annotation.SemanticsOf.NON_IDEMPOTENT_ARE_YOU_SURE
import org.apache.isis.applib.jaxbadapters.PersistentEntityAdapter
import org.apache.isis.applib.services.message.MessageService
import org.apache.isis.applib.services.repository.RepositoryService
import org.apache.isis.applib.services.title.TitleService
import org.apache.isis.domox.modules.ksimple.SimpleModule
import org.apache.isis.domox.modules.ksimple.types.Name
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*
import javax.inject.Inject
import javax.jdo.annotations.IdGeneratorStrategy
import javax.jdo.annotations.IdentityType
import javax.jdo.annotations.Version
import javax.jdo.annotations.VersionStrategy
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

@javax.jdo.annotations.PersistenceCapable(identityType = IdentityType.DATASTORE, schema = "simple")
@javax.jdo.annotations.DatastoreIdentity(strategy = IdGeneratorStrategy.IDENTITY, column = "id")
@Version(strategy = VersionStrategy.DATE_TIME, column = "version")
@javax.jdo.annotations.Unique(name = "SimpleObject_name_UNQ", members = ["name"])
@DomainObject
@DomainObjectLayout
@XmlJavaTypeAdapter(PersistentEntityAdapter::class)
@Component
class SimpleObject private constructor() : Comparable<SimpleObject?> {
    open class ActionDomainEvent : SimpleModule.ActionDomainEvent<SimpleObject?>()

    @Inject
    lateinit var repositoryService: RepositoryService

    @Inject
    lateinit var titleService: TitleService

    @Inject
    lateinit var messageService: MessageService

    fun title(): String? {
        return "Object: " + getName()
    }

    private var name: String = ""
    fun getName(): String? {
        return name
    }

    fun setName(value: String) {
        name = value
    }


    val notes: String? = null

    class UpdateNameActionDomainEvent : ActionDomainEvent()

    @Action(semantics = IDEMPOTENT, command = CommandReification.ENABLED, publishing = Publishing.ENABLED, associateWith = "name", domainEvent = UpdateNameActionDomainEvent::class)
    fun updateName(
            @Name name: String?): SimpleObject? {
        setName(name!!)
        return this
    }

    fun default0UpdateName(): String? {
        return name
    }

    class DeleteActionDomainEvent : ActionDomainEvent()

    @Action(semantics = NON_IDEMPOTENT_ARE_YOU_SURE, domainEvent = DeleteActionDomainEvent::class)
    fun delete() {
        val title: String = titleService.titleOf(this)
        messageService.informUser(String.format("'%s' deleted", title))
        repositoryService.removeAndFlush(this)
    }

    override fun compareTo(other: SimpleObject?): Int {
        return comparator!!.compare(this, other)
    }

    companion object {
        fun withName(name: String?): SimpleObject? {
            val simpleObject = SimpleObject()
            simpleObject.setName(name!!)
            return simpleObject
        }

        private val comparator: Comparator<SimpleObject?>? =
                Comparator.comparing { obj: SimpleObject? -> obj?.getName() }
    }
}
