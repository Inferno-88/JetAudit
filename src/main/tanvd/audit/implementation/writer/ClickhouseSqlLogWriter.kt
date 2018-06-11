package tanvd.audit.implementation.writer

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tanvd.aorm.InsertExpression
import tanvd.aorm.InsertRow
import tanvd.audit.implementation.clickhouse.ClickhouseRecordSerializer
import tanvd.audit.implementation.clickhouse.aorm.AuditTable
import tanvd.audit.model.internal.AuditRecordInternal

internal class ClickhouseSqlLogWriter : AuditReserveWriter {

    private val writer: Logger

    constructor(loggerName: String) {
        writer = LoggerFactory.getLogger(loggerName)
    }

    constructor(writer: Logger) {
        this.writer = writer
    }

    override fun flush() {
        //nothing to do here
    }

    override fun write(record: AuditRecordInternal) {
        val row = ClickhouseRecordSerializer.serialize(record)
        writer.error(InsertExpression(AuditTable, InsertRow(row.toMutableMap())).toSql())
    }

    override fun close() {
        //nothing to do here
    }


}