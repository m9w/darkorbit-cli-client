package com.github.m9w.feature.suspend

import com.darkorbit.ProtocolPacket

class ExceptPacketException(val packet: ProtocolPacket) : RuntimeException("Received except packet") {
}