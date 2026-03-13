package com.nexopos.erp.feature.settings.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.nexopos.erp.ui.theme.appColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.koin.androidx.compose.koinViewModel
import com.nexopos.erp.R
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.print.PrinterConfig
import com.nexopos.erp.core.print.PrinterType
import com.nexopos.erp.print.PrintUtil
import kotlinx.coroutines.launch

import com.nexopos.erp.feature.settings.vm.SettingsViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppTextField

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = koinViewModel()

    val state by vm.state.collectAsState()

    var urlState by remember { mutableStateOf(state.baseUrl.ifEmpty { SettingsRepository.DEFAULT_BASE_URL }) }
    var printerMac by remember { mutableStateOf(state.printerConfig.macAddress ?: "") }
    var storeNameState by remember { mutableStateOf(state.storeName.ifEmpty { SettingsRepository.DEFAULT_STORE_NAME }) }
    var logoUri by remember { mutableStateOf(state.printerConfig.logoUri?.let(Uri::parse)) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    LaunchedEffect(state.baseUrl) { urlState = state.baseUrl }
    LaunchedEffect(state.storeName) { storeNameState = state.storeName }
    LaunchedEffect(state.printerConfig.macAddress, state.printerConfig.logoUri) {
        printerMac = state.printerConfig.macAddress.orEmpty()
        logoUri = state.printerConfig.logoUri?.let(Uri::parse)
    }

    val scope = rememberCoroutineScope()
    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.isEmpty() || results.values.any { !it }) {
            Toast.makeText(context, context.getString(R.string.toast_bluetooth_permissions_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val resolver = context.contentResolver
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                resolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Ignore if permission already granted
            }
        }
        val previous = logoUri
        logoUri = uri
        if (previous != null && previous != uri) {
            try {
                resolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Ignore if permission could not be released
            }
        }
    }

    fun ensureBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val missing = listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        return if (missing.isEmpty()) {
            true
        } else {
            btPermissionLauncher.launch(missing.toTypedArray())
            false
        }
    }

    var printerDropdownExpanded by remember { mutableStateOf(false) }

    // Tablet-optimized layout using BoxWithConstraints
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.surfaceVariant.copy(alpha = 0.3f))
    ) {
        val isWideScreen = maxWidth > 600.dp
        val horizontalPadding = if (isWideScreen) 32.dp else 16.dp
        val contentMaxWidth = if (isWideScreen) 900.dp else maxWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalAlignment = if (isWideScreen) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Two-column layout for tablets
            if (isWideScreen) {
                Row(
                    modifier = Modifier.widthIn(max = contentMaxWidth),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // API Settings Card
                    ApiSettingsCard(
                        urlState = urlState,
                        onUrlChange = { urlState = it },
                        storeNameState = storeNameState,
                        onStoreNameChange = { storeNameState = it },
                        onSave = {
                            vm.save(urlState, storeNameState)
                            Toast.makeText(context, context.getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Printer Settings Card
                    PrinterSettingsCard(
                        printerMac = printerMac,
                        onPrinterMacChange = { printerMac = it },
                        pairedDevices = pairedDevices,
                        onPairedDevicesChange = { pairedDevices = it },
                        printerDropdownExpanded = printerDropdownExpanded,
                        onDropdownExpandedChange = { printerDropdownExpanded = it },
                        logoUri = logoUri,
                        onLogoUriChange = { logoUri = it },
                        ensureBluetoothPermissions = ::ensureBluetoothPermissions,
                        onSavePrinter = { cfg ->
                            vm.savePrinter(cfg)
                            Toast.makeText(context, context.getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                        },
                        onPrintTest = { cfg ->
                            scope.launch {
                                try {
                                    PrintUtil.printSampleReceipt(context, cfg)
                                    Toast.makeText(context, context.getString(R.string.toast_printed), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: context.getString(R.string.toast_print_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSelectLogo = { logoPickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Appearance Settings Card (Theme Toggle)
                AppearanceSettingsCard(
                    currentTheme = state.themeMode,
                    onThemeChange = { vm.setThemeMode(it) },
                    encryptionAvailable = state.encryptionAvailable,
                    encryptionError = state.encryptionError,
                    modifier = Modifier.widthIn(max = contentMaxWidth)
                )
            } else {
                // Single column layout for phones
                ApiSettingsCard(
                    urlState = urlState,
                    onUrlChange = { urlState = it },
                    storeNameState = storeNameState,
                    onStoreNameChange = { storeNameState = it },
                    onSave = {
                        vm.save(urlState, storeNameState)
                        Toast.makeText(context, context.getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                PrinterSettingsCard(
                    printerMac = printerMac,
                    onPrinterMacChange = { printerMac = it },
                    pairedDevices = pairedDevices,
                    onPairedDevicesChange = { pairedDevices = it },
                    printerDropdownExpanded = printerDropdownExpanded,
                    onDropdownExpandedChange = { printerDropdownExpanded = it },
                    logoUri = logoUri,
                    onLogoUriChange = { logoUri = it },
                    ensureBluetoothPermissions = ::ensureBluetoothPermissions,
                    onSavePrinter = { cfg ->
                        vm.savePrinter(cfg)
                        Toast.makeText(context, context.getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                    },
                    onPrintTest = { cfg ->
                        scope.launch {
                            try {
                                PrintUtil.printSampleReceipt(context, cfg)
                                Toast.makeText(context, context.getString(R.string.toast_printed), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: context.getString(R.string.toast_print_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSelectLogo = { logoPickerLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Appearance Settings Card (Theme Toggle)
                AppearanceSettingsCard(
                    currentTheme = state.themeMode,
                    onThemeChange = { vm.setThemeMode(it) },
                    encryptionAvailable = state.encryptionAvailable,
                    encryptionError = state.encryptionError,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            AppButtonPrimary(
                onClick = {
                    vm.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.logout_button),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun ApiSettingsCard(
    urlState: String,
    onUrlChange: (String) -> Unit,
    storeNameState: String,
    onStoreNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section Title
            Text(
                text = stringResource(R.string.settings_api_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.text
            )

            // API Fields
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = urlState,
                    onValueChange = onUrlChange,
                    label = stringResource(R.string.label_base_url),
                    singleLine = true
                )

                AppTextField(
                    value = storeNameState,
                    onValueChange = onStoreNameChange,
                    label = stringResource(R.string.label_store_name),
                    singleLine = true
                )
            }

            // Save Button
            AppButtonPrimary(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    stringResource(R.string.button_save),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun PrinterSettingsCard(
    printerMac: String,
    onPrinterMacChange: (String) -> Unit,
    pairedDevices: List<BluetoothDevice>,
    onPairedDevicesChange: (List<BluetoothDevice>) -> Unit,
    printerDropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    logoUri: Uri?,
    onLogoUriChange: (Uri?) -> Unit,
    ensureBluetoothPermissions: () -> Boolean,
    onSavePrinter: (PrinterConfig) -> Unit,
    onPrintTest: (PrinterConfig) -> Unit,
    onSelectLogo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    val selectedDevice = pairedDevices.firstOrNull { it.address == printerMac }
    val printerDisplayName = selectedDevice?.let {
        val name = it.name ?: context.getString(R.string.printer_device_unknown)
        context.getString(R.string.printer_device_display, name, it.address)
    } ?: ""
    val placeholderText = when {
        pairedDevices.isEmpty() -> stringResource(R.string.placeholder_no_paired_devices)
        printerDisplayName.isBlank() -> stringResource(R.string.placeholder_select_printer)
        else -> ""
    }

    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section Title
            Text(
                text = stringResource(R.string.settings_printer_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.text
            )

            // Printer Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppTextField(
                        value = printerDisplayName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = pairedDevices.isNotEmpty(),
                        label = stringResource(R.string.label_paired_printer),
                        placeholder = placeholderText.takeIf { it.isNotEmpty() },
                        trailingIcon = { Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = pairedDevices.isNotEmpty(),
                                interactionSource = interactionSource,
                                indication = null
                            ) { onDropdownExpandedChange(true) }
                    )
                    DropdownMenu(
                        expanded = printerDropdownExpanded,
                        onDismissRequest = { onDropdownExpandedChange(false) }
                    ) {
                        pairedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(device.name ?: context.getString(R.string.printer_device_unknown))
                                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onPrinterMacChange(device.address)
                                    onDropdownExpandedChange(false)
                                }
                            )
                        }
                    }
                }
                AppButtonSecondary(
                    onClick = {
                        if (!ensureBluetoothPermissions()) return@AppButtonSecondary
                        val manager = context.getSystemService(BluetoothManager::class.java)
                        val adapter: BluetoothAdapter? = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                        val devices = adapter?.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList()
                        onPairedDevicesChange(devices)
                        if (devices.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.toast_no_paired_devices), Toast.LENGTH_SHORT).show()
                        } else {
                            onPrinterMacChange(devices.first().address)
                            onDropdownExpandedChange(true)
                        }
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(stringResource(R.string.button_refresh))
                }
            }

            // Primary Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButtonPrimary(
                    onClick = {
                        if (printerMac.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.toast_select_printer_before_saving), Toast.LENGTH_SHORT).show()
                            return@AppButtonPrimary
                        }
                        val cfg = PrinterConfig(
                            type = PrinterType.Bluetooth,
                            macAddress = printerMac.trim().ifBlank { "" },
                            host = null,
                            port = 9100,
                            paperWidthDots = 384,
                            logoUri = logoUri?.toString()
                        )
                        onPrinterMacChange(cfg.macAddress ?: "")
                        onSavePrinter(cfg)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        stringResource(R.string.button_save_printer),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                AppButtonPrimary(
                    onClick = {
                        if (!ensureBluetoothPermissions()) return@AppButtonPrimary
                        if (printerMac.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.toast_select_printer_before_printing), Toast.LENGTH_SHORT).show()
                            return@AppButtonPrimary
                        }
                        val cfg = PrinterConfig(
                            type = PrinterType.Bluetooth,
                            macAddress = printerMac.trim().ifBlank { "" },
                            host = null,
                            port = 9100,
                            paperWidthDots = 384,
                            logoUri = logoUri?.toString()
                        )
                        onPrintTest(cfg)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        stringResource(R.string.button_print_test),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            // Logo Management Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButtonSecondary(
                    onClick = onSelectLogo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.button_select_logo))
                }
                AppButtonSecondary(
                    onClick = { onLogoUriChange(null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.button_remove_logo))
                }
            }

            // Logo Status
            logoUri?.let {
                Text(
                    text = stringResource(R.string.button_logo_selected, it.lastPathSegment ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted
                )
            }
        }
    }

    // Logout Section

}

/**
 * Appearance settings card with theme toggle
 */
@Composable
private fun AppearanceSettingsCard(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    encryptionAvailable: Boolean,
    encryptionError: String?,
    modifier: Modifier = Modifier
) {
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section Title
            Text(
                text = stringResource(R.string.settings_appearance_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.text
            )
            
            // Theme Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.theme_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.appColors.text
                )
                
                Box {
                    AppButtonSecondary(
                        onClick = { themeDropdownExpanded = true },
                        tonal = false
                    ) {
                        Text(
                            text = when (currentTheme) {
                                "light" -> stringResource(R.string.theme_light)
                                "dark" -> stringResource(R.string.theme_dark)
                                else -> stringResource(R.string.theme_system)
                            }
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_system)) },
                            onClick = {
                                onThemeChange("system")
                                themeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_light)) },
                            onClick = {
                                onThemeChange("light")
                                themeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_dark)) },
                            onClick = {
                                onThemeChange("dark")
                                themeDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Encryption Status Warning
            if (!encryptionAvailable || encryptionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.appColors.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.appColors.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = encryptionError ?: stringResource(R.string.encryption_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onErrorContainer
                    )
                }
            }
        }
    }
}
