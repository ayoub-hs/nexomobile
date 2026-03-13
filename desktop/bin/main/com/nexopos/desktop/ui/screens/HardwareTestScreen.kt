package com.nexopos.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.*
import com.nexopos.desktop.hardware.DesktopBarcodeScanner
import com.nexopos.desktop.hardware.DesktopCashDrawer
import com.nexopos.desktop.hardware.DesktopReceiptPrinter
import com.nexopos.shared.hardware.*
import kotlinx.coroutines.launch

/**
 * Hardware test screen for validating USB devices on H313.
 *
 * Use this to test:
 * - Barcode scanner input (automatic keyboard wedge mode)
 * - Manual barcode entry
 * - ESC/POS printer output
 * - Cash drawer control
 */
@Composable
fun HardwareTestScreen(onBack: () -> Unit = {}) {
    var barcodeInput by remember { mutableStateOf("") }
    var lastScannedBarcode by remember { mutableStateOf("None") }
    var printerStatus by remember { mutableStateOf("Unknown") }
    var drawerStatus by remember { mutableStateOf("Unknown") }
    var testMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Hardware instances (in real app, these would be injected)
    val scanner = remember { DesktopBarcodeScanner() }
    val printer = remember { DesktopReceiptPrinter() }
    val drawer = remember { DesktopCashDrawer() }

    // Start scanner and listen for scanned barcodes
    LaunchedEffect(Unit) {
        scanner.startScanning()
        scanner.scannedBarcodes.collect { barcode ->
            lastScannedBarcode = barcode
            testMessage = "Barcode scanned: $barcode"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .onPreviewKeyEvent { event ->
                // Feed keyboard input to scanner for automatic barcode detection
                if (event.type == KeyEventType.KeyDown) {
                    scanner.processKeyEvent(event)
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Text(
                text = "Hardware Test Panel",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Divider()

        // Barcode Scanner Test
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Scanner")
                    Text(
                        text = "USB Barcode Scanner",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text(
                    "Last scanned: $lastScannedBarcode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (lastScannedBarcode != "None") FontWeight.Bold else FontWeight.Normal,
                    color = if (lastScannedBarcode != "None") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Automatic mode: Scan barcode with USB scanner (should auto-detect)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = { barcodeInput = it },
                    label = { Text("Manual entry: Type barcode and click Submit") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                scanner.onBarcodeDetected(barcodeInput)
                                barcodeInput = ""
                            }
                        }) {
                            Icon(Icons.Default.Send, "Submit")
                        }
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                scanner.startScanning()
                                testMessage = "Scanner started - ready for input"
                            }
                        },
                        enabled = !scanner.isScanning
                    ) {
                        Text("Start Scanner")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                scanner.stopScanning()
                                testMessage = "Scanner stopped"
                            }
                        },
                        enabled = scanner.isScanning
                    ) {
                        Text("Stop Scanner")
                    }

                    if (scanner.isScanning) {
                        Text(
                            "✓ Scanner is active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Printer Test
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Print, "Printer")
                    Text(
                        text = "USB ESC/POS Thermal Printer",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text("Status: $printerStatus")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                printerStatus = if (printer.isReady()) "Ready" else "Not Ready"
                            }
                        }
                    ) {
                        Text("Check Status")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val testReceipt = Receipt(
                                    storeName = "NexoPOS Test",
                                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date()),
                                    customerName = "Test Customer",
                                    items = listOf(
                                        ReceiptItem("Test Item 1", 2.0, "5.000 DT", "10.000 DT"),
                                        ReceiptItem("Test Item 2", 1.0, "15.000 DT", "15.000 DT")
                                    ),
                                    subtotal = "25.000 DT",
                                    tax = "4.000 DT",
                                    total = "25.000 DT",
                                    paymentMethod = "Cash",
                                    footer = "Merci!"
                                )

                                val result = printer.printReceipt(testReceipt)
                                testMessage = if (result.isSuccess) {
                                    "Print successful"
                                } else {
                                    "Print failed: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    ) {
                        Text("Print Test Receipt")
                    }
                }
            }
        }

        // Cash Drawer Test
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Inbox, "Cash Drawer")
                    Text(
                        text = "USB Serial Cash Drawer",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text("Status: $drawerStatus")
                Text("Device: /dev/ttyUSB0", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                drawerStatus = if (drawer.isConnected()) "Connected" else "Disconnected"
                            }
                        }
                    ) {
                        Text("Check Connection")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val result = drawer.open()
                                testMessage = if (result.isSuccess) {
                                    "Drawer opened"
                                } else {
                                    "Failed: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Open Drawer")
                    }
                }
            }
        }

        // Status message
        if (testMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = testMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Setup Instructions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
                        Barcode Scanner:
                        - Automatic: Just scan - barcode detected at window level
                        - Manual: Type in text field and click Submit button
                        - USB scanners work as keyboard wedge (no drivers needed)

                        Hardware Setup:
                        1. Add user to groups: sudo usermod -aG dialout,lp [username]
                        2. Check permissions: ls -l /dev/ttyUSB* /dev/usb/lp*
                        3. Printer: /dev/usb/lp0 or /dev/ttyUSB1
                        4. Cash drawer: /dev/ttyUSB0
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
