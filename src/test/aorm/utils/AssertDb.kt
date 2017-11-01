package aorm.utils

import org.testng.Assert
import tanvd.aorm.Table
import tanvd.aorm.implementation.MetadataClickhouse

object AssertDb {
    fun syncedWithDb(table: Table) {
        Assert.assertTrue(MetadataClickhouse.existsTable(table))

        val metadataColumns = MetadataClickhouse.columnsOfTable(table)

        for ((name, type) in metadataColumns) {
            Assert.assertTrue(table.columns.any { column -> column.name == name && column.type.toSqlName() == type })
        }

        for (column in table.columns) {
            Assert.assertTrue(metadataColumns.any {(name, type) -> name == column.name && type == column.type.toSqlName()})
        }
    }
}