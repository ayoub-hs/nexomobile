package com.nexopos.erp.core.repo

import android.content.Context
import com.nexopos.erp.core.network.NexoApi
import com.nexopos.erp.core.network.RegisterActionRequest
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import com.nexopos.shared.repo.RegisterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class RegisterRepositoryImpl(
    context: Context,
    private val api: NexoApi,
    private val settings: SettingsRepository
) : RegisterRepository {

    @Suppress("unused")
    private val appContext = context.applicationContext

    private suspend fun baseUrl(): String = settings.baseUrlFlow.first()

    private suspend fun syncRegister(registerId: Int) {
        runCatching {
            api.syncRegister(baseUrl() + "api/cash-registers/sync/$registerId")
        }
    }

    override suspend fun getRegisters(): List<Register> = withContext(Dispatchers.IO) {
        api.getRegisters(baseUrl() + "api/cash-registers").map(::parseRegisterFromMap)
    }

    override suspend fun getRegister(id: Int): Register = withContext(Dispatchers.IO) {
        syncRegister(id)
        api.getRegister(baseUrl() + "api/cash-registers/$id")
    }

    override suspend fun getUsedRegister(): Register? = withContext(Dispatchers.IO) {
        try {
            val response = api.getUsedRegister(baseUrl() + "api/cash-registers/used")
            val data = response["data"] as? Map<*, *>
            val register = data?.get("register") as? Map<*, *>
            val usedRegister = register?.let(::parseRegisterFromMap)

            if (usedRegister != null) {
                syncRegister(usedRegister.id)
                api.getRegister(baseUrl() + "api/cash-registers/${usedRegister.id}")
            } else {
                null
            }
        } catch (error: HttpException) {
            if (error.code() == 403 || error.code() == 404) {
                null
            } else {
                throw error
            }
        }
    }

    override suspend fun openRegister(registerId: Int, amount: Double, description: String): Register =
        withContext(Dispatchers.IO) {
            val response = api.openRegister(
                url = baseUrl() + "api/cash-registers/open/$registerId",
                body = RegisterActionRequest(amount = amount, description = description)
            )
            response.data.register
        }

    override suspend fun closeRegister(registerId: Int, amount: Double, description: String): Register =
        withContext(Dispatchers.IO) {
            val response = api.closeRegister(
                url = baseUrl() + "api/cash-registers/close/$registerId",
                body = RegisterActionRequest(amount = amount, description = description)
            )
            response.data.register
        }

    override suspend fun cashIn(registerId: Int, amount: Double, description: String): RegisterHistory =
        withContext(Dispatchers.IO) {
            api.cashIn(
                url = baseUrl() + "api/cash-registers/register-cash-in/$registerId",
                body = RegisterActionRequest(amount = amount, description = description)
            )
            RegisterHistory(
                id = 0,
                registerId = registerId,
                action = "register-cash-in",
                author = 0,
                value = amount,
                description = description,
                createdAt = "",
                updatedAt = ""
            )
        }

    override suspend fun cashOut(registerId: Int, amount: Double, description: String): RegisterHistory =
        withContext(Dispatchers.IO) {
            api.cashOut(
                url = baseUrl() + "api/cash-registers/register-cash-out/$registerId",
                body = RegisterActionRequest(amount = amount, description = description)
            )
            RegisterHistory(
                id = 0,
                registerId = registerId,
                action = "register-cash-out",
                author = 0,
                value = -amount,
                description = description,
                createdAt = "",
                updatedAt = ""
            )
        }

    override suspend fun getSessionHistory(registerId: Int): List<RegisterHistory> =
        withContext(Dispatchers.IO) {
            syncRegister(registerId)
            val response = api.getRegisterHistory(baseUrl() + "api/cash-registers/session-history/$registerId")
            when {
                response["history"] is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (response["history"] as List<Map<*, *>>)
                        .map(::parseHistoryFromMap)
                        .sortedWith(compareBy<RegisterHistory> { it.createdAt }.thenBy { it.id })
                }
                response["data"] is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (response["data"] as List<Map<*, *>>)
                        .map(::parseHistoryFromMap)
                        .sortedWith(compareBy<RegisterHistory> { it.createdAt }.thenBy { it.id })
                }
                response["data"] is Map<*, *> -> {
                    val data = response["data"] as Map<*, *>
                    @Suppress("UNCHECKED_CAST")
                    (data["history"] as? List<Map<*, *>>)
                        .orEmpty()
                        .map(::parseHistoryFromMap)
                        .sortedWith(compareBy<RegisterHistory> { it.createdAt }.thenBy { it.id })
                }
                else -> emptyList()
            }
        }

    private fun parseRegisterFromMap(map: Map<*, *>): Register {
        return Register(
            id = (map["id"] as? Number)?.toInt() ?: 0,
            name = map["name"] as? String ?: "",
            description = map["description"] as? String,
            status = map["status"] as? String ?: "closed",
            usedBy = (map["used_by"] as? Number)?.toInt(),
            balance = (map["balance"] as? Number)?.toDouble() ?: 0.0,
            author = (map["author"] as? Number)?.toInt() ?: 0,
            uuid = map["uuid"] as? String,
            createdAt = map["created_at"] as? String ?: "",
            updatedAt = map["updated_at"] as? String ?: ""
        )
    }

    private fun parseHistoryFromMap(map: Map<*, *>): RegisterHistory {
        return RegisterHistory(
            id = (map["id"] as? Number)?.toInt() ?: 0,
            registerId = (map["register_id"] as? Number)?.toInt() ?: 0,
            paymentId = (map["payment_id"] as? Number)?.toInt(),
            paymentTypeId = (map["payment_type_id"] as? Number)?.toInt() ?: 0,
            orderId = (map["order_id"] as? Number)?.toInt(),
            action = map["action"] as? String ?: "",
            author = (map["author"] as? Number)?.toInt() ?: 0,
            value = (map["value"] as? Number)?.toDouble() ?: 0.0,
            description = map["description"] as? String,
            uuid = map["uuid"] as? String,
            balanceBefore = (map["balance_before"] as? Number)?.toDouble() ?: 0.0,
            transactionType = map["transaction_type"] as? String,
            balanceAfter = (map["balance_after"] as? Number)?.toDouble() ?: 0.0,
            createdAt = map["created_at"] as? String ?: "",
            updatedAt = map["updated_at"] as? String ?: ""
        )
    }
}
