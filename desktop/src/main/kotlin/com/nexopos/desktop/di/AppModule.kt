package com.nexopos.desktop.di

import com.nexopos.desktop.core.db.AppDatabase
import com.nexopos.desktop.core.network.NexoApiClient
import com.nexopos.desktop.core.prefs.AppSettings
import com.nexopos.desktop.core.repo.*
import com.nexopos.desktop.ui.pos.POSViewModel
import com.nexopos.shared.repo.ProductRepository as IProductRepository
import com.nexopos.shared.repo.CustomerRepository as ICustomerRepository
import com.nexopos.shared.repo.OrderRepository as IOrderRepository
import com.nexopos.shared.repo.RegisterRepository
import org.koin.dsl.module

/**
 * Koin dependency injection modules for Desktop app.
 * Uses shared repository interfaces for cross-platform consistency.
 * Matches Android's DI structure from app/src/main/java/com/nexopos/mobile/di/AppModule.kt
 */

val databaseModule = module {
    single(createdAtStart = true) {
        // Eagerly initialize database before any repositories are created
        AppDatabase.apply { init() }
    }
}

val settingsModule = module {
    single { AppSettings.getInstance() }
}

val networkModule = module {
    single { NexoApiClient(get()) }
}

val repositoryModule = module {
    // Concrete implementations (for direct injection in platform code)
    single { ProductRepository(get()) }
    single { CustomerRepository(get()) }
    single { OrderRepository(get()) }
    single { PaymentMethodRepository(get()) }
    single { CategoryRepository(get()) }
    single { OrderQueueRepository() }
    single { RegisterRepositoryImpl(get()) }
    
    // Bind to shared interfaces (for cross-platform code)
    single<IProductRepository> { get<ProductRepository>() }
    single<ICustomerRepository> { get<CustomerRepository>() }
    single<IOrderRepository> { get<OrderRepository>() }
    single<RegisterRepository> { get<RegisterRepositoryImpl>() }
    
    // Settings
    single { com.nexopos.desktop.core.settings.KeyboardShortcutsManager() }
}

val viewModelModule = module {
    factory { 
        POSViewModel(
            productRepo = get(),
            customerRepo = get(),
            orderRepo = get(),
            categoryRepo = get(),
            registerRepo = get(),
            orderQueueRepository = get()
        )
    }
    factory {
        com.nexopos.desktop.ui.pos.OrdersViewModel(
            queueRepository = get(),
            api = get()
        )
    }
    factory {
        com.nexopos.desktop.ui.pos.ReceiveContainersViewModel(
            api = get()
        )
    }
}

val appModules = listOf(
    databaseModule,
    settingsModule,
    networkModule,
    repositoryModule,
    viewModelModule
)
