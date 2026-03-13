package com.nexopos.desktop.core.repo

import com.nexopos.desktop.core.network.NexoApiClient
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import com.nexopos.shared.repo.RegisterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegisterRepositoryImpl(
    private val apiClient: NexoApiClient
) : RegisterRepository {
    
    override suspend fun getRegisters(): List<Register> {
        return withContext(Dispatchers.IO) {
            apiClient.getRegisters().getOrElse {
                println("[RegisterRepositoryImpl] Failed to fetch registers: ${it.message}")
                emptyList()
            }
        }
    }
    
    override suspend fun getRegister(id: Int): Register {
        return withContext(Dispatchers.IO) {
            apiClient.getRegister(id).getOrElse {
                println("[RegisterRepositoryImpl] Failed to fetch register: ${it.message}")
                throw Exception("Failed to fetch register: ${it.message}")
            }
        }
    }
    
    override suspend fun getUsedRegister(): Register? {
        return withContext(Dispatchers.IO) {
            try {
                apiClient.getUsedRegister().getOrNull()
            } catch (e: Exception) {
                println("[RegisterRepositoryImpl] No register in use: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun openRegister(registerId: Int, amount: Double, description: String): Register {
        return withContext(Dispatchers.IO) {
            apiClient.openRegister(registerId, amount, description).getOrElse {
                throw Exception("Failed to open register: ${it.message}")
            }
        }
    }
    
    override suspend fun closeRegister(registerId: Int, amount: Double, description: String): Register {
        return withContext(Dispatchers.IO) {
            apiClient.closeRegister(registerId, amount, description).getOrElse {
                throw Exception("Failed to close register: ${it.message}")
            }
        }
    }
    
    override suspend fun cashIn(registerId: Int, amount: Double, description: String): RegisterHistory {
        return withContext(Dispatchers.IO) {
            apiClient.cashIn(registerId, amount, description).getOrElse {
                throw Exception("Failed to cash in: ${it.message}")
            }
        }
    }
    
    override suspend fun cashOut(registerId: Int, amount: Double, description: String): RegisterHistory {
        return withContext(Dispatchers.IO) {
            apiClient.cashOut(registerId, amount, description).getOrElse {
                throw Exception("Failed to cash out: ${it.message}")
            }
        }
    }
    
    override suspend fun getSessionHistory(registerId: Int): List<RegisterHistory> {
        return withContext(Dispatchers.IO) {
            apiClient.getSessionHistory(registerId).getOrElse {
                throw Exception("Failed to fetch session history: ${it.message}")
            }
        }
    }
}
