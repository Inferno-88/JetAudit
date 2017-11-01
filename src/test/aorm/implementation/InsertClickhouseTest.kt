package aorm.implementation

import aorm.utils.AormTestBase
import aorm.utils.ExampleTable
import org.testng.Assert
import org.testng.annotations.Test
import tanvd.aorm.Column
import tanvd.aorm.DbType
import tanvd.aorm.InsertExpression
import tanvd.aorm.Row
import tanvd.aorm.exceptions.BasicDbException
import tanvd.aorm.implementation.InsertClickhouse
import tanvd.aorm.query.eq
import tanvd.aorm.query.where
import utils.getDate

class InsertClickhouseTest: AormTestBase() {
    @Test
    fun insert_tableExistsRowValid_rowInserted() {
        ExampleTable.create()
        val row = Row(mapOf(ExampleTable.id to 2L, ExampleTable.value to "value",
                ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>)

        InsertClickhouse.insert(InsertExpression(ExampleTable, row))

        val select = ExampleTable.select() where (ExampleTable.id eq 2L)
        Assert.assertEquals(select.toResult().single(), row)
    }

    @Test
    fun insert_tableNotExistsRowValid_basicDbException() {
        val row = Row(mapOf(ExampleTable.id to 1L, ExampleTable.value to "value",
                ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>)

        try {
            InsertClickhouse.insert(InsertExpression(ExampleTable, row))
        } catch (e : BasicDbException) {
            return
        }

        Assert.fail()
    }

    @Test
    fun insert_tableExistsRowWithDefaults_rowInserted() {
        ExampleTable.create()
        val row = Row(mapOf(ExampleTable.value to "value",
                ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>)

        InsertClickhouse.insert(InsertExpression(ExampleTable, row))

        val select = ExampleTable.select() where (ExampleTable.id eq 1L)
        val rowGot = Row(mapOf(ExampleTable.id to 1L, ExampleTable.value to "value",
                ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>)
        Assert.assertEquals(select.toResult().single(), rowGot)
    }

    @Test
    fun insert_tableExistsRowsValid_rowsInserted() {
        ExampleTable.create()
        val rows = arrayListOf(
                Row(mapOf(ExampleTable.id to 2L, ExampleTable.value to "value",
                ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>),
                Row(mapOf(ExampleTable.id to 3L, ExampleTable.value to "value",
                        ExampleTable.date to getDate("2000-02-02")) as Map<Column<Any, DbType<Any>>, Any>)
        )

        InsertClickhouse.insert(InsertExpression(ExampleTable, ExampleTable.columns, rows))

        val select = ExampleTable.select() where (ExampleTable.value eq "value")
        Assert.assertEquals(select.toResult().toSet(), rows.toSet())
    }

    @Test
    fun insert_tableExistsRowsWithDefaults_rowsInserted() {
        ExampleTable.create()
        val rows = arrayListOf(
                Row(mapOf(ExampleTable.value to "value1",
                        ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>),
                Row(mapOf(ExampleTable.value to "value2",
                        ExampleTable.date to getDate("2000-02-02")) as Map<Column<Any, DbType<Any>>, Any>)
        )

        InsertClickhouse.insert(InsertExpression(ExampleTable, ExampleTable.columns, rows))

        val gotRows = arrayListOf(
                Row(mapOf(ExampleTable.id to 1L, ExampleTable.value to "value1",
                        ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>),
                Row(mapOf(ExampleTable.id to 1L, ExampleTable.value to "value2",
                        ExampleTable.date to getDate("2000-02-02")) as Map<Column<Any, DbType<Any>>, Any>)
        )
        val select = ExampleTable.select() where (ExampleTable.id eq 1L)
        Assert.assertEquals(select.toResult().toSet(), gotRows.toSet())
    }

    @Test
    fun constructInsert_oneRow_equalsToPredefined() {
        ExampleTable.create()
        val row = Row(mapOf(ExampleTable.date to getDate("2000-02-02"), ExampleTable.id to 3L,
                ExampleTable.value to "value") as Map<Column<Any, DbType<Any>>, Any>)

        val sql = InsertClickhouse.constructInsert(InsertExpression(ExampleTable, row))

        Assert.assertEquals(sql, "INSERT INTO ExampleTable (date, id, value) VALUES ('2000-02-02', 3, 'value');")
    }

    @Test
    fun constructInsert_twoRows_equalsToPredefined() {
        ExampleTable.create()
        val rows = arrayListOf(
                Row(mapOf(ExampleTable.id to 2L, ExampleTable.value to "value",
                        ExampleTable.date to getDate("2000-01-01")) as Map<Column<Any, DbType<Any>>, Any>),
                Row(mapOf(ExampleTable.id to 3L, ExampleTable.value to "value",
                        ExampleTable.date to getDate("2000-02-02")) as Map<Column<Any, DbType<Any>>, Any>)
        )

        val sql = InsertClickhouse.constructInsert(InsertExpression(ExampleTable, ExampleTable.columns, rows))

        Assert.assertEquals(sql, "INSERT INTO ExampleTable (date, id, value) VALUES ('2000-01-01', 2, 'value')," +
                " ('2000-02-02', 3, 'value');")
    }
}