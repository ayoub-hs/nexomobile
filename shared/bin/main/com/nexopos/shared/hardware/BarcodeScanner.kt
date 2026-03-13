package com.nexopos.shared.hardware

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic barcode scanner interface.
 * 
 * Android: Uses CameraX + MLKit
 * Desktop: Uses USB keyboard wedge input or direct device access
 */
interface BarcodeScanner {
    /**
     * Flow of scanned barcodes.
     * Emits barcode string whenever a scan is detected.
     */
    val scannedBarcodes: Flow<String>
    
    /**
     * Start listening for barcode scans.
     */
    suspend fun startScanning()
    
    /**
     * Stop listening for barcode scans.
     */
    suspend fun stopScanning()
    
    /**
     * Check if scanner is currently active.
     */
    val isScanning: Boolean
}
