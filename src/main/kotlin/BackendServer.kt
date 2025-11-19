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
import io.ktor.http.content.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import java.util.Base64
import com.google.gson.JsonParser
import io.ktor.http.*
import org.mindrot.jbcrypt.BCrypt
import java.sql.DriverManager


fun main() {
    val jdbcUrl = DatabaseConfig.jdbcUrl
    val username = DatabaseConfig.username
    val password = DatabaseConfig.password
    val apiKey = DatabaseConfig.googleKey





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

        val client = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                gson()
            }
        }

        suspend fun identifyWithGoogleVision(imageBytes: ByteArray): Map<String, String> {
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)
            val jsonRequest = """
        {
          "requests": [
            {
              "image": { "content": "$base64Image" },
              "features": [
                { "type": "LABEL_DETECTION", "maxResults": 5 }
              ]
            }
          ]
        }
    """.trimIndent()

            val response: HttpResponse = client.post(
                "https://vision.googleapis.com/v1/images:annotate?key=$apiKey"
            ) {
                header(HttpHeaders.ContentType, "application/json")
                setBody(jsonRequest)
            }

            val responseBody = response.bodyAsText()
            println("Raw Google Vision Response: $responseBody") // log the raw response

            val gsonObj = com.google.gson.JsonParser.parseString(responseBody).asJsonObject

            val responses = gsonObj["responses"]?.asJsonArray
            if (responses == null || responses.size() == 0) {
                println("Warning: 'responses' is missing or empty")
                return mapOf(
                    "name" to "Unknown",
                    "confidence" to "0%",
                    "description" to "No labels returned by Google Vision API"
                )
            }

            val labelAnnotations = responses[0].asJsonObject["labelAnnotations"]?.asJsonArray
            if (labelAnnotations == null || labelAnnotations.size() == 0) {
                println("Warning: 'labelAnnotations' is missing or empty")
                return mapOf(
                    "name" to "Unknown",
                    "confidence" to "0%",
                    "description" to "No labels returned by Google Vision API"
                )
            }

            val label = labelAnnotations[0].asJsonObject
            val name = label["description"]?.asString ?: "Unknown"
            val score = (label["score"]?.asFloat?.times(100)?.toInt()) ?: 0

            return mapOf(
                "name" to name,
                "confidence" to "$score%",
                "description" to "Identified by Google Vision API"
            )
        }




        routing {



            post("/register") {
                val params = call.receiveParameters()
                val email = params["email"] ?: ""
                val pass = params["password"] ?: ""

                val hashedPass = BCrypt.hashpw(pass, BCrypt.gensalt())

                DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                    val stmt = conn.prepareStatement(
                        "INSERT INTO users (email, password_hash) VALUES (?, ?)"
                    )
                    stmt.setString(1, email)
                    stmt.setString(2, hashedPass)
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
                        "SELECT id, tier, password_hash FROM users WHERE email=?"
                    )
                    stmt.setString(1, email)
                    val rs = stmt.executeQuery()

                    if (rs.next()) {
                        val hashedPassword = rs.getString("password_hash")
                        if (BCrypt.checkpw(pass, hashedPassword)) {
                            // Password matches
                            call.respond(
                                mapOf(
                                    "id" to rs.getInt("id"),
                                    "tier" to rs.getString("tier")
                                )
                            )
                        } else {
                            // Password incorrect
                            call.respond(mapOf("id" to null, "tier" to null))
                        }
                    } else {
                        // User not found
                        call.respond(mapOf("id" to null, "tier" to null))
                    }
                }
            }


            post("/identify") {
                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        imageBytes = part.streamProvider().readBytes()
                    }
                    part.dispose()
                }
                if (imageBytes == null) {
                    println("No image received in /identify request")
                    call.respond(mapOf("error" to "No image provided"))
                    return@post
                }
                val result = identifyWithGoogleVision(imageBytes!!)
                println(
                    """
                |=== Google Vision Result ===
                |Name: ${result["name"]}
                |Confidence: ${result["confidence"]}
                |Description: ${result["description"]}
                |===========================
                """.trimMargin()
                        )

                call.respond(result)
            }


        }
    }.start(wait = true)






}


