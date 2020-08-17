package cool.db.entity

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType


@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class EntityScopeAware

@EntityScopeAware
abstract class DataEntityIdTable<PK : Any, DE : Any>(
    name: String,
    type: KClass<DE>
) : Table(name) {

    abstract val id: Column<PK>
    override val primaryKey by lazy { PrimaryKey(id) }

    private val boundColumns: BoundColumns<DE> =
        BoundColumns(type, this)

    override val columns: List<Column<*>>
        get() = super.columns.also {
            boundColumns.embeddedColumnsInit
            return it
        }

    fun columnFromFieldName(fieldName: String): Column<*> {
        return boundColumns.columnByFieldName(fieldName)
    }

    open fun dataEntity(rs: ResultRow): DE {
        return boundColumns.deserializer.dataEntity(rs)
    }

    open fun saveStatement(dataEntity: DE, statement: UpdateBuilder<Any>) {
        return boundColumns.serializer.saveStatement(dataEntity, statement)
    }

    protected fun <CT> Column<CT>.bindProperty(property: KProperty1<DE, CT>, name: String? = null): Column<CT> {
        val boundProperty = BoundProperty(property, name)
        boundColumns[this] = boundProperty
        return this
    }

    @EntityScopeAware
    abstract class Embeddable<PARENT_TYPE : Any, EMBEDDED_TYPE : Any>(
        boundToProperty: KProperty1<PARENT_TYPE, EMBEDDED_TYPE>,
        name: String? = null
    ) {
        val boundProperty = BoundProperty(boundToProperty, name)

        private val boundType = boundType()
        internal val boundColumns = BoundColumns(boundType)

        @Suppress("UNCHECKED_CAST")
        private fun boundType(): KClass<EMBEDDED_TYPE> {
            return this::class.supertypes
                .single { it.isSubtypeOf(Embeddable::class.starProjectedType) }
                .arguments[1]
                .type!!.classifier as KClass<EMBEDDED_TYPE>
        }

        protected fun <T : Any?> Column<T>.bindProperty(
            property: KProperty1<EMBEDDED_TYPE, T>,
            name: String? = null
        ): Column<T> {
            boundColumns[this] = BoundProperty(property, name)
            return this
        }

        open fun saveStatement(dataEntity: PARENT_TYPE, statement: UpdateBuilder<Any>) {
            boundColumns.serializer.saveStatement(boundProperty.call(dataEntity), statement)
        }

        open fun dataEntity(rs: ResultRow): EMBEDDED_TYPE {
            return boundColumns.deserializer.dataEntity(rs)
        }
    }
}
