package com.nexopos.erp.feature.scanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexopos.erp.R
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

@Composable
fun ScannerHomeScreen(
    onScan: () -> Unit,
    onCreateProduct: () -> Unit,
    onManualEntry: (String) -> Unit
) {
    var manualEntry by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf("") }
    val colors = MaterialTheme.appColors

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.appSpacing.screen, vertical = MaterialTheme.appSpacing.l),
        contentPadding = PaddingValues(bottom = MaterialTheme.appSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScannerActionTile(
                title = stringResource(R.string.scanner_action_scan_title),
                icon = Icons.Filled.CameraAlt,
                accent = colors.primary,
                modifier = Modifier.heightIn(min = 176.dp),
                onClick = onScan
            )
        }
        item {
            ScannerActionTile(
                title = stringResource(R.string.scanner_action_manual_title),
                icon = Icons.Filled.Edit,
                accent = colors.warning,
                modifier = Modifier.heightIn(min = 152.dp),
                onClick = { manualEntry = true }
            )
        }
        item {
            ScannerActionTile(
                title = stringResource(R.string.scanner_action_create_title),
                icon = Icons.Filled.AddBox,
                accent = colors.success,
                modifier = Modifier.heightIn(min = 152.dp),
                onClick = onCreateProduct
            )
        }
    }

    if (manualEntry) {
        Dialog(onDismissRequest = { manualEntry = false }) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(MaterialTheme.appSpacing.l)
            ) {
                Text(
                    text = stringResource(R.string.scanner_manual_entry_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                AppTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = stringResource(R.string.scanner_label_barcode),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.appSpacing.s,
                        Alignment.End
                    )
                ) {
                    AppButtonSecondary(onClick = { manualEntry = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    AppButtonPrimary(
                        onClick = {
                            onManualEntry(barcode)
                            manualEntry = false
                            barcode = ""
                        }
                    ) {
                        Text(stringResource(R.string.scanner_lookup_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerActionTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(MaterialTheme.appSpacing.l),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(MaterialTheme.appRadii.large)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier
                            .padding(MaterialTheme.appSpacing.m)
                            .size(34.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
