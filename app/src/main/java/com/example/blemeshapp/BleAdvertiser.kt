package com.example.blemeshapp

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS

class BleAdvertiser(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    private val advertiser =
        bluetoothManager.adapter.bluetoothLeAdvertiser

    fun startAdvertising() {

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            )
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(
                ParcelUuid(Constants.SERVICE_UUID)
            )
            .build()

        advertiser.startAdvertising(
            settings,
            advertiseData,
            callback
        )
    }

    private val callback = object : AdvertiseCallback() {

        override fun onStartSuccess(
            settingsInEffect: AdvertiseSettings?
        ) {
            Log.d(
                "BLE",
                "Advertising started successfully"
            )
        }

        override fun onStartFailure(errorCode: Int) {

            Log.e(
                "BLE",
                "Advertising failed: $errorCode"
            )

            when (errorCode) {

                ADVERTISE_FAILED_ALREADY_STARTED ->
                    Log.e(
                        "BLE",
                        "Advertising already started"
                    )

                ADVERTISE_FAILED_DATA_TOO_LARGE ->
                    Log.e(
                        "BLE",
                        "Advertising data too large"
                    )

                ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                    Log.e(
                        "BLE",
                        "Advertising feature unsupported"
                    )

                ADVERTISE_FAILED_INTERNAL_ERROR ->
                    Log.e(
                        "BLE",
                        "Advertising internal error"
                    )

                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                    Log.e(
                        "BLE",
                        "Too many advertisers running"
                    )

                else ->
                    Log.e(
                        "BLE",
                        "Unknown advertising error"
                    )
            }
        }
    }
}