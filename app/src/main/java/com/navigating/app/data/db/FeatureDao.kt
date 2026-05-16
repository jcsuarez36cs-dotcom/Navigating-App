package com.navigating.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.navigating.app.data.Feature
import com.navigating.app.data.ShapeType

@Dao
interface FeatureDao {
    @Query("SELECT * FROM features")
    fun getAllFeatures(): LiveData<List<Feature>>

    @Query("SELECT * FROM features WHERE shapeType = :type")
    fun getFeaturesByType(type: ShapeType): LiveData<List<Feature>>

    @Query("SELECT * FROM features WHERE shapeType = 'POINT' AND (name LIKE '%' || :query || '%' OR attributes LIKE '%' || :query || '%')")
    suspend fun searchPoints(query: String): List<Feature>

    @Query("SELECT * FROM features WHERE id = :id")
    suspend fun getById(id: Long): Feature?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feature: Feature): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(features: List<Feature>)

    @Update
    suspend fun update(feature: Feature)

    @Delete
    suspend fun delete(feature: Feature)

    @Query("DELETE FROM features")
    suspend fun deleteAll()

    @Query("SELECT * FROM features")
    suspend fun getAllSync(): List<Feature>
}
