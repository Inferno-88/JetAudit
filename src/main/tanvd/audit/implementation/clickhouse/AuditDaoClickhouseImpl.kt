package tanvd.audit.implementation.clickhouse

import tanvd.audit.implementation.clickhouse.model.DbColumnHeader
import tanvd.audit.implementation.clickhouse.model.DbColumnType
import tanvd.audit.implementation.clickhouse.model.DbTableHeader
import tanvd.audit.implementation.dao.AuditDao
import tanvd.audit.model.external.AuditType
import tanvd.audit.model.external.QueryExpression
import tanvd.audit.model.external.QueryParameters
import tanvd.audit.model.internal.AuditRecordInternal
import tanvd.audit.utils.PropertyLoader
import javax.sql.DataSource

/**
 * Dao to Clickhouse DB.
 */
internal class AuditDaoClickhouseImpl(dataSource: DataSource) : AuditDao {

    /**
     * Predefined scheme for clickhouse base.
     */
    companion object Scheme {
        val auditTable = PropertyLoader.load("schemeClickhouse.properties", "AuditTable")
        val dateColumn = PropertyLoader.load("schemeClickhouse.properties", "DateColumn")
        val descriptionColumn = PropertyLoader.load("schemeClickhouse.properties", "DescriptionColumn")
        val unixTimeStampColumn = PropertyLoader.load("schemeClickhouse.properties", "UnixTimeStampColumn")

        /** Default column with date for MergeTree Engine. Should not be inserted or selected.*/
        val defaultColumns = arrayOf(DbColumnHeader(dateColumn, DbColumnType.DbDate))

        /**
         * Mandatory columns for audit. Should be presented in every insert. Treated specifically rather than
         * normal types.
         */
        val mandatoryColumns = arrayOf(DbColumnHeader(descriptionColumn, DbColumnType.DbArrayString),
                DbColumnHeader(unixTimeStampColumn, DbColumnType.DbInt))

        fun getPredefinedAuditTableColumn(name: String): DbColumnHeader {
            return arrayOf(*defaultColumns, *mandatoryColumns).find { it.name == name }!!
        }
    }

    private val clickhouseConnection = JdbcClickhouseConnection(dataSource)

    init {
        initTables()
    }

    /**
     * Creates necessary columns for current types
     */
    private fun initTables() {
        val columnsHeader = arrayListOf(*defaultColumns, *mandatoryColumns)
        AuditType.getTypes().mapTo(columnsHeader) { DbColumnHeader(it.code, DbColumnType.DbArrayString) }
        clickhouseConnection.createTable(auditTable, DbTableHeader(columnsHeader), dateColumn, dateColumn)
    }

    /**
     * Saves audit record and all its objects
     */
    override fun saveRecord(auditRecordInternal: AuditRecordInternal) {
        val row = ClickhouseRecordSerializer.serialize(auditRecordInternal)
        clickhouseConnection.insertRow(auditTable, row)
    }

    /**
     * Saves audit records. Makes it faster, than for loop with saveRecord
     */
    override fun saveRecords(auditRecordInternals: List<AuditRecordInternal>) {
        val columnsHeader = arrayListOf(*mandatoryColumns)
        AuditType.getTypes().mapTo(columnsHeader) { DbColumnHeader(it.code, DbColumnType.DbArrayString) }

        val rows = auditRecordInternals.map { ClickhouseRecordSerializer.serialize(it) }

        clickhouseConnection.insertRows(auditTable, DbTableHeader(columnsHeader), rows)
    }

    /**
     * Adds new type and creates column for it
     */
    override fun <T> addTypeInDbModel(type: AuditType<T>) {
        clickhouseConnection.addColumn(auditTable, DbColumnHeader(type.code, DbColumnType.DbArrayString))
    }

    /**
     * Loads all auditRecords with specified object
     */
    override fun loadRecords(expression: QueryExpression, parameters: QueryParameters): List<AuditRecordInternal> {
        val selectColumns = arrayListOf(*mandatoryColumns)
        AuditType.getTypes().mapTo(selectColumns) { DbColumnHeader(it.code, DbColumnType.DbArrayString) }

        if (parameters.orderBy.isOrderedByTimeStamp) {
            parameters.orderBy.codes.add(0, Pair(unixTimeStampColumn, parameters.orderBy.timeStampOrder))
        }

        val resultList = clickhouseConnection.loadRows(auditTable, DbTableHeader(selectColumns), expression, parameters)
        val auditRecordList = resultList.map { ClickhouseRecordSerializer.deserialize(it) }
        return auditRecordList
    }

    override fun countRecords(expression: QueryExpression): Int {
        val resultNumber = clickhouseConnection.countRows(auditTable, expression)
        return resultNumber
    }

    /**
     * Drops table with specified name
     */
    fun dropTable(tableName: String) {
        clickhouseConnection.dropTable(tableName, true)
    }
}

