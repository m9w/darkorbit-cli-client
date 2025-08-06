package com.github.m9w.metaplugins

import com.darkorbit.POIType
import com.darkorbit.ShapeType
import com.darkorbit.ShipInitializationCommand
import com.github.m9w.Scheduler
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.waitMs
import com.github.m9w.metaplugins.game.PositionImpl
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.metaplugins.game.entities.*
import java.awt.*
import java.awt.event.*
import java.util.*
import com.github.m9w.plugins.NpcKiller
import com.github.m9w.plugins.NpcKillerConfigDialog
import javax.swing.*

class EntitiesDebugUiModule(private val npcKiller: NpcKiller, private val block: (AuthenticationProvider, Any) -> Unit) : JPanel(), Runnable {
    private val instances: MutableSet<InnerModule> = HashSet()
    private val instance: InnerModule? get() = instances.run { find { it.selected } ?: firstOrNull() }
    private val instanceSelector = JComboBox<InnerModule>()
    private var pointerEntity: EntityImpl? = null
    private var copy = HashSet<EntityImpl>()
    private var path = listOf<Pair<Int, Int>>()
    private val entityCanvas = JFrame("Entities canvas")

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        background = Color.black
        instance?.draw(g)
    }

    private fun InnerModule.draw(g: Graphics) {
        g.color = Color.gray
        g.drawString("Map ${map.map.name}", 5, 15)

        entities.hero.windowPosition.let { (x, y) ->
            g.drawLine(x, 0, x, height)
            g.drawLine(0, y, width, y)
        }
        val viewMarkerSize = 6
        g.color(Color.lightGray) {
            map.frameRect.let { it.first.windowPosition to it.second.windowPosition }.let {
                g.drawLine(it.first.x, it.first.y, it.first.x + viewMarkerSize, it.first.y)
                g.drawLine(it.first.x, it.first.y, it.first.x, it.first.y + viewMarkerSize)
                g.drawLine(it.second.x, it.first.y, it.second.x - viewMarkerSize, it.first.y)
                g.drawLine(it.second.x, it.first.y, it.second.x, it.first.y + viewMarkerSize)
                g.drawLine(it.second.x, it.second.y, it.second.x - viewMarkerSize, it.second.y)
                g.drawLine(it.second.x, it.second.y, it.second.x, it.second.y - viewMarkerSize)
                g.drawLine(it.first.x, it.second.y, it.first.x, it.second.y - viewMarkerSize)
                g.drawLine(it.first.x, it.second.y, it.first.x + viewMarkerSize, it.second.y)
            }
        }

        try {
            val heroIds = instances.mapTo(HashSet()) { it.entities.hero.id }
            copy.clear()
            copy.add(entities.hero)
            copy.addAll(entities.values.filter { !heroIds.contains(it.id) })
            instances.filter { it.key == key && it.entities.hero != entities.hero }.forEach { container ->
                copy.add(container.entities.hero)
                copy.addAll(container.entities.values.filter { !heroIds.contains(it.id) })
            }
        } catch (_: ConcurrentModificationException) { return }

        copy.filterIsInstance<PoiImpl>().forEach { g.drawPoi(it) }
        copy.filterIsInstance<JumpgateImpl>().forEach { g.drawGate(it) }
        copy.filterIsInstance<AssetImpl>().forEach { g.drawAsset(it) }
        copy.filterIsInstance<ShipImpl>().forEach { g.drawShip(it) }
        copy.filterIsInstance<BoxImpl>().forEach { g.drawBox(it) }
        if (entities.hero.isMoving && entities.moveModule.nextPoints.size > 1) g.color(Color.cyan) { g.drawLine(entities.moveModule.nextPoints) }
        if (path.size > 1) g.color(Color.yellow) { g.drawLine(listOf(entities.hero.position) + path) }
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
        if (ship.isMoving) color(Color.cyan) { line(pos, ship.destination.windowPosition) }
        if (ship.id == instance!!.entities.hero.id) return
        color(if (ship is HeroPet || ship is HeroShip) Color.white else if (ship.isSafe) Color.blue else Color.red) {
            rect(pos, when(ship) { is HeroShip -> 5; is PetImpl -> 2; else -> 4 }, ship is HeroShip)
        }
    }

    private fun Graphics.drawGate(gate: JumpgateImpl) = color(Color.gray) { gate.windowPosition.also { oval(it, 11) } }.let {pos -> color(if(gate.initiated) Color.cyan else Color.yellow) { if (gate.canInvoke()) oval(pos, 9, gate.initiated) } }

    private fun Graphics.drawAsset(asset: AssetImpl) = color(if (asset.isSafe) Color.green else Color.red) {
        val pos = asset.windowPosition.also { oval(it, 7, true) }
        if (asset.isMoving) color(Color.cyan) { line(pos, asset.destination.windowPosition) }
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

    private val Int.xWindow get() = (this.toDouble() / instance!!.map.map.width * width).toInt()
    private val Int.yWindow get() = (this.toDouble() / instance!!.map.map.height * height).toInt()
    private val Pair<Int, Int>.windowPosition: Pair<Int, Int> get() = first.xWindow to second.yWindow
    private val PositionImpl.windowPosition: Pair<Int, Int> get() = position.windowPosition

    init {
        val scale = 4
        preferredSize = Dimension(210*scale, 131*scale)
        SwingUtilities.invokeLater { entityCanvas.apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            contentPane.add(this@EntitiesDebugUiModule)
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        } }

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                instance?.apply {
                    val pointer = e.point.mapPosition
                    pointerEntity = copy.filter { it !is PoiImpl && it != entities.hero }.minByOrNull { it.distanceTo(pointer) }
                        ?.takeIf { it.distanceTo(pointer) < 125 }
                        ?: copy.firstOrNull { it is PoiImpl && it.containsPoint(pointer.position)} ?: entities.hero
                    path = pathTracer.traceTo(pointer.position)
                }
            }
        })

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                instance?.apply {
                    if (e.button == 1) entities.hero.moveTo(e.point.mapPosition.position)
                    if (e.button == 3) pointerEntity?.let {
                        if (it is HeroShip && it != entities.hero) instances.find { instance -> instance.entities.hero == it }?.selectContainer()
                        else entities[it.id]?.invoke()
                    }
                }
            }
        })
        Thread(this, "UI").apply { isDaemon = true }.start()
        showControls()
    }

    private val <T:Component> T.center: T get() = this.also { setAlignmentX(LEFT_ALIGNMENT) }

    private fun showControls() {
        instanceSelector.removeAllItems()
        instanceSelector.addActionListener { (instanceSelector.selectedItem as? InnerModule)?.selectContainer() }
        SwingUtilities.invokeLater {
            val frame = JFrame("Controls")
            frame.pack()
            frame.setLocationRelativeTo(null)
            frame.setSize(300, 550)
            val panel = JPanel()
            panel.setLayout(BoxLayout(panel, BoxLayout.Y_AXIS))
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
            fun JPanel.addWithPadding(comp: Component) = add(comp).let { add(Box.createRigidArea(Dimension(0, 5))) }
            fun button(name: String, block: InnerModule.() -> Unit) = panel.addWithPadding(JButton(name).center.apply { addActionListener { instance?.apply(block) } })
            fun buttonI(name: String, block: () -> Unit) = panel.addWithPadding(JButton(name).center.apply { addActionListener { block() } })

            buttonI("Login + Password") { InputDialog(frame, "Login", "Password") {
                block(AuthenticationProvider.byLoginPassword(this["Login"]!!, this["Password"]!!), InnerModule() )
            } }
            buttonI("Server + SID") { InputDialog(frame, "Server", "SID") {
                block(AuthenticationProvider.byServerSid(this["Server"]!!, this["SID"]!!), InnerModule())
            } }
            buttonI("External login") { InputDialog(frame, "Login", "Password") {
                block(AuthenticationProvider.byLoginPasswordExternal(this["Login"]!!, this["Password"]!!), InnerModule())
            } }
            panel.addWithPadding(instanceSelector.center)
            panel.addWithPadding(JButton("Disconnect selected").center.apply { addActionListener { (instanceSelector.selectedItem as? InnerModule)?.apply {
                scheduler.close()
                instances.remove(this)
                instanceSelector.removeItem(this)
            } } })
            button("Toggle network debug") { NetworkLayer.debug = !NetworkLayer.debug }
            button("Toggle config") { entities.hero.shipConfig = when (entities.hero.shipConfig) { 1 -> 2; 2 -> 1; else -> 1 } }
            button("Toggle PET") { entities.hero.pet?.deactivate() ?: entities.hero.enablePet() }
            
            button("Set Normal Mode") { entities.gameEngine.state = GameEngine.State.NORMAL }
            button("Set Escaping Mode") { entities.gameEngine.state = GameEngine.State.ESCAPING }
            button("Set Traveling Mode") { entities.gameEngine.state = GameEngine.State.TRAVELING }
            button("Set Normal Mode for All") { instances.forEach { it.entities.gameEngine.state = GameEngine.State.NORMAL } }
            buttonI("Set Escaping Mode for All") { instances.forEach { it.entities.gameEngine.state = GameEngine.State.ESCAPING } }
            buttonI("Set Traveling Mode for All") { instances.forEach { it.entities.gameEngine.state = GameEngine.State.TRAVELING } }
            buttonI("Show / Hide entity canvas") { entityCanvas.isVisible = !entityCanvas.isVisible }

            val destinationMapField = JTextField(10)
            panel.addWithPadding(JLabel("Destination Map:").center)
            panel.addWithPadding(destinationMapField.center)
            button("Start Map Travel") { mapTraveler.startTravel(destinationMapField.text) }
            button("Stop Map Travel") { mapTraveler.stopTravel() }

            buttonI("NPC Killer Config") { NpcKillerConfigDialog(frame, npcKiller).isVisible = true }

            panel.add(Box.createRigidArea(Dimension(0, 12000)))
            frame.contentPane = panel
            frame.isVisible = true
        }
    }

    private val Point.mapPosition: PositionImpl get() = PositionImpl((x.toDouble() / width * instance!!.map.map.width).toInt(), (y.toDouble() / height * instance!!.map.map.height).toInt())

    override fun run() { while (true) {
        if (entityCanvas.isVisible) {
            repaint()
            Thread.sleep(1000/60)
        } else {
            Thread.sleep(1000)
        }
    } }

    private inner class InnerModule() {
        val auth: AuthenticationProvider by context
        val pathTracer: PathTracerModule by context
        val entities: EntitiesModule by context
        val scheduler: Scheduler by context
        val map: MapModule by context
        val mapTraveler: com.github.m9w.plugins.MapTraveler by context
        
        val key: String get() = "${auth.address}|${map.map.id}"
        var selected: Boolean = false; private set
        private var name = "Loading..."
        init { instanceSelector.addItem(this) }
        @OnPackage
        private suspend fun init(init: ShipInitializationCommand) {
            waitMs(0)
            name = "${map.map.name} - ${init.userName}"
            instanceSelector.repaint()
            instances.add(this)
        }
        fun selectContainer() = instances.forEach { it.selected = false }.also { selected = true }
        override fun toString() = name
    }

    private class InputDialog(owner: Frame?, vararg fields: String, private val block: Map<String, String>.() -> Unit) : JDialog(owner, "Input Dialog", true) {
        private var jFields: MutableMap<String, JTextField> = mutableMapOf()
        private var okButton: JButton = JButton("OK")
        private var cancelButton: JButton = JButton("Cancel")

        init {
            val inputPanel = JPanel(GridLayout(fields.size, 2, 5, 5)).apply {
                setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
                fields.forEach { label -> add(JLabel(label)); add(JTextField(20).also { jFields.put(label, it) }) }
            }
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(okButton); add(cancelButton) }
            okButton.addActionListener { isVisible = false; block.invoke(jFields.entries.associate { (k, v) -> k to v.getText() }) }
            cancelButton.addActionListener { isVisible = false }
            getRootPane().registerKeyboardAction({ isVisible = false }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW)
            getRootPane().setDefaultButton(okButton)
            layout = BorderLayout(10, 10)
            add(inputPanel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
            pack()
            setLocationRelativeTo(owner)
            isVisible = true
        }
    }
}
