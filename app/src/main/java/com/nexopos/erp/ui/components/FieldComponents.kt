package com.nexopos.erp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

data class ChipOption<T>(
    val id: T,
    val label: String
)

@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.appColors.primary,
    unfocusedBorderColor = MaterialTheme.appColors.border,
    disabledBorderColor = MaterialTheme.appColors.border.copy(alpha = 0.6f),
    errorBorderColor = MaterialTheme.appColors.error,
    focusedContainerColor = MaterialTheme.appColors.surface,
    unfocusedContainerColor = MaterialTheme.appColors.surface,
    disabledContainerColor = MaterialTheme.appColors.surfaceVariant,
    errorContainerColor = MaterialTheme.appColors.surface,
    focusedTextColor = MaterialTheme.appColors.text,
    unfocusedTextColor = MaterialTheme.appColors.text,
    disabledTextColor = MaterialTheme.appColors.muted,
    focusedLabelColor = MaterialTheme.appColors.primary,
    unfocusedLabelColor = MaterialTheme.appColors.muted,
    focusedPlaceholderColor = MaterialTheme.appColors.muted,
    unfocusedPlaceholderColor = MaterialTheme.appColors.muted,
    focusedLeadingIconColor = MaterialTheme.appColors.muted,
    unfocusedLeadingIconColor = MaterialTheme.appColors.muted,
    focusedTrailingIconColor = MaterialTheme.appColors.muted,
    unfocusedTrailingIconColor = MaterialTheme.appColors.muted,
    cursorColor = MaterialTheme.appColors.primary
)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        label = label?.let { { Text(text = it) } },
        placeholder = placeholder?.let { { Text(text = it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(MaterialTheme.appRadii.small),
        colors = appTextFieldColors()
    )
}

@Composable
fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        label = label?.let { { Text(text = it) } },
        placeholder = placeholder?.let { { Text(text = it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(MaterialTheme.appRadii.small),
        colors = appTextFieldColors()
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    secondaryTrailingIcon: ImageVector? = null,
    secondaryTrailingDescription: String? = null,
    onSecondaryTrailingClick: (() -> Unit)? = null,
    clearDescription: String? = null,
    onClear: (() -> Unit)? = null
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = true,
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.appColors.muted
                )
            }
        },
        trailingIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)) {
                if (value.isNotBlank() && onClear != null) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.semantics {
                            clearDescription?.let { contentDescription = it }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null
                        )
                    }
                }
                if (trailingIcon != null && onTrailingClick != null) {
                    IconButton(
                        onClick = onTrailingClick,
                        modifier = Modifier.semantics {
                            trailingDescription?.let { contentDescription = it }
                        }
                    ) {
                        Icon(imageVector = trailingIcon, contentDescription = null)
                    }
                }
                if (secondaryTrailingIcon != null && onSecondaryTrailingClick != null) {
                    IconButton(
                        onClick = onSecondaryTrailingClick,
                        modifier = Modifier.semantics {
                            secondaryTrailingDescription?.let { contentDescription = it }
                        }
                    ) {
                        Icon(imageVector = secondaryTrailingIcon, contentDescription = null)
                    }
                }
            }
        }
    )
}

@Composable
fun AppChipFilter(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        leadingIcon = leadingIcon,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(MaterialTheme.appRadii.small),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.appColors.border,
            selectedBorderColor = MaterialTheme.appColors.surfaceRaised
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.appColors.surfaceRaised,
            selectedLabelColor = MaterialTheme.appColors.text,
            selectedLeadingIconColor = MaterialTheme.appColors.primary,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.appColors.muted,
            iconColor = MaterialTheme.appColors.muted
        )
    )
}

@Composable
fun AppChipStatus(
    label: String,
    tone: AppStatusTone,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (tone) {
        AppStatusTone.Success -> MaterialTheme.appColors.successDim to MaterialTheme.appColors.success
        AppStatusTone.Warning -> MaterialTheme.appColors.warningDim to MaterialTheme.appColors.warning
        AppStatusTone.Error -> MaterialTheme.appColors.errorDim to MaterialTheme.appColors.error
        AppStatusTone.Info -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.info
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.appRadii.small),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = MaterialTheme.appSpacing.s, vertical = MaterialTheme.appSpacing.xs)
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun <T> ChipRow(
    options: List<ChipOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
    ) {
        options.forEach { option ->
            AppChipFilter(
                selected = option.id == selected,
                onClick = { onSelected(option.id) },
                label = {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
