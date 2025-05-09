package com.github.m9w.feature.suspend

class ExceptPacketException(val packet: Any) : RuntimeException("Received except packet") {
}