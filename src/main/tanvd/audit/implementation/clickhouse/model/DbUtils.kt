package tanvd.audit.implementation.clickhouse.model

import tanvd.audit.model.external.types.information.InformationType
import java.util.*


/**
 * Row of Clickhouse DB.
 */
internal data class DbRow(val columns: List<DbColumn> = ArrayList()) {

    fun toStringHeader(): String {
        return columns.joinToString { it.name }
    }

    fun toPlaceholders(): String {
        return columns.joinToString { "?" }
    }

    fun toValues(): String {
        return columns.joinToString { it.toValues() }
    }

}

/**
 * Column of Clickhouse DB.
 */
internal data class DbColumn(val name: String, val elements: List<String>, val type: DbColumnType) {
    constructor(header: DbColumnHeader, elements: List<String>) : this(header.name, elements, header.type)
    constructor(header: DbColumnHeader, vararg elements: String) : this(header.name, elements.toList(), header.type)

    fun toValues(): String {
        return if (type.isArray) {
            elements.joinToString(prefix = "[", postfix = "]") { valueToSQL(it) }
        } else {
            valueToSQL(elements[0])
        }
    }

    private fun valueToSQL(value: String): String {
        return when (type) {
            DbColumnType.DbString -> {
                "\'" + value + "\'"
            }
            DbColumnType.DbArrayString -> {
                "\'" + value + "\'"
            }
            DbColumnType.DbArrayBoolean -> {
                if (value.toBoolean()) "1" else "0"
            }
            DbColumnType.DbBoolean -> {
                if (value.toBoolean()) "1" else "0"
            }
            DbColumnType.DbDate -> {
                "\'" + value + "\'"
            }
            DbColumnType.DbArrayDate -> {
                "\'" + value + "\'"
            }
            else -> {
                value
            }

        }
    }
}

/**
 * Clickhouse column type.
 * ToString returns appropriate for db string representation of ColumnType
 */
internal enum class DbColumnType {
    DbDate {
        override val isArray = false

        override fun toString(): String {
            return "Date"
        }
    },
    DbArrayDate {
        override val isArray = true

        override fun toString(): String {
            return "Array(Date)"
        }
    },
    DbULong {
        override val isArray = false

        override fun toString(): String {
            return "UInt64"
        }
    },
    DbArrayULong {
        override val isArray = true

        override fun toString(): String {
            return "Array(UInt64)"
        }
    },
    DbBoolean {
        override val isArray = false

        override fun toString(): String {
            return "UInt8"
        }
    },
    DbArrayBoolean {
        override val isArray = true

        override fun toString(): String {
            return "Array(UInt8)"
        }
    },
    DbString {
        override val isArray = false

        override fun toString(): String {
            return "String"
        }
    },
    DbArrayString {
        override val isArray = true

        override fun toString(): String {
            return "Array(String)"
        }
    },
    DbLong {
        override val isArray = false

        override fun toString(): String {
            return "Int64"
        }
    },
    DbArrayLong {
        override val isArray = true

        override fun toString(): String {
            return "Array(Int64)"
        }
    };

    abstract val isArray: Boolean

}


/**
 * Header for Clickhouse DB
 */
internal data class DbTableHeader(val columnsHeader: List<DbColumnHeader>) {
    /** Returns string definition of TableHeader -- columnFirstName, columnSecondName, ... **/
    fun toDefString(): String {
        return columnsHeader.joinToString { it.toDefString() }
    }

    fun toPlaceholders(): String {
        return columnsHeader.joinToString { "?" }
    }
}

/**
 * Header for Clickhouse Column
 */
internal data class DbColumnHeader(val name: String, val type: DbColumnType) {

    /** Returns string definition of ColumnHeader -- name **/
    fun toDefString(): String {
        return name
    }
}

internal fun InformationType<*>.toDbColumnHeader(): DbColumnHeader {
    return DbColumnHeader(this.code, this.toDbColumnType())
}

internal fun String.toSqlDate(): java.sql.Date {
    val date = getDateFormat().parse(this)
    val currentServerTime = date.time + ClickhouseConfig.timeZone.rawOffset
    return java.sql.Date(currentServerTime)
}

internal fun java.sql.Date.toStringFromDb(): String {
    val serverTime = this.time - ClickhouseConfig.timeZone.rawOffset
    return getDateFormat().format(java.util.Date(serverTime))
}

internal fun java.util.Date.toStringSQL(): String {
    val utcTime = this.time
    val serverTime = utcTime + ClickhouseConfig.timeZone.rawOffset
    return "'" + getDateFormat().format(Date(serverTime)) + "'"
}