package tanvd.jetaudit.writer

import org.junit.*
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.modules.junit4.PowerMockRunner
import org.slf4j.Logger
import tanvd.jetaudit.implementation.clickhouse.aorm.AuditTable
import tanvd.jetaudit.implementation.writer.ClickhouseSqlLogWriter
import tanvd.jetaudit.model.external.presenters.*
import tanvd.jetaudit.model.external.records.InformationObject
import tanvd.jetaudit.utils.*
import tanvd.jetaudit.utils.SamplesGenerator.getRecordInternal

@RunWith(PowerMockRunner::class)
@PowerMockIgnore("javax.management.*", "javax.xml.parsers.*", "com.sun.org.apache.xerces.internal.jaxp.*", "ch.qos.logback.*", "org.slf4j.*")
internal class ClickhouseSqlLogWriterTest {

    @Before
    fun init() {
        TestUtil.create()
    }

    @After
    fun clean() {
        TestUtil.drop()
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
                "${AuditTable.description.name}, " +
                "${LongPresenter.value.column.name}) VALUES " +
                "([123], ['456'], 0, 2, 1, '2000-01-01', 0, ['Int', 'String'], []);")
    }

    private fun getSampleInformation(id: Long, timeStamp: Long, version: Long): LinkedHashSet<InformationObject<*>> {
        return InformationUtils.getPrimitiveInformation(id, timeStamp, version, SamplesGenerator.getMillenniumStart())
    }
}
