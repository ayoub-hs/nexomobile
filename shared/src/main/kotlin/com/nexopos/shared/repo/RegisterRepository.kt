package com.nexopos.shared.repo

import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory

interface RegisterRepository {
    suspend fun getRegisters(): List<Register>
    suspend fun getRegister(id: Int): Register
    suspend fun getUsedRegister(): Register?
    suspend fun openRegister(registerId: Int, amount: Double, description: String = ""): Register
    suspend fun closeRegister(registerId: Int, amount: Double, description: String = ""): Register
    suspend fun cashIn(registerId: Int, amount: Double, description: String): RegisterHistory
    suspend fun cashOut(registerId: Int, amount: Double, description: String): RegisterHistory
    suspend fun getSessionHistory(registerId: Int): List<RegisterHistory>
}
