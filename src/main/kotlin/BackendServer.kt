// BackendServer.kt
import edu.farmingdale.DatabaseConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import java.sql.DriverManager

fun main() {
    val jdbcUrl = DatabaseConfig.jdbcUrl
    val username = DatabaseConfig.username
    val password = DatabaseConfig.password


    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) { gson() }

        // Create table once at startup
//        DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
//            conn.createStatement().use { stmt ->
//                stmt.execute("""
//                    CREATE TABLE IF NOT EXISTS users (
//                        id SERIAL PRIMARY KEY,
//                        email TEXT UNIQUE NOT NULL,
//                        password_hash TEXT NOT NULL,
//                        tier TEXT DEFAULT 'free'
//                    )
//                """.trimIndent())
//            }
//
//
//        }

        routing {
            post("/register") {
                val params = call.receiveParameters()
                val email = params["email"] ?: ""
                val pass = params["password"] ?: ""

                DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                    val stmt = conn.prepareStatement(
                        "INSERT INTO users (email, password_hash) VALUES (?, ?)"
                    )
                    stmt.setString(1, email)
                    stmt.setString(2, pass) // hash in production!
                    val inserted = stmt.executeUpdate() > 0
                    call.respond(mapOf("success" to inserted))
                }
            }

            post("/login") {
                val params = call.receiveParameters()
                val email = params["email"] ?: ""
                val pass = params["password"] ?: ""

                DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                    val stmt = conn.prepareStatement(
                        "SELECT tier FROM users WHERE email=? AND password_hash=?"
                    )
                    stmt.setString(1, email)
                    stmt.setString(2, pass)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        call.respond(mapOf("tier" to rs.getString("tier")))
                    } else {
                        call.respond(mapOf("tier" to null))
                    }
                }
            }

            get("/users") {
                DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                    val stmt = conn.prepareStatement("SELECT id, email, tier FROM users")
                    val rs = stmt.executeQuery()

                    val users = mutableListOf<Map<String, Any?>>()
                    while (rs.next()) {
                        users.add(
                            mapOf(
                                "id" to rs.getInt("id"),
                                "email" to rs.getString("email"),
                                "tier" to rs.getString("tier")
                            )
                        )
                    }
                    call.respond(users)
                }
            }

        }
    }.start(wait = true)
}