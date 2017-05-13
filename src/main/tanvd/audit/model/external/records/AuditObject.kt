package tanvd.audit.model.external.records

import tanvd.audit.model.external.types.objects.ObjectType
import tanvd.audit.model.external.types.objects.StateType

/**
 * External representation of state of AuditObject
 */
data class ObjectState(val stateList: Map<StateType<*>, String>) {
    fun getState(stateType: StateType<*>): String? {
        return stateList[stateType]
    }
}


/**
 * External representation of loaded audit object with rich type informations.
 */
data class AuditObject(val type: ObjectType<*>, val obj: Any?, val state: ObjectState) {
    fun isObjectNotFound(): Boolean {
        return type.useDeserialization && obj == null
    }
}