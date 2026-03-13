package com.nexopos.erp.feature.specialcustomer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import java.text.NumberFormat

/**
 * Pay From Wallet Popup
 * 
 * AlertDialog for confirming payment of selected tickets from wallet.
 * Shows selected tickets, total amount, and wallet balance.
 */
@Composable
fun PayFromWalletPopup(
    selectedTickets: List<OutstandingTicket>,
    totalAmount: Double,
    walletBalance: Double,
    isProcessing: Boolean = false,
    error: String? = null,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val hasInsufficientBalance = totalAmount > walletBalance
    
    AppDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(text = stringResource(R.string.pay_from_wallet))
        },
        icon = {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (hasInsufficientBalance) MaterialTheme.appColors.error else MaterialTheme.appColors.primary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.appColors.surfaceRaised
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.appColors.primary
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.m))
                        Column {
                            Text(
                                text = stringResource(R.string.wallet_balance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.muted
                            )
                            Text(
                                text = currencyFormat.format(walletBalance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.appColors.text
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.selected_tickets, selectedTickets.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                ) {
                    items(selectedTickets) { ticket ->
                        TicketSummaryRow(
                            ticket = ticket,
                            currencyFormat = currencyFormat
                        )
                    }
                }

                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (hasInsufficientBalance) {
                        MaterialTheme.appColors.errorDim
                    } else {
                        MaterialTheme.appColors.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total_to_pay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasInsufficientBalance) MaterialTheme.appColors.error else MaterialTheme.appColors.muted
                        )
                        Text(
                            text = currencyFormat.format(totalAmount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasInsufficientBalance) MaterialTheme.appColors.error else MaterialTheme.appColors.primary
                        )
                    }
                }

                if (hasInsufficientBalance) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.appColors.error
                        )
                        Text(
                            text = stringResource(R.string.insufficient_wallet_balance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.error
                        )
                    }
                }

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
                onClick = onConfirm,
                enabled = !isProcessing && !hasInsufficientBalance
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.appColors.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.confirm_payment))
            }
        },
        dismissButton = {
            AppButtonTertiary(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TicketSummaryRow(
    ticket: OutstandingTicket,
    currencyFormat: NumberFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = ticket.code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = currencyFormat.format(ticket.dueAmount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.error
        )
    }
}
