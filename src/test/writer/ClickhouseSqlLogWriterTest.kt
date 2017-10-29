package writer

import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.modules.testng.PowerMockTestCase
import org.slf4j.Logger
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouseImpl
import tanvd.audit.implementation.clickhouse.aorm.AuditTable
import tanvd.audit.implementation.writer.ClickhouseSqlLogWriter
import tanvd.audit.model.external.presenters.*
import tanvd.audit.model.external.records.InformationObject
import tanvd.audit.model.external.types.information.InformationType
import utils.InformationUtils
import utils.SamplesGenerator
import utils.SamplesGenerator.getRecordInternal
import utils.TestUtil

@PowerMockIgnore("javax.management.*", "javax.xml.parsers.*", "com.sun.org.apache.xerces.internal.jaxp.*", "ch.qos.logback.*", "org.slf4j.*")
internal class ClickhouseSqlLogWriterTest : PowerMockTestCase() {

    @BeforeMethod
    fun init() {
        TestUtil.addObjectTypePrimitives()
        TestUtil.addInformationTypesPrimitive()
    }

    @AfterMethod
    fun clean() {
        TestUtil.clearTypes()
    }

    @Test
    fun write_gotAuditRecordInternal_AppropriateSqlInsertWritten() {
        val id = 0L
        val version = 1L
        val timeStamp = 2L
        val auditRecord = getRecordInternal(123, "456", information = getSampleInformation(id, timeStamp, version))
        val logWriter = PowerMockito.mock(Logger::class.java)
        val reserveWriter = ClickhouseSqlLogWriter(logWriter)

        reserveWriter.write(auditRecord)


        Mockito.verify(logWriter).error("INSERT INTO ${AuditTable.name} (" +
                "${IntPresenter.value.column.name}, ${StringPresenter.value.column.name}, " +
                "${IdType.column.name}, " +
                "${TimeStampType.column.name}, " +
                "${VersionType.column.name}, " +
                "${DateType.column.name}, " +
                "${IsDeletedType.column.name}, " +
                "${AuditTable.description.name}) VALUES " +
                "([123], ['456'], 0, 2, 1, '2000-01-01', 0, ['Int', 'String']);")
    }

    private fun getSampleInformation(id: Long, timeStamp: Long, version: Long): LinkedHashSet<InformationObject<*>> {
        return InformationUtils.getPrimitiveInformation(id, timeStamp, version, SamplesGenerator.getMillenniumStart())
    }
}
