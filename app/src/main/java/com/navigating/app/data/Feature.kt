package com.navigating.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.navigating.app.data.db.Converters

@Entity(tableName = "features")
@TypeConverters(Converters::class)
data class Feature(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shapeType: ShapeType,
    val coordinates: List<List<Double>>,
    val attributes: Map<String, String> = emptyMap(),
    val sourceFile: String = ""
)

enum class ShapeType { POINT, POLYLINE, POLYGON }
