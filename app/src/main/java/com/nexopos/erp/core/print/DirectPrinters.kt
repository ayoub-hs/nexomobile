package com.nexopos.erp.core.print

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

internal interface PrinterTransport {
    suspend fun send(data: ByteArray)
}

internal class TcpPrinter(private val host: String, private val port: Int) : PrinterTransport {
    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.soTimeout = 10_000 // 10 second read/write timeout
            socket.connect(InetSocketAddress(host, port), 5_000) // 5 second connect timeout
            val out: OutputStream = socket.getOutputStream()
            out.write(data)
            out.flush()
        } finally {
            runCatching { socket.close() }
        }
    }
}

internal class BluetoothPrinter(private val device: BluetoothDevice) : PrinterTransport {
    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        // Standard SerialPortService ID
        val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val socket = device.createRfcommSocketToServiceRecord(spp)
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        try {
            // Note: BluetoothSocket doesn't support timeout directly
            // Connection attempt will use system default (~12 seconds)
            socket.connect()
            val out = socket.outputStream
            out.write(data)
            out.flush()
        } finally {
            runCatching { socket.close() }
        }
    }
}

internal object TransportFactory {
    fun bluetooth(context: Context, mac: String?): BluetoothPrinter? {
        if (mac.isNullOrBlank()) return null
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        val device = adapter?.getRemoteDevice(mac)
        return if (device != null) BluetoothPrinter(device) else null
    }

    fun tcp(host: String?, port: Int): TcpPrinter? {
        if (host.isNullOrBlank()) return null
        return TcpPrinter(host, port)
    }
}
