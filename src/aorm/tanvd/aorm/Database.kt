package tanvd.aorm

import ru.yandex.clickhouse.ClickHouseDataSource
import ru.yandex.clickhouse.settings.ClickHouseProperties
import java.sql.Connection
import javax.sql.DataSource

abstract class Database {
    abstract val url: String
    abstract val password: String
    abstract val user: String

    abstract val useSsl: Boolean
    abstract val sslCertPath: String
    abstract val sslVerifyMode: String

    val dataSource: DataSource by lazy {
        val properties = ClickHouseProperties()
        properties.user = user
        properties.password = password
        ClickHouseDataSource(url, properties)
    }

    fun <T>withConnection(body: Connection.() -> T) : T {
        return dataSource.connection.use {
            it.body()
        }
    }

    fun execute(sql: String) {
        withConnection {
            prepareStatement(sql).use {
                it.execute()
            }
        }
    }
}