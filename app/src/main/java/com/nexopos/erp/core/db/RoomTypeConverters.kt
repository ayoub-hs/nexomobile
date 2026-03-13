package com.nexopos.erp.core.db

import androidx.room.TypeConverter
import com.nexopos.erp.core.db.entities.QueuedOrderStatus

class RoomTypeConverters {

    @TypeConverter
    fun fromQueuedOrderStatus(status: QueuedOrderStatus): String = status.name

    @TypeConverter
    fun toQueuedOrderStatus(value: String): QueuedOrderStatus =
        runCatching { QueuedOrderStatus.valueOf(value) }
            .getOrDefault(QueuedOrderStatus.PENDING)
}
