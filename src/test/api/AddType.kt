package api

import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.powermock.api.mockito.PowerMockito.mock
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.testng.PowerMockTestCase
import org.testng.Assert
import org.testng.Assert.assertEquals
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import tanvd.audit.AuditAPI
import tanvd.audit.exceptions.AddExistingAuditTypeException
import tanvd.audit.implementation.AuditExecutor
import tanvd.audit.implementation.dao.AuditDao
import tanvd.audit.model.external.types.AuditType
import tanvd.audit.model.internal.AuditRecordInternal
import utils.TestClassFirst
import utils.TypeUtils
import java.util.concurrent.BlockingQueue


@PowerMockIgnore("javax.management.*", "javax.xml.parsers.*", "com.sun.org.apache.xerces.internal.jaxp.*", "ch.qos.logback.*", "org.slf4j.*")
@PrepareForTest(AuditExecutor::class, AuditType::class)
internal class AddType : PowerMockTestCase() {

    private var auditDao: AuditDao? = null

    private var auditExecutor: AuditExecutor? = null

    private var auditQueueInternal: BlockingQueue<AuditRecordInternal>? = null

    private var auditRecordsNotCommitted: ThreadLocal<ArrayList<AuditRecordInternal>>? = null

    private var auditApi: AuditAPI? = null

    @BeforeClass
    fun setMocks() {
        auditDao = mock(AuditDao::class.java)
        auditExecutor = mock(AuditExecutor::class.java)
        @Suppress("UNCHECKED_CAST")
        auditQueueInternal = mock(BlockingQueue::class.java) as BlockingQueue<AuditRecordInternal>
        auditRecordsNotCommitted = object : ThreadLocal<ArrayList<AuditRecordInternal>>() {
            override fun initialValue(): ArrayList<AuditRecordInternal>? {
                return ArrayList()
            }
        }
        auditApi = AuditAPI(auditDao!!, auditExecutor!!, auditQueueInternal!!, auditRecordsNotCommitted!!)
    }

    @AfterMethod
    fun resetMocks() {
        auditRecordsNotCommitted!!.remove()
        reset(auditDao)
        reset(auditExecutor)
        reset(auditQueueInternal)
        TypeUtils.clearTypes()
    }


    @Test
    fun addType_typeAdded_typeToAuditTypesAdded() {
        val type = createTestClassFirstType()

        auditApi?.addAuditType(type)

        assertEquals(setOf(type), AuditType.getTypes())
    }

    @Test
    fun addType_typeAdded_typeToAuditDaoAdded() {
        val type = createTestClassFirstType()

        auditApi?.addAuditType(type)

        verify(auditDao)?.addTypeInDbModel(type)
    }

    @Test
    fun addType_typeExistingAdded_exceptionThrown() {
        val type = createTestClassFirstType()

        auditApi?.addAuditType(type)

        try {
            auditApi?.addAuditType(type)
        } catch (e: AddExistingAuditTypeException) {
            return
        }
        Assert.fail()
    }

    private fun createTestClassFirstType(): AuditType<TestClassFirst> {
        return AuditType(TestClassFirst::class, "TestClassFirst", TestClassFirst)
    }
}
