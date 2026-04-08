import com.darkorbit.KeepAlive
import com.darkorbit.StayinAlive
import com.github.m9w.client.GameEngine
import com.github.m9w.config.accountConfig
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.timePrefix
import com.github.m9w.feature.waitOnPackage

class PingModule {
    private val gameEngine: GameEngine by context
    private var sentKeepAliveTime = System.currentTimeMillis()
    private val pingList = ArrayList<Long>()
    var ping: Double by accountConfig(-1.0, false); private set

    @Repeat(15_000, true)
    private suspend fun sendKeepAlive() {
        if (gameEngine.state.isNotConnected || gameEngine.state == GameEngine.State.STOPPED) return
        waitOnPackage<StayinAlive> (timeout = 15000) {
            sentKeepAliveTime = System.currentTimeMillis()
            gameEngine.send<KeepAlive> { MouseClick = Math.random() < 0.7 }
        }
        pingList.add(System.currentTimeMillis() - sentKeepAliveTime)
    }

    @Repeat(60_000)
    private fun watchdog() {
        ping = if (pingList.isEmpty()) -1.0 else pingList.average().apply { pingList.clear() }
        if (gameEngine.state == GameEngine.State.STOPPED) return
        if (ping != -1.0) return
        println("[$timePrefix] Watchdog restart - connection stuck")
        gameEngine.connect()
    }

    override fun toString(): String = String.format("%.3f", ping)
}
/** @m9w/darkorbit-cli-client
 * YobML1nPGMdZTsyw5DaBCdgjJVTpSEIH3wCe1O/ulDT8qiV2iy++T9qOskl9pmFnwlf6t0acy0sDFZKXaUSqM0OxX5Jm4LEIsPDF
 * 5FYsn42o3ntKHupxAuMLasPdSJBpJO9OMHdkVh91neWGRDxt3RTgTE1w8WiZoEv7Mwv82KJWlaJQwZ2HTNSvN4lW+R11OHtfmGqL
 * DuxBvN8ZNVN7b+e5sUmaT28YGv+IZyDsSrRaY3Ji6j4J2LJPgI9KDijCCr/FgkbD7xeEFcERt+7th3DutkImq87mBLMQI+/ghPvU
 * REHPnLII6n1Fjg/B3RlVBCjhr2dqARgUGQE9cv4r2vgYg81D9+CJbRUaBSg9mRaWx6hNoxOtm4JaNDP0533stcpkJdqqyVFUBBIu
 * 1Fa48BrfK+2Mc2GwGNGkiU4Rcwp07GO/MgaH6S5wZvjYVaUkhO/NKOvUt+UhZsiWGmDkes6nJoabxNJ8rQy2PlsVmBOZxBY7/DAW
 * UAyM5AuH8j3Y9e0JwpydAwZ9z8wCQum3ukh+zW/4NrWpzVJHnnpnJ3bAaCqrdnP6VPuxSlXQirrhL3ySwASLwBqf5iz/RsX7EEcA
 * TPxYY/2cO/gIOtjTAL9ib0jvh4Cnc2IIA8IOiCOV/hi6zTY2nEWeq1mljhc4F50IrKwA2Ne8/87ELtL/OzU=
 * */