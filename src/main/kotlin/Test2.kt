import com.darkorbit.*
import com.github.m9w.Core
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.feature.Inject
import com.github.m9w.feature.OnPackage
import com.github.m9w.feature.Repeat
import com.github.m9w.util.waitMs
import com.github.m9w.util.waitOnPackage


object C {
    @Inject
    private lateinit var n: NetworkLayer

    @OnPackage
    suspend fun a(t: AddBoxCommand): String {
        println("A event $t")
        val x = waitOnPackage<AddOreCommand>(setOf(AddOreCommand::class), timeout = 3000)
        println("X event $x")
        waitMs(1500)
        println("T event")
        return "ok"
    }

    @Repeat(1000)
    suspend fun b(): Int {
        println("5000: "+System.currentTimeMillis())
        val x = waitOnPackage<AddOreCommand>(setOf(AddOreCommand::class), timeout = 3000)

        println("5000: "+System.currentTimeMillis())
        return 5000
    }

    @Repeat(1000)
    suspend fun c(): Int {
        println("1000: "+System.currentTimeMillis())
        waitMs(1000)
        println("2000: "+System.currentTimeMillis())
        waitMs(1000)
        println("3000: "+System.currentTimeMillis())
        waitMs(1000)
        println("4000: "+System.currentTimeMillis())
        waitMs(1000)
        println("5000: "+System.currentTimeMillis())
        waitMs(1000)
        println("6000: "+System.currentTimeMillis())
        waitMs(1000)
        println("7000: "+System.currentTimeMillis())
        waitMs(1000)
        println("8000: "+System.currentTimeMillis())
        waitMs(1000)
        println("9000: "+System.currentTimeMillis())
        waitMs(1000)
        println("10000: "+System.currentTimeMillis())
        return -1
    }

    @OnPackage
    fun d(t: AddBoxCommand) {
        //some logic
    }
}

fun main() {
    Core(AuthenticationProvider.static(0,"",""), C)
}
