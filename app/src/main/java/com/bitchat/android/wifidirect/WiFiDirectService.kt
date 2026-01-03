package com.bitchat.android.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.bitchat.android.protocol.BitchatPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * WiFi Direct Service for long-range messaging (100-200m)
 *
 * This service provides:
 * - Peer discovery via WiFi P2P
 * - Connection management (group owner negotiation)
 * - Socket-based packet transmission
 * - 10x range improvement over Bluetooth
 *
 * Architecture:
 * - Uses WifiP2pManager for peer discovery and connection
 * - Group Owner runs server socket (accepts connections)
 * - Clients connect to Group Owner's socket
 * - Bidirectional communication via TCP sockets
 *
 * @param context Application context
 */
class WiFiDirectService(private val context: Context) {

    companion object {
        private const val TAG = "WiFiDirectService"
        private const val SOCKET_PORT = 8888 // Fixed port for BitChat WiFi Direct

        // Discovery intervals (adaptive based on battery)
        private const val DISCOVERY_INTERVAL_NORMAL_MS = 15_000L // 15 seconds (battery > 50%)
        private const val DISCOVERY_INTERVAL_MODERATE_MS = 30_000L // 30 seconds (battery 20-50%)
        private const val DISCOVERY_INTERVAL_POWER_SAVE_MS = 60_000L // 1 minute (battery 10-20%)
        private const val DISCOVERY_INTERVAL_CRITICAL_MS = 120_000L // 2 minutes (battery < 10%)

        // Connection retry settings
        private const val MAX_CONNECTION_ATTEMPTS = 3
        private const val CONNECTION_RETRY_DELAY_MS = 3_000L
    }

    // WiFi P2P Manager and Channel
    private val wifiP2pManager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }

    private lateinit var channel: WifiP2pManager.Channel

    // Service state
    private var isActive = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Peer tracking
    private val discoveredPeers = ConcurrentHashMap<String, WifiP2pDevice>()
    private val connectedPeers = ConcurrentHashMap<String, WiFiDirectPeer>()

    // Connection info
    private var isGroupOwner = false
    private var groupOwnerAddress: InetAddress? = null

    // State flows for UI
    private val _peers = MutableStateFlow<List<WiFiDirectPeer>>(emptyList())
    val peers: StateFlow<List<WiFiDirectPeer>> = _peers.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WiFiDirectConnectionInfo?>(null)
    val connectionInfo: StateFlow<WiFiDirectConnectionInfo?> = _connectionInfo.asStateFlow()

    // Socket managers (will be initialized when needed)
    private var serverSocket: WiFiDirectServerSocket? = null
    private var clientSockets = ConcurrentHashMap<String, WiFiDirectClientSocket>()

    // Peer ID mapping (WiFi device address ↔ BitChat peer ID)
    private val peerMapper = WiFiDirectPeerMapper()

    // Reconnection tracking
    private data class ReconnectionState(
        val device: WifiP2pDevice,
        var attemptCount: Int = 0,
        var lastAttemptTime: Long = 0
    )
    private val reconnectionQueue = ConcurrentHashMap<String, ReconnectionState>()

    // Delegate for packet reception
    var delegate: WiFiDirectServiceDelegate? = null

    // Broadcast receiver for WiFi P2P events
    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    handleWifiP2pStateChanged(intent)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    handlePeersChanged()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    handleConnectionChanged()
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    handleThisDeviceChanged(intent)
                }
            }
        }
    }

    /**
     * Start WiFi Direct service
     * Initializes WiFi P2P and begins peer discovery
     */
    fun start() {
        if (isActive) {
            Log.w(TAG, "WiFi Direct service already active")
            return
        }

        try {
            // Initialize WiFi P2P channel
            channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), null)

            // Register broadcast receiver
            val intentFilter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            context.registerReceiver(wifiP2pReceiver, intentFilter)

            isActive = true

            // Start discovery
            startDiscovery()

            // Schedule periodic discovery
            schedulePeriodicDiscovery()

            // Start reconnection queue processing
            processReconnectionQueue()

            Log.i(TAG, "WiFi Direct service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WiFi Direct service", e)
        }
    }

    /**
     * Stop WiFi Direct service
     * Disconnects from all peers and stops discovery
     */
    fun stop() {
        if (!isActive) return

        try {
            // Disconnect from all peers
            disconnectAll()

            // Stop server socket
            serverSocket?.close()
            serverSocket = null

            // Close all client sockets
            clientSockets.values.forEach { it.close() }
            clientSockets.clear()

            // Unregister receiver
            try {
                context.unregisterReceiver(wifiP2pReceiver)
            } catch (e: Exception) {
                // Receiver might not be registered
            }

            // Clear peer mappings
            peerMapper.clear()

            // Clear reconnection queue
            reconnectionQueue.clear()

            isActive = false

            Log.i(TAG, "WiFi Direct service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WiFi Direct service", e)
        }
    }

    /**
     * Start peer discovery
     * Scans for nearby WiFi Direct devices
     */
    private fun startDiscovery() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started successfully")
            }

            override fun onFailure(reason: Int) {
                val reasonStr = when (reason) {
                    WifiP2pManager.ERROR -> "ERROR"
                    WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
                    WifiP2pManager.BUSY -> "BUSY"
                    else -> "UNKNOWN($reason)"
                }
                Log.e(TAG, "Peer discovery failed: $reasonStr")
            }
        })
    }

    /**
     * Schedule periodic peer discovery
     * Discovers peers at regular intervals (battery-aware)
     */
    private fun schedulePeriodicDiscovery() {
        serviceScope.launch {
            while (isActive) {
                val interval = getDiscoveryInterval()
                delay(interval)

                if (isActive) {
                    startDiscovery()
                }
            }
        }
    }

    /**
     * Get discovery interval based on battery level
     * Low battery → slower discovery (save power)
     */
    private fun getDiscoveryInterval(): Long {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

            val interval = when {
                batteryLevel < 10 -> {
                    Log.d(TAG, "🔋 Battery critical ($batteryLevel%), using 2-minute discovery interval")
                    DISCOVERY_INTERVAL_CRITICAL_MS
                }
                batteryLevel < 20 -> {
                    Log.d(TAG, "🔋 Battery low ($batteryLevel%), using 1-minute discovery interval")
                    DISCOVERY_INTERVAL_POWER_SAVE_MS
                }
                batteryLevel < 50 -> {
                    Log.d(TAG, "🔋 Battery moderate ($batteryLevel%), using 30-second discovery interval")
                    DISCOVERY_INTERVAL_MODERATE_MS
                }
                else -> {
                    Log.d(TAG, "🔋 Battery good ($batteryLevel%), using 15-second discovery interval")
                    DISCOVERY_INTERVAL_NORMAL_MS
                }
            }

            interval
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery level, using normal interval", e)
            DISCOVERY_INTERVAL_NORMAL_MS
        }
    }

    /**
     * Connect to a discovered peer
     * Initiates WiFi P2P connection
     */
    fun connectToPeer(device: WifiP2pDevice) {
        Log.d(TAG, "Connecting to peer: ${device.deviceName} (${device.deviceAddress})")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0 // Prefer to be client (not group owner)
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed to ${device.deviceName}, reason: $reason")
            }
        })
    }

    /**
     * Disconnect from a peer
     */
    fun disconnectFromPeer(deviceAddress: String) {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected from peer")
                connectedPeers.remove(deviceAddress)
                updatePeersList()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to disconnect, reason: $reason")
            }
        })
    }

    /**
     * Disconnect from all peers
     */
    private fun disconnectAll() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected from all peers")
                connectedPeers.clear()
                updatePeersList()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to disconnect from all peers, reason: $reason")
            }
        })
    }

    /**
     * Send packet to a peer via WiFi Direct
     * @param peerID BitChat peer ID (16-char fingerprint)
     * @param packet Packet to send
     */
    suspend fun sendPacket(peerID: String, packet: BitchatPacket): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get device address from peer ID (or use peer ID as address if not mapped yet)
                val deviceAddress = peerMapper.getDeviceAddress(peerID) ?: peerID

                if (isGroupOwner) {
                    // We are group owner, send to client via server socket
                    val server = serverSocket
                    if (server != null) {
                        val success = server.sendToClient(deviceAddress, packet)
                        if (success) {
                            Log.d(TAG, "📤 Sent packet to peer $peerID ($deviceAddress) via WiFi Direct (type: ${packet.type})")
                        }
                        success
                    } else {
                        Log.e(TAG, "Server socket not initialized, cannot send to $peerID")
                        false
                    }
                } else {
                    // We are client, connect to group owner
                    val groupOwnerAddr = groupOwnerAddress
                    if (groupOwnerAddr == null) {
                        Log.e(TAG, "Group owner address unknown, cannot send")
                        return@withContext false
                    }

                    // Get or create client socket
                    val clientSocket = clientSockets.getOrPut(deviceAddress) {
                        WiFiDirectClientSocket(groupOwnerAddr, SOCKET_PORT)
                    }

                    // Send packet
                    val success = clientSocket.sendPacket(packet)
                    if (success) {
                        Log.d(TAG, "📤 Sent packet to group owner via WiFi Direct (type: ${packet.type})")
                    }
                    success
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send packet to $peerID", e)
                false
            }
        }
    }

    // Event handlers

    private fun handleWifiP2pStateChanged(intent: Intent) {
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
        val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED

        Log.d(TAG, "WiFi P2P state changed: ${if (isEnabled) "ENABLED" else "DISABLED"}")

        if (!isEnabled) {
            // WiFi P2P disabled, clear peers
            discoveredPeers.clear()
            connectedPeers.clear()
            updatePeersList()
        }
    }

    private fun handlePeersChanged() {
        // Request peer list
        wifiP2pManager.requestPeers(channel) { peers: WifiP2pDeviceList? ->
            if (peers == null) {
                Log.w(TAG, "Peer list is null")
                return@requestPeers
            }

            Log.d(TAG, "Discovered ${peers.deviceList.size} peers")

            // Update discovered peers map
            discoveredPeers.clear()
            for (device in peers.deviceList) {
                discoveredPeers[device.deviceAddress] = device
                Log.d(TAG, "  - ${device.deviceName} (${device.deviceAddress}) status=${device.status}")
            }

            updatePeersList()
        }
    }

    private fun handleConnectionChanged() {
        // Request connection info
        wifiP2pManager.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
            if (info == null) {
                Log.w(TAG, "Connection info is null")
                return@requestConnectionInfo
            }

            Log.d(TAG, "Connection info: groupFormed=${info.groupFormed}, isOwner=${info.isGroupOwner}, ownerAddr=${info.groupOwnerAddress}")

            if (info.groupFormed) {
                // Connection established
                isGroupOwner = info.isGroupOwner
                groupOwnerAddress = info.groupOwnerAddress

                _isConnected.value = true
                _connectionInfo.value = WiFiDirectConnectionInfo(
                    isGroupOwner = info.isGroupOwner,
                    groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: "unknown"
                )

                // Clear reconnection attempts for successfully connected peers
                // (We'll identify them when we receive their packets)

                if (info.isGroupOwner) {
                    // We are group owner - start server socket
                    Log.i(TAG, "We are GROUP OWNER, starting server socket on port $SOCKET_PORT")
                    startServerSocket()
                } else {
                    // We are client - prepare to connect to group owner
                    Log.i(TAG, "We are CLIENT, group owner is at ${info.groupOwnerAddress?.hostAddress}")
                }
            } else {
                // Disconnected - schedule reconnection for previously connected peers
                Log.w(TAG, "WiFi Direct connection lost")

                // Schedule reconnection for all discovered peers that were previously connected
                discoveredPeers.values.forEach { device ->
                    if (connectedPeers.containsKey(device.deviceAddress)) {
                        Log.d(TAG, "Scheduling reconnection for ${device.deviceName}")
                        scheduleReconnection(device)
                    }
                }

                _isConnected.value = false
                _connectionInfo.value = null
            }
        }
    }

    private fun handleThisDeviceChanged(intent: Intent) {
        val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        if (device != null) {
            Log.d(TAG, "This device: ${device.deviceName} (${device.deviceAddress}) status=${device.status}")
        }
    }

    /**
     * Start server socket (for group owner)
     */
    private fun startServerSocket() {
        serviceScope.launch {
            try {
                serverSocket = WiFiDirectServerSocket(SOCKET_PORT)
                serverSocket?.start { packet, fromAddress ->
                    // Handle received packet
                    Log.d(TAG, "📶 Received packet from $fromAddress via WiFi Direct (type: ${packet.type})")

                    // Clear reconnection attempts (receiving packet means connection is stable)
                    clearReconnection(fromAddress)

                    // Extract peer ID from packet (if available) and register mapping
                    // The delegate will handle peer ID extraction from identity announcements
                    // For now, use device address as fallback peer ID
                    val peerID = peerMapper.getPeerID(fromAddress) ?: fromAddress

                    // Forward to delegate (BluetoothMeshService)
                    delegate?.onPacketReceived(packet, fromAddress)
                }
                Log.i(TAG, "WiFi Direct server socket started on port $SOCKET_PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server socket", e)
            }
        }
    }

    /**
     * Update peers list state flow
     */
    private fun updatePeersList() {
        val peersList = connectedPeers.values.toList() +
                       discoveredPeers.values.map { device ->
                           WiFiDirectPeer(
                               deviceAddress = device.deviceAddress,
                               deviceName = device.deviceName ?: "Unknown",
                               isConnected = connectedPeers.containsKey(device.deviceAddress),
                               status = device.status
                           )
                       }
        _peers.value = peersList
    }

    /**
     * Clear reconnection state for a device (called when successfully connected)
     *
     * @param deviceAddress Device address to clear
     */
    private fun clearReconnection(deviceAddress: String) {
        val removed = reconnectionQueue.remove(deviceAddress)
        if (removed != null) {
            Log.d(TAG, "✅ Cleared reconnection queue for ${removed.device.deviceName} (connection successful)")
        }
    }

    /**
     * Schedule automatic reconnection for a disconnected peer
     *
     * @param device WiFi Direct device to reconnect to
     */
    private fun scheduleReconnection(device: WifiP2pDevice) {
        val state = reconnectionQueue.getOrPut(device.deviceAddress) {
            ReconnectionState(device)
        }

        if (state.attemptCount >= MAX_CONNECTION_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached for ${device.deviceName} (${device.deviceAddress})")
            reconnectionQueue.remove(device.deviceAddress)
            return
        }

        Log.d(TAG, "Scheduled reconnection for ${device.deviceName} (attempt ${state.attemptCount + 1}/$MAX_CONNECTION_ATTEMPTS)")
    }

    /**
     * Process reconnection queue
     * Called periodically to retry failed connections
     */
    private fun processReconnectionQueue() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val peersToReconnect = reconnectionQueue.values.filter {
                        currentTime - it.lastAttemptTime >= CONNECTION_RETRY_DELAY_MS
                    }

                    for (state in peersToReconnect) {
                        if (!isActive) break

                        state.attemptCount++
                        state.lastAttemptTime = currentTime

                        if (state.attemptCount > MAX_CONNECTION_ATTEMPTS) {
                            Log.w(TAG, "Max reconnection attempts reached for ${state.device.deviceName}")
                            reconnectionQueue.remove(state.device.deviceAddress)
                            continue
                        }

                        Log.i(TAG, "🔄 Attempting reconnection to ${state.device.deviceName} (attempt ${state.attemptCount}/$MAX_CONNECTION_ATTEMPTS)")
                        connectToPeer(state.device)
                    }

                    delay(CONNECTION_RETRY_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in reconnection queue processing", e)
                }
            }
        }
    }
}

/**
 * WiFi Direct peer information
 */
data class WiFiDirectPeer(
    val deviceAddress: String,
    val deviceName: String,
    val isConnected: Boolean,
    val status: Int // WifiP2pDevice status
)

/**
 * WiFi Direct connection information
 */
data class WiFiDirectConnectionInfo(
    val isGroupOwner: Boolean,
    val groupOwnerAddress: String
)

/**
 * Delegate interface for WiFi Direct packet reception
 */
interface WiFiDirectServiceDelegate {
    /**
     * Called when a packet is received via WiFi Direct
     * @param packet The received BitchatPacket
     * @param fromAddress Device address of sender
     */
    fun onPacketReceived(packet: BitchatPacket, fromAddress: String)
}
