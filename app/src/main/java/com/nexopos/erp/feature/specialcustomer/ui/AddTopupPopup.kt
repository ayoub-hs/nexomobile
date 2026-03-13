package com.nexopos.erp.feature.specialcustomer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import java.time.LocalDate

/**
 * Add Topup Popup
 * 
 * AlertDialog for creating a new wallet topup.
 * Contains fields for amount, description/notes, and received date.
 */
@Composable
fun AddTopupPopup(
    isCreating: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, description: String?, receivedDate: String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var receivedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    
    var amountError by remember { mutableStateOf<String?>(null) }
    var receivedDateError by remember { mutableStateOf<String?>(null) }
    
    val errorInvalidAmount = stringResource(R.string.error_invalid_amount)
    val errorInvalidReceivedDate = stringResource(R.string.error_invalid_received_date)
    
    fun validateAndSubmit() {
        val amountValue = amount.toDoubleOrNull()
        
        if (amountValue == null || amountValue <= 0) {
            amountError = errorInvalidAmount
            return
        }

        if (!isValidIsoDate(receivedDate)) {
            receivedDateError = errorInvalidReceivedDate
            return
        }
        
        amountError = null
        receivedDateError = null
        onConfirm(amountValue, description.ifBlank { null }, receivedDate)
    }
    
    AppDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Text(text = stringResource(R.string.add_topup))
        },
        icon = {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.appColors.primary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
            ) {
                AppTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amount = newValue
                            amountError = null
                        }
                    },
                    label = stringResource(R.string.topup_amount),
                    leadingIcon = {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    singleLine = true,
                    enabled = !isCreating
                )

                AppTextField(
                    value = description,
                    onValueChange = { 
                        description = it 
                    },
                    label = stringResource(R.string.description_optional),
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null)
                    },
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isCreating
                )

                AppTextField(
                    value = receivedDate,
                    onValueChange = {
                        receivedDate = it
                        receivedDateError = null
                    },
                    label = stringResource(R.string.received_date),
                    placeholder = stringResource(R.string.date_format_hint),
                    isError = receivedDateError != null,
                    supportingText = {
                        Text(receivedDateError ?: stringResource(R.string.received_date_help))
                    },
                    singleLine = true,
                    enabled = !isCreating
                )

                error?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.error
                    )
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = { validateAndSubmit() },
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.appColors.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            AppButtonTertiary(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun isValidIsoDate(value: String): Boolean {
    return runCatching { LocalDate.parse(value) }.isSuccess
}
