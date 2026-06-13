import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import edu.farmingdale.DatabaseConfig
import java.sql.Connection

object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource

    fun init(jdbcUrl: String, username: String, pass: String) {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = pass
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 25
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        dataSource = HikariDataSource(config)
    }

    fun getConnection(): Connection = dataSource.connection
}