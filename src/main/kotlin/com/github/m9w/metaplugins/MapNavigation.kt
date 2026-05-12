package com.github.m9w.metaplugins

import com.github.m9w.feature.Classifier
import com.github.m9w.game.entities.GameMapEnum

interface MapNavigation : Classifier<MapNavigation> {
    fun travelTo(destination: GameMapEnum)
    fun interrupt()
}