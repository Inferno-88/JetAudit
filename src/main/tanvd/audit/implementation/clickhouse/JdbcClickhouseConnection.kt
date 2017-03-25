package tanvd.audit.implementation.clickhouse

import org.slf4j.LoggerFactory
import tanvd.audit.implementation.clickhouse.model.*
import tanvd.audit.model.AuditType
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

/** Be aware that all exceptions in Clickhouse will be saved to the log as errors and than ignored. **/
class JdbcClickhouseConnection(val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(JdbcClickhouseConnection::class.java)


    /**
     * Creates table with specified header (uses ifNotExists modifier by default)
     * You must specify date field cause we use MergeTree Engine
     */
    fun createTable(tableName: String, tableHeader: DbTableHeader, primaryKey: String, dateKey: String,
                    setDefaultDate: Boolean = true, ifNotExists: Boolean = true) {
        val sqlCreate = StringBuilder()
        sqlCreate.append("CREATE TABLE ${if (ifNotExists) "IF NOT EXISTS " else ""} ")
        sqlCreate.append(tableHeader.columnsHeader.joinToString(prefix = "$tableName (", postfix = ") ") {
            if (it.name == dateKey && setDefaultDate)
                "${it.name} ${it.type} DEFAULT today()"
            else
                "${it.name} ${it.type}"
        })
        sqlCreate.append("ENGINE = MergeTree($dateKey, ($primaryKey), 8192);")

        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            connection = dataSource.connection
            preparedStatement = connection.prepareStatement(sqlCreate.toString())
            preparedStatement.executeUpdate()
        } catch (e: Throwable) {
            logger.error("Error inside Clickhouse occurred: ", e)
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    /**
     * Adds column in a table with specified name and name
     */
    fun addColumn(tableName: String, columnHeader: DbColumnHeader) {
        val sqlAlter = "ALTER TABLE $tableName ADD COLUMN ${columnHeader.name} ${columnHeader.type} ;"

        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            connection = dataSource.connection
            preparedStatement = connection.prepareStatement(sqlAlter)
            preparedStatement.executeUpdate()
        } catch (e: Throwable) {
            logger.error("Error inside Clickhouse occurred: ", e)
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    /**
     * Insert only in columns which row contains
     * Inserted values will sanitized by Clickhouse JDBC driver
     */
    fun insertRow(tableName: String, row: DbRow) {
        val sqlInsert = "INSERT INTO $tableName (${row.toStringHeader()}) VALUES (${row.toPlaceholders()});"

        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            connection = dataSource.connection
            preparedStatement = connection.prepareStatement(sqlInsert)
            for ((index, column) in row.columns.withIndex()) {
                preparedStatement.setColumn(column, index + 1)
            }
            preparedStatement.executeUpdate()
        } catch (e: Throwable) {
            logger.error("Error inside Clickhouse occurred: ", e)
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    /**
     * Insert in all columns (Some maybe not stated).
     * Use batches. Inserted values will be sanitized by Clickhouse JDBC driver
     */
    fun insertRows(tableName: String, tableHeader: DbTableHeader, rows: List<DbRow>) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            connection = dataSource.connection
            preparedStatement = connection.prepareStatement("INSERT INTO $tableName (${tableHeader.toDefString()})" +
                    " VALUES (${tableHeader.toPlaceholders()});")

            for ((columns) in rows) {
                for ((index, column) in tableHeader.columnsHeader.withIndex()) {
                    val dbIndex = index + 1
                    val appropriateColumn = columns.find { it.name == column.name }
                    if (appropriateColumn != null) {
                        preparedStatement.setColumn(appropriateColumn, dbIndex)
                    } else {
                        preparedStatement.setColumn(DbColumn("", emptyList(), DbColumnType.DbArrayString), dbIndex)
                    }
                }
                preparedStatement.addBatch()
            }

            preparedStatement?.executeBatch()
        } catch (e: Throwable) {
            logger.error("Error inside Clickhouse occurred: ", e)
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    /**
     * Loads rows with all specified columns from specified table where column typeName has element id
     * Id will be sanitized by Clickhouse JDBC driver
     */
    fun loadRows(tableName: String, typeName: String, id: String, columnsToSelect: DbTableHeader): List<DbRow> {
        val sqlSelect = "SELECT ${columnsToSelect.toDefString()} FROM $tableName WHERE has($typeName, ?);"

        val rows = ArrayList<DbRow>()

        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        try {
            connection = dataSource.connection
            preparedStatement = connection.prepareStatement(sqlSelect)
            preparedStatement.setString(1, id)

            resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                val elements = ArrayList<DbColumn>()
                for (columnHeader in columnsToSelect.columnsHeader) {
                    elements.add(resultSet.getColumn(columnHeader))
                }
                rows.add(DbRow(elements))
            }
        } catch (e: Throwable) {
            logger.error("Error inside Clickhouse occurred: ", e)
        } finally {
            resultSet?.close()
            preparedStatement?.close()
            connection?.close()
        }


        return rows
    }

    fun dropTable(tableName: String, ifExists: Boolean) {
        val sqlDrop = "DROP TABLE ${if (ifExists) "IF EXISTS" else ""} $tableName;"

        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null
        try {
            connection = dataSource.connection
            preparedStatement = connection.prepareStatement(sqlDrop)
            preparedStatement.executeUpdate()
        } catch (e: Throwable) {
            logger.error("Error inside Clickhouse occurred: ", e)
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    private fun PreparedStatement.setColumn(column : DbColumn, dbIndex: Int) {
        when (column.type) {
            DbColumnType.DbString -> {
                this.setString(dbIndex, column.elements[0])
            }
            DbColumnType.DbArrayString -> {
                this.setArray(dbIndex,
                        connection.createArrayOf("String", column.elements.toTypedArray()))
            }
            DbColumnType.DbDate -> {
                logger.error("Default field got in insert. Scheme violation.")
            }
            DbColumnType.DbInt -> {
                this.setInt(dbIndex, column.elements[0].toInt())
            }
        }
    }

    private fun ResultSet.getColumn(column : DbColumnHeader) : DbColumn {
        when (column.type) {
            DbColumnType.DbDate -> {
                logger.error("Default field got in load. Scheme violation.")
                return DbColumn("", emptyList(), DbColumnType.DbArrayString)
            }
            DbColumnType.DbArrayString -> {
                @Suppress("UNCHECKED_CAST")
                val resultArray = this.getArray(column.name).array as Array<String>
                return DbColumn(column.name, resultArray.toList(), DbColumnType.DbArrayString)
            }
            DbColumnType.DbString -> {
                val result = this.getString(column.name)
                return DbColumn(column.name, listOf(result), DbColumnType.DbString)
            }
            DbColumnType.DbInt -> {
                val result = this.getInt(column.name)
                return DbColumn(column.name, listOf(result.toString()), DbColumnType.DbInt)
            }
        }
    }
}
