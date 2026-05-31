package com.example.blemeshapp

import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

class BleGattServer(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager

    private val callback =
        object : BluetoothGattServerCallback() {

            @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {

                val message = String(value ?: byteArrayOf())

                Log.d("MESH_RECEIVE", "====================")
                Log.d("MESH_RECEIVE", "Packet received")
                Log.d("MESH_RECEIVE", "From device: ${device?.address}")
                Log.d("MESH_RECEIVE", "Message: $message")
                Log.d("MESH_RECEIVE", "====================")

                if (context is MainActivity) {
                    context.showReceivedMessage(message)
                }

                try {
                    MeshManager.relayPacket(message)
                } catch (e: Exception) {
                    Log.e(
                        "MESH_RECEIVE",
                        "Failed to relay packet: ${e.message}"
                    )
                }

                if (responseNeeded) {
                    Log.d("MESH_RECEIVE", "Sending GATT_SUCCESS response")
                    this@BleGattServer.gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }
        }

    private var gattServer: BluetoothGattServer? = null

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer() {

        if (
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "BLUETOOTH_CONNECT permission not granted")
            return
        }

        if (gattServer == null) {
            gattServer = bluetoothManager.openGattServer(
                context,
                callback
            )
        }

        val service = BluetoothGattService(
            Constants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val characteristic = BluetoothGattCharacteristic(
            Constants.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                    BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        Log.d("MESH_RECEIVE", "GATT server started")
        Log.d("MESH_RECEIVE", "Service UUID: ${Constants.SERVICE_UUID}")
        Log.d("MESH_RECEIVE", "Characteristic UUID: ${Constants.CHARACTERISTIC_UUID}")
    }
}