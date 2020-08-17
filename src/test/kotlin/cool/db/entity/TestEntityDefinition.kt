package cool.db.entity

import org.jetbrains.exposed.sql.Column
import java.math.BigDecimal
import java.math.BigDecimal.valueOf
import java.util.*

const val defaultOptionalValue = "Optional string"

data class TestEntity(
    val id: UUID = UUID.randomUUID(),
    val testStringField: String,
    val optionalTestStringField: String = defaultOptionalValue,
    val testEmbeddableTestClass: EmbeddableTestClass = EmbeddableTestClass(
        "firstFields",
        valueOf(0, 4)
    )
) {
    val missingProperty: String = "missingProperty"

    object TestTable : DataEntityIdTable<UUID, TestEntity>("test_table", TestEntity::class) {
        override val id: Column<UUID> = uuid("id").bindProperty(TestEntity::id)
        val testStringField = varchar("testStringField", 200).bindProperty(TestEntity::testStringField)
        val missingConstructorParameterTestStringColumn =
            varchar("missingConstructorParameterTestStringColumn", 200).default(defaultOptionalValue)

        object testEmbbed : Embeddable<TestEntity, EmbeddableTestClass>(
            TestEntity::testEmbeddableTestClass
        ) {
            val firstField = TestTable.varchar("first_embedded_field", 200)
                .bindProperty(EmbeddableTestClass::firstField)
            val secondField = TestTable.decimal("second_embedded_field", 19, 4)
                .bindProperty(EmbeddableTestClass::secondField)
        }
    }
}


data class EmbeddableTestClass(val firstField: String, val secondField: BigDecimal)




