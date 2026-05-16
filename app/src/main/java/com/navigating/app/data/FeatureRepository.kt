package com.navigating.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.navigating.app.data.db.AppDatabase

class FeatureRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).featureDao()

    val allFeatures: LiveData<List<Feature>> = dao.getAllFeatures()

    suspend fun insert(feature: Feature): Long = dao.insert(feature)
    suspend fun insertAll(features: List<Feature>) = dao.insertAll(features)
    suspend fun update(feature: Feature) = dao.update(feature)
    suspend fun delete(feature: Feature) = dao.delete(feature)
    suspend fun searchPoints(query: String): List<Feature> = dao.searchPoints(query)
    suspend fun getById(id: Long): Feature? = dao.getById(id)
    suspend fun getAllSync(): List<Feature> = dao.getAllSync()
}
