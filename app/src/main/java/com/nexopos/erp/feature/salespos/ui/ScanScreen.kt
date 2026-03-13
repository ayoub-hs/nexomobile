package com.nexopos.erp.feature.salespos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import android.widget.Toast
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.nexopos.erp.ui.theme.appColors
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.nexopos.erp.R
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.repo.ProductRepository
import com.nexopos.erp.feature.salespos.ui.CartViewModel
import com.nexopos.erp.feature.salespos.ui.QuantityDialog
import com.nexopos.erp.feature.salespos.ui.QuantityDialogData
import com.nexopos.erp.ui.components.AppButtonPrimary
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

class ScanViewModel(private val repo: ProductRepository) : ViewModel() {
    var lastCode by mutableStateOf<String?>(null)
        private set
    var product by mutableStateOf<Product?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var fetchJob: Job? = null
    private var lastProcessedCode: String? = null
    private var lastProcessedAt: Long = 0L
    private var lastSuccessAt: Long = 0L

    private val scanCooldownMs = 5_000L

    fun onBarcode(code: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSuccessAt < scanCooldownMs) {
            return
        }
        if (code == lastProcessedCode && now - lastProcessedAt < scanCooldownMs) {
            return
        }
        lastProcessedCode = code
        lastProcessedAt = now
        if (code == lastCode) return
        lastCode = code
        error = null // Clear error on new search
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            isLoading = true
            try {
                product = repo.searchByBarcode(code).getOrNull()
                lastSuccessAt = SystemClock.elapsedRealtime()
            } catch (e: Exception) {
                product = null
                error = e.message ?: "Barcode search failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearSelection() {
        product = null
        lastCode = null
        error = null
    }

    fun clearError() {
        error = null
    }
}

@Composable
fun ScanScreen(cartViewModel: CartViewModel, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: ScanViewModel = org.koin.androidx.compose.koinViewModel()

    var quantityDialogData by remember { mutableStateOf<QuantityDialogData?>(null) }

    LaunchedEffect(vm.product) {
        val product = vm.product ?: return@LaunchedEffect
        val units = product.unitQuantities.orEmpty()
        if (units.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_units_for, product.name),
                Toast.LENGTH_SHORT
            ).show()
            vm.clearSelection()
            return@LaunchedEffect
        }

        val pricedUnits = units.filter {
            (it.salePrice ?: 0.0) > 0.0 || (it.wholesalePriceWithTax ?: 0.0) > 0.0
        }
        if (pricedUnits.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_price_for, product.name),
                Toast.LENGTH_SHORT
            ).show()
            vm.clearSelection()
            return@LaunchedEffect
        }

        quantityDialogData = buildQuantityDialogData(context, product)
        if (quantityDialogData == null) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_price_for, product.name),
                Toast.LENGTH_SHORT
            ).show()
            vm.clearSelection()
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(onBarcode = vm::onBarcode, onClose = onClose)
        
        // Error display
        vm.error?.let { errorMessage ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(androidx.compose.ui.Alignment.BottomCenter),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.appColors.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.appColors.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.clearError() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.appColors.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    quantityDialogData?.let { data ->
        QuantityDialog(
            data = data,
            onConfirm = { result ->
                if (result.unitPrice <= 0.0) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_no_price_for, data.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    cartViewModel.addProduct(
                        productId = data.productId,
                        name = data.name,
                        unitQuantityId = result.option.unitQuantityId,
                        unitId = result.option.unitId,
                        unitName = result.option.unitName,
                        unitPrice = result.unitPrice,
                        quantity = result.quantity,
                        salePrice = result.option.salePrice?.takeIf { it > 0.0 },
                        wholesalePriceWithTax = result.option.wholesalePriceWithTax?.takeIf { it > 0.0 },
                        useWholesale = result.useWholesale,
                        isCustomPrice = false,
                        containerLink = result.option.containerLink,
                        hasContainerMetadata = result.option.containerLink != null
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_added_product, data.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    quantityDialogData = null
                    vm.clearSelection()
                    onClose()
                }
            },
            onDismiss = {
                quantityDialogData = null
                vm.clearSelection()
            }
        )
    }
}

@Composable
private fun CameraPreview(onBarcode: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isFlashOn by remember { mutableStateOf(false) }

    // If permission not granted, show info text
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    var requestedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasPermission && !requestedOnce) {
            requestedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    if (!hasPermission) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Camera permission required to scan.")
            AppButtonPrimary(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Grant Permission")
            }
        }
        return
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember(onBarcode) { BarcodeAnalyzer(onBarcode) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cameraController.setImageAnalysisAnalyzer(executor, analyzer)
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    cameraController.clearImageAnalysisAnalyzer()
                    cameraController.unbind()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            lifecycleOwner.lifecycle.removeObserver(observer)
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController
                }
            }
        )

        // Scan Frame (Centered)
        Box(
            modifier = Modifier
                .size(280.dp)
                .border(3.dp, MaterialTheme.appColors.primary, RoundedCornerShape(12.dp))
                .align(androidx.compose.ui.Alignment.Center)
        )

        // Flash Toggle (Bottom Center)
        IconButton(
            onClick = {
                isFlashOn = !isFlashOn
                cameraController.enableTorch(isFlashOn)
            },
            modifier = Modifier
                .size(56.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Icon(
                imageVector = if (isFlashOn) {
                    androidx.compose.material.icons.Icons.Filled.FlashlightOn
                } else {
                    androidx.compose.material.icons.Icons.Filled.FlashlightOff
                },
                contentDescription = "Flash",
                tint = MaterialTheme.appColors.text,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()
    private val scanner = BarcodeScanning.getClient(options)

    private var lock = false

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        if (lock) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { it.displayValue != null }?.displayValue
                if (!value.isNullOrBlank()) {
                    lock = true
                    onBarcode(value)
                }
            }
            .addOnFailureListener {
                // Ignore individual frame failures
            }
            .addOnCompleteListener {
                imageProxy.close()
                lock = false
            }
    }
}
