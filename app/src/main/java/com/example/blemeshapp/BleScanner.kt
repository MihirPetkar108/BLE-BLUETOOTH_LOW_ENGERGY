package com.example.blemeshapp

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build

class BleScanner(
    private val context: Context
) {

    private val client = BleGattClient(context)
    private var hasConnectedToMeshNode = false

    private val scanner =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter
            .bluetoothLeScanner

    fun startScanning() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("BLE", "BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        hasConnectedToMeshNode = false
        scanner.startScan(scanCallback)
        Log.d("BLE", "Scanning started")
    }

    private val scanCallback = object : ScanCallback() {

        private fun hasPermissions(): Boolean {

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED

            } else {
                true
            }
        }

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            if (!hasPermissions()) {
                Log.e("BLE", "Required BLE permissions not granted")
                return
            }

            val device = result.device

            if (hasConnectedToMeshNode) {
                return
            }

            Log.d("BLE", "Found device: ${device.address}")

            val serviceUuids = result.scanRecord?.serviceUuids
            val hasMeshService = serviceUuids?.any {
                it.uuid == Constants.SERVICE_UUID
            } == true

            if (!hasMeshService) {
                Log.d("BLE", "Ignoring non-mesh device: ${device.address}")
                return
            }

            Log.d("BLE", "Mesh node found: ${device.address}")

            hasConnectedToMeshNode = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e("BLE", "BLUETOOTH_SCAN permission not granted")
                    return
                }
            }

            scanner.stopScan(this)
            Log.d("BLE", "Scanning stopped")

            client.connect(device)
            MeshManager.registerClient(client)
        }
    }
}