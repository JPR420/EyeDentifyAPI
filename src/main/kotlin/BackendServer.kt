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

            val gsonObj = JsonParser.parseString(responseBody).asJsonObject

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

            // Get all labels as a comma-separated string
            val allLabels = labelAnnotations.drop(1).joinToString(", ") { it.asJsonObject["description"].asString }


            val mainLabel = labelAnnotations[0].asJsonObject["description"]?.asString ?: "Unknown"
            val score = (labelAnnotations[0].asJsonObject["score"]?.asFloat ?: 0f) * 100

            return mapOf(
                "name" to mainLabel,
                "confidence" to "%.2f%%".format(score),
                "description" to allLabels
            )
        }




        routing {



            post("/"){

                call.respond("Hello World!")

            }



            post("/register") {
                println("=== /register endpoint called ===")

                val params = call.receiveParameters()
                val email = params["email"] ?: ""
                val pass = params["password"] ?: ""

                println("Register params: email=${params["email"]}, password=${params["password"]?.replace(Regex("."), "*")}")

                val hashedPass = BCrypt.hashpw(pass, BCrypt.gensalt())

                try {
                    DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                        val stmt = conn.prepareStatement(
                            "INSERT INTO users (email, password_hash) VALUES (?, ?)"
                        )
                        stmt.setString(1, email)
                        stmt.setString(2, hashedPass)

                        val inserted = stmt.executeUpdate() > 0
                        call.respond(mapOf("success" to inserted))
                    }

                } catch (e: java.sql.SQLException) {
                    if (e.sqlState == "23505") {
                        call.respond(
                            mapOf(
                                "success" to false,
                                "message" to "Email already exists"
                            )
                        )
                    } else {
                        call.respond(
                            mapOf(
                                "success" to false,
                                "message" to "Database error: ${e.message}"
                            )
                        )
                    }
                }
            }


            post("/login") {
                println("=== /login endpoint called ===")

                val params = call.receiveParameters()
                val email = params["email"] ?: ""
                val pass = params["password"] ?: ""

                println("Login params: email=${params["email"]}, password=${params["password"]?.replace(Regex("."), "*")}")

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
                println("=== /identify endpoint called ===")

                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        println("Received file in /identify, size=${imageBytes?.size}")
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

            post("/saveResult") {
                println("=== /saveResult endpoint called ===")

                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                var objectName = ""
                var confidenceRaw = ""
                var description = ""
                var buyLink: String? = null
                var userId : Int = 0

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            imageBytes = part.streamProvider().readBytes()
                            println("Received file in /saveResult, size=${imageBytes?.size}")

                        }
                        is PartData.FormItem -> {
                            when (part.name) {
                                "object_name" -> objectName = part.value
                                "confidence" -> confidenceRaw = part.value
                                "description" -> description = part.value
                                "buy_link" -> buyLink = part.value
                                "user_id" -> userId = part.value.toInt()
                            }
                        }
                        else -> {
                            // ignore
                        }
                    }
                    part.dispose()
                }

                val confidenceValue: Double = confidenceRaw
                    .replace("%", "")
                    .trim()
                    .toDoubleOrNull() ?: 0.0

                if (imageBytes == null) {
                    println("No image received in /identify request")
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No image provided"))
                    return@post
                }

                DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                    val stmt = conn.prepareStatement("""
                        INSERT INTO capture_results 
                        (user_id, image_data, object_name, confidence, description, buy_link)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent())
                    stmt.setInt(1, userId)
                    stmt.setBytes(2, imageBytes)
                    stmt.setString(3, objectName)
                    stmt.setDouble(4, confidenceValue)
                    stmt.setString(5, description)
                    stmt.setString(6, buyLink)
                    println("Inserting capture result into database...")
                    stmt.executeUpdate()
                    println("Insert complete")
                }

                call.respond(mapOf("success" to true))
            }



            post("/getUserHistory") {

                println("=== /getUserHistory endpoint called ===")

                //  Get User ID from request
                val params = call.receiveParameters()
                val userIdString = params["user_id"]

                if (userIdString == null) {
                    call.respond(mapOf("error" to "Missing user_id"))
                    return@post
                }

                val userId = userIdString.toInt()
                val historyList = mutableListOf<Map<String, Any?>>()

                try {
                    DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
                       //Select all captures for this user
                        val stmt = conn.prepareStatement("""
                            SELECT object_name, confidence, description, image_data, buy_link 
                            FROM capture_results 
                            WHERE user_id = ? 
                            ORDER BY id DESC
                        """.trimIndent())

                        stmt.setInt(1, userId)
                        val rs = stmt.executeQuery()

                        while (rs.next()) {
                            //  Convert bytes to Base64 string for JSON transfer
                            val imageBytes = rs.getBytes("image_data")
                            val base64Image = if (imageBytes != null) {
                                Base64.getEncoder().encodeToString(imageBytes)
                            } else null

                            val item = mapOf(
                                "name" to rs.getString("object_name"),
                                "confidence" to rs.getDouble("confidence"),
                                "description" to rs.getString("description"),
                                "link" to rs.getString("buy_link"),
                                "image_base64" to base64Image
                            )
                            historyList.add(item)
                        }
                    }

                    println("Found ${historyList.size} items for user $userId")
                    call.respond(historyList)

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }




        }
    }.start(wait = true)






}


