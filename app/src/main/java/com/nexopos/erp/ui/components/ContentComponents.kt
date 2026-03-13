package com.nexopos.erp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appElevations
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

data class SummaryRowData(
    val label: String,
    val value: String,
    val valueColor: Color? = null
)

enum class AppStatusTone {
    Success,
    Warning,
    Error,
    Info
}

@Composable
fun AppButtonPrimary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.appColors.primary,
            contentColor = MaterialTheme.appColors.onPrimary,
            disabledContainerColor = MaterialTheme.appColors.primaryDim.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.appColors.onPrimary.copy(alpha = 0.7f)
        ),
        content = content
    )
}

@Composable
fun AppButtonSecondary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    if (tonal) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.appColors.surfaceOverlay,
                contentColor = MaterialTheme.appColors.text,
                disabledContainerColor = MaterialTheme.appColors.surfaceRaised.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.appColors.muted
            ),
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
            border = BorderStroke(1.dp, MaterialTheme.appColors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.appColors.text,
                disabledContentColor = MaterialTheme.appColors.muted
            ),
            content = content
        )
    }
}

@Composable
fun AppButtonTertiary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.appColors.primary,
            disabledContentColor = MaterialTheme.appColors.muted
        ),
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    outlined: Boolean = true,
    elevated: Boolean = true,
    containerColor: Color = MaterialTheme.appColors.surfaceRaised,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.appColors.border) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (elevated) MaterialTheme.appElevations.level1 else 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding ?: PaddingValues(MaterialTheme.appSpacing.card)),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m),
            content = content
        )
    }
}

@Composable
fun AppMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    valueColor: Color = MaterialTheme.appColors.text
) {
    AppCard(
        modifier = modifier,
        elevated = false,
        containerColor = MaterialTheme.appColors.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.appTypography.amountM,
            color = valueColor
        )
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }
    }
}

@Composable
fun AppListRow(
    title: String,
    subtitle: String,
    trailingValue: String,
    modifier: Modifier = Modifier,
    trailingColor: Color = MaterialTheme.appColors.primary,
    chips: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                chips?.invoke()
            }
            Text(
                text = trailingValue,
                style = MaterialTheme.appTypography.amountM.copy(textAlign = TextAlign.End),
                color = trailingColor
            )
        }
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    trailingValue: String,
    modifier: Modifier = Modifier,
    chips: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) = AppListRow(
    title = title,
    subtitle = subtitle,
    trailingValue = trailingValue,
    modifier = modifier,
    chips = chips,
    onClick = onClick
)

@Composable
fun StepperQty(
    quantity: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.appColors.surfaceVariant,
                shape = RoundedCornerShape(MaterialTheme.appRadii.medium)
            )
            .padding(horizontal = MaterialTheme.appSpacing.s, vertical = MaterialTheme.appSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
    ) {
        IconButton(onClick = onDecrease, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            text = quantity,
            style = MaterialTheme.appTypography.amountM,
            color = MaterialTheme.appColors.text
        )
        IconButton(onClick = onIncrease, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
fun PriceChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    AppChipFilter(
        selected = active,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier,
        label = { Text(label) }
    )
}

@Composable
fun SummaryCard(
    rows: List<SummaryRowData>,
    totalLabel: String,
    totalValue: String,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null
) {
    AppCard(modifier = modifier) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.muted
                )
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = row.valueColor ?: MaterialTheme.appColors.text
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.appColors.text
            )
            Text(
                text = totalValue,
                style = MaterialTheme.appTypography.amountL,
                color = MaterialTheme.appColors.primary
            )
        }
        footer?.invoke()
    }
}
