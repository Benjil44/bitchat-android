package com.bitchat.android.wifidirect

import android.util.Log
import com.bitchat.android.protocol.BitchatPacket
import kotlinx.coroutines.*
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * WiFi Direct Server Socket (Group Owner)
 *
 * Listens for incoming connections from WiFi Direct clients
 * Receives packets and forwards to packet processor
 *
 * @param port Port to listen on (default: 8888)
 */
class WiFiDirectServerSocket(private val port: Int = 8888) {

    companion object {
        private const val TAG = "WiFiDirectServerSocket"
        private const val SOCKET_TIMEOUT_MS = 30_000 // 30 seconds
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val clientHandlers = ConcurrentHashMap<String, ClientHandler>()

    /**
     * Start server socket and listen for connections
     *
     * @param onPacketReceived Callback when packet is received (packet, fromAddress)
     */
    fun start(onPacketReceived: (BitchatPacket, String) -> Unit) {
        if (isRunning) {
            Log.w(TAG, "Server socket already running")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning = true

            Log.i(TAG, "Server socket started on port $port")

            // Accept connections in background thread
            Thread {
                try {
                    while (isRunning) {
                        try {
                            // Accept client connection (blocking)
                            val clientSocket = serverSocket?.accept()

                            if (clientSocket != null) {
                                val clientAddress = clientSocket.inetAddress.hostAddress ?: "unknown"
                                Log.d(TAG, "Client connected: $clientAddress")

                                // Handle client in separate thread
                                val handler = ClientHandler(clientSocket, clientAddress, onPacketReceived)
                                clientHandlers[clientAddress] = handler
                                handler.start()
                            }
                        } catch (e: IOException) {
                            if (isRunning) {
                                Log.e(TAG, "Error accepting client connection", e)
                            }
                            // If server is stopping, this is expected
                        }
                    }
                } finally {
                    Log.d(TAG, "Server socket accept loop ended")
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server socket", e)
            isRunning = false
        }
    }

    /**
     * Send packet to a specific client
     * (Server can send back to connected clients)
     */
    fun sendToClient(clientAddress: String, packet: BitchatPacket): Boolean {
        val handler = clientHandlers[clientAddress]
        return if (handler != null) {
            handler.sendPacket(packet)
        } else {
            Log.w(TAG, "No handler found for client: $clientAddress")
            false
        }
    }

    /**
     * Close server socket and disconnect all clients
     */
    fun close() {
        isRunning = false

        // Close all client handlers
        clientHandlers.values.forEach { it.close() }
        clientHandlers.clear()

        // Close server socket
        try {
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "Server socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }

    /**
     * Client handler - manages communication with a single client
     */
    private class ClientHandler(
        private val socket: Socket,
        private val clientAddress: String,
        private val onPacketReceived: (BitchatPacket, String) -> Unit
    ) {
        private var isRunning = false
        private var outputStream: ObjectOutputStream? = null

        fun start() {
            isRunning = true

            Thread {
                try {
                    // Set socket timeout
                    socket.soTimeout = SOCKET_TIMEOUT_MS

                    // Get streams
                    outputStream = ObjectOutputStream(socket.getOutputStream())
                    val inputStream = ObjectInputStream(socket.getInputStream())

                    Log.d(TAG, "Client handler started for $clientAddress")

                    // Read packets in loop
                    while (isRunning) {
                        try {
                            // Read packet (blocking)
                            val packet = inputStream.readObject() as? BitchatPacket

                            if (packet != null) {
                                Log.d(TAG, "Received packet from $clientAddress: type=${packet.type}")
                                onPacketReceived(packet, clientAddress)
                            } else {
                                Log.w(TAG, "Received null or invalid packet from $clientAddress")
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Timeout - continue waiting
                            continue
                        } catch (e: Exception) {
                            if (isRunning) {
                                Log.e(TAG, "Error reading from client $clientAddress", e)
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Client handler error for $clientAddress", e)
                } finally {
                    close()
                }
            }.start()
        }

        fun sendPacket(packet: BitchatPacket): Boolean {
            return try {
                outputStream?.writeObject(packet)
                outputStream?.flush()
                Log.d(TAG, "Sent packet to client $clientAddress")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send packet to client $clientAddress", e)
                false
            }
        }

        fun close() {
            isRunning = false
            try {
                socket.close()
                Log.d(TAG, "Client handler closed for $clientAddress")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }
}
