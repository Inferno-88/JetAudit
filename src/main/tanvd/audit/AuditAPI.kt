package tanvd.audit

import org.slf4j.LoggerFactory
import tanvd.aorm.Table
import tanvd.aorm.query.LimitExpression
import tanvd.aorm.query.OrderByExpression
import tanvd.aorm.query.QueryExpression
import tanvd.audit.exceptions.AddExistingAuditTypeException
import tanvd.audit.exceptions.AddExistingInformationTypeException
import tanvd.audit.exceptions.AuditQueueFullException
import tanvd.audit.exceptions.UnknownObjectTypeException
import tanvd.audit.implementation.AuditExecutor
import tanvd.audit.implementation.QueueCommand
import tanvd.audit.implementation.SaveRecords
import tanvd.audit.implementation.clickhouse.AuditDao
import tanvd.audit.implementation.clickhouse.AuditDaoClickhouse
import tanvd.audit.implementation.clickhouse.aorm.AuditTable
import tanvd.audit.model.external.presenters.*
import tanvd.audit.model.external.records.AuditObject
import tanvd.audit.model.external.records.AuditRecord
import tanvd.audit.model.external.records.InformationObject
import tanvd.audit.model.external.records.ObjectState
import tanvd.audit.model.external.types.information.InformationType
import tanvd.audit.model.external.types.objects.ObjectType
import tanvd.audit.model.internal.AuditRecordInternal
import tanvd.audit.utils.PropertyLoader
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.collections.LinkedHashSet

/**
 * Asynchronous saving of entities.
 *
 *
 * Saves ID's of entities in arrays, it's order and also directly saves all primitive types in array.
 *
 * Primitive types: String, Long, Int
 *
 * Also it saves information which saved right to Db primitive types. Use presenters to save specified informations
 * field. Remember, that every audit record contains all information fields. But some of them can be set to default
 * values according to getDefault() method of appropriate presenter.
 *
 *
 * You can configure AuditApi and Clickhouse scheme using properties file.
 *
 * Or use properties object
 *
 * Configuration may include:
 *      #Clickhouse config
 *      Url                    (required if datasource not present),
 *      Username               (required if datasource not present),
 *      Password               (required if datasource not present),
 *      UseSSL                 (default false),
 *      SSLCertPath            (default empty),
 *      SSLVerifyMode          (default empty) (may be strict|none),
 *
 *      MaxTotalHttpThreads            (default 1000)  (max total threads),
 *      MaxPerRouteTotalHttpThreads    (default 500)   (max threads per route),
 *
 *      UseDefaultDDL          (default true),
 *      SocketTimeout          (default 30000) (ms)
 *      ConnectionTimeout      (default 10000) (ms) (timeout of connection to Clickhouse),
 *      KeepAliveTimeout       (default 30000) (ms) (time connection can be safely kept idle),
 *      TimeToLive             (default 60000) (ms) (time to live for one connection in pool unconditionally)
 *      DataTransferTimeout    (default 20000) (ms) (max time for request execution)
 *
 *      MaxIdleConnections     (default 30)          (max idle jdbc connections)
 *      MinIdleConnections     (default 1)           (min idle jdbc connections)
 *      MaxTotalConnections    (default 60)          (max number of jdbc connections)
 *      TimeBetweenEvictionRuns (default 30000) (ms) (time in ms between eviction passes through the pool)
 *
 *      #AuditApi config
 *      CapacityOfQueue        (default 20000 records),
 *      NumberOfWorkers        (default 3 threads),
 *      CapacityOfWorkerBuffer (default 5000 records),
 *      WaitingQueueTime       (default 10) (ms)
 *
 *      #Reserving config
 *      MaxGeneration          (default 15 gen),
 *      ReservePath            (default reserve.txt or ReserveLogger),
 *      S3BucketFailover       (default ClickhouseFailover),
 *      ReserveWriter          (default File) (may be File|Log|S3)
 *
 *      #Clickouse scheme config
 *      AuditTable             (default AuditTable),
 *      DescriptionColumn      (default Description),
 *      DateColumn             (default DateColumn),
 *      TimeStampColumn        (default TimeStampColumn),
 *      VersionColumn          (default VersionColumn),
 *      IdColumn               (default IdColumn),
 *      IsDeletedColumn        (default IsDeletedColumn)
 *      UseIsDeleted           (default false)
 *
 * If properties file or some properties not found default values will be used.
 *
 *
 * Pay attention, that normal work of AuditAPI depends on external persistent context.
 *
 *
 * Pay attention to used in JetAudit replacing strategy. There is no guarantee that if your query
 * will find only old record and will not find new ones old one will not be returned. You can force
 * deleting of old records executing OPTIMIZE FINAL in Clickhouse database directly. In other situations
 * you should change fields only by which records will not be seeked (like service information).
 *
 *
 * You can either use methods which throw exception (to be confident that audit records was saved),
 * or use methods logging exceptions (to be confident that exception will not crash calling thread).
 *
 * In case of method with exception JetAudit guarantee that record will be saved or exception will be thrown.
 * In other case JetAudit tries to save record, but not guarantee that it will be saved in exceptional situations.
 *
 * Timezone notice: all timestamps will be returned in UTC and should be saved in UTC.
 * Timezone notice: all java.utils.Date objects assumed to be UTC (as in docs)
 */
class AuditAPI {

    private var shuttingDown = false

    private val logger = LoggerFactory.getLogger(AuditAPI::class.java)

    internal val auditDao: AuditDao

    internal val executor: AuditExecutor

    internal val auditQueueInternal: BlockingQueue<QueueCommand>

    internal val auditRecordsNotCommitted: ThreadLocal<ArrayList<AuditRecordInternal>>

    internal companion object Config {
        val capacityOfQueue by lazy { PropertyLoader["CapacityOfQueue"]?.toInt() ?: 20000 }
    }

    /**
     * Create AuditApi with default dataSource
     */
    constructor(configPath: String?) {
        if (configPath != null) {
            PropertyLoader.setPropertyFilePath(configPath)
        }

        auditQueueInternal = ArrayBlockingQueue(capacityOfQueue)
        auditRecordsNotCommitted = object : ThreadLocal<ArrayList<AuditRecordInternal>>() {
            override fun initialValue(): ArrayList<AuditRecordInternal>? {
                return ArrayList()
            }
        }

        auditDao = AuditDaoClickhouse()

        executor = AuditExecutor(auditQueueInternal)

        initTable()
        addPrimitiveTypes()
        addServiceInformation()
    }

    constructor(properties: Properties?) {
        if (properties != null) {
            PropertyLoader.setProperties(properties)
        }

        auditQueueInternal = ArrayBlockingQueue(capacityOfQueue)
        auditRecordsNotCommitted = object : ThreadLocal<ArrayList<AuditRecordInternal>>() {
            override fun initialValue(): ArrayList<AuditRecordInternal>? {
                return ArrayList()
            }
        }

        auditDao = AuditDaoClickhouse()

        executor = AuditExecutor(auditQueueInternal)

        initTable()
        addPrimitiveTypes()
        addServiceInformation()
    }


    /**
     * Constructor for test needs
     */
    internal constructor(auditDao: AuditDao, executor: AuditExecutor,
                         auditQueueInternal: BlockingQueue<QueueCommand>,
                         auditRecordsNotCommitted: ThreadLocal<ArrayList<AuditRecordInternal>>,
                         properties: Properties) {

        PropertyLoader.setProperties(properties)

        this.auditRecordsNotCommitted = auditRecordsNotCommitted
        this.auditQueueInternal = auditQueueInternal
        this.auditDao = auditDao
        this.executor = executor
    }

    /**
     * Initializing type system with primitive types
     */
    internal fun addPrimitiveTypes() {
        addObjectType(ObjectType(String::class, StringPresenter))
        addObjectType(ObjectType(Int::class, IntPresenter))
        addObjectType(ObjectType(Long::class, LongPresenter))
    }

    internal fun addServiceInformation() {
        InformationType.addType(IdType)
        InformationType.addType(TimeStampType)
        InformationType.addType(VersionType)
        InformationType.addType(DateType)
        InformationType.addType(IsDeletedType)
    }

    internal fun initTable() {
        if (AuditTable.useIsDeleted) {
            AuditTable.isDeleted
        }
        AuditTable.create()
    }

    /**
     * Add type of audit entity to JetAudit type system
     *
     * In case of ObjectType duplicate
     * @throws AddExistingAuditTypeException
     */
    fun <T : Any> addObjectType(type: ObjectType<T>) {
        @Suppress("UNCHECKED_CAST")
        if (ObjectType.getTypes().contains(type as ObjectType<Any>)) {
            throw AddExistingAuditTypeException("Already existing audit type requested to add -- ${type.klass}")
        }
        auditDao.addTypeInDbModel(type)
        ObjectType.addType(type)
    }

    /**
     * Add type of informations to JetAudit type system
     *
     * In case of InformationType duplicate
     * @throws AddExistingAuditTypeException
     */
    fun <T : Any> addInformationType(type: InformationType<T>) {
        @Suppress("UNCHECKED_CAST")
        if (InformationType.getTypes().contains(type as InformationType<Any>)) {
            throw AddExistingInformationTypeException("Already existing informations type requested to add -- ${type.code}")
        }
        auditDao.addInformationInDbModel(type)
        InformationType.addType(type)
    }

    /**
     * Add audit entry (resolving dependencies) to group of audit records associated with Thread.
     *
     * This method not throwing any exceptions.
     * Unknown types will be ignored and reported with log error.
     *
     * In case of shutting down audit all records will be ignored including partly saved, but not committed
     */
    fun save(vararg objects: Any, information: Set<InformationObject<*>> = emptySet()) {
        if (shuttingDown) {
            return
        }

        val recordObjects = ArrayList<Pair<ObjectType<Any>, ObjectState>>()
        for (o in objects) {
            try {
                val type = ObjectType.resolveType(o::class)
                recordObjects.add(Pair(type, type.serialize(o)))
            } catch (e: UnknownObjectTypeException) {
                logger.error("AuditAPI met unknown ObjectType. Object will be ignored", e)
            }
        }

        auditRecordsNotCommitted.get().add(AuditRecordInternal(recordObjects, LinkedHashSet(information)))
    }


    /**
     * Add audit entry (resolving dependencies) to group of audit records associated with Thread.
     *
     * This method throws exceptions related to AuditApi. Exceptions of Db are ignored anyway.
     *
     * In case of shutting down audit all records will be ignored including partly saved, but not committed
     *
     * @throws UnknownObjectTypeException
     */
    fun saveWithException(vararg objects: Any, information: MutableSet<InformationObject<*>> = HashSet()) {
        if (shuttingDown) {
            return
        }

        val recordObjects = objects.map { o -> ObjectType.resolveType(o::class).let { it to it.serialize(o) } }

        auditRecordsNotCommitted.get().add(AuditRecordInternal(recordObjects, LinkedHashSet(information)))
    }

    /**
     * Commit all audit records associated with Thread.
     *
     * This method not throwing any exceptions.
     */
    fun commit() {
        val listRecords = auditRecordsNotCommitted.get()
        if (listRecords != null) {
            if (listRecords.size > (capacityOfQueue - auditQueueInternal.size)) {
                logger.error("Audit queue full. Records was not committed.")
            } else {
                auditQueueInternal += SaveRecords(listRecords)
                auditRecordsNotCommitted.remove()
            }
        }
    }

    /**
     * Commit all audit records associated with Thread.
     *
     * This method throws exceptions related to AuditApi. Exceptions of Db are ignored anyway.
     *
     * @throws AuditQueueFullException
     */
    fun commitWithExceptions() {
        val listRecords = auditRecordsNotCommitted.get()
        if (listRecords != null) {
            if (listRecords.size > (capacityOfQueue - auditQueueInternal.size)) {
                throw AuditQueueFullException("Audit queue full. Records was not committed.")
            } else {
                auditQueueInternal += SaveRecords(listRecords)
                auditRecordsNotCommitted.remove()
            }
        }
    }

    /**
     * Deletes group of audits associated with Thread.
     */
    fun rollback() {
        auditRecordsNotCommitted.remove()
    }

    /**
     * Load audits containing specified object. Supports paging and ordering.
     *
     * If some entities was not found null instead will be returned
     *
     * Beware that due to use of batching you will have only one real object and a lot of links to it for every value
     *
     * This method not throwing any exceptions.
     */
    fun load(expression: QueryExpression, orderByExpression: OrderByExpression? = null,
             limitExpression: LimitExpression? = null, useBatching: Boolean = true): List<AuditRecord> {
        val auditRecords = try {
            auditDao.loadRecords(expression, orderByExpression, limitExpression)
        } catch (e: UnknownObjectTypeException) {
            logger.error("AuditAPI met unknown ObjectType. Empty list will be returned.", e)
            return emptyList()
        }

        return if (useBatching) {
            deserializeAuditRecordsWithBatching(auditRecords)
        } else {
            deserializeAuditRecords(auditRecords)
        }
    }

    /**
     * Load audits containing specified object. Supports paging and ordering.
     *
     * If some entities was not found null instead will be returned
     *
     * This method throws exceptions related to AuditApi. Exceptions of Db are ignored anyway.
     *
     * @throws UnknownObjectTypeException
     */
    fun loadAuditWithExceptions(expression: QueryExpression, orderByExpression: OrderByExpression? = null,
                                limitExpression: LimitExpression? = null,  useBatching: Boolean = true):
            List<AuditRecord> {
        val auditRecords: List<AuditRecordInternal> = auditDao.loadRecords(expression, orderByExpression, limitExpression)

        return if (useBatching) {
            deserializeAuditRecordsWithBatching(auditRecords)
        } else {
            deserializeAuditRecords(auditRecords)
        }
    }

    /**
     * Count number of rows satisfying expression.
     */
    fun count(expression: QueryExpression): Long {
        return auditDao.countRecords(expression)
    }


    /**
     * Replaces rows with new rows with new version.
     * New version will be assigned automatically
     */
    fun replace(auditRecords: List<AuditRecord>) {
        auditRecordsNotCommitted.get() += auditRecords.map { AuditRecordInternal.createFromRecordWithNewVersion(it) }
    }

    /**
     * Stop audit saving
     *
     * Time measured in ms
     */
    fun stopAudit(timeToWaitWorkers: Long, timeToWaitExecutor: Long = 1000) {
        shuttingDown = true
        executor.stopWorkers(timeToWaitWorkers)
        executor.executorService.shutdownNow()
        executor.executorService.awaitTermination(timeToWaitExecutor, TimeUnit.MILLISECONDS)
    }

    /**
     * Get inner representation of Audit.
     *
     * WARNING: Be EXTREMELY careful with this method.
     * You can erase whole audit data with one call.
     */
    fun getTable(): Table {
        return AuditTable
    }

    /**
     * Deserialize AuditRecordsInternal to AuditRecords using batching deserialization.
     */
    private fun deserializeAuditRecordsWithBatching(auditRecords: List<AuditRecordInternal>): List<AuditRecord> {
        val preparedForBatchDeserialization = auditRecords.flatMap { it.objects }.groupBy { it.first }
                .mapValues { it.value.map { it.second }.distinct() }

        val deserializedMaps = preparedForBatchDeserialization.mapValues {
            if (it.key.useDeserialization)
                it.key.deserializeBatch(it.value)
            else
                emptyMap()
        }

        return auditRecords.map { (objects, information) ->
            AuditRecord(objects.map { (type, state) ->
                if (deserializedMaps[type]?.get(state) == null) {
                    AuditObject(type, null, state)
                } else {
                    deserializedMaps[type]!![state]!!.let { AuditObject(type, it, state) }
                }
            }, information)
        }
    }

    private fun deserializeAuditRecords(auditRecords: List<AuditRecordInternal>): List<AuditRecord> {
        return auditRecords.map {
            AuditRecord(it.objects.map {
                val type = it.first
                if (type.useDeserialization) {
                    AuditObject(type, type.deserialize(it.second), it.second)
                } else {
                    AuditObject(type, null, it.second)
                }
            }, it.information)
        }
    }

}