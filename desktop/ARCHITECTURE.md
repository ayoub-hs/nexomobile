# NexoPOS Desktop - Architecture Documentation

## Overview

This desktop application is a **full implementation of the Android NexoPOS app** for Linux ARM devices (H313 box), using the **exact same data architecture** and connecting to the **real NexoPOS server**.

## Key Differences from Sample Code

❌ **NO SAMPLE DATA** - All data comes from your NexoPOS server  
✅ **Real API Integration** - OkHttp + Moshi (matching Android)  
✅ **Local Database** - SQLite with Exposed ORM (matching Android Room)  
✅ **Repositories** - Same repository pattern as Android app  
✅ **Offline Support** - Queue orders when offline, sync when online  
✅ **ViewModel Pattern** - State management matching Android app  

## Architecture Layers

```
┌─────────────────────────────────────────────────┐
│              UI Layer (Compose)                 │
│  - POSScreenWithViewModel                       │
│  - SettingsScreen                               │
│  - OrdersScreen                                 │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│           ViewModel Layer                       │
│  - POSViewModel (state management)              │
│  - Collects data from repositories              │
│  - Exposes StateFlows to UI                     │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│          Repository Layer                       │
│  - ProductRepository (products + search)        │
│  - CustomerRepository (customers)               │
│  - PaymentMethodRepository (payment types)      │
│  - OrderRepository (order submission)           │
│                                                  │
│  Pattern: Network first, cache fallback         │
└─────────────────────────────────────────────────┘
           ↙                    ↘
┌──────────────────┐    ┌──────────────────────┐
│  Network Layer   │    │   Database Layer     │
│  (NexoApiClient) │    │   (Exposed + SQLite) │
│                  │    │                      │
│  - OkHttp        │    │  Tables:             │
│  - Moshi JSON    │    │  - Products          │
│  - Retrofit-like │    │  - Customers         │
│    endpoints     │    │  - PaymentMethods    │
│                  │    │  - QueuedOrders      │
└──────────────────┘    └──────────────────────┘
         ↓
┌─────────────────────────────────────────────────┐
│         NexoPOS Server (Your Backend)           │
│  - api/nexopos/v4/products                      │
│  - api/nexopos/v4/customers                     │
│  - api/nexopos/v4/orders                        │
│  - api/nexopos/v4/orders/payment-types          │
└─────────────────────────────────────────────────┘
```

## Data Flow

### 1. Initial Setup
1. User enters **Server URL** and **API Token** in Settings
2. App saves to Java Preferences (`~/.java/.userPrefs/com/nexopos/desktop`)
3. Click "Save & Sync" triggers initial data fetch:
   - Products from `/api/nexopos/v4/products`
   - Customers from `/api/nexopos/v4/customers`
   - Payment Methods from `/api/nexopos/v4/orders/payment-types`
4. Data cached in local SQLite (`~/.nexopos/nexopos.db`)

### 2. Product Search (Barcode Scan)
1. Barcode scanner inputs barcode
2. ViewModel calls `ProductRepository.searchByBarcode()`
3. Repository checks **network first**: `/api/nexopos/v4/products/barcode/{barcode}`
4. If offline or not found, falls back to **local cache**
5. Product returned to ViewModel, added to cart

### 3. Order Submission
1. User fills cart, selects customer, payment, discount
2. Clicks "Submit Order"
3. ViewModel creates `CreateOrderRequest` (matching Android format)
4. OrderRepository submits to `/api/nexopos/v4/orders`
5. **If online**: Order sent immediately, receipt printed, drawer opened
6. **If offline**: Order queued in `QueuedOrders` table, syncs later

### 4. Offline Support
- Orders saved to local database if server unreachable
- Background sync retries failed orders automatically
- Pending order count visible in UI

## Database Schema (Matching Android Room)

### Products Table
```sql
CREATE TABLE products (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    barcode TEXT,
    barcode_type TEXT,
    sku TEXT,
    status TEXT,
    category_id INTEGER,
    unit_quantities_json TEXT,  -- JSON array of pricing/units
    updated_at INTEGER,
    is_deleted BOOLEAN DEFAULT 0
);
CREATE INDEX idx_products_barcode ON products(barcode);
```

### Customers Table
```sql
CREATE TABLE customers (
    id INTEGER PRIMARY KEY,
    username TEXT,
    name TEXT,
    first_name TEXT,
    last_name TEXT,
    email TEXT,
    phone TEXT,
    group_id INTEGER,
    group_name TEXT,
    is_default BOOLEAN,
    updated_at INTEGER
);
```

### Payment Methods Table
```sql
CREATE TABLE payment_methods (
    identifier TEXT PRIMARY KEY,
    label TEXT,
    selected BOOLEAN,
    is_readonly BOOLEAN,
    updated_at INTEGER
);
```

### Queued Orders Table (Offline Support)
```sql
CREATE TABLE queued_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_json TEXT NOT NULL,  -- Full CreateOrderRequest as JSON
    status TEXT,               -- 'pending', 'synced', 'failed'
    error_message TEXT,
    server_order_id INTEGER,
    created_at INTEGER,
    updated_at INTEGER
);
```

## API Models (Matching Android)

All models use **Moshi** with `@Json` annotations matching Android app:

```kotlin
@JsonClass(generateAdapter = true)
data class CreateOrderRequest(
    val customer_id: Long,
    val type: String = "takeaway",
    val discount_type: String?,        // "flat" or "percentage"
    val discount: Double?,              // Amount if flat
    val discount_percentage: Double?,   // Percentage if percentage
    val products: List<OrderProduct>,
    val payments: List<OrderPayment>
)

@JsonClass(generateAdapter = true)
data class OrderProduct(
    val product_id: Long?,
    val name: String,
    val quantity: Double,
    val unit_quantity_id: Long?,
    val unit_id: Long?,
    val unit_name: String?,
    val unit_price: Double,
    val price_with_tax: Double?,
    val price_without_tax: Double?,
    val product_type: String = "product",
    val discount: Double = 0.0
)
```

## Dependencies

### Network
- **OkHttp 4.12.0** - HTTP client (same as Android)
- **Moshi 1.15.0** - JSON serialization (same as Android)
- **Moshi-Kotlin 1.15.0** - Kotlin reflection support

### Database
- **Exposed 0.45.0** - Kotlin SQL framework (equivalent to Room)
- **SQLite JDBC 3.44.1.0** - SQLite database

### Hardware
- **JNA 5.13.0** - Native library access (libusb for printer)

## Settings Storage

Uses Java Preferences API (cross-platform):
- **Location**: `~/.java/.userPrefs/com/nexopos/desktop/prefs.xml`
- **Keys**:
  - `base_url` - NexoPOS server URL
  - `token` - API bearer token
  - `store_name` - Display name (optional)

## Initialization Flow

```kotlin
fun main() {
    AppInitializer.initialize()  // Called on app start
    // Creates:
    // 1. Database connection (~/.nexopos/nexopos.db)
    // 2. Settings instance
    // 3. API client with auth headers
    // 4. Repositories
    // 5. ViewModels
}
```

## State Management

Uses **Kotlin StateFlow** (equivalent to Android LiveData/StateFlow):

```kotlin
class POSViewModel {
    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()
    
    // UI collects:
    val products by viewModel.products.collectAsState()
}
```

## Error Handling

1. **Network errors**: Fallback to cache, queue orders offline
2. **API errors**: Display user-friendly messages
3. **Database errors**: Log and show alert
4. **Hardware errors**: Continue order processing, log error

## Security

- **API Token**: Stored in Java Preferences (not encrypted, same as Android SharedPreferences)
- **HTTPS Required**: Server must use HTTPS in production
- **Bearer Auth**: All requests include `Authorization: Bearer {token}` header

## Testing Locally

1. Set up NexoPOS server (or use demo.nexopos.com)
2. Get API token from NexoPOS dashboard
3. Run desktop app:
   ```bash
   ./gradlew :desktop:run
   ```
4. Enter server URL and token in Settings
5. Click "Save & Sync"
6. Navigate to POS - products should load from server

## Deployment to H313

```bash
# Build uber JAR on ARM64 machine
./gradlew :desktop:packageReleaseUberJarForCurrentOS

# JAR location:
# desktop/build/compose/jars/NexoPOS-Desktop-linux-arm64-5.1.0-release.jar

# Run:
java -jar NexoPOS-Desktop-linux-arm64-5.1.0-release.jar

# Database will be created at:
# ~/.nexopos/nexopos.db

# Settings stored at:
# ~/.java/.userPrefs/com/nexopos/desktop/
```

## Comparison: Android vs Desktop

| Feature | Android | Desktop |
|---------|---------|---------|
| **Database** | Room | Exposed + SQLite |
| **HTTP** | Retrofit + OkHttp | OkHttp direct |
| **JSON** | Moshi | Moshi |
| **Settings** | SharedPreferences | Java Preferences |
| **State** | LiveData/StateFlow | StateFlow |
| **DI** | Hilt | Manual singleton |
| **Coroutines** | ✅ | ✅ |
| **Offline Sync** | ✅ | ✅ |

## No Sample Data!

This implementation **DOES NOT USE**:
- ❌ Hardcoded product lists
- ❌ Sample customers
- ❌ Mock API responses
- ❌ In-memory data

Everything comes from **your actual NexoPOS server** via the REST API, just like the Android app.
