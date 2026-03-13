# Mobile API Specification for NexoPOS

## Base URL: `/api/mobile/`

## Authentication
All endpoints require: `Authorization: Bearer {token}`

---

## Endpoints Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/sync/bootstrap` | Full initial sync |
| GET | `/sync/delta?since={token}` | Incremental changes |
| GET | `/sync/status` | Check if sync needed |
| GET | `/categories/{id}/products` | Category products with variations |
| POST | `/products/search` | Search with full details |
| POST | `/orders/batch` | Submit multiple orders |
| GET | `/orders?cursor={id}&limit={n}` | Paginated orders |
| GET | `/register/config` | Register settings |

---

## 1. Bootstrap Sync
`GET /api/mobile/sync/bootstrap`

Returns all: categories, products (with unit_quantities), customers, payment_methods, order_types, sync_token, server_time.

## 2. Delta Sync  
`GET /api/mobile/sync/delta?since={sync_token}`

Returns only changes since last sync:
- `products.created`, `products.updated`, `products.deleted_ids`
- Same structure for customers, categories, payment_methods

## 3. Category Products
`GET /api/mobile/categories/{id}/products`

Returns category info + products with all unit_quantities bundled.

## 4. Batch Orders
`POST /api/mobile/orders/batch`

Request: `{ "orders": [...] }`
Response: `{ "results": [...], "success_count": N, "failure_count": N }`

---

## Laravel Routes

```php
// routes/api.php
Route::prefix('mobile')->middleware('auth:sanctum')->group(function () {
    Route::get('sync/bootstrap', [MobileSyncController::class, 'bootstrap']);
    Route::get('sync/delta', [MobileSyncController::class, 'delta']);
    Route::get('sync/status', [MobileSyncController::class, 'status']);
    Route::get('categories/{id}/products', [MobileCategoryController::class, 'products']);
    Route::post('products/search', [MobileProductController::class, 'search']);
    Route::post('orders/batch', [MobileOrderController::class, 'batch']);
    Route::get('orders', [MobileOrderController::class, 'index']);
    Route::get('register/config', [MobileConfigController::class, 'show']);
});
```

## Key Implementation Notes

1. **Products must include unit_quantities** with sale_price, wholesale_price
2. **Use sync_token** (base64 encoded timestamp) for delta sync
3. **Batch orders** should check client_reference for duplicates
4. **Soft deletes** - return deleted_ids in delta sync
5. **Add indexes** on updated_at, deleted_at for performance
