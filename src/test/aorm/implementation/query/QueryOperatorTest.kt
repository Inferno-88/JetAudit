package aorm.implementation.query

import aorm.utils.ExampleTable
import org.testng.Assert
import org.testng.annotations.Test
import tanvd.aorm.implementation.QueryClickhouse
import tanvd.aorm.query.*

class QueryOperatorTest {
    @Test
    fun and_twoConditions_validSQL() {
        val expression = (ExampleTable.id eq 1L) and (ExampleTable.value eq "string")
        val query = ExampleTable.select() where expression

        val sql = QueryClickhouse.constructQuery(query)
        Assert.assertEquals(sql, "SELECT ${ExampleTable.columns.joinToString { it.name }} FROM" +
                " ExampleTable WHERE ((id = 1) AND (value = 'string')) ;")
    }

    @Test
    fun or_twoConditions_validSQL() {
        val expression = (ExampleTable.id eq 1L) or (ExampleTable.value eq "string")
        val query = ExampleTable.select() where expression

        val sql = QueryClickhouse.constructQuery(query)
        Assert.assertEquals(sql, "SELECT ${ExampleTable.columns.joinToString { it.name }} FROM" +
                " ExampleTable WHERE ((id = 1) OR (value = 'string')) ;")
    }

    @Test
    fun not_oneCondition_validSQL() {
        val expression = not (ExampleTable.id eq 1L)
        val query = ExampleTable.select() where expression

        val sql = QueryClickhouse.constructQuery(query)
        Assert.assertEquals(sql, "SELECT ${ExampleTable.columns.joinToString { it.name }} FROM" +
                " ExampleTable WHERE (NOT (id = 1)) ;")
    }
}