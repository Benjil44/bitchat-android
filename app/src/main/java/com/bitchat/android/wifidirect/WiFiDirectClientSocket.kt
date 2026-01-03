package com.bitchat.android.wifidirect

import android.util.Log
import com.bitchat.android.protocol.BitchatPacket
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * WiFi Direct Client Socket
 *
 * Connects to WiFi Direct Group Owner and sends/receives packets
 *
 * @param serverAddress Group owner's IP address
 * @param serverPort Server port (default: 8888)
 */
class WiFiDirectClientSocket(
    private val serverAddress: InetAddress,
    private val serverPort: Int = 8888
) {

    companion object {
        private const val TAG = "WiFiDirectClientSocket"
        private const val CONNECTION_TIMEOUT_MS = 10_000 // 10 seconds
        private const val SOCKET_TIMEOUT_MS = 30_000 // 30 seconds
    }

    private var socket: Socket? = null
    private var outputStream: ObjectOutputStream? = null
    private var inputStream: ObjectInputStream? = null
    private var isConnected = false

    /**
     * Connect to server
     * Establishes TCP connection to group owner
     */
    private fun connect(): Boolean {
        if (isConnected) {
            return true
        }

        try {
            Log.d(TAG, "Connecting to server ${serverAddress.hostAddress}:$serverPort")

            socket = Socket()
            socket?.connect(InetSocketAddress(serverAddress, serverPort), CONNECTION_TIMEOUT_MS)
            socket?.soTimeout = SOCKET_TIMEOUT_MS

            // Initialize streams
            outputStream = ObjectOutputStream(socket!!.getOutputStream())
            inputStream = ObjectInputStream(socket!!.getInputStream())

            isConnected = true
            Log.i(TAG, "Connected to server ${serverAddress.hostAddress}:$serverPort")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to server", e)
            close()
            return false
        }
    }

    /**
     * Send packet to server
     *
     * @param packet BitchatPacket to send
     * @return true if sent successfully, false otherwise
     */
    fun sendPacket(packet: BitchatPacket): Boolean {
        // Ensure connected
        if (!isConnected && !connect()) {
            return false
        }

        return try {
            outputStream?.writeObject(packet)
            outputStream?.flush()
            Log.d(TAG, "Sent packet to server: type=${packet.type}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet", e)
            // Connection may be broken, mark as disconnected
            isConnected = false
            false
        }
    }

    /**
     * Receive packet from server
     * Blocking call - waits for packet
     *
     * @return BitchatPacket or null if error
     */
    fun receivePacket(): BitchatPacket? {
        // Ensure connected
        if (!isConnected && !connect()) {
            return null
        }

        return try {
            val packet = inputStream?.readObject() as? BitchatPacket
            if (packet != null) {
                Log.d(TAG, "Received packet from server: type=${packet.type}")
            }
            packet
        } catch (e: java.net.SocketTimeoutException) {
            // Timeout - not an error, just no data
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive packet", e)
            isConnected = false
            null
        }
    }

    /**
     * Close socket connection
     */
    fun close() {
        isConnected = false

        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()

            outputStream = null
            inputStream = null
            socket = null

            Log.d(TAG, "Socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
    }

    /**
     * Check if socket is connected
     */
    fun isConnected(): Boolean {
        return isConnected && socket?.isConnected == true && !socket!!.isClosed
    }
}
