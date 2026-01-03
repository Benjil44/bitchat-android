package com.bitchat.android.wifidirect

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * WiFi Direct Permission Manager
 *
 * Handles WiFi Direct permission requests across different Android versions
 *
 * Required permissions:
 * - ACCESS_WIFI_STATE (all versions)
 * - CHANGE_WIFI_STATE (all versions)
 * - CHANGE_NETWORK_STATE (all versions)
 * - ACCESS_FINE_LOCATION (Android < 13, required for WiFi Direct)
 * - NEARBY_WIFI_DEVICES (Android 13+, replaces location for WiFi Direct)
 *
 * @param context Application context
 */
class WiFiDirectPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "WiFiDirectPermissionMgr"

        // Permission request code
        const val WIFI_DIRECT_PERMISSION_REQUEST_CODE = 1003

        // Required permissions (all Android versions)
        private val BASE_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )

        // Location permission (Android < 13)
        private val LOCATION_PERMISSION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Nearby WiFi Devices permission (Android 13+)
        private val NEARBY_WIFI_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            emptyArray()
        }
    }

    /**
     * Check if all WiFi Direct permissions are granted
     *
     * @return true if all permissions granted, false otherwise
     */
    fun hasAllPermissions(): Boolean {
        // Check base permissions
        val hasBasePermissions = BASE_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasBasePermissions) {
            Log.d(TAG, "Missing base WiFi permissions")
            return false
        }

        // Check version-specific permissions
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Check NEARBY_WIFI_DEVICES
            val hasNearbyWifi = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNearbyWifi) {
                Log.d(TAG, "Missing NEARBY_WIFI_DEVICES permission (Android 13+)")
            }

            hasNearbyWifi
        } else {
            // Android < 13: Check FINE_LOCATION (required for WiFi Direct)
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocation) {
                Log.d(TAG, "Missing FINE_LOCATION permission (required for WiFi Direct)")
            }

            hasLocation
        }
    }

    /**
     * Get list of missing permissions
     *
     * @return Array of missing permission names
     */
    fun getMissingPermissions(): Array<String> {
        val missing = mutableListOf<String>()

        // Check base permissions
        BASE_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission)
            }
        }

        // Check version-specific permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Check NEARBY_WIFI_DEVICES
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            // Android < 13: Check FINE_LOCATION
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return missing.toTypedArray()
    }

    /**
     * Request WiFi Direct permissions
     * Call this from an Activity
     *
     * @param activity Activity context for requesting permissions
     */
    fun requestPermissions(activity: Activity) {
        val missingPermissions = getMissingPermissions()

        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All WiFi Direct permissions already granted")
            return
        }

        Log.d(TAG, "Requesting WiFi Direct permissions: ${missingPermissions.joinToString()}")

        ActivityCompat.requestPermissions(
            activity,
            missingPermissions,
            WIFI_DIRECT_PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Handle permission request result
     * Call this from onRequestPermissionsResult
     *
     * @param requestCode Request code from onRequestPermissionsResult
     * @param permissions Permissions array from onRequestPermissionsResult
     * @param grantResults Grant results array from onRequestPermissionsResult
     * @return true if all WiFi Direct permissions were granted, false otherwise
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != WIFI_DIRECT_PERMISSION_REQUEST_CODE) {
            return false
        }

        // Check if all requested permissions were granted
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            Log.i(TAG, "All WiFi Direct permissions granted")
        } else {
            Log.w(TAG, "Some WiFi Direct permissions denied")
            // Log which permissions were denied
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "  - Denied: $permission")
                }
            }
        }

        return allGranted
    }

    /**
     * Check if WiFi Direct is available on this device
     *
     * @return true if WiFi Direct is supported, false otherwise
     */
    fun isWiFiDirectAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    }

    /**
     * Get user-friendly explanation for WiFi Direct permissions
     *
     * @return Permission explanation text
     */
    fun getPermissionExplanation(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            """
            WiFi Direct requires the following permissions:

            • Nearby WiFi Devices: To discover and connect to other BitChat users within 100-200 meters
            • WiFi State: To check WiFi availability
            • Change WiFi State: To enable WiFi Direct connections

            Note: WiFi Direct provides 10x better range than Bluetooth (100-200m vs 10-30m)
            """.trimIndent()
        } else {
            """
            WiFi Direct requires the following permissions:

            • Location: Required by Android for WiFi Direct discovery (we don't use your actual location)
            • WiFi State: To check WiFi availability
            • Change WiFi State: To enable WiFi Direct connections

            Note: WiFi Direct provides 10x better range than Bluetooth (100-200m vs 10-30m)
            """.trimIndent()
        }
    }
}
