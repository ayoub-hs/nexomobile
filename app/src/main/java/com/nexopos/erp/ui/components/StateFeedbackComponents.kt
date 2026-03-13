package com.nexopos.erp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.appRadii.large),
        containerColor = MaterialTheme.appColors.surfaceOverlay,
        iconContentColor = MaterialTheme.appColors.primary,
        titleContentColor = MaterialTheme.appColors.text,
        textContentColor = MaterialTheme.appColors.text,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon
    )
}

@Composable
fun AppEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    Column(
        modifier = modifier.padding(MaterialTheme.appSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.appColors.muted
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.appColors.text
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.muted
        )
        if (actionLabel != null && onAction != null) {
            AppButtonPrimary(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null
) = AppEmptyState(
    title = title,
    message = message,
    modifier = modifier,
    actionLabel = actionLabel,
    onAction = onAction,
    icon = icon
)

@Composable
fun SkeletonListRows(
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(
                        color = MaterialTheme.appColors.surfaceRaised,
                        shape = RoundedCornerShape(MaterialTheme.appRadii.medium)
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.appColors.surfaceOverlay,
        shape = RoundedCornerShape(
            topStart = MaterialTheme.appRadii.large,
            topEnd = MaterialTheme.appRadii.large
        )
    ) {
        Column(
            modifier = Modifier.padding(
                start = MaterialTheme.appSpacing.screen,
                end = MaterialTheme.appSpacing.screen,
                bottom = MaterialTheme.appSpacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.appColors.text
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.muted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AppButtonSecondary(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
                AppButtonPrimary(
                    onClick = onConfirm,
                    modifier = Modifier.padding(start = MaterialTheme.appSpacing.s)
                ) {
                    Text(text = confirmLabel)
                }
            }
        }
    }
}
