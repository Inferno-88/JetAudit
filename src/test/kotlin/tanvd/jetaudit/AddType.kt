package tanvd.jetaudit

import junit.framework.Assert.assertEquals
import org.junit.*
import org.junit.runner.RunWith
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.powermock.api.mockito.PowerMockito.mock
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import tanvd.jetaudit.exceptions.AddExistingAuditTypeException
import tanvd.jetaudit.implementation.AuditExecutor
import tanvd.jetaudit.implementation.QueueCommand
import tanvd.jetaudit.implementation.clickhouse.AuditDao
import tanvd.jetaudit.implementation.clickhouse.aorm.AuditTable
import tanvd.jetaudit.model.external.types.objects.ObjectType
import tanvd.jetaudit.model.internal.AuditRecordInternal
import tanvd.jetaudit.utils.*
import java.util.concurrent.BlockingQueue


@RunWith(PowerMockRunner::class)
@PowerMockIgnore("javax.management.*", "javax.xml.parsers.*", "javax.net.ssl.*",
        "jdk.*",
        "com.sun.org.apache.xerces.internal.jaxp.*", "ch.qos.logback.*",
        "org.slf4j.*")
@PrepareForTest(AuditExecutor::class, ObjectType::class)
internal class AddType {

    private var auditDao: AuditDao? = null

    private var auditExecutor: AuditExecutor? = null

    private var auditQueueInternal: BlockingQueue<QueueCommand>? = null

    private var auditRecordsNotCommitted: ThreadLocal<ArrayList<AuditRecordInternal>>? = null

    private var auditApi: AuditAPI? = null

    @Before
    fun setMocks() {
        auditDao = mock(AuditDao::class.java)
        auditExecutor = mock(AuditExecutor::class.java)
        @Suppress("UNCHECKED_CAST")
        auditQueueInternal = mock(BlockingQueue::class.java) as BlockingQueue<QueueCommand>
        auditRecordsNotCommitted = object : ThreadLocal<ArrayList<AuditRecordInternal>>() {
            override fun initialValue(): ArrayList<AuditRecordInternal>? {
                return ArrayList()
            }
        }
        auditApi = AuditAPI(auditDao!!, auditExecutor!!, auditQueueInternal!!, auditRecordsNotCommitted!!, DbUtils.getProperties(), DbUtils.getDataSource())
    }

    @After
    fun resetMocks() {
        auditRecordsNotCommitted!!.remove()
        reset(auditDao)
        reset(auditExecutor)
        reset(auditQueueInternal)
        TestUtil.clearTypes()
        AuditTable.resetColumns()
    }


    @Test
    fun addType_typeAdded_typeToAuditTypesAdded() {
        val type = createTestClassFirstType()

        auditApi?.addObjectType(type)

        assertEquals(setOf(type), ObjectType.getTypes())
    }

    @Test
    fun addType_typeAdded_typeToAuditDaoAdded() {
        val type = createTestClassFirstType()

        auditApi?.addObjectType(type)

        verify(auditDao)?.addTypeInDbModel(type)
    }

    @Test
    fun addType_typeExistingAdded_exceptionThrown() {
        val type = createTestClassFirstType()

        auditApi?.addObjectType(type)

        try {
            auditApi?.addObjectType(type)
        } catch (e: AddExistingAuditTypeException) {
            return
        }
        Assert.fail()
    }

    private fun createTestClassFirstType(): ObjectType<TestClassString> {
        return ObjectType(TestClassString::class, TestClassStringPresenter)
    }
}
