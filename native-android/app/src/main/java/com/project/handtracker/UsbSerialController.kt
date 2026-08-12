package com.project.handtracker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbSerialController(private val context: Context) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.project.handtracker.USB_PERMISSION"
    }

    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var endpointOut: UsbEndpoint? = null
    private var claimedInterface: UsbInterface? = null
    private var lastSent: Int? = null

    fun firstDevice(): UsbDevice? = manager.deviceList.values.firstOrNull()

    fun hasPermission(device: UsbDevice?): Boolean = device != null && manager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        manager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }

    fun connect(device: UsbDevice): Boolean {
        close()
        val usbConnection = manager.openDevice(device) ?: return false
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            val candidate = (0 until usbInterface.endpointCount)
                .map { usbInterface.getEndpoint(it) }
                .firstOrNull {
                    it.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        it.direction == UsbConstants.USB_DIR_OUT
                }
            if (candidate != null && usbConnection.claimInterface(usbInterface, true)) {
                connection = usbConnection
                endpointOut = candidate
                claimedInterface = usbInterface
                return true
            }
        }
        usbConnection.close()
        return false
    }

    fun sendFingerCount(count: Int): Boolean {
        if (count !in 1..5 || count == lastSent) return false
        val bytes = "$count\n".toByteArray(Charsets.UTF_8)
        val sent = connection?.bulkTransfer(endpointOut, bytes, bytes.size, 250) ?: -1
        if (sent == bytes.size) {
            lastSent = count
            return true
        }
        return false
    }

    fun close() {
        claimedInterface?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        endpointOut = null
        claimedInterface = null
        lastSent = null
    }
}