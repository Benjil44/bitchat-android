package com.bitchat.android.transport

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.wifidirect.WiFiDirectPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transport Router - Intelligent transport selection
 *
 * Decides whether to send messages via:
 * - Bluetooth LE (short range, low power)
 * - WiFi Direct (long range, higher power)
 *
 * Selection criteria:
 * 1. Peer availability (which transports can reach this peer?)
 * 2. Distance (RSSI signal strength)
 * 3. Battery level (conserve power when low)
 * 4. Packet size (use WiFi for large files)
 * 5. Message priority (use fastest for urgent)
 *
 * @param context Application context
 */
class TransportRouter(private val context: Context) {

    companion object {
        private const val TAG = "TransportRouter"

        // RSSI thresholds for transport selection
        private const val BLUETOOTH_CLOSE_RSSI = -60 // > -60 dBm = very close, use BT
        private const val BLUETOOTH_FAR_RSSI = -80   // < -80 dBm = far, prefer WiFi

        // Battery thresholds
        private const val LOW_BATTERY_PERCENT = 20   // < 20% = conserve power
        private const val CRITICAL_BATTERY_PERCENT = 10 // < 10% = Bluetooth only

        // Packet size thresholds
        private const val LARGE_PACKET_SIZE = 10_000 // > 10KB = prefer WiFi
    }

    // Transport statistics
    private val _stats = MutableStateFlow(TransportStats())
    val stats: StateFlow<TransportStats> = _stats.asStateFlow()

    // Current battery level
    private var currentBatteryLevel: Int = 100

    /**
     * Select best transport for sending a packet to a peer
     *
     * @param peerID Target peer ID
     * @param packet Packet to send
     * @param bluetoothPeers List of Bluetooth peers
     * @param wifiDirectPeers List of WiFi Direct peers
     * @return Selected transport type
     */
    fun selectTransport(
        peerID: String,
        packet: BitchatPacket,
        bluetoothPeers: List<BluetoothPeerInfo>,
        wifiDirectPeers: List<WiFiDirectPeer>
    ): TransportType {

        // Update battery level
        currentBatteryLevel = getBatteryLevel()

        // Find peer in both transports
        val btPeer = bluetoothPeers.find { it.peerID == peerID }
        val wifiPeer = wifiDirectPeers.find { it.deviceAddress == peerID }

        // Decision logic
        val selectedTransport = when {
            // CRITICAL BATTERY: Bluetooth only (save power)
            currentBatteryLevel < CRITICAL_BATTERY_PERCENT -> {
                Log.d(TAG, "Critical battery ($currentBatteryLevel%), using Bluetooth only")
                TransportType.BLUETOOTH
            }

            // WIFI DIRECT ONLY AVAILABLE
            wifiPeer != null && btPeer == null -> {
                Log.d(TAG, "WiFi Direct only option for $peerID")
                TransportType.WIFI_DIRECT
            }

            // BLUETOOTH ONLY AVAILABLE
            btPeer != null && wifiPeer == null -> {
                Log.d(TAG, "Bluetooth only option for $peerID")
                TransportType.BLUETOOTH
            }

            // BOTH AVAILABLE: Intelligent selection
            btPeer != null && wifiPeer != null -> {
                selectBestTransport(peerID, packet, btPeer, wifiPeer)
            }

            // NEITHER AVAILABLE: Queue for later (return Bluetooth as default)
            else -> {
                Log.w(TAG, "No transport available for $peerID, will queue")
                TransportType.BLUETOOTH // Default fallback
            }
        }

        // Update statistics
        updateStats(selectedTransport)

        Log.d(TAG, "Selected $selectedTransport for $peerID (BT RSSI: ${btPeer?.rssi}, Battery: $currentBatteryLevel%)")

        return selectedTransport
    }

    /**
     * Select best transport when both Bluetooth and WiFi are available
     */
    private fun selectBestTransport(
        peerID: String,
        packet: BitchatPacket,
        btPeer: BluetoothPeerInfo,
        wifiPeer: WiFiDirectPeer
    ): TransportType {

        // Factor 1: Packet size (large files → WiFi)
        val packetSize = estimatePacketSize(packet)
        if (packetSize > LARGE_PACKET_SIZE) {
            Log.d(TAG, "Large packet ($packetSize bytes), preferring WiFi Direct")
            return TransportType.WIFI_DIRECT
        }

        // Factor 2: Distance (RSSI signal strength)
        val btRssi = btPeer.rssi

        when {
            // Peer is very close (strong Bluetooth signal)
            btRssi > BLUETOOTH_CLOSE_RSSI -> {
                Log.d(TAG, "Peer very close (RSSI: $btRssi), using Bluetooth")
                return TransportType.BLUETOOTH
            }

            // Peer is far (weak Bluetooth signal)
            btRssi < BLUETOOTH_FAR_RSSI -> {
                Log.d(TAG, "Peer far away (RSSI: $btRssi), using WiFi Direct")
                return TransportType.WIFI_DIRECT
            }

            // Peer is medium distance - consider battery
            else -> {
                return if (currentBatteryLevel < LOW_BATTERY_PERCENT) {
                    Log.d(TAG, "Low battery ($currentBatteryLevel%), using Bluetooth despite medium distance")
                    TransportType.BLUETOOTH
                } else {
                    // Medium distance, good battery → prefer WiFi for reliability
                    Log.d(TAG, "Medium distance, good battery → WiFi Direct")
                    TransportType.WIFI_DIRECT
                }
            }
        }
    }

    /**
     * Estimate packet size in bytes
     */
    private fun estimatePacketSize(packet: BitchatPacket): Int {
        // Return payload size
        return packet.payload.size
    }

    /**
     * Get current battery level (0-100)
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery level", e)
            100 // Assume full battery on error
        }
    }

    /**
     * Update transport usage statistics
     */
    private fun updateStats(transport: TransportType) {
        val currentStats = _stats.value
        _stats.value = when (transport) {
            TransportType.BLUETOOTH -> currentStats.copy(
                bluetoothCount = currentStats.bluetoothCount + 1
            )
            TransportType.WIFI_DIRECT -> currentStats.copy(
                wifiDirectCount = currentStats.wifiDirectCount + 1
            )
        }
    }

    /**
     * Get transport usage statistics
     */
    fun getStats(): TransportStats = _stats.value

    /**
     * Reset statistics
     */
    fun resetStats() {
        _stats.value = TransportStats()
    }
}

/**
 * Transport type enumeration
 */
enum class TransportType {
    BLUETOOTH,
    WIFI_DIRECT
}

/**
 * Bluetooth peer information for routing decisions
 */
data class BluetoothPeerInfo(
    val peerID: String,
    val rssi: Int, // Signal strength in dBm
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Transport usage statistics
 */
data class TransportStats(
    val bluetoothCount: Int = 0,
    val wifiDirectCount: Int = 0
) {
    /**
     * Get percentage of messages sent via Bluetooth
     */
    fun getBluetoothPercentage(): Int {
        val total = bluetoothCount + wifiDirectCount
        return if (total > 0) {
            (bluetoothCount * 100) / total
        } else {
            0
        }
    }

    /**
     * Get percentage of messages sent via WiFi Direct
     */
    fun getWiFiDirectPercentage(): Int {
        val total = bluetoothCount + wifiDirectCount
        return if (total > 0) {
            (wifiDirectCount * 100) / total
        } else {
            0
        }
    }

    /**
     * Get total messages routed
     */
    fun getTotalCount(): Int = bluetoothCount + wifiDirectCount
}
