package com.nexopos.desktop.hardware

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * JNA bindings for libusb-1.0
 * Based on: https://libusb.sourceforge.io/api-1.0/
 */
interface LibUSB : Library {
    companion object {
        val INSTANCE: LibUSB = Native.load("usb-1.0", LibUSB::class.java)
        
        // Error codes
        const val LIBUSB_SUCCESS = 0
        const val LIBUSB_ERROR_IO = -1
        const val LIBUSB_ERROR_INVALID_PARAM = -2
        const val LIBUSB_ERROR_ACCESS = -3
        const val LIBUSB_ERROR_NO_DEVICE = -4
        const val LIBUSB_ERROR_NOT_FOUND = -5
        const val LIBUSB_ERROR_BUSY = -6
        const val LIBUSB_ERROR_TIMEOUT = -7
    }
    
    // Initialize libusb
    fun libusb_init(ctx: PointerByReference?): Int
    
    // Cleanup libusb
    fun libusb_exit(ctx: Pointer?)
    
    // Open device with VID/PID
    fun libusb_open_device_with_vid_pid(
        ctx: Pointer?,
        vendorId: Short,
        productId: Short
    ): Pointer?
    
    // Close device
    fun libusb_close(dev_handle: Pointer?)
    
    // Set auto detach kernel driver
    fun libusb_set_auto_detach_kernel_driver(dev_handle: Pointer?, enable: Int): Int
    
    // Claim interface
    fun libusb_claim_interface(dev_handle: Pointer?, interface_number: Int): Int
    
    // Release interface
    fun libusb_release_interface(dev_handle: Pointer?, interface_number: Int): Int
    
    // Bulk transfer
    fun libusb_bulk_transfer(
        dev_handle: Pointer?,
        endpoint: Byte,
        data: ByteArray,
        length: Int,
        transferred: IntByReference?,
        timeout: Int
    ): Int
    
    // Get error name
    fun libusb_error_name(errcode: Int): String
}
