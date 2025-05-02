import com.darkorbit.LoginRequest
import com.darkorbit.LoginResponse
import com.darkorbit.ReadyRequest
import com.darkorbit.VersionCommand
import com.darkorbit.VersionRequest
import com.github.m9w.client.GameClient

fun main(args: Array<String>) {
    GameClient("170.33.12.50", 6022) {
        /** This block call on receiving each package */
        println(it)
        when (it) {
            is VersionCommand -> {
                if(it.equal) send(LoginRequest::class) {
                    userID = 94795731
                    sessionID = args[0]
                    instanceId = 68 // 68 - flash 1396 - unity
                    isMiniClient = true
                }
            }
            is LoginResponse -> {
                send(ReadyRequest::class) { readyType = 1 }
                send(ReadyRequest::class) { readyType = 2 }
            }
        }
    }.send(VersionRequest::class) { version = "e0978404ac77a5751f514f0e07650fa9" }
}