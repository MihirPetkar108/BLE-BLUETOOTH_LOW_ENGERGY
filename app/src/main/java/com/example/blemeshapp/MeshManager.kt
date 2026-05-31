package com.example.blemeshapp

import android.util.Log
import org.json.JSONObject

object MeshManager {

    private val clients = mutableListOf<BleGattClient>()

    fun registerClient(client: BleGattClient) {
        clients.add(client)
    }

    fun relayPacket(packetJson: String) {

        val json = JSONObject(packetJson)

        val packetId = json.getString("packetId")
        val ttl = json.getInt("ttl")

        if (PacketStore.hasSeen(packetId)) {
            return
        }

        PacketStore.markSeen(packetId)

        Log.d("MESH", "Relaying packet: $packetId")

        if (ttl <= 0) {
            return
        }

        json.put("ttl", ttl - 1)

        clients.forEach {
            it.sendMessage(json.toString())
        }
    }

}