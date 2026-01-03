package com.bitchat.android.wifidirect

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * WiFi Direct Peer Mapper
 *
 * Maps WiFi Direct device addresses to BitChat peer IDs and vice versa.
 * Similar to Bluetooth's addressPeerMap pattern.
 *
 * This is critical because:
 * - WiFi Direct uses device MAC addresses (e.g., "02:00:00:00:00:00")
 * - BitChat uses peer IDs derived from public keys (16-char fingerprints)
 * - We need to track which WiFi address corresponds to which peer ID
 *
 * The mapping is established when:
 * 1. We receive identity announcement packets (contain peer ID)
 * 2. We receive encrypted messages (extract peer ID from Noise session)
 * 3. We manually register known peers
 */
class WiFiDirectPeerMapper {

    companion object {
        private const val TAG = "WiFiDirectPeerMapper"
    }

    // Device address → Peer ID mapping
    private val addressToPeerMap = ConcurrentHashMap<String, String>()

    // Peer ID → Device address mapping (reverse lookup)
    private val peerToAddressMap = ConcurrentHashMap<String, String>()

    /**
     * Register a peer mapping
     *
     * @param deviceAddress WiFi Direct device address (MAC address)
     * @param peerID BitChat peer ID (16-char fingerprint)
     */
    fun registerPeer(deviceAddress: String, peerID: String) {
        val previousPeer = addressToPeerMap.put(deviceAddress, peerID)
        peerToAddressMap[peerID] = deviceAddress

        if (previousPeer != null && previousPeer != peerID) {
            Log.i(TAG, "Updated mapping: $deviceAddress -> $previousPeer to $peerID")
        } else {
            Log.d(TAG, "Registered peer: $deviceAddress -> $peerID")
        }
    }

    /**
     * Get peer ID from device address
     *
     * @param deviceAddress WiFi Direct device address
     * @return BitChat peer ID, or null if not found
     */
    fun getPeerID(deviceAddress: String): String? {
        return addressToPeerMap[deviceAddress]
    }

    /**
     * Get device address from peer ID
     *
     * @param peerID BitChat peer ID
     * @return WiFi Direct device address, or null if not found
     */
    fun getDeviceAddress(peerID: String): String? {
        return peerToAddressMap[peerID]
    }

    /**
     * Check if device address is mapped
     *
     * @param deviceAddress WiFi Direct device address
     * @return true if mapping exists
     */
    fun hasMapping(deviceAddress: String): Boolean {
        return addressToPeerMap.containsKey(deviceAddress)
    }

    /**
     * Remove peer mapping
     * Used when peer disconnects or leaves
     *
     * @param deviceAddress WiFi Direct device address to remove
     */
    fun removePeer(deviceAddress: String) {
        val peerID = addressToPeerMap.remove(deviceAddress)
        if (peerID != null) {
            peerToAddressMap.remove(peerID)
            Log.d(TAG, "Removed peer mapping: $deviceAddress -> $peerID")
        }
    }

    /**
     * Remove peer mapping by peer ID
     *
     * @param peerID BitChat peer ID to remove
     */
    fun removePeerByID(peerID: String) {
        val deviceAddress = peerToAddressMap.remove(peerID)
        if (deviceAddress != null) {
            addressToPeerMap.remove(deviceAddress)
            Log.d(TAG, "Removed peer mapping: $peerID -> $deviceAddress")
        }
    }

    /**
     * Clear all mappings
     * Used when WiFi Direct service stops or resets
     */
    fun clear() {
        val count = addressToPeerMap.size
        addressToPeerMap.clear()
        peerToAddressMap.clear()
        Log.d(TAG, "Cleared $count peer mappings")
    }

    /**
     * Get all mapped device addresses
     *
     * @return Set of device addresses
     */
    fun getAllDeviceAddresses(): Set<String> {
        return addressToPeerMap.keys.toSet()
    }

    /**
     * Get all mapped peer IDs
     *
     * @return Set of peer IDs
     */
    fun getAllPeerIDs(): Set<String> {
        return peerToAddressMap.keys.toSet()
    }

    /**
     * Get mapping count
     *
     * @return Number of registered peer mappings
     */
    fun getMappingCount(): Int {
        return addressToPeerMap.size
    }

    /**
     * Get debug string with all mappings
     * Useful for diagnostics
     *
     * @return String representation of all mappings
     */
    fun getDebugString(): String {
        val sb = StringBuilder("WiFiDirectPeerMapper (${getMappingCount()} mappings):\n")
        addressToPeerMap.forEach { (address, peerID) ->
            sb.append("  $address -> $peerID\n")
        }
        return sb.toString()
    }
}
