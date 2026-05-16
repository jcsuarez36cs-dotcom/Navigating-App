package com.navigating.app.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.navigating.app.data.ShapeType

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromCoordinates(value: String): List<List<Double>> {
        val type = object : TypeToken<List<List<Double>>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toCoordinates(coords: List<List<Double>>): String = gson.toJson(coords)

    @TypeConverter
    fun fromAttributes(value: String): Map<String, String> {
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toAttributes(attrs: Map<String, String>): String = gson.toJson(attrs)

    @TypeConverter
    fun fromShapeType(value: String): ShapeType = ShapeType.valueOf(value)

    @TypeConverter
    fun toShapeType(type: ShapeType): String = type.name
}
