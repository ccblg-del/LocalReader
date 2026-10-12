package com.example.localreader.data.db

import androidx.room.TypeConverter
import com.example.localreader.data.model.BookFormat
import com.example.localreader.data.model.LocationType

/**
 * Room 类型转换：枚举以 name 字符串存储。
 */
class Converters {

    @TypeConverter
    fun bookFormatToString(value: BookFormat): String = value.name

    @TypeConverter
    fun stringToBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun locationTypeToString(value: LocationType): String = value.name

    @TypeConverter
    fun stringToLocationType(value: String): LocationType = LocationType.valueOf(value)
}
