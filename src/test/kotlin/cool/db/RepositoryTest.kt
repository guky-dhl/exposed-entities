package cool.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

abstract class RepositoryTest {
    companion object {
        @JvmStatic
        private val postgres = PostgreSQLContainer<Nothing>().apply {
            this.withPassword("test")
            this.withUsername("test")
            this.withDatabaseName("test")
            this.withExposedPorts(5432)
            this.start()
            this.waitingFor(Wait.forListeningPort())
        }

        private val ds by lazy {
            postgres.waitingFor(Wait.forListeningPort())
            val ds = PGSimpleDataSource()
            ds.user = "test"
            ds.password = "test"
            ds.setURL("jdbc:postgresql://localhost:${postgres.firstMappedPort}/test")
            ds
        }

        private val db by lazy { Database.connect(ds) }
    }

    protected fun <T> inTestTransaction(statement: Transaction.() -> T): T {
        return transaction(db, statement)
    }
}
