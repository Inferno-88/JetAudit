package Clickhouse.AuditDao.Loading.Objects

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouseImpl
import tanvd.audit.model.external.db.DbType
import tanvd.audit.model.external.queries.*
import tanvd.audit.model.external.records.InformationObject
import tanvd.audit.model.external.types.objects.ObjectType
import utils.*
import utils.SamplesGenerator.getRecordInternal

internal class StateBooleanTypeTest {

    companion object {
        var currentId = 0L
        var auditDao: AuditDaoClickhouseImpl? = null
    }

    @BeforeMethod
    @Suppress("UNCHECKED_CAST")
    fun createAll() {
        TypeUtils.addAuditTypesPrimitive()
        TypeUtils.addInformationTypesPrimitive()

        auditDao = DbType.Clickhouse.getDao(DbUtils.getDbProperties()) as AuditDaoClickhouseImpl

        TypeUtils.addAuditTypePrimitive(auditDao!!)

        val type = ObjectType(TestClassBoolean::class, TestClassBooleanPresenter) as ObjectType<Any>
        ObjectType.addType(type)
        auditDao!!.addTypeInDbModel(type)

    }

    @AfterMethod
    fun clearAll() {
        auditDao!!.dropTable(AuditDaoClickhouseImpl.auditTable)
        TypeUtils.clearTypes()
        currentId = 0
    }

    //Equality
    @Test
    fun loadRow_LoadByEqual_loadedOne() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id equal true, QueryParameters())
        Assert.assertEquals(recordsLoaded, listOf(auditRecordFirstOriginal))
    }

    @Test
    fun loadRow_LoadByEqual_loadedNone() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id equal false, QueryParameters())
        Assert.assertEquals(recordsLoaded.size, 0)
    }

    @Test
    fun loadRow_LoadByNotEqual_loadedOne() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id notEqual false, QueryParameters())
        Assert.assertEquals(recordsLoaded, listOf(auditRecordFirstOriginal))
    }

    @Test
    fun loadRow_LoadByNotEqual_loadedNone() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id notEqual true, QueryParameters())
        Assert.assertEquals(recordsLoaded.size, 0)
    }

    //List
    @Test
    fun loadRow_LoadByInList_loadedOne() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id inList listOf(true), QueryParameters())
        Assert.assertEquals(recordsLoaded, listOf(auditRecordFirstOriginal))
    }

    @Test
    fun loadRow_LoadByInList_loadedNone() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id inList listOf(false), QueryParameters())
        Assert.assertEquals(recordsLoaded.size, 0)
    }

    @Test
    fun loadRow_LoadByNotInList_loadedOne() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id notInList listOf(false), QueryParameters())
        Assert.assertEquals(recordsLoaded, listOf(auditRecordFirstOriginal))
    }

    @Test
    fun loadRow_LoadByNotInList_loadedNone() {
        val auditRecordFirstOriginal = getRecordInternal(TestClassBoolean(true), information = getSampleInformation())

        auditDao!!.saveRecords(listOf(auditRecordFirstOriginal))

        val recordsLoaded = auditDao!!.loadRecords(TestClassBooleanPresenter.id notInList listOf(true), QueryParameters())
        Assert.assertEquals(recordsLoaded.size, 0)
    }

    private fun getSampleInformation(): MutableSet<InformationObject> {
        return InformationUtils.getPrimitiveInformation(currentId++, 1, 2, SamplesGenerator.getMillenniumStart())

    }
}
