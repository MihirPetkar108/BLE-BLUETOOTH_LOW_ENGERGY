package com.example.blemeshapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient

class MainActivity : AppCompatActivity() {

    private lateinit var receivedMessageView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation()

        val eventTypeSpinner = findViewById<Spinner>(R.id.eventTypeSpinner)
        val severitySpinner = findViewById<Spinner>(R.id.severitySpinner)

        val eventTypes = arrayOf(
            "collision",
            "road_block",
            "traffic_jam",
            "accident",
            "hazard"
        )

        val severities = arrayOf(
            "low",
            "medium",
            "high",
            "critical"
        )

        eventTypeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            eventTypes
        )

        severitySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            severities
        )

        requestPermissions()

        if (hasBlePermissions()) {
            startBleComponents()
        }

        findViewById<Button>(R.id.sendPacketBtn)
            .setOnClickListener {

                val packet = JSONObject()

                packet.put("packetId", UUID.randomUUID().toString())
                packet.put("eventType", eventTypeSpinner.selectedItem.toString())
                packet.put("severity", severitySpinner.selectedItem.toString())
                packet.put("latitude", currentLatitude)
                packet.put("longitude", currentLongitude)
                packet.put("timestamp", System.currentTimeMillis())
                packet.put("ttl", 150)

                MeshManager.relayPacket(packet.toString())
            }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                    }
                }
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