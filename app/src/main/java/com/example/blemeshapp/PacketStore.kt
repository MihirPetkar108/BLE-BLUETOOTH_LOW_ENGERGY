package com.example.blemeshapp

object PacketStore {

    private val seenPackets = mutableSetOf<String>()

    fun hasSeen(packetId: String): Boolean {
        return seenPackets.contains(packetId)
    }

    fun markSeen(packetId: String) {
        seenPackets.add(packetId)
    }
}