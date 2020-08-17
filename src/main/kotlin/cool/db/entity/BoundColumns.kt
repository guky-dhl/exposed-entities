package cool.db.entity

import cool.db.first
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import kotlin.reflect.*
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

internal class BoundColumn {
    private var column: Expression<*>? = null
    private var embeddable: DataEntityIdTable.Embeddable<*, *>? = null

    constructor(column: Expression<*>) {
        this.column = column
    }

    constructor(embeddable: DataEntityIdTable.Embeddable<*, *>) {
        this.embeddable = embeddable
    }

    val expression
        get() = column ?: embeddable

    fun isEmbeddable() = embeddable != null
    fun isColumn() = column != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BoundColumn

        if (column?.hashCode() != other.column.hashCode()) return false
        if (embeddable != other.embeddable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = column?.hashCode() ?: 0
        result = 31 * result + (embeddable?.hashCode() ?: 0)
        return result
    }
}

internal class BoundColumns<DE : Any>(private val type: KClass<DE>, private val table: ColumnSet? = null) {
    private val boundProperties: MutableMap<BoundColumn, BoundProperty<*, *>> = mutableMapOf()
    val deserializer = ColumnDeserializer()
    val serializer = ColumnSerializer()

    private val constructor by lazy {
        BoundMatchingConstructor(type, boundProperties.values)
    }

    val embeddedColumnsInit by lazy {
        table?.let {
            table::class.nestedClasses.filter { it.isSubclassOf(DataEntityIdTable.Embeddable::class) }
                .forEach {
                    val embeddable = it.objectInstance as DataEntityIdTable.Embeddable<*, *>
                    val boundProperty =
                        DataEntityIdTable.Embeddable<*, *>::boundProperty
                    boundProperties[BoundColumn(embeddable)] = boundProperty.call(embeddable)
                }
        }
        true
    }

    fun columnByFieldName(fieldName: String): Column<*> {
        check(fieldName.last() != '.') { IllegalArgumentException("Field name can't end with [.]") }
        return columnByFieldName(this, fieldName)
    }

    private tailrec fun columnByFieldName(columns: BoundColumns<*>, fieldName: String): Column<*> {
        fun findBoundColumn(name: String): BoundColumn {
            return columns.boundProperties.entries
                .filter { it.value.name == name }
                .map {
                    it.key
                }
                .single()
        }

        fun leafColumn(fieldName: String): Column<*> {
            val column = findBoundColumn(fieldName)
            check(column.isColumn())
            {
                IllegalArgumentException(
                    "columnByFieldName Expected to find column " +
                            "but found embeddable for part [$fieldName]"
                )
            }
            return column.expression as Column<*>
        }

        fun boundEmbeddable(fieldName: String) =
            (findBoundColumn(fieldName.substringBefore(".")).expression as DataEntityIdTable.Embeddable<*, *>)
                .boundColumns



        if (fieldName.contains(".").not()) {
            return leafColumn(fieldName)
        }

        return columnByFieldName(boundEmbeddable(fieldName), fieldName.substringAfter("."))
    }


    operator fun set(column: Expression<*>, property: BoundProperty<*, *>) {
        check(boundProperties.values.contains(property).not()) {
            DuplicatePropertyBinding(property, column, type)
        }
        val columnToBound = BoundColumn(column)

        boundProperties.computeIfPresent(columnToBound) { _, presentBinding ->
            throw DuplicateColumnBinding(presentBinding.name, column, type)
        }

        boundProperties[columnToBound] = property
    }

    inner class ColumnDeserializer {
        fun dataEntity(rs: ResultRow): DE {
            val valuesFromDb = boundProperties.map {
                constructor.findParameterByName(it.value.name) to when (it.key.expression) {
                    is Column<*> -> {
                        rs[it.key.expression as Column<*>]
                    }
                    is BiCompositeColumn<*, *, *> -> {
                        rs[it.key.expression as BiCompositeColumn<*, *, *>]
                    }
                    is DataEntityIdTable.Embeddable<*, *> -> {
                        (it.key.expression as DataEntityIdTable.Embeddable<*, *>).dataEntity(rs)
                    }
                    else -> throw java.lang.IllegalArgumentException("Unknown type of binding")
                }
            }.toMap()
            return constructor(valuesFromDb)
        }
    }

    inner class ColumnSerializer {
        @Suppress("UNCHECKED_CAST")
        fun saveStatement(dataEntity: DE, statement: UpdateBuilder<Any>) {
            boundProperties.forEach {
                when (it.key.expression) {
                    is Column<*> -> {
                        statement[it.key.expression as Column<Any?>] =
                            (it.value as BoundProperty<DE, Any>).call(dataEntity)
                    }
                    is BiCompositeColumn<*, *, *> -> {
                        statement[it.key.expression as BiCompositeColumn<Any, Any, Any?>] =
                            (it.value as BoundProperty<DE, Any>).call(dataEntity)
                    }
                    is DataEntityIdTable.Embeddable<*, *> -> {
                        (it.key.expression as DataEntityIdTable.Embeddable<DE, Any>).saveStatement(
                            dataEntity,
                            statement
                        )
                    }
                    else -> throw IllegalArgumentException("Unnable proccess bound property ${it.key}")
                }
            }
        }
    }
}

class BoundProperty<P : Any, E>(private val property: KProperty1<P, E>, name: String? = null) {
    val name: String

    init {
        property.isAccessible = true
        this.name = name ?: property.name
    }

    val returnType
        get() = property.returnType

    fun call(dataEntity: P): E = property.call(dataEntity)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BoundProperty<*, *>

        if (property != other.property) return false

        return true
    }

    override fun hashCode(): Int {
        return property.hashCode()
    }

    override fun toString(): String {
        return "BoundProperty(property=$property, name=${name})"
    }
}

private class BoundMatchingConstructor<T : Any>(
    private val forType: KClass<T>,
    byProperties: Collection<BoundProperty<*, *>>
) {
    val constructor = matchingConstructor(byProperties)

    operator fun invoke(params: Map<KParameter, Any?>): T {
        constructor.isAccessible = true
        return constructor.callBy(params)
    }

    private fun matchingConstructor(byProperties: Collection<BoundProperty<*, *>>): KFunction<T> {
        val constructorArgs = byProperties.map { it.name to it.returnType }.toMap()

        val constructor = forType.constructors
            .filter { it ->
                enoughRequiredParameters(it, constructorArgs) &&
                        constructorArgs.size <= it.parameters.size
            }.first({ constructorMatchesArguments(it, constructorArgs) }) {
                MissingBoundConstructor(forType, constructorArgs)
            }

        return constructor
    }

    private fun enoughRequiredParameters(
        constructor: KFunction<T>,
        constructorArgs: Map<String, KType>
    ) = requiredParameters(constructor).size <= constructorArgs.size

    private fun requiredParameters(constructor: KFunction<T>) =
        constructor.parameters.filter { !it.isOptional }

    private fun constructorMatchesArguments(
        constructor: KFunction<T>,
        constructorArgs: Map<String, KType>
    ): Boolean {
        val constructorHasAllParametersForBindings = constructorArgs.all(hasParameterForArgument(constructor))

        val allRequiredParametersProvided = constructor.parameters
            .filter(required())
            .all(argumentProvided(constructorArgs))

        return constructorHasAllParametersForBindings && allRequiredParametersProvided
    }

    private fun hasParameterForArgument(constructor: KFunction<T>): (Map.Entry<String, KType>) -> Boolean {
        return { (name, type) ->
            constructor.findParameterByName(name)?.type == type
        }
    }

    private fun argumentProvided(constructorArgs: Map<String, KType>): (KParameter) -> Boolean {
        return { parameter: KParameter -> constructorArgs[parameter.name] != null }
    }

    private fun required() = { parameter: KParameter -> parameter.isOptional.not() }

    fun findParameterByName(name: String) = constructor.findParameterByName(name)!!
}
