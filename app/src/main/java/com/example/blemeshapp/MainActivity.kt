package com.example.blemeshapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var receivedMessageView: TextView

    fun showReceivedMessage(message: String) {
        runOnUiThread {
            receivedMessageView.text = message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        receivedMessageView = findViewById(R.id.receivedDataText)
        receivedMessageView.text = getString(R.string.waiting_for_packets)

        requestPermissions()

        if (hasBlePermissions()) {
            startBleComponents()
        }

        findViewById<Button>(R.id.sendPacketBtn)
            .setOnClickListener {

                val packet = JSONObject()

                packet.put("packetId", UUID.randomUUID().toString())
                packet.put("eventType", "collision")
                packet.put("severity", "critical")
                packet.put("latitude", 27.7172)
                packet.put("longitude", 85.3240)
                packet.put("timestamp", System.currentTimeMillis())
                packet.put("ttl", 3)

                MeshManager.relayPacket(packet.toString())
            }
    }

    private fun startBleComponents() {
        BleAdvertiser(this).startAdvertising()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            BleGattServer(this).startServer()
        }

        BleScanner(this).startScanning()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && hasBlePermissions()) {
            startBleComponents()
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        }
    }
}