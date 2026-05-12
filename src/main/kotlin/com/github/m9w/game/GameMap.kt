package com.github.m9w.game

import com.github.m9w.game.entities.GameMapEnum

class GameMap(val id: Int, val name: String, val width: Int, val height: Int) {
    fun isOutOfMap(x: Int, y: Int): Boolean = x < 0 || y < 0 || x > width || y > height
    val entity get() = GameMapEnum.findById(id)
}
