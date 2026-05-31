package com.example.blemeshapp

data class EmergencyPacket(
    val packetId: String,
    val eventType: String,
    val severity: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val ttl: Int
)