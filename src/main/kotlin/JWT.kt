import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import edu.farmingdale.DatabaseConfig.googleKey
import java.util.Date

object JWT {
    const val keySigner =""

    fun generateToken(userID: Int, userEmail: String) : String {

        var algorithm = Algorithm.HMAC256(keySigner)

        try {

            return JWT.create()
                .withIssuer("Eyedentify")
                .withIssuedAt(Date())
                .withSubject(userID.toString())
                .withSubject(userEmail)
                .sign(algorithm)

        }

    }

}