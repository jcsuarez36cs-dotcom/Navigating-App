package com.navigating.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.navigating.app.data.Feature
import com.navigating.app.data.FeatureRepository
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FeatureRepository(app)
    val allFeatures: LiveData<List<Feature>> = repo.allFeatures

    private val _selectedFeature = MutableLiveData<Feature?>()
    val selectedFeature: LiveData<Feature?> = _selectedFeature

    private val _gpsPosition = MutableLiveData<GeoPoint?>()
    val gpsPosition: LiveData<GeoPoint?> = _gpsPosition

    private val _navigationTarget = MutableLiveData<Feature?>()
    val navigationTarget: LiveData<Feature?> = _navigationTarget

    private val _searchResults = MutableLiveData<List<Feature>>()
    val searchResults: LiveData<List<Feature>> = _searchResults

    fun selectFeature(f: Feature?) { _selectedFeature.value = f }
    fun updateGps(gp: GeoPoint) { _gpsPosition.value = gp }
    fun setNavigationTarget(f: Feature?) { _navigationTarget.value = f }

    fun insertFeature(f: Feature) = viewModelScope.launch { repo.insert(f) }
    fun insertAll(list: List<Feature>) = viewModelScope.launch { repo.insertAll(list) }
    fun updateFeature(f: Feature) = viewModelScope.launch { repo.update(f) }
    fun deleteFeature(f: Feature) = viewModelScope.launch { repo.delete(f) }

    fun searchPoints(query: String) = viewModelScope.launch {
        _searchResults.postValue(repo.searchPoints(query))
    }

    fun getAllSync(callback: (List<Feature>) -> Unit) = viewModelScope.launch {
        callback(repo.getAllSync())
    }
}
