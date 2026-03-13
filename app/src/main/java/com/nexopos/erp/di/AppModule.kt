package com.nexopos.erp.di

import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.NexoApi
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.FeatureFlags
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.repo.AuthRepository
import com.nexopos.erp.core.repo.CategoryRepository
import com.nexopos.erp.core.repo.CustomerRepository
import com.nexopos.erp.core.repo.MobileSyncRepository
import com.nexopos.erp.core.repo.OrderQueueRepository
import com.nexopos.erp.core.repo.OrderRepository
import com.nexopos.erp.core.repo.ProductAdminRepository
import com.nexopos.erp.core.repo.ProductRepository
import com.nexopos.erp.core.repo.RegisterRepositoryImpl
import com.nexopos.erp.core.repo.SyncMetadataRepository
import com.nexopos.erp.feature.auth.AuthViewModel
import com.nexopos.erp.feature.salespos.ui.CartViewModel
import com.nexopos.erp.feature.salespos.ui.RegisterViewModel
import com.nexopos.erp.feature.salespos.ui.ScanViewModel
import com.nexopos.erp.feature.salespos.ui.SearchViewModel
import com.nexopos.erp.feature.settings.vm.SettingsViewModel
import com.nexopos.erp.feature.scanner.vm.ScannerLookupViewModel
import com.nexopos.erp.feature.scanner.vm.ScannerProductDetailViewModel
import com.nexopos.erp.feature.scanner.vm.ScannerProductEditorViewModel
import com.nexopos.erp.feature.specialcustomer.vm.SpecialCustomerViewModel
import com.nexopos.erp.feature.specialcustomer.vm.CustomerListViewModel
import com.nexopos.erp.feature.specialcustomer.vm.CustomerDashboardViewModel
import com.nexopos.erp.feature.procurement.vm.ProcurementViewModel
import com.nexopos.erp.feature.pricelookup.vm.PriceLookupDetailViewModel
import com.nexopos.erp.feature.pricelookup.vm.PriceLookupSearchViewModel
import com.nexopos.erp.feature.manufacturing.vm.ManufacturingViewModel
import com.nexopos.erp.feature.orders.vm.OrdersViewModel
import com.nexopos.erp.feature.inventory.vm.InventoryViewModel
import com.nexopos.erp.feature.containermanagement.vm.ContainerViewModel
import com.nexopos.erp.feature.containermanagement.vm.ContainerInventoryViewModel
import com.nexopos.erp.feature.containermanagement.vm.ContainerMovementsViewModel
import com.nexopos.shared.repo.RegisterRepository as IRegisterRepository
import com.nexopos.shared.repo.ProductRepository as IProductRepository
import com.nexopos.shared.repo.CustomerRepository as ICustomerRepository
import com.nexopos.shared.repo.OrderRepository as IOrderRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().productDao() }
    single { get<AppDatabase>().categoryDao() }
    single { get<AppDatabase>().queuedOrderDao() }
    single { get<AppDatabase>().syncMetadataDao() }
}

val networkModule = module {
    single { SecureTokenStorage(androidContext()) }
    single { SettingsRepository(androidContext(), get()) }
    single { FeatureFlags() }
    single<NexoApi> { ServiceLocator.api(androidContext(), get()) }
    single<MobileApi> { ServiceLocator.mobileApi(androidContext(), get()) }
}

val repositoryModule = module {
    single { AuthRepository(androidContext(), get(), get(), get()) }
    single { CategoryRepository(get(), get()) }
    single { ProductRepository(androidContext(), get(), get()) }
    single { ProductAdminRepository(get()) }
    single { CustomerRepository(androidContext(), get(), get(), get()) }
    single { OrderRepository(androidContext(), get(), get(), get()) }
    single<IProductRepository> { get<ProductRepository>() }
    single<ICustomerRepository> { get<CustomerRepository>() }
    single<IOrderRepository> { get<OrderRepository>() }
    single { OrderQueueRepository(androidContext()) }
    single { SyncMetadataRepository(androidContext()) }
    single { MobileSyncRepository(androidContext(), get()) }
    single<IRegisterRepository> { RegisterRepositoryImpl(androidContext(), get(), get()) }
}

val viewModelModule = module {
    viewModel { AuthViewModel(get(), get()) }
    viewModel { CartViewModel(androidContext(), get(), get(), get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ScanViewModel(get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { SpecialCustomerViewModel(get(), get()) }
    viewModel { CustomerListViewModel(get()) }
    viewModel { CustomerDashboardViewModel(get(), get()) }
    viewModel { ProcurementViewModel(get(), get()) }
    viewModel { ManufacturingViewModel(get(), get()) }
    viewModel { com.nexopos.erp.feature.manufacturing.vm.ProductionOrderEditViewModel(get()) }
    viewModel { com.nexopos.erp.feature.manufacturing.vm.BomItemsEditViewModel(get()) }
    viewModel { OrdersViewModel(androidContext(), get(), get(), get()) }
    viewModel { InventoryViewModel(get()) }
    viewModel { ScannerLookupViewModel(get()) }
    viewModel { ScannerProductDetailViewModel(get()) }
    viewModel { ScannerProductEditorViewModel(get()) }
    viewModel { PriceLookupSearchViewModel(get()) }
    viewModel { PriceLookupDetailViewModel(get()) }
    viewModel { ContainerViewModel(get()) }
    viewModel { ContainerInventoryViewModel(get()) }
    viewModel { ContainerMovementsViewModel(get()) }
}

val appModules = listOf(
    databaseModule,
    networkModule,
    repositoryModule,
    viewModelModule
)
