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

        try { copy.addAll(entities.values) } catch (_: ConcurrentModificationException) { return }
        copy.filterIsInstance<PoiImpl>().forEach { g.drawPoi(it) }
        copy.filterIsInstance<JumpgateImpl>().forEach { g.drawGate(it) }
        copy.filterIsInstance<AssetImpl>().forEach { g.drawAsset(it) }
        copy.filterIsInstance<ShipImpl>().forEach { g.drawShip(it) }
        copy.filterIsInstance<BoxImpl>().forEach { g.drawBox(it) }
        copy.clear()
        g.color = Color.cyan
        if (entities.hero.isMoving && moveModule.nextPoints.size > 1) g.drawLine(moveModule.nextPoints)
        g.color = Color.yellow
        if (path.size > 1) g.drawLine(listOf(entities.hero.position) + path)
        g.color = Color.gray
        pointerEntity?.let { g.drawText(it.toString(), 5, 30) }
        entities.hero.target?.let { g.drawText(it.toString(), width - 5, 15, true) }
    }

    private fun Graphics.drawLine(line: List<Pair<Int,Int>>) = line.zipWithNext { a, b -> a.windowPosition to b.windowPosition }.forEach { (a, b) -> drawLine(a.x, a.y, b.x, b.y) }

    private fun Graphics.drawText(text: String, x: Int, y: Int, alignRight: Boolean = false) = text.split('\n').filter { it.isNotEmpty() }.forEachIndexed { i, s -> drawString(s, if (alignRight) x-fontMetrics.stringWidth(s) else x, y + i * 15) }

    private fun Graphics.rect(pos: Pair<Int, Int>, size: Int, fill: Boolean = false) = if(fill) fillRect(pos.x-size/2, pos.y-size/2, size, size) else drawRect(pos.x-size/2, pos.y-size/2, size, size)

    private fun Graphics.oval(pos: Pair<Int, Int>, size: Int, fill: Boolean = false) = if(fill) fillOval(pos.x-size/2, pos.y-size/2, size, size) else drawOval(pos.x-size/2, pos.y-size/2, size, size)

    private fun Graphics.line(pos: Pair<Int, Int>, pos1: Pair<Int, Int>) = drawLine(pos.x, pos.y, pos1.x, pos1.y)

    private fun <T> Graphics.color(newColor: Color, block: ()->T): T = color.let { current -> color = newColor; block.invoke().also {color = current } }

    private fun Graphics.drawShip(ship: ShipImpl) = ship.windowPosition.let { pos ->
        if (ship.isMoving) color(Color.cyan) { line(pos, ship.direction.windowPosition) }
        color(if (ship is HeroShip) Color.white else if (ship.isSafe) Color.blue else Color.red) {
            rect(pos, if (ship is HeroShip) 5 else 4, ship is HeroShip)
        }
    }

    private fun Graphics.drawGate(gate: JumpgateImpl) = color(Color.gray) { gate.windowPosition.also { oval(it, 11) } }.let {pos -> color(if(gate.initiated) Color.cyan else Color.yellow) { if (gate.canInvoke()) oval(pos, 9, gate.initiated) } }

    private fun Graphics.drawAsset(asset: AssetImpl) = color(if (asset.isSafe) Color.green else Color.red) {
        val pos = asset.windowPosition.also { oval(it, 7, true) }
        if (asset.isMoving) color(Color.cyan) { line(pos, asset.direction.windowPosition) }
        if (asset.canInvoke()) color(Color.yellow) { oval(pos, 9) }
    }

    private fun Graphics.drawBox(asset: BoxImpl) = when (asset.type) {
        BoxImpl.Type.MINE -> drawMine(asset)
        BoxImpl.Type.BOX, BoxImpl.Type.ORE -> { color(Color.yellow) { rect(asset.windowPosition, 4, asset.canInvoke()) } }
        else -> Unit
    }

    private fun Graphics.drawMine(asset: BoxImpl) = color(Color.pink) { rect(asset.windowPosition, 4, true) }

    private fun Graphics.drawPoi(poi: PoiImpl) = color(if (poi.type == POIType.NO_ACCESS) Color.gray else Color.magenta) {
        when (poi.shapeType) {
            ShapeType.CIRCLE -> poi.windowPosition.let { (x, y) -> (poi.radius.xWindow to poi.radius.yWindow).let { (xr, yr) -> drawOval(x - xr, y - yr, 2 * xr, 2 * yr) } }
            ShapeType.RECTANGLE, ShapeType.POLYGON -> poi.cords.map { it.windowPosition }.let { cords ->
                drawPolygon(cords.map { it.first }.toIntArray(), cords.map { it.second }.toIntArray(), cords.size)
            }
        }
    }

    private val Int.xWindow get() = (this.toDouble() / map.map.width * width).toInt()
    private val Int.yWindow get() = (this.toDouble() / map.map.height * height).toInt()
    private val Pair<Int, Int>.windowPosition: Pair<Int, Int> get() = first.xWindow to second.yWindow
    private val PositionImpl.windowPosition: Pair<Int, Int> get() = position.windowPosition

    private var isInit = false
    @OnPackage
    private fun init(init: ShipInitializationCommand) {
        if (isInit) return
        isInit = true
        val scale = 4
        preferredSize = Dimension(210*scale, 131*scale)
        SwingUtilities.invokeLater { JFrame("Entities canvas").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE; contentPane.add(this@EntitiesDebugUiModule)
            pack(); setLocationRelativeTo(null); isVisible = true
        } }

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val pointer = e.point.mapPosition
                pointerEntity = entities.values.filter { it !is PoiImpl && it !is HeroShip }.minByOrNull { it.distanceTo(pointer) }
                    ?.takeIf { it.distanceTo(pointer) < 125 }
                    ?: entities.values.firstOrNull { it is PoiImpl && it.containsPoint(pointer.position)} ?: entities.hero
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
