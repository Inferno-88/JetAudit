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
import tanvd.audit.implementation.writer.ClickhouseSqlLogWriter
import tanvd.audit.model.external.presenters.IdPresenter
import tanvd.audit.model.external.presenters.TimeStampPresenter
import tanvd.audit.model.external.presenters.VersionPresenter
import tanvd.audit.model.external.records.InformationObject
import tanvd.audit.model.external.types.AuditType
import tanvd.audit.model.external.types.InformationType
import tanvd.audit.model.internal.AuditRecordInternal
import utils.InformationUtils
import utils.TypeUtils

@PowerMockIgnore("javax.management.*", "javax.xml.parsers.*", "com.sun.org.apache.xerces.internal.jaxp.*", "ch.qos.logback.*", "org.slf4j.*")
internal class ClickhouseSqlLogWriterTest : PowerMockTestCase() {

    @BeforeMethod
    fun init() {
        TypeUtils.addAuditTypesPrimitive()
        TypeUtils.addInformationTypesPrimitive()
    }

    @AfterMethod
    fun clean() {
        TypeUtils.clearTypes()
    }

    @Test
    fun write_gotAuditRecordInternal_AppropriateSqlInsertWritten() {
        val id = 0L
        val version = 1L
        val timeStamp = 2L
        val auditRecord = AuditRecordInternal(123, 456L, information = getSampleInformation(id, timeStamp, version))
        val logWriter = PowerMockito.mock(Logger::class.java)
        val reserveWriter = ClickhouseSqlLogWriter(logWriter)

        reserveWriter.write(auditRecord)

        val typeInt = AuditType.resolveType(Int::class)
        val typeLong = AuditType.resolveType(Long::class)


        Mockito.verify(logWriter).error("INSERT INTO ${AuditDaoClickhouseImpl.auditTable} (" +
                "${typeInt.code}, ${typeLong.code}, ${AuditDaoClickhouseImpl.descriptionColumn}," +
                " ${InformationType.resolveType(IdPresenter).code}, ${InformationType.resolveType(VersionPresenter).code}," +
                " ${InformationType.resolveType(TimeStampPresenter).code}) VALUES " +
                "(['${typeInt.serialize(123)}'], ['${typeLong.serialize(456)}'], ['${typeInt.code}', '${typeLong.code}']," +
                " $id, $version, $timeStamp);")
    }

    private fun getSampleInformation(id: Long, timeStamp: Long, version: Long): MutableSet<InformationObject> {
        return InformationUtils.getPrimitiveInformation(id, timeStamp, version)
    }
}
