package com.nexopos.erp.core.repo

import com.nexopos.erp.core.network.AdminProductResponse
import com.nexopos.erp.core.network.ApiResponse
import com.nexopos.erp.core.network.CategoryResponse
import com.nexopos.erp.core.network.ContainerType
import com.nexopos.erp.core.network.CreateProductRequest
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.TaxGroupResponse
import com.nexopos.erp.core.network.UnitGroupResponse
import com.nexopos.erp.core.network.UnitResponse
import com.nexopos.erp.core.network.UpdateProductRequest
import com.nexopos.erp.core.network.UpdateUnitQuantityRequest
import com.nexopos.erp.core.network.UnitQuantityUpdateResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONObject
import org.json.JSONTokener
import retrofit2.HttpException

class ProductAdminRepository(
    private val mobileApi: MobileApi
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val productAdapter = moshi.adapter(AdminProductResponse::class.java).lenient()
    private val wrappedProductAdapter = moshi.adapter<ApiResponse<AdminProductResponse>>(
        Types.newParameterizedType(ApiResponse::class.java, AdminProductResponse::class.java)
    ).lenient()

    suspend fun getProduct(productId: Long): AdminProductResponse {
        return mobileApi.getAdminProduct(productId)
    }

    suspend fun searchByBarcode(barcode: String): AdminProductResponse? {
        val response = mobileApi.searchAdminByBarcodeRaw(barcode)
        if (response.code() == 404) {
            return null
        }
        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        val body = response.body()?.string()?.trim().orEmpty()
        if (body.isBlank() || body == "null") {
            return null
        }

        wrappedProductAdapter.fromJson(body)?.data?.let { return it }

        val parsed = JSONTokener(body).nextValue()
        if (parsed == JSONObject.NULL) {
            return null
        }
        if (parsed is JSONObject && parsed.has("data")) {
            val data = parsed.opt("data")
            if (data == null || data == JSONObject.NULL) {
                return null
            }
            return productAdapter.fromJson(data.toString())
        }

        if (body == "{}") {
            return null
        }

        return productAdapter.fromJson(body)
    }

    suspend fun createProduct(request: CreateProductRequest): AdminProductResponse {
        return mobileApi.createProduct(request)
    }

    suspend fun updateProduct(productId: Long, request: UpdateProductRequest): AdminProductResponse {
        return mobileApi.updateProduct(productId, request)
    }

    suspend fun updateUnitQuantity(unitQuantityId: Long, request: UpdateUnitQuantityRequest): UnitQuantityUpdateResponse {
        return mobileApi.updateUnitQuantity(unitQuantityId, request)
    }

    suspend fun getCategories(): List<CategoryResponse> {
        return mobileApi.getCategories()
    }

    suspend fun getUnits(): List<UnitResponse> {
        return mobileApi.getUnits()
    }

    suspend fun getUnitGroups(): List<UnitGroupResponse> {
        return mobileApi.getUnitGroups()
    }

    suspend fun getTaxGroups(): List<TaxGroupResponse> {
        return mobileApi.getTaxGroups()
    }

    suspend fun getContainerTypes(): List<ContainerType> {
        return mobileApi.getContainerTypes().data
    }
}
