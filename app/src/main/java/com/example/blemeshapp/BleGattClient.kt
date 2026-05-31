package com.example.blemeshapp


import android.bluetooth.*
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BleGattClient(
    private val context: Context
) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnectingOrConnected = false

    fun connect(device: BluetoothDevice) {
        if (
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "BLUETOOTH_CONNECT permission not granted")
            return
        }
        if (isConnectingOrConnected) {
            Log.d("BLE", "Already connected or connecting. Ignoring device: ${device.address}")
            return
        }

        isConnectingOrConnected = true
        Log.d("BLE", "Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(
            context,
            false,
            callback
        )
    }

    fun sendMessage(message: String) {
        if (
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "BLUETOOTH_CONNECT permission not granted")
            return
        }
        val service = bluetoothGatt?.getService(Constants.SERVICE_UUID)

        if (service == null) {
            Log.e("BLE", "Service not found. Device is not ready.")
            return
        }

        val characteristic =
            service.getCharacteristic(Constants.CHARACTERISTIC_UUID)

        val data = message.toByteArray(Charsets.UTF_8)
        Log.d("BLE", "Preparing to send message (${data.size} bytes): $message")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data

            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }

        Log.d("BLE", "Sent packet")
    }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                Log.d("BLE", "Connection state changed. status=$status newState=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE", "Connected")

                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d("BLE", "Requesting MTU 512")
                        gatt.requestMtu(512)
                        gatt.discoverServices()
                    }
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE", "Disconnected")
                    isConnectingOrConnected = false
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                Log.d("BLE", "Services discovered")

                val service = gatt.getService(Constants.SERVICE_UUID)
                if (service == null) {
                    Log.e("BLE", "Mesh service not found")
                    return
                }

                val characteristic = service.getCharacteristic(Constants.CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    Log.e("BLE", "Mesh characteristic not found")
                    return
                }

                Log.d("BLE", "Mesh service and characteristic found")
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                Log.d("BLE", "MTU changed. mtu=$mtu status=$status")
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Write completed successfully")
                } else {
                    Log.e("BLE", "Write failed. status=$status")
                }
            }
        }
}