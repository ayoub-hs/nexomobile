package com.nexopos.erp.feature.more.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nexopos.erp.R
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing

private data class MoreSectionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToManufacturing: () -> Unit,
    onNavigateToProcurement: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToContainers: () -> Unit,
    onSyncCatalog: () -> Unit
) {
    val primary = listOf(
        MoreSectionItem(
            title = stringResource(R.string.nav_settings),
            subtitle = stringResource(R.string.more_settings_subtitle),
            icon = Icons.Filled.Settings,
            onClick = onNavigateToSettings
        ),
        MoreSectionItem(
            title = stringResource(R.string.more_sync_title),
            subtitle = stringResource(R.string.more_sync_subtitle),
            icon = Icons.Filled.CloudSync,
            onClick = onSyncCatalog
        )
    )
    val modules = listOf(
        MoreSectionItem(
            title = stringResource(R.string.nav_manufacturing),
            subtitle = stringResource(R.string.more_manufacturing_subtitle),
            icon = Icons.Filled.Build,
            onClick = onNavigateToManufacturing
        ),
        MoreSectionItem(
            title = stringResource(R.string.nav_procurement),
            subtitle = stringResource(R.string.more_procurement_subtitle),
            icon = Icons.Filled.LocalShipping,
            onClick = onNavigateToProcurement
        ),
        MoreSectionItem(
            title = stringResource(R.string.nav_inventory),
            subtitle = stringResource(R.string.more_inventory_subtitle),
            icon = Icons.Filled.Inventory,
            onClick = onNavigateToInventory
        ),
        MoreSectionItem(
            title = stringResource(R.string.containers),
            subtitle = stringResource(R.string.more_containers_subtitle),
            icon = Icons.Filled.Folder,
            onClick = onNavigateToContainers
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.appSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.more_hub_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.appColors.text
        )
        Text(
            text = stringResource(R.string.more_hub_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.muted
        )
        primary.forEach { item ->
            MoreItemCard(item)
        }
        Text(
            text = stringResource(R.string.more_modules_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.text,
            modifier = Modifier.padding(top = MaterialTheme.appSpacing.sm)
        )
        modules.forEach { item ->
            MoreItemCard(item)
        }
    }
}

@Composable
private fun MoreItemCard(item: MoreSectionItem) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = item.onClick
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.appColors.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.text
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted
                )
            }
        }
    }
}
