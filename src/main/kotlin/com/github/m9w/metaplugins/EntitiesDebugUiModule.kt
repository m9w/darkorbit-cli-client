package com.github.m9w.metaplugins

import com.darkorbit.POIType
import com.darkorbit.ShapeType
import com.darkorbit.ShipInitializationCommand
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.metaplugins.game.PositionImpl
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.metaplugins.game.entities.*
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
    private val pathTracer: PathTracerModule by context
    private val moveModule: MoveModule by context
    private val entities: EntitiesModule by context
    private val map: MapModule by context
    private var pointerEntity: EntityImpl? = null
    private var copy = HashSet<EntityImpl>()
    private var path = listOf<Pair<Int, Int>>()

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        background = Color.black

        g.color = Color.gray
        g.drawString("Map ${map.map.name}", 5, 15)

        entities.hero.windowPosition.let { (x, y) ->
            g.drawLine(x, 0, x, height)
            g.drawLine(0, y, width, y)
        }

        if (pointerEntity != null)
           pointerEntity.toString().split('\n').filter { it.isNotEmpty() }
               .forEachIndexed { i, s -> g.drawString(s, 5, 30 + i * 15) }

        try { copy.addAll(entities.values) } catch (_: ConcurrentModificationException) { return }
        copy.filter { it is PoiImpl }.map { it as PoiImpl }.forEach { g.drawPoi(it) }
        copy.filter { it is JumpgateImpl }.map { it as JumpgateImpl }.forEach { g.drawGate(it) }
        copy.filter { it is AssetImpl }.map { it as AssetImpl }.forEach { g.drawAsset(it) }
        copy.filter { it is ShipImpl }.map { it as ShipImpl }.forEach { g.drawShip(it) }
        copy.filter { it is BoxImpl }.map { it as BoxImpl }.forEach { g.drawBox(it) }
        copy.clear()
        g.color = Color.cyan
        if (entities.hero.isMoving && moveModule.nextPoints.size > 1) moveModule.nextPoints.drawLine(g)
        g.color = Color.yellow
        if (path.size > 1) (mutableListOf<Pair<Int, Int>>().apply { add(entities.hero.position) } + path).drawLine(g)
    }

    private fun List<Pair<Int,Int>>.drawLine(g: Graphics) = zipWithNext { a, b -> a.windowPosition to b.windowPosition }.forEach { (a, b) -> g.drawLine(a.x, a.y, b.x, b.y) }

    fun Graphics.drawShip(ship: ShipImpl) {
        ship.windowPosition.let { (x, y) ->
            if (ship.isMoving) {
                color = Color.cyan
                val (x2, y2) = ship.direction.windowPosition
                drawLine(x, y, x2, y2)
            }
            color = if (ship is HeroShip) Color.white else if(ship.isSafe) Color.blue else Color.red
            if (ship is HeroShip) fillRect(x-2, y-2, 5, 5)
            else drawRect(x-2, y-2, 4, 4)
        }
    }

    fun Graphics.drawGate(gate: JumpgateImpl) {
        color = Color.gray
        val (x, y) = gate.windowPosition
        drawOval(x-5, y-5, 11, 11)
        if (gate.initiated) {
            color = Color.cyan
            fillOval(x-4, y-4, 9, 9)
        } else if (gate.canInvoke()) {
            color = Color.yellow
            drawOval(x-4, y-4, 9, 9)
        }
    }

    fun Graphics.drawAsset(asset: AssetImpl) {
        color = if (asset.isSafe) Color.green else Color.red
        val (x, y) = asset.windowPosition
        fillOval(x-3, y-3, 7, 7)
        if (asset.isMoving) {
            color = Color.cyan
            val (x2, y2) = asset.direction.windowPosition
            drawLine(x, y, x2, y2)
        }
        if (asset.canInvoke()) {
            color = Color.yellow
            drawOval(x-4, y-4, 9, 9)
        }
    }

    fun Graphics.drawBox(asset: BoxImpl) {
        if (asset.type == BoxImpl.Type.MINE) drawMine(asset)
        else if (asset.type == BoxImpl.Type.BOX || asset.type == BoxImpl.Type.ORE) {
            color = Color.yellow
            val (x, y) = asset.windowPosition
            if (asset.canInvoke()) fillRect(x-2, y-2, 4, 4)
            else drawRect(x-2, y-2, 4, 4)
        }
    }

    fun Graphics.drawMine(asset: BoxImpl) {
        color = Color.pink
        val (x, y) = asset.windowPosition
        fillRect(x-2, y-2, 4, 4)
    }

    fun Graphics.drawPoi(poi: PoiImpl) {
        color = if (poi.type == POIType.NO_ACCESS) Color.gray else Color.magenta
        when (poi.shapeType) {
            ShapeType.CIRCLE -> poi.windowPosition.let { (x, y) ->
                (poi.radius.xWindow to poi.radius.yWindow).let { (xr, yr) -> drawOval(x - xr, y - yr, 2*xr, 2*yr) }
            }
            ShapeType.RECTANGLE,
            ShapeType.POLYGON -> poi.cords.map { it.windowPosition }.let { cords ->
                drawPolygon(cords.map { it.first }.toIntArray(), cords.map { it.second }.toIntArray(), cords.size)
            }
        }
    }

    private val Int.xWindow get() = (this.toDouble() / map.map.width * width).toInt()
    private val Int.yWindow get() = (this.toDouble() / map.map.height * height).toInt()
    private val Pair<Int, Int>.windowPosition: Pair<Int, Int> get() = first.xWindow to second.yWindow
    private val PositionImpl.windowPosition: Pair<Int, Int> get() = position.windowPosition

    @OnPackage
    private fun init(init: ShipInitializationCommand) {
        val scale = 4
        preferredSize = Dimension(210*scale, 131*scale)
        SwingUtilities.invokeLater { JFrame("Entities canvas").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE; contentPane.add(this@EntitiesDebugUiModule)
            pack(); setLocationRelativeTo(null); isVisible = true
        } }

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val pointer = e.point.mapPosition
                pointerEntity = entities.values.filter { it !is PoiImpl }.minByOrNull { it.distanceTo(pointer) }
                    ?.takeIf { it.distanceTo(pointer) < 125 }
                    ?: entities.values.firstOrNull { it is PoiImpl && it.containsPoint(pointer.position)}
                path = pathTracer.traceTo(pointer.position)
            }
        })

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == 1) entities.hero.moveTo(e.point.mapPosition.position)
                if (e.button == 3) pointerEntity?.invoke()
            }
        })
        Thread(this, "UI").apply { isDaemon = true }.start()
    }

    private val Point.mapPosition: PositionImpl get() = PositionImpl((x.toDouble() / width * map.map.width).toInt(), (y.toDouble() / height * map.map.height).toInt())

    override fun run() { while (true) { repaint(); Thread.sleep(1000/60) } }
}
