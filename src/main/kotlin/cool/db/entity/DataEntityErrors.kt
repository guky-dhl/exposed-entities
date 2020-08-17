package cool.db.entity

import org.jetbrains.exposed.sql.Expression
import kotlin.reflect.KClass
import kotlin.reflect.KType

class MissingBoundConstructor(forType: KClass<*>, constructorArgs: Map<String, KType>) : IllegalStateException(
    "Data entity or embedded type:[${forType.qualifiedName}]" +
            " missing constructor[$constructorArgs] matching bound arguments " +
            "by name and type." +
            " Please create such constructor or override " +
            "[${DataEntityIdTable<*, *>::dataEntity}]"
)

class DuplicatePropertyBinding(
    property: BoundProperty<*, *>,
    column: Expression<*>,
    boundType: Any
) : IllegalStateException(
    "Data entity property[$property] already present " +
            "for column [${column}] in type [$boundType]"
)

class DuplicateColumnBinding(
    propertyName: String,
    column: Expression<*>,
    boundType: KClass<*>
) : IllegalStateException(
    "Data entity column [${column}] already mapped " +
            "to property [$propertyName] in table [$boundType]"
)

