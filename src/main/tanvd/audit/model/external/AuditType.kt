package tanvd.audit.model.external

import tanvd.audit.exceptions.UnknownAuditTypeException
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass

interface AuditSerializer<T> {
    fun deserializeBatch(serializedStrings: List<String>): Map<String, T> {
        val deserializedMap: MutableMap<String, T> = HashMap()
        for (string in serializedStrings) {
            deserializedMap.put(string, deserialize(string))
        }
        return deserializedMap
    }

    fun deserialize(serializedString: String): T
    fun serialize(value: T): String

    fun display(value: T): String
}

data class AuditType<T>(val klass: KClass<*>, val code: String, val serializer: AuditSerializer<T>) :
        AuditSerializer<T> by serializer {

    internal companion object TypesResolution {
        private val auditTypes: MutableList<AuditType<Any>> = ArrayList()
        private val auditTypesByClass: MutableMap<KClass<*>, AuditType<Any>> = HashMap()
        private val auditTypesByCode: MutableMap<String, AuditType<Any>> = HashMap()

        /**
         * Resolve KClass to AuditType
         */
        @Throws(UnknownAuditTypeException::class)
        fun resolveType(klass: KClass<*>): AuditType<Any> {
            val auditType = auditTypesByClass[klass]
            if (auditType == null) {
                throw UnknownAuditTypeException("Unknown AuditType requested to resolve by klass --" +
                        " ${klass.qualifiedName}")
            } else {
                return auditType
            }
        }

        /**
         * Resolve code to AuditType
         */
        @Throws(UnknownAuditTypeException::class)
        fun resolveType(code: String): AuditType<Any> {
            val auditType = auditTypesByCode[code]
            if (auditType == null) {
                throw UnknownAuditTypeException("Unknown AuditType requested to resolve by code -- $code")
            } else {
                return auditType
            }
        }

        fun addType(type: AuditType<Any>) {
            auditTypes.add(type)
            auditTypesByClass.put(type.klass, type)
            auditTypesByCode.put(type.code, type)
        }

        fun getTypes(): List<AuditType<Any>> {
            return auditTypes
        }

        fun clearTypes() {
            auditTypes.clear()
            auditTypesByCode.clear()
            auditTypesByCode.clear()
        }
    }
}
