package com.nexopos.erp.feature.salespos.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun MissingRegisterCard(
    title: String,
    message: String,
    onManageRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.appColors.warningDim
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.text
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.text
        )
        AppButtonTertiary(onClick = onManageRegister) {
            Text(stringResource(R.string.register_manage_action))
        }
    }
}

@Composable
fun CurrentRegisterCard(
    register: Register,
    onManageRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.register_current_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted
                )
                Text(
                    text = register.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.text
                )
                Text(
                    text = stringResource(R.string.register_balance_label, formatAppCurrency(register.balance)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.text
                )
                RegisterStatusPill(status = register.status)
            }
            AppButtonTertiary(onClick = onManageRegister) {
                Text(stringResource(R.string.register_manage_action))
            }
        }
    }
}

private enum class RegisterSheet {
    Quick,
    Selection,
    History
}

private sealed interface RegisterDialogState {
    data class Open(val register: Register) : RegisterDialogState
    data object Close : RegisterDialogState
    data object CashIn : RegisterDialogState
    data object CashOut : RegisterDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterHost(
    registerViewModel: RegisterViewModel,
    isOnline: Boolean,
    visible: Boolean,
    onDismissRequest: () -> Unit
) {
    val uiState by registerViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var activeSheet by remember(visible) {
        mutableStateOf(if (visible) RegisterSheet.Quick else null)
    }
    var dialogState by remember(visible) { mutableStateOf<RegisterDialogState?>(null) }

    LaunchedEffect(visible) {
        if (!visible) {
            activeSheet = null
            dialogState = null
        } else if (activeSheet == null) {
            activeSheet = RegisterSheet.Quick
        }
    }

    LaunchedEffect(Unit) {
        registerViewModel.events.collect { event ->
            if (event is RegisterEvent.Message && event.message.isNotBlank()) {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    when {
        visible && activeSheet == RegisterSheet.Quick -> {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                containerColor = MaterialTheme.appColors.surfaceOverlay,
                shape = RoundedCornerShape(
                    topStart = MaterialTheme.appRadii.large,
                    topEnd = MaterialTheme.appRadii.large
                )
            ) {
                RegisterQuickSheet(
                    uiState = uiState,
                    isOnline = isOnline,
                    onOpenSelection = { activeSheet = RegisterSheet.Selection },
                    onShowHistory = { activeSheet = RegisterSheet.History },
                    onOpenCashIn = { dialogState = RegisterDialogState.CashIn },
                    onOpenCashOut = { dialogState = RegisterDialogState.CashOut },
                    onOpenClose = { dialogState = RegisterDialogState.Close },
                    onDismiss = onDismissRequest
                )
            }
        }

        visible && activeSheet == RegisterSheet.Selection -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = RegisterSheet.Quick },
                containerColor = MaterialTheme.appColors.surfaceOverlay,
                shape = RoundedCornerShape(
                    topStart = MaterialTheme.appRadii.large,
                    topEnd = MaterialTheme.appRadii.large
                )
            ) {
                RegisterSelectionSheet(
                    registers = uiState.registers,
                    currentRegister = uiState.currentRegister,
                    isLoading = uiState.isLoading,
                    isOnline = isOnline,
                    onSelectRegister = { register ->
                        dialogState = RegisterDialogState.Open(register)
                    },
                    onBack = { activeSheet = RegisterSheet.Quick }
                )
            }
        }

        visible && activeSheet == RegisterSheet.History -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = RegisterSheet.Quick },
                containerColor = MaterialTheme.appColors.surfaceOverlay,
                shape = RoundedCornerShape(
                    topStart = MaterialTheme.appRadii.large,
                    topEnd = MaterialTheme.appRadii.large
                )
            ) {
                RegisterHistorySheet(
                    currentRegister = uiState.currentRegister,
                    history = uiState.registerHistory,
                    onBack = { activeSheet = RegisterSheet.Quick }
                )
            }
        }
    }

    when (val currentDialog = dialogState) {
        is RegisterDialogState.Open -> {
            RegisterAmountDialog(
                title = stringResource(R.string.register_open_title, currentDialog.register.name),
                confirmLabel = stringResource(R.string.register_open_action),
                amountLabel = stringResource(R.string.register_amount_label),
                descriptionLabel = stringResource(R.string.register_description_label),
                isSubmitting = uiState.isSubmitting,
                onDismiss = { if (!uiState.isSubmitting) dialogState = null },
                onSubmit = { amount, description ->
                    registerViewModel.openRegister(currentDialog.register.id, amount, description)
                },
                onSuccess = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.register_open_success, currentDialog.register.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialogState = null
                    activeSheet = RegisterSheet.Quick
                }
            )
        }

        RegisterDialogState.Close -> {
            RegisterAmountDialog(
                title = stringResource(R.string.register_close_title),
                confirmLabel = stringResource(R.string.register_close_action),
                amountLabel = stringResource(R.string.register_amount_label),
                descriptionLabel = stringResource(R.string.register_description_label),
                isSubmitting = uiState.isSubmitting,
                onDismiss = { if (!uiState.isSubmitting) dialogState = null },
                onSubmit = { amount, description ->
                    registerViewModel.closeCurrentRegister(amount, description)
                },
                onSuccess = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.register_close_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialogState = null
                    activeSheet = RegisterSheet.Quick
                }
            )
        }

        RegisterDialogState.CashIn -> {
            RegisterAmountDialog(
                title = stringResource(R.string.register_cash_in_title),
                confirmLabel = stringResource(R.string.register_cash_in_action),
                amountLabel = stringResource(R.string.register_amount_label),
                descriptionLabel = stringResource(R.string.register_description_label),
                isSubmitting = uiState.isSubmitting,
                onDismiss = { if (!uiState.isSubmitting) dialogState = null },
                onSubmit = { amount, description ->
                    registerViewModel.cashInCurrentRegister(amount, description)
                },
                onSuccess = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.register_cash_in_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialogState = null
                }
            )
        }

        RegisterDialogState.CashOut -> {
            RegisterAmountDialog(
                title = stringResource(R.string.register_cash_out_title),
                confirmLabel = stringResource(R.string.register_cash_out_action),
                amountLabel = stringResource(R.string.register_amount_label),
                descriptionLabel = stringResource(R.string.register_description_label),
                isSubmitting = uiState.isSubmitting,
                maxAmount = uiState.currentRegister?.balance,
                onDismiss = { if (!uiState.isSubmitting) dialogState = null },
                onSubmit = { amount, description ->
                    registerViewModel.cashOutCurrentRegister(amount, description)
                },
                onSuccess = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.register_cash_out_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialogState = null
                }
            )
        }

        null -> Unit
    }
}

@Composable
private fun RegisterQuickSheet(
    uiState: RegisterUiState,
    isOnline: Boolean,
    onOpenSelection: () -> Unit,
    onShowHistory: () -> Unit,
    onOpenCashIn: () -> Unit,
    onOpenCashOut: () -> Unit,
    onOpenClose: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.appSpacing.screen,
                end = MaterialTheme.appSpacing.screen,
                bottom = MaterialTheme.appSpacing.xxl
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.register_manage_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.appColors.text
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.appSpacing.lg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (!isOnline) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.appColors.warningDim
            ) {
                Text(
                    text = stringResource(R.string.register_offline_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.text
                )
            }
        }

        uiState.error?.takeIf { it.isNotBlank() }?.let { error ->
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.appColors.errorDim
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.text
                )
            }
        }

        uiState.currentRegister?.let { currentRegister ->
            CurrentRegisterCard(
                register = currentRegister,
                onManageRegister = onOpenSelection
            )

            RegisterActionCard(
                icon = Icons.Filled.History,
                title = stringResource(R.string.register_history_action),
                enabled = true,
                onClick = onShowHistory
            )
            RegisterActionCard(
                icon = Icons.Filled.Add,
                title = stringResource(R.string.register_cash_in_action),
                enabled = isOnline,
                onClick = onOpenCashIn
            )
            RegisterActionCard(
                icon = Icons.Filled.Remove,
                title = stringResource(R.string.register_cash_out_action),
                enabled = isOnline,
                onClick = onOpenCashOut
            )
            RegisterActionCard(
                icon = Icons.Filled.Close,
                title = stringResource(R.string.register_close_action),
                enabled = isOnline,
                onClick = onOpenClose
            )
            RegisterActionCard(
                icon = Icons.Filled.ChangeCircle,
                title = stringResource(R.string.register_change_action),
                enabled = isOnline,
                onClick = onOpenSelection
            )
        } ?: run {
            MissingRegisterCard(
                title = stringResource(R.string.register_none_title),
                message = stringResource(R.string.register_none_message),
                onManageRegister = onOpenSelection
            )
        }

        AppButtonSecondary(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            tonal = true
        ) {
            Text(stringResource(R.string.common_cancel))
        }
    }
}

@Composable
private fun RegisterSelectionSheet(
    registers: List<Register>,
    currentRegister: Register?,
    isLoading: Boolean,
    isOnline: Boolean,
    onSelectRegister: (Register) -> Unit,
    onBack: () -> Unit
) {
    val availableRegisters = remember(registers) {
        registers.filterNot { it.status.equals("disabled", ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.appSpacing.screen,
                end = MaterialTheme.appSpacing.screen,
                bottom = MaterialTheme.appSpacing.xxl
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.register_select_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.appColors.text
        )

        if (isLoading && availableRegisters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.appSpacing.lg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (availableRegisters.isEmpty()) {
            AppEmptyState(
                title = stringResource(R.string.register_select_empty_title),
                message = stringResource(R.string.register_select_empty_message)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                items(availableRegisters, key = { it.id }) { register ->
                    val isOpenable = register.status.equals("closed", ignoreCase = true) && isOnline
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = if (isOpenable) ({ onSelectRegister(register) }) else null,
                        containerColor = if (register.id == currentRegister?.id) {
                            MaterialTheme.appColors.primaryDim
                        } else {
                            MaterialTheme.appColors.surfaceRaised
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                            ) {
                                Text(
                                    text = register.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.appColors.text
                                )
                                register.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.appColors.muted
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.register_balance_label,
                                        formatAppCurrency(register.balance)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.muted
                                )
                            }
                            RegisterStatusPill(status = register.status)
                        }
                        if (!isOpenable) {
                            Text(
                                text = when {
                                    !isOnline -> stringResource(R.string.register_offline_unavailable)
                                    register.status.equals("opened", ignoreCase = true) ->
                                        stringResource(R.string.register_select_unavailable_opened)
                                    else -> stringResource(R.string.register_select_unavailable)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.muted
                            )
                        }
                    }
                }
            }
        }

        AppButtonSecondary(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            tonal = true
        ) {
            Text(stringResource(R.string.common_cancel))
        }
    }
}

@Composable
private fun RegisterHistorySheet(
    currentRegister: Register?,
    history: List<RegisterHistory>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.appSpacing.screen,
                end = MaterialTheme.appSpacing.screen,
                bottom = MaterialTheme.appSpacing.xxl
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.register_history_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.appColors.text
        )

        currentRegister?.let {
            Text(
                text = it.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.muted
            )
        }

        if (history.isEmpty()) {
            AppEmptyState(
                title = stringResource(R.string.register_history_empty_title),
                message = stringResource(R.string.register_history_empty_message)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                items(history, key = { "${it.id}-${it.createdAt}" }) { entry ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.appColors.surfaceRaised
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                            ) {
                                Text(
                                    text = actionLabel(entry.action),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.appColors.text
                                )
                                entry.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.appColors.muted
                                    )
                                }
                                Text(
                                    text = entry.createdAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.muted
                                )
                            }
                            Text(
                                text = formatAppCurrency(entry.value),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = historyValueColor(entry.action, entry.transactionType, entry.value)
                            )
                        }
                    }
                }
            }
        }

        AppButtonSecondary(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            tonal = true
        ) {
            Text(stringResource(R.string.common_cancel))
        }
    }
}

@Composable
private fun RegisterStatusPill(status: String) {
    val (label, background) = when (status.lowercase()) {
        "opened" -> stringResource(R.string.register_status_opened) to MaterialTheme.appColors.successDim
        "disabled" -> stringResource(R.string.register_status_disabled) to MaterialTheme.appColors.errorDim
        else -> stringResource(R.string.register_status_closed) to MaterialTheme.appColors.surfaceVariant
    }
    Text(
        text = label,
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.appColors.text
    )
}

@Composable
private fun RegisterActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onClick else null,
        containerColor = if (enabled) {
            MaterialTheme.appColors.surfaceRaised
        } else {
            MaterialTheme.appColors.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (enabled) MaterialTheme.appColors.primaryDim else MaterialTheme.appColors.surface,
                        shape = RoundedCornerShape(MaterialTheme.appRadii.medium)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.appColors.primary else MaterialTheme.appColors.muted
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.appColors.text else MaterialTheme.appColors.muted
            )
        }
    }
}

@Composable
private fun RegisterAmountDialog(
    title: String,
    confirmLabel: String,
    amountLabel: String,
    descriptionLabel: String,
    isSubmitting: Boolean,
    maxAmount: Double? = null,
    onDismiss: () -> Unit,
    onSubmit: suspend (Double, String) -> Result<*>,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
                AppTextField(
                    value = amount,
                    onValueChange = { value ->
                        val cleaned = value.replace(',', '.')
                        if (cleaned.isEmpty() || Regex("^\\d*(?:\\.\\d{0,3})?$").matches(cleaned)) {
                            amount = cleaned
                            errorMessage = null
                        }
                    },
                    label = amountLabel,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                AppTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        errorMessage = null
                    },
                    label = descriptionLabel,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.error
                    )
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    when {
                        amountValue == null || amountValue <= 0.0 -> {
                            errorMessage = context.getString(R.string.register_validation_amount_positive)
                        }
                        maxAmount != null && amountValue - maxAmount > 0.0001 -> {
                            errorMessage = context.getString(R.string.register_validation_cash_out_limit)
                        }
                        else -> {
                            errorMessage = null
                            scope.launch {
                                val result = onSubmit(amountValue, description.trim())
                                if (result.isSuccess) {
                                    onSuccess()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message
                                        ?: context.getString(R.string.register_operation_failed)
                                }
                            }
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 2.dp))
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            AppButtonSecondary(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun historyValueColor(action: String, transactionType: String?, value: Double): Color {
    val normalizedAction = canonicalRegisterAction(action)
    return when (normalizedAction) {
        "register-opening" -> MaterialTheme.appColors.info
        "register-cash-in", "register-cashin" -> MaterialTheme.appColors.success
        "register-cash-out", "register-cashout" -> MaterialTheme.appColors.error
        "register-order-payment" -> MaterialTheme.appColors.warning
        "register-order-change", "register-refund" -> MaterialTheme.appColors.error
        else -> when (transactionType?.lowercase()?.trim()) {
            "positive" -> MaterialTheme.appColors.success
            "negative" -> MaterialTheme.appColors.error
            "unchanged" -> MaterialTheme.appColors.info
            else -> when {
                value < 0.0 -> MaterialTheme.appColors.error
                value > 0.0 -> MaterialTheme.appColors.success
                else -> MaterialTheme.appColors.text
            }
        }
    }
}

private fun canonicalRegisterAction(action: String): String {
    val normalized = action
        .lowercase()
        .trim()
        .replace("_", "-")
        .replace(" ", "-")
        .replace(Regex("-+"), "-")
    return when (normalized) {
        "initial", "opening", "open", "register-opening" -> "register-opening"
        "closing", "close", "register-closing" -> "register-closing"
        "cashin", "cash-in", "register-cash-in" -> "register-cash-in"
        "cashout", "cash-out", "register-cash-out" -> "register-cash-out"
        "orderpayment", "order-payment", "order-pay", "register-order-payment" -> "register-order-payment"
        "orderchange", "order-change", "register-order-change" -> "register-order-change"
        "refund", "register-refund" -> "register-refund"
        else -> normalized
    }
}

@Composable
private fun actionLabel(action: String): String = when (canonicalRegisterAction(action)) {
    "register-opening" -> stringResource(R.string.register_history_opening)
    "register-closing" -> stringResource(R.string.register_history_closing)
    "register-cash-in" -> stringResource(R.string.register_history_cash_in)
    "register-cash-out" -> stringResource(R.string.register_history_cash_out)
    "register-order-payment" -> stringResource(R.string.register_history_order_payment)
    else -> action
}
