package com.project.handusb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

private const val ACTION_USB_PERMISSION = "com.project.handusb.USB_PERMISSION"

/** يفتح منفذ USB Serial ويرسل رقم الأصابع إلى الأردوينو */
class UsbSerialManager(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private var port: UsbSerialPort? = null
    private val manager get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) connect()
        }
    }

    fun register() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
        close()
    }

    /** يطلب صلاحية USB ثم يتصل */
    fun requestAndConnect() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            onStatus("USB: لا يوجد جهاز")
            return
        }
        val device = drivers[0].device
        if (!manager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= 31)
                PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )
            onStatus("USB: بانتظار الصلاحية")
            manager.requestPermission(device, pi)
        } else {
            connect()
        }
    }

    private fun connect() {
        runCatching {
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
                ?: return onStatus("USB: لا يوجد جهاز")
            val connection = manager.openDevice(driver.device) ?: return onStatus("USB: رُفضت الصلاحية")
            val p = driver.ports[0]
            p.open(connection)
            p.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            onStatus("USB: متصل")
        }.onFailure { onStatus("USB: خطأ ${it.message}") }
    }

    fun send(value: Int) {
        val p = port ?: return
        runCatching { p.write("$value\n".toByteArray(), 300) }
            .onFailure { onStatus("USB: انقطع الاتصال"); close() }
    }

    fun close() {
        runCatching { port?.close() }
        port = null
    }
}
