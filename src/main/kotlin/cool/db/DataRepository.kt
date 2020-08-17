package cool.db

import cool.db.entity.DataEntityIdTable
import org.jetbrains.exposed.sql.*
import kotlin.reflect.KClass

abstract class DataRepository<TABLE : DataEntityIdTable<ID, DE>, ID : Any, DE : Any>(
    val table: TABLE,
    val type: KClass<DE>
) {
    fun find(id: ID): DE {
        return table.select { table.id.eq(id) }
            .map { table.dataEntity(it) }
            .first()
    }

    fun findForUpdate(id: ID): DE {
        return table.select { table.id.eq(id) }
            .forUpdate()
            .map { table.dataEntity(it) }
            .first()
    }

    fun findOrNull(id: ID): DE? {
        return table.select { table.id.eq(id) }
            .map { table.dataEntity(it) }
            .singleOrNull()
    }

    fun find(where: SqlExpressionBuilder.() -> Op<Boolean>) = table.select(where).map { table.dataEntity(it) }

    fun all() = table.selectAll().asSequence().map { table.dataEntity(it) }

    open fun save(dataEntity: DE): DE {
        table.insert {
            table.saveStatement(dataEntity, it)
        }
        return dataEntity
    }

    fun delete(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        table.deleteWhere(op = where)
    }
}
