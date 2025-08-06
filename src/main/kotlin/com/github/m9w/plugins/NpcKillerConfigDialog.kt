package com.github.m9w.plugins

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class NpcKillerConfigDialog(owner: JFrame, private val npcKiller: NpcKiller) : JDialog(owner, "NPC Killer Configuration", true) {

    private val checkboxes = mutableListOf<JCheckBox>()
    private val checkboxPanel = JPanel()

    init {
        layout = BorderLayout()

        val mainPanel = JPanel(BorderLayout())
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val enableNpcKillerCheckBox = JCheckBox("Enable NPC Killer", npcKiller.enabled)
        enableNpcKillerCheckBox.addActionListener {
            npcKiller.enabled = enableNpcKillerCheckBox.isSelected
        }
        topPanel.add(enableNpcKillerCheckBox)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        checkboxPanel.layout = BoxLayout(checkboxPanel, BoxLayout.Y_AXIS)

        val scrollPane = JScrollPane(checkboxPanel)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        NpcData.getAllNpcNames().forEach { npcName ->
            val panel = JPanel(FlowLayout(FlowLayout.LEFT))
            val npcInfo = NpcData.getNpcInfo(npcName)
            val checkBox = JCheckBox(npcName, npcInfo.killable)
            checkBox.addActionListener {
                NpcData.setKillable(npcName, checkBox.isSelected)
            }
            panel.add(checkBox)
            checkboxes.add(checkBox)

            val radiusField = JTextField(npcInfo.attackRadius.toString(), 5)
            radiusField.addActionListener {
                println("Action Listener triggered for NPC: $npcName. Current text in field: ${radiusField.text}")
                val newRadius = radiusField.text.toIntOrNull()
                if (newRadius != null) {
                    println("Before setRadius - NpcData.getNpcInfo($npcName).attackRadius: ${NpcData.getNpcInfo(npcName).attackRadius}")
                    NpcData.setRadius(npcName, newRadius)
                    println("After setRadius - NpcData.getNpcInfo($npcName).attackRadius: ${NpcData.getNpcInfo(npcName).attackRadius}")
                } else {
                    println("Invalid radius input for $npcName: ${radiusField.text}. Reverting to ${npcInfo.attackRadius}")
                    radiusField.text = npcInfo.attackRadius.toString() // Revert on invalid input
                }
            }
            panel.add(JLabel("Radius:"))
            panel.add(radiusField)
            checkboxPanel.add(panel)
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val selectAllButton = JButton("Select All")
        selectAllButton.addActionListener {
            checkboxes.forEach { it.isSelected = true }
            NpcData.getAllNpcNames().forEach { NpcData.setKillable(it, true) }
        }
        buttonPanel.add(selectAllButton)

        val deselectAllButton = JButton("Deselect All")
        deselectAllButton.addActionListener {
            checkboxes.forEach { it.isSelected = false }
            NpcData.getAllNpcNames().forEach { NpcData.setKillable(it, false) }
        }
        buttonPanel.add(deselectAllButton)

        val okButton = JButton("OK")
        okButton.addActionListener {
            isVisible = false
            dispose()
        }
        buttonPanel.add(okButton)

        add(mainPanel, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(owner)
        getRootPane().registerKeyboardAction({ dispose() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent?) {
                refreshNpcList()
            }
        })
    }

    fun refreshNpcList() {
        checkboxPanel.removeAll()
        checkboxes.clear()
        NpcData.getAllNpcNames().forEach { npcName ->
            val panel = JPanel(FlowLayout(FlowLayout.LEFT))
            val npcInfo = NpcData.getNpcInfo(npcName)
            val checkBox = JCheckBox(npcName, npcInfo.killable)
            checkBox.addActionListener {
                NpcData.setKillable(npcName, checkBox.isSelected)
            }
            panel.add(checkBox)
            checkboxes.add(checkBox)

            val radiusField = JTextField(npcInfo.attackRadius.toString(), 5)
            radiusField.addActionListener {
                println("Action Listener triggered for NPC: $npcName. Current text in field: ${radiusField.text}")
                val newRadius = radiusField.text.toIntOrNull()
                if (newRadius != null) {
                    println("Before setRadius - NpcData.getNpcInfo($npcName).attackRadius: ${NpcData.getNpcInfo(npcName).attackRadius}")
                    NpcData.setRadius(npcName, newRadius)
                    println("After setRadius - NpcData.getNpcInfo($npcName).attackRadius: ${NpcData.getNpcInfo(npcName).attackRadius}")
                } else {
                    println("Invalid radius input for $npcName: ${radiusField.text}. Reverting to ${npcInfo.attackRadius}")
                    radiusField.text = npcInfo.attackRadius.toString() // Revert on invalid input
                }
            }
            panel.add(JLabel("Radius:"))
            panel.add(radiusField)
            checkboxPanel.add(panel)
        }
        checkboxPanel.revalidate()
        checkboxPanel.repaint()
    }
}
