package com.github.m9w.metaplugins
import com.darkorbit.Type
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.metaplugins.game.PositionImpl
import com.github.m9w.metaplugins.game.entities.AssetImpl
import com.github.m9w.metaplugins.game.entities.EntityImpl
import com.github.m9w.metaplugins.game.entities.HeroShip
import com.github.m9w.metaplugins.game.entities.JumpgateImpl
import com.github.m9w.metaplugins.game.entities.ShipImpl
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

class EntitiesDebugUiModule : JPanel(), Runnable {
    @Inject private lateinit var entities: EntitiesModule
    @Inject private lateinit var map: MapModule
    private var pointerEntity: EntityImpl? = null

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        background = Color.black
        if (!isInit) return

        g.color = Color.gray
        g.drawString("Map ${map.map.name}", 5, 15)
        g.drawString("${pointerEntity ?: " - "}", 5, 30)

        entities.values.forEach {
            when (it) {
                is ShipImpl -> g.drawShip(it)
                is JumpgateImpl -> g.drawGate(it)
                is AssetImpl -> g.drawAsset(it)
            }
        }
    }

    fun Graphics.drawShip(ship: ShipImpl) {
        color = if (ship is HeroShip) Color.white else if(ship.faction != entities.hero?.faction || ship.diplomacy == Type.AT_WAR) Color.red else Color.blue
        val (x, y) = ship.windowPosition
        drawRect(x-2, y-2, 4, 4)
    }

    fun Graphics.drawGate(gate: JumpgateImpl) {
        color = Color.gray
        val (x, y) = gate.windowPosition
        drawOval(x-5, y-5, 10, 10)
    }

    fun Graphics.drawAsset(asset: AssetImpl) {
        color = Color.green
        val (x, y) = asset.windowPosition
        fillOval(x-3, y-3, 6, 6)
    }

    private val PositionImpl.windowPosition: Pair<Int, Int> get() {
        val (x, y) = position
        return (x.toDouble() / map.map.width * width).toInt() to (y.toDouble() / map.map.height * height).toInt()
    }

    init {
        val scale = 4
        preferredSize = Dimension(210*scale, 131*scale)
        SwingUtilities.invokeLater { JFrame("Entities canvas").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE; contentPane.add(this@EntitiesDebugUiModule)
            pack(); setLocationRelativeTo(null); isVisible = true
        } }

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                if (!isInit) return
                val pointer = e.point.mapPosition
                val entity= entities.values.minByOrNull { it.distanceTo(pointer) }
                pointerEntity = if (entity != null && entity.distanceTo(pointer) < 250) entity else null
            }
        })

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == 1) entities.hero?.moveTo(e.point.mapPosition)
                if (e.button == 3) pointerEntity?.invoke()
            }
        })

        Thread(this, "UI").apply { isDaemon = true }.start()
    }

    private val Point.mapPosition: PositionImpl get() = PositionImpl((x.toDouble() / width * map.map.width).toInt(), (y.toDouble() / height * map.map.height).toInt())

    private val isInit get() = this@EntitiesDebugUiModule::entities.isInitialized && this@EntitiesDebugUiModule::map.isInitialized

    override fun run() { while (true) { repaint(); Thread.sleep(16) } }
}
