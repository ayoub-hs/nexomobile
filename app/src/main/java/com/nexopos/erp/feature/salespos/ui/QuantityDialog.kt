package com.nexopos.erp.feature.salespos.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.ContainerLink
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipFilter
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import java.util.Locale
import kotlin.math.abs

@Immutable
data class QuantityDialogData(
    val title: String,
    val productId: Long,
    val name: String,
    val options: List<UnitOptionData>,
    val initialUnitQuantityId: Long,
    val defaultQuantity: Double = 1.0,
    val defaultWholesale: Boolean = false
)

@Immutable
data class UnitOptionData(
    val unitQuantityId: Long,
    val unitId: Long?,
    val unitName: String?,
    val salePrice: Double?,
    val wholesalePriceWithTax: Double?,
    val containerLink: ContainerLink? = null
)

@Immutable
data class QuantityDialogResult(
    val option: UnitOptionData,
    val quantity: Double,
    val useWholesale: Boolean,
    val unitPrice: Double,
    val isCustomPrice: Boolean
)

fun buildQuantityDialogData(
    context: Context,
    product: Product
): QuantityDialogData? {
    val eligibleOptions = product.unitQuantities.orEmpty()
        .filter { (it.salePrice ?: 0.0) > 0.0 || (it.wholesalePriceWithTax ?: 0.0) > 0.0 }
        .map { unit ->
            UnitOptionData(
                unitQuantityId = unit.id,
                unitId = unit.unitId,
                unitName = unit.unit?.name,
                salePrice = unit.salePrice,
                wholesalePriceWithTax = unit.wholesalePriceWithTax,
                containerLink = unit.containerLink
            )
        }

    val initialOption = eligibleOptions.firstOrNull() ?: return null
    return QuantityDialogData(
        title = context.getString(R.string.title_add_product, product.name),
        productId = product.id,
        name = product.name,
        options = eligibleOptions,
        initialUnitQuantityId = initialOption.unitQuantityId,
        defaultWholesale = !hasSalePrice(initialOption) && hasWholesalePrice(initialOption)
    )
}

@Composable
fun QuantityDialog(
    data: QuantityDialogData,
    onConfirm: (QuantityDialogResult) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val numberPattern = remember { Regex("^\\d*(?:\\.\\d{0,4})?$") }
    val pricePattern = remember { Regex("^\\d*(?:\\.\\d{0,3})?$") }
    val scrollState = rememberScrollState()

    var quantityField by rememberSaveable(data.productId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(initialQuantityValue(data.defaultQuantity))
    }
    var selectedUnitQuantityId by rememberSaveable(data.productId) {
        mutableStateOf(data.initialUnitQuantityId)
    }
    var useWholesale by rememberSaveable(data.productId) {
        mutableStateOf(data.defaultWholesale)
    }
    var quantityError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    val selectedOption = data.options.firstOrNull { it.unitQuantityId == selectedUnitQuantityId } ?: data.options.first()
    val hasSalePrice = hasSalePrice(selectedOption)
    val hasWholesalePrice = hasWholesalePrice(selectedOption)
    val canToggleWholesale = hasSalePrice && hasWholesalePrice
    val forcedWholesale = !hasSalePrice && hasWholesalePrice
    val effectiveUseWholesale = when {
        forcedWholesale -> true
        canToggleWholesale -> useWholesale
        else -> false
    }
    val defaultUnitPrice = effectiveUnitPrice(selectedOption, effectiveUseWholesale) ?: 0.0
    var unitPriceField by rememberSaveable(data.productId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val editedUnitPrice = unitPriceField.text.toDoubleOrNull() ?: defaultUnitPrice
    val selectedUnitLabel = optionLabel(selectedOption)

    LaunchedEffect(selectedUnitQuantityId, effectiveUseWholesale) {
        val text = formatPriceValue(defaultUnitPrice)
        unitPriceField = TextFieldValue(text, selection = TextRange(text.length))
        priceError = false
    }

    val confirmSelection = {
        val qty = quantityField.text.toDoubleOrNull()
        val unitPrice = unitPriceField.text.toDoubleOrNull()
        val hasValidQuantity = qty != null && qty.isFinite() && qty > 0.0
        val hasValidUnitPrice = unitPrice != null && unitPrice.isFinite() && unitPrice >= 0.0
        quantityError = !hasValidQuantity
        priceError = !hasValidUnitPrice
        if (hasValidQuantity && hasValidUnitPrice) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onConfirm(
                QuantityDialogResult(
                    option = selectedOption,
                    quantity = qty!!,
                    useWholesale = effectiveUseWholesale,
                    unitPrice = unitPrice!!,
                    isCustomPrice = abs(unitPrice - defaultUnitPrice) > 0.0005
                )
            )
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 460.dp),
        title = {
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.appColors.surfaceRaised,
                    contentPadding = PaddingValues(MaterialTheme.appSpacing.sm)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                            ) {
                                Text(
                                    text = selectedUnitLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.appColors.muted
                                )
                                Text(
                                    text = formatCurrencyLabel(editedUnitPrice),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.appColors.text
                                )
                                Text(
                                    text = when {
                                        forcedWholesale -> stringResource(R.string.wholesale_only_label)
                                        canToggleWholesale && effectiveUseWholesale -> stringResource(R.string.sell_price_wholesale)
                                        !hasWholesalePrice && hasSalePrice -> stringResource(R.string.retail_only_label)
                                        else -> stringResource(R.string.sell_price_retail)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (effectiveUseWholesale) {
                                        MaterialTheme.appColors.primary
                                    } else {
                                        MaterialTheme.appColors.muted
                                    }
                                )
                            }

                                if (canToggleWholesale) {
                                    Surface(
                                        shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
                                        color = MaterialTheme.appColors.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.appColors.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = MaterialTheme.appSpacing.xs,
                                                vertical = MaterialTheme.appSpacing.xxs
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.sell_price_wholesale),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.appColors.text
                                            )
                                            Switch(
                                                checked = effectiveUseWholesale,
                                                onCheckedChange = { useWholesale = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.appColors.surface,
                                                    checkedTrackColor = MaterialTheme.appColors.primary,
                                                    uncheckedThumbColor = MaterialTheme.appColors.surface,
                                                    uncheckedTrackColor = MaterialTheme.appColors.surfaceVariant,
                                                    uncheckedBorderColor = MaterialTheme.appColors.border,
                                                    checkedBorderColor = MaterialTheme.appColors.primary
                                                )
                                            )
                                        }
                                    }
                                } else if (forcedWholesale || !hasWholesalePrice) {
                                    Surface(
                                        shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
                                        color = MaterialTheme.appColors.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.appColors.border)
                                    ) {
                                        Text(
                                            text = when {
                                                forcedWholesale -> stringResource(R.string.wholesale_only_label)
                                                else -> stringResource(R.string.retail_only_label)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.appColors.muted,
                                            modifier = Modifier.padding(
                                                horizontal = MaterialTheme.appSpacing.sm,
                                                vertical = MaterialTheme.appSpacing.xs
                                            )
                                        )
                                    }
                                }
                            }
                        }
                }

                if (data.options.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
                        Text(
                            text = stringResource(R.string.select_unit_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.appColors.muted
                        )

                        val maxItemsPerRow = 3
                        data.options.chunked(maxItemsPerRow).forEach { optionRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                            ) {
                                optionRow.forEach { option ->
                                    val optionSelected = option.unitQuantityId == selectedOption.unitQuantityId
                                    val optionPrice = effectiveUnitPrice(option, effectiveUseWholesale)
                                        ?: option.salePrice
                                        ?: option.wholesalePriceWithTax
                                        ?: 0.0

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 82.dp)
                                            .clickable {
                                                selectedUnitQuantityId = option.unitQuantityId
                                                useWholesale = when {
                                                    !hasSalePrice(option) && hasWholesalePrice(option) -> true
                                                    !hasWholesalePrice(option) -> false
                                                    else -> useWholesale
                                                }
                                                quantityError = false
                                                priceError = false
                                            },
                                        shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
                                        color = if (optionSelected) {
                                            MaterialTheme.appColors.primaryContainer.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.appColors.surfaceRaised
                                        },
                                        border = BorderStroke(
                                            width = if (optionSelected) 1.5.dp else 1.dp,
                                            color = if (optionSelected) {
                                                MaterialTheme.appColors.primary
                                            } else {
                                                MaterialTheme.appColors.border
                                            }
                                        ),
                                        tonalElevation = if (optionSelected) 3.dp else 0.dp,
                                        shadowElevation = if (optionSelected) 2.dp else 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = MaterialTheme.appSpacing.sm,
                                                    vertical = MaterialTheme.appSpacing.xs
                                                ),
                                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                                        ) {
                                            Text(
                                                text = optionLabel(option),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.appColors.text
                                            )
                                            Text(
                                                text = formatCurrencyLabel(optionPrice),
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (optionSelected) {
                                                    MaterialTheme.appColors.primary
                                                } else {
                                                    MaterialTheme.appColors.text
                                                }
                                            )
                                            Text(
                                                text = when {
                                                    !hasSalePrice(option) && hasWholesalePrice(option) -> stringResource(R.string.wholesale_only_label)
                                                    hasWholesalePrice(option) && effectiveUseWholesale -> stringResource(R.string.sell_price_wholesale)
                                                    else -> stringResource(R.string.sell_price_retail)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.appColors.muted
                                            )
                                        }
                                    }
                                }
                                repeat(maxItemsPerRow - optionRow.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.appColors.surfaceRaised,
                    contentPadding = PaddingValues(MaterialTheme.appSpacing.sm)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.custom_item_price_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.muted
                        )
                        AppTextField(
                            value = unitPriceField,
                            onValueChange = { newValue ->
                                val cleaned = newValue.text.replace(',', '.')
                                if (cleaned.isEmpty() || pricePattern.matches(cleaned)) {
                                    val selection = TextRange(
                                        newValue.selection.start.coerceIn(0, cleaned.length),
                                        newValue.selection.end.coerceIn(0, cleaned.length)
                                    )
                                    unitPriceField = TextFieldValue(cleaned, selection, newValue.composition)
                                    priceError = false
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = priceError,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        unitPriceField = unitPriceField.copy(
                                            selection = TextRange(0, unitPriceField.text.length)
                                        )
                                    }
                                }
                        )

                        Text(
                            text = stringResource(R.string.quantity_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.muted
                        )
                        AppTextField(
                            value = quantityField,
                            onValueChange = { newValue ->
                                val cleaned = newValue.text.replace(',', '.')
                                if (cleaned.isEmpty() || numberPattern.matches(cleaned)) {
                                    val selection = TextRange(
                                        newValue.selection.start.coerceIn(0, cleaned.length),
                                        newValue.selection.end.coerceIn(0, cleaned.length)
                                    )
                                    quantityField = TextFieldValue(cleaned, selection, newValue.composition)
                                    quantityError = false
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { confirmSelection() }),
                            singleLine = true,
                            isError = quantityError,
                            trailingIcon = {
                                Text(
                                    text = selectedUnitLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.appColors.muted
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        quantityField = quantityField.copy(
                                            selection = TextRange(quantityField.text.length)
                                        )
                                    }
                                }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                        ) {
                            listOf(
                                R.string.quantity_shortcut_3 to 3.0,
                                R.string.quantity_shortcut_5 to 5.0,
                                R.string.quantity_shortcut_10 to 10.0,
                                R.string.quantity_shortcut_20 to 20.0
                            ).forEach { (labelRes, value) ->
                                val formattedValue = formatQuantityValue(value)
                                val shortcutSelected = quantityField.text == formattedValue
                                Surface(
                                    modifier = Modifier
                                        .heightIn(min = 43.dp)
                                        .clickable {
                                            quantityField = TextFieldValue(
                                                text = formattedValue,
                                                selection = TextRange(formattedValue.length)
                                            )
                                            quantityError = false
                                        },
                                    shape = RoundedCornerShape(MaterialTheme.appRadii.small),
                                    color = if (shortcutSelected) {
                                        MaterialTheme.appColors.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.appColors.surfaceVariant
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (shortcutSelected) MaterialTheme.appColors.primary else MaterialTheme.appColors.border
                                    )
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (shortcutSelected) MaterialTheme.appColors.primary else MaterialTheme.appColors.text,
                                        modifier = Modifier.padding(
                                            horizontal = 17.dp,
                                            vertical = 10.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (quantityError) {
                    Text(
                        text = stringResource(R.string.quantity_error_positive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.error
                    )
                }
                if (priceError) {
                    Text(
                        text = stringResource(R.string.price_error_positive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.error
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                AppButtonSecondary(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.common_cancel),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                AppButtonPrimary(
                    onClick = { confirmSelection() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.button_add),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        dismissButton = null
    )
}

private fun hasSalePrice(option: UnitOptionData): Boolean = (option.salePrice ?: 0.0) > 0.0

private fun hasWholesalePrice(option: UnitOptionData): Boolean = (option.wholesalePriceWithTax ?: 0.0) > 0.0

private fun effectiveUnitPrice(option: UnitOptionData, useWholesale: Boolean): Double? {
    return when {
        useWholesale && hasWholesalePrice(option) -> option.wholesalePriceWithTax
        hasSalePrice(option) -> option.salePrice
        hasWholesalePrice(option) -> option.wholesalePriceWithTax
        else -> null
    }
}

@Composable
private fun optionLabel(option: UnitOptionData): String {
    return option.unitName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unit_default_label)
}

private fun initialQuantityValue(defaultQuantity: Double): TextFieldValue {
    val baseline = if (defaultQuantity > 0) defaultQuantity else 1.0
    val text = formatQuantityValue(baseline)
    return TextFieldValue(text, selection = TextRange(text.length))
}

private fun formatQuantityValue(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }
}

private fun formatCurrencyLabel(value: Double): String = String.format(Locale.US, "%.3f DT", value)

private fun formatPriceValue(value: Double): String {
    return String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
}
