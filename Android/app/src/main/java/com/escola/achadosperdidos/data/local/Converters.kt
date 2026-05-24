package com.escola.achadosperdidos.data.local

import androidx.room.TypeConverter
import com.escola.achadosperdidos.data.model.StatusItem
import java.util.Date

/**
 * Conversores de tipos não primitivos para colunas SQLite:
 *  - Date  <-> Long (timestamp em ms, UTC)
 *  - StatusItem <-> String (nome do enum, mais legível em queries do que ordinal)
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromStatusItem(s: StatusItem?): String? = s?.name

    @TypeConverter
    fun toStatusItem(s: String?): StatusItem? = s?.let { StatusItem.valueOf(it) }
}
