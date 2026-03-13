package com.nexopos.erp.core.db

import com.nexopos.erp.core.db.entities.CategoryEntity
import com.nexopos.erp.core.db.entities.CategoryProductEntity
import com.nexopos.erp.core.db.entities.CustomerEntity
import com.nexopos.erp.core.db.entities.PaymentMethodEntity
import com.nexopos.erp.core.db.entities.ProductEntity
import com.nexopos.erp.core.db.entities.QueuedOrderEntity
import com.nexopos.erp.core.network.Category
import com.nexopos.erp.core.network.CreateOrderRequest
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.core.network.CustomerGroup
import com.nexopos.erp.core.network.MobileCategory
import com.nexopos.erp.core.network.MobileProduct
import com.nexopos.erp.core.network.PaymentMethod
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.network.UnitDetail
import com.nexopos.erp.core.network.UnitQuantity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

private val moshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val unitQuantityListType = Types.newParameterizedType(List::class.java, UnitQuantity::class.java)
private val unitQuantityAdapter = moshi.adapter<List<UnitQuantity>>(unitQuantityListType)
private val createOrderAdapter = moshi.adapter(CreateOrderRequest::class.java)

fun Product.toEntity(updatedAt: Long, categoryId: Long? = null): ProductEntity {
    val unitJson = unitQuantities?.let { unitQuantityAdapter.toJson(it) }
    return ProductEntity(
        id = id,
        name = name,
        barcode = barcode,
        barcodeType = barcodeType,
        sku = sku,
        status = status,
        categoryId = categoryId,
        unitQuantitiesJson = unitJson,
        updatedAt = updatedAt
    )
}

fun MobileProduct.toEntity(updatedAt: Long): ProductEntity {
    val unitJson = unitQuantities.let { unitQuantityAdapter.toJson(it) }
    return ProductEntity(
        id = id,
        name = name,
        barcode = barcode,
        barcodeType = barcodeType,
        sku = sku,
        status = status,
        categoryId = categoryId,
        unitQuantitiesJson = unitJson,
        updatedAt = updatedAt,
        serverUpdatedAt = this.updatedAt,
        isDeleted = deletedAt != null
    )
}

fun MobileCategory.toEntity(updatedAt: Long): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        description = description,
        productsCount = productsCount,
        displayOrder = displayOrder,
        updatedAt = updatedAt
    )
}

fun CategoryEntity.toMobileCategory(): MobileCategory {
    return MobileCategory(
        id = id,
        name = name,
        description = description,
        productsCount = productsCount,
        displayOrder = displayOrder
    )
}

/**
 * Convert Category (from GET /api/categories) to CategoryEntity
 */
fun Category.toEntity(updatedAt: Long): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        description = description,
        productsCount = totalItems,
        displayOrder = 0, // Not provided by API, use default
        updatedAt = updatedAt
    )
}

fun PaymentMethod.toEntity(updatedAt: Long): PaymentMethodEntity {
    return PaymentMethodEntity(
        identifier = identifier,
        label = label,
        selected = selected,
        readonly = readonly,
        updatedAt = updatedAt
    )
}

fun PaymentMethodEntity.toModel(): PaymentMethod {
    return PaymentMethod(
        identifier = identifier,
        label = label,
        selected = selected,
        readonly = readonly
    )
}

fun ProductEntity.toModel(): Product {
    val units = unitQuantitiesJson?.let(unitQuantityAdapter::fromJson)
    return Product(
        id = id,
        name = name,
        barcode = barcode,
        barcodeType = barcodeType,
        sku = sku,
        status = status,
        unitQuantities = units
    )
}

fun ProductEntity.toMobileProduct(): MobileProduct {
    val units = unitQuantitiesJson?.let(unitQuantityAdapter::fromJson) ?: emptyList()
    return MobileProduct(
        id = id,
        name = name,
        barcode = barcode,
        barcodeType = barcodeType,
        sku = sku,
        status = status,
        categoryId = categoryId,
        unitQuantities = units,
        updatedAt = serverUpdatedAt,
        deletedAt = if (isDeleted) serverUpdatedAt else null
    )
}

fun Customer.toEntity(updatedAt: Long): CustomerEntity {
    return CustomerEntity(
        id = id,
        username = username,
        name = name,
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        groupId = group?.id,
        groupName = group?.name,
        isDefault = isDefault,
        updatedAt = updatedAt
    )
}

fun CustomerEntity.toModel(): Customer {
    val group = groupId?.let { id -> CustomerGroup(id = id, name = groupName, description = null) }
    return Customer(
        id = id,
        username = username,
        name = name,
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        group = group,
        isDefault = isDefault
    )
}

fun CreateOrderRequest.toEntity(clientReference: String, createdAt: Long): QueuedOrderEntity {
    val payload = createOrderAdapter.toJson(copy(clientReference = clientReference))
    return QueuedOrderEntity(
        clientReference = clientReference,
        payloadJson = payload,
        createdAt = createdAt
    )
}

fun QueuedOrderEntity.toRequest(): CreateOrderRequest {
    val request = createOrderAdapter.fromJson(payloadJson)
    requireNotNull(request) { "Unable to decode queued order payload" }
    return request
}
