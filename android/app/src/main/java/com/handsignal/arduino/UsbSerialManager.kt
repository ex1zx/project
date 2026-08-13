package com.handsignal.arduino

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Opens the first attached USB serial device (Arduino) and writes finger counts to it.
 * Everything runs on-device; no network is used.
 */
class UsbSerialManager(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private var port: UsbSerialPort? = null
    private var receiver: BroadcastReceiver? = null

    fun connect() {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            onStatus("USB: لا يوجد جهاز متصل")
            return
        }
        val driver = drivers[0]
        if (!manager.hasPermission(driver.device)) {
            requestPermission(manager, driver.device.deviceName)
            return
        }
        open()
    }

    private fun requestPermission(manager: UsbManager, name: String) {
        val action = "com.handsignal.arduino.USB_PERMISSION"
        val flags = if (Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(context, 0, Intent(action), flags)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) == true) open()
                else onStatus("USB: تم رفض الصلاحية")
            }
        }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onStatus("USB: بانتظار الصلاحية ($name)")
        manager.requestPermission(
            UsbSerialProber.getDefaultProber()
                .findAllDrivers(manager)[0].device, intent
        )
    }

    private fun open() {
        try {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
                ?: return onStatus("USB: لا يوجد جهاز")
            val connection = manager.openDevice(driver.device)
                ?: return onStatus("USB: تعذر فتح الجهاز")
            val p = driver.ports[0]
            p.open(connection)
            p.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            p.dtr = true
            port = p
            onStatus("USB: متصل ✓")
        } catch (e: Exception) {
            Log.e("HandSignal", "usb open failed", e)
            onStatus("USB: خطأ في الاتصال")
        }
    }

    fun send(data: String) {
        val p = port ?: return
        try {
            p.write(data.toByteArray(), 500)
        } catch (e: Exception) {
            Log.e("HandSignal", "usb write failed", e)
        }
    }

    fun close() {
        try { port?.close() } catch (_: Exception) {}
        port = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}
