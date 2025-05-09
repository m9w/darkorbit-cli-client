import com.darkorbit.AddBoxCommand
import com.github.m9w.Core
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.feature.OnPackage
import com.github.m9w.feature.Repeat
import com.github.m9w.util.waitMs

object C {
    //val waiter = WaitOnPackage(PetDeactivationCommand::class)

    @OnPackage
    suspend fun c(n: NetworkLayer, t: AddBoxCommand): String {
        //waiter.wait()
        waitMs(100)
        return "ok"
    }

    @Repeat(5000)
    fun q(n: NetworkLayer): Int {
        //println("5000: "+System.currentTimeMillis())
        return 5000
    }

    //should support methods without suspend
    @Repeat(1000)
    suspend fun d(n: NetworkLayer): Int {
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

    //should support methods that return null
    @OnPackage
    fun b(n: NetworkLayer, t: AddBoxCommand) {
        //some logic
    }
}

fun main() {
    Core(AuthenticationProvider.static(0,"",""), C)
}
