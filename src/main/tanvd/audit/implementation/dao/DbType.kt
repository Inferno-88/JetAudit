package tanvd.audit.implementation.dao

import ru.yandex.clickhouse.ClickHouseDataSource
import ru.yandex.clickhouse.settings.ClickHouseProperties
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouseImpl
import javax.sql.DataSource

enum class DbType {
    /**
     * Default Dao for Clickhouse
     */
    Clickhouse {
        override fun getDao(dataSource: DataSource): AuditDao {
            return AuditDaoClickhouseImpl(dataSource)
        }

        override fun getDao(connectionUrl: String, username: String, password: String): AuditDao {
            val properties = ClickHouseProperties()
            properties.user = username
            properties.password = password
            val dataSource = ClickHouseDataSource(connectionUrl, properties)
            return AuditDaoClickhouseImpl(dataSource)
        }
    };

    /**
     * Get DAO by DataSource
     */
    internal abstract fun getDao(dataSource: DataSource): AuditDao

    /**
     * Get DAO by connection properties
     */
    internal abstract fun getDao(connectionUrl: String, username: String, password: String): AuditDao

}