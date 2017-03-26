package tanvd.audit.implementation.clickhouse

import org.slf4j.LoggerFactory
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouseImpl.Scheme.descriptionColumn
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouseImpl.Scheme.getPredefinedAuditTableColumn
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouseImpl.Scheme.unixTimeStampColumn
import tanvd.audit.implementation.clickhouse.model.DbColumn
import tanvd.audit.implementation.clickhouse.model.DbColumnType
import tanvd.audit.implementation.clickhouse.model.DbRow
import tanvd.audit.model.AuditRecord
import tanvd.audit.model.AuditType
import java.util.*

object ClickhouseRecordSerializer {

    private val logger = LoggerFactory.getLogger(ClickhouseRecordSerializer::class.java)


    /**
     * Serialize AuditRecord for Clickhouse
     * Serialize order to string of types
     * Serialize objects to groups by types respecting order
     */
    fun serialize(auditRecord: AuditRecord): DbRow {

        val description = auditRecord.objects.map { it.first.code }

        val groupedObjects = auditRecord.objects.groupBy { it.first }.mapValues {
            entry ->
            entry.value.map { it.second }
        }

        val elements = groupedObjects.map { DbColumn(it.key.code, it.value, DbColumnType.DbArrayString) }.toMutableList()
        elements.add(DbColumn(getPredefinedAuditTableColumn(descriptionColumn), description))
        elements.add(DbColumn(getPredefinedAuditTableColumn(unixTimeStampColumn), auditRecord.unixTimeStamp.toString()))

        return DbRow(elements)
    }

    /**
     * Deserialize MySQL.AuditRecord from string representation
     * DbString in Map -- name code
     */
    fun deserialize(row: DbRow): AuditRecord {
        val description = row.columns.find { it.name == descriptionColumn }
        if (description == null) {
            logger.error("Clickhouse scheme violated. Not found $descriptionColumn column.")
            return AuditRecord(emptyList(), 0)
        }
        val unixTimeStamp = row.columns.find { it.name == unixTimeStampColumn }
        if (unixTimeStamp == null) {
            logger.error("Clickhouse scheme violated. Not found $unixTimeStampColumn column.")
            return AuditRecord(emptyList(), 0)
        }
        val objects = ArrayList<Pair<AuditType<Any>, String>>()
        val currentNumberOfType = HashMap<String, Int>()

        for (code in description.elements) {
            val pair = row.columns.find { it.name == code }
            if (pair != null) {
                val index = currentNumberOfType[code] ?: 0
                if (pair.elements[index].isNotEmpty()) {
                    objects.add(Pair(AuditType.resolveType(code), pair.elements[index]))
                }
                currentNumberOfType.put(code, index + 1)
            }
        }

        return AuditRecord(objects, unixTimeStamp.elements[0].toInt())
    }
}