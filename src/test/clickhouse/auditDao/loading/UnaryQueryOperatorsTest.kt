package clickhouse.auditDao.loading

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import tanvd.aorm.query.not
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouse
import tanvd.audit.model.external.presenters.StringPresenter
import tanvd.audit.model.external.queries.equal
import tanvd.audit.model.external.records.InformationObject
import utils.*

internal class UnaryQueryOperatorsTest {

    companion object {
        var currentId = 0L
        var auditDao: AuditDaoClickhouse? = null
    }


    @BeforeMethod
    @Suppress("UNCHECKED_CAST")
    fun createAll() {
        auditDao = TestUtil.create()
    }

    @AfterMethod
    fun clearAll() {
        TestUtil.drop()
        currentId = 0
    }

    @Test
    fun loadRows_NotStringsEqual_loadedBoth() {
        val auditRecordFirstOriginal = SamplesGenerator.getRecordInternal("string1", "string2", information = getSampleInformation())
        val auditRecordSecondOriginal = SamplesGenerator.getRecordInternal("string1", "string2", information = getSampleInformation())
        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal, auditRecordSecondOriginal))

        val recordsLoaded = auditDao!!.loadRecords(
                not(StringPresenter.value equal "string3"))
        Assert.assertEquals(recordsLoaded.toSet(), setOf(auditRecordFirstOriginal, auditRecordSecondOriginal))

    }

    @Test
    fun loadRows_NotStringsEqual_loadedOne() {
        val auditRecordFirstOriginal = SamplesGenerator.getRecordInternal("string1", "string2", information = getSampleInformation())
        val auditRecordSecondOriginal = SamplesGenerator.getRecordInternal("string1", "string1", information = getSampleInformation())
        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal, auditRecordSecondOriginal))

        val recordsLoaded = auditDao!!.loadRecords(
                not(StringPresenter.value equal "string2"))
        Assert.assertEquals(recordsLoaded, listOf(auditRecordSecondOriginal))

    }

    @Test
    fun loadRows_NotStringsEqual_loadedNone() {
        val auditRecordFirstOriginal = SamplesGenerator.getRecordInternal("string1", "string1", information = getSampleInformation())
        val auditRecordSecondOriginal = SamplesGenerator.getRecordInternal("string1", "string1", information = getSampleInformation())
        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal, auditRecordSecondOriginal))

        val recordsLoaded = auditDao!!.loadRecords(
                not(StringPresenter.value equal "string1"))
        Assert.assertEquals(recordsLoaded.size, 0)
    }

    private fun getSampleInformation(): LinkedHashSet<InformationObject<*>> {
        return InformationUtils.getPrimitiveInformation(currentId++, 1, 2, SamplesGenerator.getMillenniumStart())
    }
}