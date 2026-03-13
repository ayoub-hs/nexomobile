package com.nexopos.erp.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

private data class HomeFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onNavigateToPos: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToSpecialCustomer: () -> Unit,
    onNavigateToContainers: () -> Unit,
    onNavigateToManufacturing: () -> Unit,
    onNavigateToProcurements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPriceLookup: () -> Unit
) {
    val colors = MaterialTheme.appColors
    val features = buildList {
        add(
        HomeFeature(
            title = stringResource(R.string.nav_pos),
            description = stringResource(R.string.home_feature_pos_description),
            icon = Icons.Filled.ShoppingCart,
            accent = colors.primary,
            onClick = onNavigateToPos
        ))
        add(
        HomeFeature(
            title = stringResource(R.string.nav_orders),
            description = stringResource(R.string.home_feature_orders_description),
            icon = Icons.Filled.History,
            accent = colors.success,
            onClick = onNavigateToOrders
        ))
        add(
        HomeFeature(
            title = stringResource(R.string.nav_inventory),
            description = stringResource(R.string.home_feature_inventory_description),
            icon = Icons.Filled.Inventory,
            accent = colors.warning,
            onClick = onNavigateToInventory
        ))

        add(
        HomeFeature(
            title = stringResource(R.string.nav_price_lookup),
            description = stringResource(R.string.home_feature_price_lookup_description),
            icon = Icons.Filled.LocalOffer,
            accent = colors.info,
            onClick = onNavigateToPriceLookup
        ))

        add(
        HomeFeature(
            title = stringResource(R.string.nav_scanner),
            description = stringResource(R.string.home_feature_scanner_description),
            icon = Icons.Filled.CameraAlt,
            accent = colors.primaryHover,
            onClick = onNavigateToScanner
        ))

        add(
        HomeFeature(
            title = stringResource(R.string.nav_customers),
            description = stringResource(R.string.home_feature_special_customer_description),
            icon = Icons.Filled.People,
            accent = colors.info,
            onClick = onNavigateToSpecialCustomer
        ))
        add(
        HomeFeature(
            title = stringResource(R.string.containers),
            description = stringResource(R.string.home_feature_containers_description),
            icon = Icons.Filled.Archive,
            accent = colors.primaryHover,
            onClick = onNavigateToContainers
        ))
        add(
        HomeFeature(
            title = stringResource(R.string.nav_manufacturing),
            description = stringResource(R.string.home_feature_manufacturing_description),
            icon = Icons.Filled.Build,
            accent = colors.primaryDim,
            onClick = onNavigateToManufacturing
        ))
        add(
        HomeFeature(
            title = stringResource(R.string.nav_procurement),
            description = stringResource(R.string.home_feature_procurements_description),
            icon = Icons.Filled.LocalShipping,
            accent = colors.primaryPressed,
            onClick = onNavigateToProcurements
        ))
        add(
        HomeFeature(
            title = stringResource(R.string.nav_settings),
            description = stringResource(R.string.home_feature_settings_description),
            icon = Icons.Filled.Settings,
            accent = colors.muted,
            onClick = onNavigateToSettings
        ))
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaterialTheme.appSpacing.md,
            top = MaterialTheme.appSpacing.sm,
            end = MaterialTheme.appSpacing.md,
            bottom = MaterialTheme.appSpacing.md
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
    ) {
        items(features) { feature ->
            HomeFeatureCard(feature = feature)
        }
    }
}

@Composable
private fun HomeFeatureCard(feature: HomeFeature) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp),
        onClick = feature.onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(MaterialTheme.appRadii.lg),
                color = feature.accent.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = feature.accent,
                    modifier = Modifier
                        .padding(MaterialTheme.appSpacing.sm)
                        .size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.appSpacing.sm))

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(MaterialTheme.appSpacing.xxs))

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
