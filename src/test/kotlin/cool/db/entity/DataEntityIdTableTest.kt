package cool.db.entity

import cool.db.RepositoryTest
import cool.db.entity.TestEntity.TestTable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*


internal class DataEntityIdTableShould : RepositoryTest() {
    @Test
    fun `upsert entity and return it from database`() {
        val entity = TestEntity(testStringField = "Test field value")

        inTestTransaction {
            TestTable.insert {
                saveStatement(entity, it)
            }
        }

        val entityFromDb = inTestTransaction {
            TestTable.select { TestTable.id eq entity.id }.map { TestTable.dataEntity(it) }.single()
        }

        entityFromDb.id shouldBe entity.id
        entityFromDb.testStringField shouldBe entity.testStringField
        entityFromDb.optionalTestStringField shouldBe entity.optionalTestStringField
        entityFromDb.testEmbeddableTestClass shouldBe entity.testEmbeddableTestClass
    }

    @Test
    fun `throw if get entity with column bound to missing constructor parameter`() {
        val entity = TestEntity(testStringField = "Test field value")

        val wrongTestTable: DataEntityIdTable<UUID, TestEntity> =
            createTableWithColumnBoundToMissingConstructorParam()


        inTestTransaction {
            wrongTestTable.insert {
                saveStatement(entity, it)
            }
        }

        val error = shouldThrow<MissingBoundConstructor> {
            inTestTransaction {
                wrongTestTable.selectAll().map { wrongTestTable.dataEntity(it) }.single()
            }
        }

        error.message shouldContain "missingProperty=kotlin.String"
    }

    private fun createTableWithColumnBoundToMissingConstructorParam(): DataEntityIdTable<UUID, TestEntity> {
        val wrongTestTable = object : DataEntityIdTable<UUID, TestEntity>("wrongTestTable", TestEntity::class) {
            override val id = uuid("id").bindProperty(
                TestEntity::id
            )
            val missingProperty = varchar("testStringField", 200).bindProperty(TestEntity::missingProperty)
        }
        inTestTransaction {
            SchemaUtils.drop(wrongTestTable)
            SchemaUtils.create(wrongTestTable)
        }
        return wrongTestTable
    }


    @Test
    fun `throw if binding same property twice`() {
        val columnName = "test_string_field"
        val differentColumnName = "test_string_field2"
        val error = shouldThrow<java.lang.IllegalStateException> {
            object : DataEntityIdTable<UUID, TestEntity>("wrongTestTable", TestEntity::class) {
                override val id: Column<UUID> = uuid("id").bindProperty(TestEntity::id)
                val column1 = varchar(columnName, 200).bindProperty(TestEntity::testStringField)
                val column2 =
                    varchar(differentColumnName, 200).bindProperty(TestEntity::testStringField)
            }
        }
        error.message shouldContain "property"
        error.message shouldContain TestEntity::testStringField.name
        error.message shouldContain "already present for column"
        error.message shouldContain columnName
    }

    @Test
    fun `throw if binding column twice to different properties`() {
        val columnName = "test_string_field"

        val error = shouldThrow<DuplicateColumnBinding> {
            object : DataEntityIdTable<UUID, TestEntity>("wrongTestTable", TestEntity::class) {
                override val id = uuid("id").bindProperty(
                    TestEntity::id
                )
                val column1 = varchar(columnName, 200)
                    .bindProperty(TestEntity::testStringField)
                val column2 = column1
                    .bindProperty(TestEntity::optionalTestStringField)
            }
        }

        error.message shouldContain "column"
        error.message shouldContain columnName
        error.message shouldContain "already mapped to property"
        error.message shouldContain TestEntity::testStringField.name
    }

    @BeforeEach
    fun init() {
        inTestTransaction {
            SchemaUtils.create(TestTable)
        }
    }

    @AfterEach
    fun cleanUp() {
        inTestTransaction {
            SchemaUtils.drop(TestTable)
        }
    }
}
