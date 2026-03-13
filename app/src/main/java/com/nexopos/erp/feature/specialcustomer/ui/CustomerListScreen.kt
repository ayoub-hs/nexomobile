package com.nexopos.erp.feature.specialcustomer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.SpecialCustomerDto
import com.nexopos.erp.feature.specialcustomer.CustomerRoutes
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.SearchField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import com.nexopos.erp.ui.rememberAppCurrencyFormatter
import java.text.NumberFormat

/**
 * Customer List Screen
 * 
 * Displays a list of special customers with search functionality.
 * Each customer card shows name, wallet balance, and total due.
 * Clicking on a customer navigates to their dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    customers: List<SpecialCustomerDto>,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onCustomerClick: (customerId: Long) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) {
            customers
        } else {
            customers.filter { customer ->
                customer.name?.contains(searchQuery, ignoreCase = true) == true ||
                customer.firstName?.contains(searchQuery, ignoreCase = true) == true ||
                customer.lastName?.contains(searchQuery, ignoreCase = true) == true ||
                customer.email?.contains(searchQuery, ignoreCase = true) == true ||
                customer.phone?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }
    
    val currencyFormat = rememberAppCurrencyFormatter()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.appSpacing.screen),
                placeholder = stringResource(R.string.search_customers),
                leadingIcon = Icons.Default.Search
            )
            
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.appColors.error
                            )
                            AppButtonSecondary(onClick = onRefresh) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.size(MaterialTheme.appSpacing.s))
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                filteredCustomers.isEmpty() -> {
                    AppEmptyState(
                        title = if (searchQuery.isNotBlank()) {
                            stringResource(R.string.no_customers_found)
                        } else {
                            stringResource(R.string.no_customers_available)
                        },
                        message = if (searchQuery.isNotBlank()) {
                            stringResource(R.string.search_customers)
                        } else {
                            stringResource(R.string.no_customers_available)
                        },
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Default.Person
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            CustomerCard(
                                customer = customer,
                                currencyFormat = currencyFormat,
                                onClick = { onCustomerClick(customer.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(
    customer: SpecialCustomerDto,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.appSpacing.screen),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.appColors.primary
            )
            
            Spacer(modifier = Modifier.size(MaterialTheme.appSpacing.l))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = customer.name 
                        ?: listOfNotNull(customer.firstName, customer.lastName).takeIf { it.isNotEmpty() }?.joinToString(" ")
                        ?: "Customer #${customer.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                customer.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
                
                customer.phone?.let { phone ->
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }

                if (customer.walletBalance != 0.0) {
                    Text(
                        text = "Balance: ${currencyFormat.format(customer.walletBalance)}",
                        style = MaterialTheme.appTypography.amountM,
                        color = MaterialTheme.appColors.primary
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.appColors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
