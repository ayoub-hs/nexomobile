package com.nexopos.erp.feature.salespos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppQuantity
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

@Composable
internal fun ContainerTrackingSummary(
    item: CartItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (item.containerLink == null && !item.hasContainerMetadata) {
        return
    }

    val summary = when {
        item.containerTrackingEnabled && item.containerLink != null -> {
            stringResource(
                R.string.container_tracking_summary,
                item.effectiveContainerQuantity ?: 0,
                item.containerLink.containerTypeName
            )
        }
        item.containerTrackingEnabled -> stringResource(R.string.container_tracking_enabled)
        else -> stringResource(R.string.container_tracking_off)
    }

    val containerModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Row(
        modifier = containerModifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.appSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.muted
        )
        if (onClick != null) {
            Text(
                text = stringResource(R.string.container_tracking_manage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.primary
            )
        }
    }
}

@Composable
internal fun ContainerTrackingDialog(
    item: CartItem,
    onDismiss: () -> Unit,
    onApply: (Boolean, Int?) -> Unit
) {
    var trackingEnabled by rememberSaveable(item.key) { mutableStateOf(item.containerTrackingEnabled) }
    var overrideValue by rememberSaveable(item.key + "_override") {
        mutableStateOf(item.containerQuantityOverride?.toString().orEmpty())
    }
    var error by rememberSaveable(item.key + "_override_error") { mutableStateOf<String?>(null) }
    val overrideHint = stringResource(R.string.container_tracking_override_hint)

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = item.unitName?.takeIf { it.isNotBlank() }?.let { "${item.name} ($it)" } ?: item.name
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)) {
                item.containerLink?.let { link ->
                    Surface(
                        shape = RoundedCornerShape(MaterialTheme.appRadii.md),
                        color = MaterialTheme.appColors.surfaceRaised
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.appSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                        ) {
                            Text(
                                text = link.containerTypeName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.appColors.text
                            )
                            Text(
                                text = stringResource(
                                    R.string.container_tracking_capacity,
                                    formatAppQuantity(link.capacity, maxDecimals = 3),
                                    link.capacityUnit
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.muted
                            )
                            Text(
                                text = stringResource(
                                    R.string.container_tracking_deposit,
                                    formatAppCurrency(link.depositFee)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.muted
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(MaterialTheme.appRadii.md),
                    color = MaterialTheme.appColors.surfaceRaised
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.appSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.container_tracking_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.appColors.text
                        )
                        Switch(
                            checked = trackingEnabled,
                            onCheckedChange = {
                                trackingEnabled = it
                                if (!it) {
                                    error = null
                                    overrideValue = ""
                                }
                            }
                        )
                    }
                }

                item.requiredContainerQuantity?.let { required ->
                    Text(
                        text = stringResource(R.string.container_tracking_required, required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted
                    )
                }

                if (trackingEnabled) {
                    AppTextField(
                        value = overrideValue,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.all(Char::isDigit)) {
                                overrideValue = value
                                error = null
                            }
                        },
                        label = stringResource(R.string.container_tracking_override_label),
                        supportingText = {
                            Text(
                                text = error ?: overrideHint,
                                color = if (error != null) MaterialTheme.appColors.danger else MaterialTheme.appColors.muted
                            )
                        },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    val parsedOverride = overrideValue.takeIf { it.isNotBlank() }?.toIntOrNull()
                    if (overrideValue.isNotBlank() && parsedOverride == null) {
                        error = overrideHint
                        return@AppButtonPrimary
                    }
                    onApply(trackingEnabled, parsedOverride)
                }
            ) {
                Text(stringResource(R.string.container_tracking_apply))
            }
        },
        dismissButton = {
            AppButtonSecondary(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
