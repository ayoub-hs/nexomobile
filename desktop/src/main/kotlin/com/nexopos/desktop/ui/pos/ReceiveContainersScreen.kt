package com.nexopos.desktop.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun ReceiveContainersScreen(
    viewModel: ReceiveContainersViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val selectedCustomer = state.customers.firstOrNull { it.id == state.selectedCustomerId }
    val selectedContainerType = state.containerTypes.firstOrNull { it.id == state.selectedContainerTypeId }

    var customerExpanded by remember { mutableStateOf(false) }
    var customerQuery by remember { mutableStateOf("") }

    LaunchedEffect(selectedCustomer?.id) {
        customerQuery = selectedCustomer?.name.orEmpty()
    }

    val filteredCustomers = remember(customerQuery, state.customers) {
        if (customerQuery.isBlank()) {
            state.customers
        } else {
            state.customers.filter { it.name.contains(customerQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Réception des contenants",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Customer selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = customerQuery,
                            onValueChange = {
                                customerQuery = it
                                customerExpanded = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Client") },
                            placeholder = { Text("Rechercher un client") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { customerExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            singleLine = true
                        )
                        DropdownMenu(
                            expanded = customerExpanded,
                            onDismissRequest = { customerExpanded = false }
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Aucun client trouvé") },
                                    onClick = { customerExpanded = false }
                                )
                            } else {
                                filteredCustomers.take(20).forEach { customer ->
                                    DropdownMenuItem(
                                        text = { Text(customer.name) },
                                        onClick = {
                                            customerQuery = customer.name
                                            customerExpanded = false
                                            viewModel.selectCustomer(customer.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isCompact = maxWidth < 900.dp

                        if (isCompact) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CustomerBalancesPanel(
                                    customerName = selectedCustomer?.name,
                                    balances = state.balances,
                                    onSelectContainer = { id -> viewModel.selectContainerType(id) }
                                )
                                ReceiveFormPanel(
                                    containerTypes = state.containerTypes,
                                    selectedContainerType = selectedContainerType,
                                    quantityText = state.quantityText,
                                    notes = state.notes,
                                    isSubmitting = state.isSubmitting,
                                    onSelectContainer = { id -> viewModel.selectContainerType(id) },
                                    onQuantityChange = viewModel::updateQuantityText,
                                    onNotesChange = viewModel::updateNotes,
                                    onSubmit = viewModel::submitReceive
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CustomerBalancesPanel(
                                    customerName = selectedCustomer?.name,
                                    balances = state.balances,
                                    onSelectContainer = { id -> viewModel.selectContainerType(id) },
                                    modifier = Modifier.weight(1f)
                                )
                                ReceiveFormPanel(
                                    containerTypes = state.containerTypes,
                                    selectedContainerType = selectedContainerType,
                                    quantityText = state.quantityText,
                                    notes = state.notes,
                                    isSubmitting = state.isSubmitting,
                                    onSelectContainer = { id -> viewModel.selectContainerType(id) },
                                    onQuantityChange = viewModel::updateQuantityText,
                                    onNotesChange = viewModel::updateNotes,
                                    onSubmit = viewModel::submitReceive,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerBalancesPanel(
    customerName: String?,
    balances: List<com.nexopos.desktop.core.network.CustomerContainerBalance>,
    onSelectContainer: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = customerName?.let { "Soldes des contenants • $it" } ?: "Soldes des contenants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (customerName == null) {
                Text(
                    text = "Sélectionnez un client pour afficher ses soldes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val totalQuantity = balances.sumOf { it.quantityHeld }
                val totalDeposit = balances.sumOf { it.depositTotal }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total contenants", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(totalQuantity.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Dépôt total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(totalDeposit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (balances.isEmpty()) {
                    Text(
                        text = "Aucun solde de contenants pour ce client.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(balances, key = { "${it.customerId}-${it.containerTypeId}" }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.containerTypeName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Dépôt: ${formatCurrency(item.depositTotal)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            formatRemoteTimestamp(item.lastTransactionAt)?.let { timestamp ->
                                                Text(
                                                    text = "Dernier mouvement: $timestamp",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = item.quantityHeld.toString(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.End
                                            )
                                            Text(
                                                text = "Contenants",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = { onSelectContainer(item.containerTypeId.toLong()) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Pré-sélectionner")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiveFormPanel(
    containerTypes: List<com.nexopos.desktop.core.network.ContainerType>,
    selectedContainerType: com.nexopos.desktop.core.network.ContainerType?,
    quantityText: String,
    notes: String,
    isSubmitting: Boolean,
    onSelectContainer: (Long?) -> Unit,
    onQuantityChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var containerExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Réception",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedContainerType?.name ?: "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type de contenant") },
                    placeholder = { Text("Sélectionner un type") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { containerExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    singleLine = true
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { containerExpanded = true }
                )
                DropdownMenu(
                    expanded = containerExpanded,
                    onDismissRequest = { containerExpanded = false }
                ) {
                    if (containerTypes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Aucun type disponible") },
                            onClick = { containerExpanded = false }
                        )
                    } else {
                        containerTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    onSelectContainer(type.id)
                                    containerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Quantité") },
                placeholder = { Text("0") },
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                placeholder = { Text("Notes optionnelles") },
                minLines = 3,
                maxLines = 3
            )

            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("Recevoir")
            }
        }
    }
}

private fun formatCurrency(value: Double): String {
    return String.format(Locale.getDefault(), "%.3f DT", value)
}

private fun formatRemoteTimestamp(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val instant = parseRemoteInstant(raw) ?: return raw
    return formatInstant(instant)
}

private fun formatInstant(instant: Instant): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date.from(instant))
}

private fun parseRemoteInstant(raw: String): Instant? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    try {
        return Instant.parse(trimmed)
    } catch (_: Exception) {
        // Ignore ISO parse failures
    }

    if (trimmed.endsWith("Z")) {
        val withoutZone = trimmed.removeSuffix("Z")
        val utcPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in utcPatterns) {
            try {
                val dt = LocalDateTime.parse(withoutZone, DateTimeFormatter.ofPattern(pattern))
                return dt.toInstant(ZoneOffset.UTC)
            } catch (_: Exception) {
                // try next
            }
        }
    }

    val localPatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in localPatterns) {
        try {
            val dt = LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern))
            return dt.atZone(ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            // try next
        }
    }

    return null
}
