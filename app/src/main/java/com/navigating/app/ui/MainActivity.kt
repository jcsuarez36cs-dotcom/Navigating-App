package com.navigating.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.*
import com.navigating.app.R
import com.navigating.app.data.Feature
import com.navigating.app.data.ShapeType
import com.navigating.app.databinding.ActivityMainBinding
import com.navigating.app.io.GpxExporter
import com.navigating.app.io.KmzExporter
import com.navigating.app.io.ShapefileParser
import com.navigating.app.io.ShapefileWriter
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MapViewModel
    private lateinit var map: MapView
    private lateinit var locationClient: FusedLocationProviderClient

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val featureOverlays = mutableMapOf<Long, Overlay>()
    private var navigationLineOverlay: Polyline? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val gp = GeoPoint(loc.latitude, loc.longitude)
                viewModel.updateGps(gp)
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", 0))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        map = binding.map

        setupMap()
        setupSearch()
        requestPermissions()

        viewModel.allFeatures.observe(this) { features ->
            refreshFeatureOverlays(features)
        }
        viewModel.gpsPosition.observe(this) { gp ->
            gp?.let { updateNavigationLine(it) }
        }
        viewModel.navigationTarget.observe(this) {
            updateNavigationLine(viewModel.gpsPosition.value)
        }

        binding.fabImport.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(0.0, 0.0))

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        map.overlays.add(myLocationOverlay)
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString().trim()
            if (query.isEmpty()) return@setOnClickListener
            viewModel.searchPoints(query)
        }
        viewModel.searchResults.observe(this) { results ->
            if (results.isEmpty()) {
                Toast.makeText(this, "No points found", Toast.LENGTH_SHORT).show()
                return@observe
            }
            showSearchResults(results)
        }
    }

    private fun showSearchResults(results: List<Feature>) {
        val names = results.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Point")
            .setItems(names) { _, i ->
                val f = results[i]
                viewModel.setNavigationTarget(f)
                val pt = f.coordinates.firstOrNull() ?: return@setItems
                val gp = GeoPoint(pt[1], pt[0])
                map.controller.animateTo(gp)
                map.controller.setZoom(16.0)
                Toast.makeText(this, "Navigating to: ${f.name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun handleImport(uri: Uri) {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            c.moveToFirst()
            if (idx >= 0) c.getString(idx) else "import"
        } ?: "import"

        val ext = displayName.substringAfterLast('.', "")
        if (ext.lowercase() != "shp") {
            Toast.makeText(this, "Please select a .shp file", Toast.LENGTH_LONG).show()
            return
        }

        val tmpDir = File(cacheDir, "shp_import")
        tmpDir.mkdirs()
        val baseName = displayName.substringBeforeLast('.')
        val shpFile = File(tmpDir, "$baseName.shp")

        contentResolver.openInputStream(uri)?.use { inp ->
            FileOutputStream(shpFile).use { out -> inp.copyTo(out) }
        }

        val dbfFile = File(tmpDir, "$baseName.dbf")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val features = ShapefileParser.parse(shpFile, if (dbfFile.exists()) dbfFile else null)
                withContext(Dispatchers.Main) {
                    viewModel.insertAll(features)
                    Toast.makeText(this@MainActivity, "Imported ${features.size} features", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshFeatureOverlays(features: List<Feature>) {
        featureOverlays.values.forEach { map.overlays.remove(it) }
        featureOverlays.clear()
        features.forEach { f ->
            val overlay = createOverlay(f)
            if (overlay != null) {
                map.overlays.add(overlay)
                featureOverlays[f.id] = overlay
            }
        }
        map.invalidate()
    }

    private fun createOverlay(f: Feature): Overlay? {
        return when (f.shapeType) {
            ShapeType.POINT -> {
                val pt = f.coordinates.firstOrNull() ?: return null
                Marker(map).apply {
                    position = GeoPoint(pt[1], pt[0])
                    title = f.name
                    snippet = f.attributes.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    setOnMarkerClickListener { _, _ ->
                        viewModel.selectFeature(f)
                        showFeatureOptions(f)
                        true
                    }
                }
            }
            ShapeType.POLYLINE -> {
                Polyline(map).apply {
                    setPoints(f.coordinates.map { GeoPoint(it[1], it[0]) })
                    outlinePaint.color = 0xFF0000FF.toInt()
                    outlinePaint.strokeWidth = 4f
                    setOnClickListener(Polyline.OnClickListener { _, _, _ ->
                        viewModel.selectFeature(f)
                        showFeatureOptions(f)
                        true
                    })
                }
            }
            ShapeType.POLYGON -> {
                Polygon(map).apply {
                    val pts = f.coordinates.map { GeoPoint(it[1], it[0]) }.toMutableList()
                    if (pts.isNotEmpty() && pts.first() != pts.last()) {
                        pts.add(pts.first())
                    }
                    points = pts
                    fillPaint.color = 0x220000FF
                    outlinePaint.color = 0xFF0000FF.toInt()
                    outlinePaint.strokeWidth = 3f
                    title = f.name
                    setOnClickListener(Polygon.OnClickListener { _, _, _ ->
                        viewModel.selectFeature(f)
                        showFeatureOptions(f)
                        true
                    })
                }
            }
        }
    }

    private fun showFeatureOptions(f: Feature) {
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(arrayOf("Edit", "Navigate To", "Delete")) { _, which ->
                when (which) {
                    0 -> editFeature(f)
                    1 -> navigateTo(f)
                    2 -> confirmDelete(f)
                }
            }
            .show()
    }

    private fun editFeature(f: Feature) {
        EditFeatureDialog.newInstance(f) { updated ->
            viewModel.updateFeature(updated)
        }.show(supportFragmentManager, "edit")
    }

    private fun navigateTo(f: Feature) {
        viewModel.setNavigationTarget(f)
        val pt = f.coordinates.firstOrNull() ?: return
        map.controller.animateTo(GeoPoint(pt[1], pt[0]))
        Toast.makeText(this, "Navigating to: ${f.name}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(f: Feature) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${f.name}?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteFeature(f) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateNavigationLine(gpsPos: GeoPoint?) {
        val target = viewModel.navigationTarget.value ?: run {
            navigationLineOverlay?.let { map.overlays.remove(it) }
            navigationLineOverlay = null
            map.invalidate()
            return
        }
        val targetPt = target.coordinates.firstOrNull() ?: return
        val targetGp = GeoPoint(targetPt[1], targetPt[0])
        val startGp = gpsPos ?: return
        navigationLineOverlay?.let { map.overlays.remove(it) }
        navigationLineOverlay = Polyline(map).apply {
            setPoints(listOf(startGp, targetGp))
            outlinePaint.color = 0xFFFF0000.toInt()
            outlinePaint.strokeWidth = 5f
        }
        map.overlays.add(navigationLineOverlay)
        map.invalidate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_export_shp -> { exportShapefile(); true }
            R.id.menu_export_kmz -> { exportKmz(); true }
            R.id.menu_export_gpx -> { exportGpx(); true }
            R.id.menu_clear_navigation -> { viewModel.setNavigationTarget(null); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportShapefile() {
        viewModel.getAllSync { features ->
            if (features.isEmpty()) { showToast("No features to export"); return@getAllSync }
            val outDir = File(getExternalFilesDir(null) ?: filesDir, "exports")
            outDir.mkdirs()
            listOf(ShapeType.POINT, ShapeType.POLYLINE, ShapeType.POLYGON).forEach { type ->
                val group = features.filter { it.shapeType == type }
                if (group.isNotEmpty()) {
                    ShapefileWriter.write(group, outDir, type.name.lowercase())
                }
            }
            showToast("Exported to ${outDir.absolutePath}")
        }
    }

    private fun exportKmz() {
        viewModel.getAllSync { features ->
            if (features.isEmpty()) { showToast("No features to export"); return@getAllSync }
            val outDir = File(getExternalFilesDir(null) ?: filesDir, "exports")
            outDir.mkdirs()
            val outFile = File(outDir, "export.kmz")
            KmzExporter.export(features, outFile)
            showToast("Exported KMZ to ${outFile.absolutePath}")
        }
    }

    private fun exportGpx() {
        viewModel.getAllSync { features ->
            if (features.isEmpty()) { showToast("No features to export"); return@getAllSync }
            val outDir = File(getExternalFilesDir(null) ?: filesDir, "exports")
            outDir.mkdirs()
            val outFile = File(outDir, "export.gpx")
            GpxExporter.export(features, outFile)
            showToast("Exported GPX to ${outFile.absolutePath}")
        }
    }

    private fun showToast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1001 && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        locationClient.requestLocationUpdates(req, locationCallback, mainLooper)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        myLocationOverlay?.disableMyLocation()
        locationClient.removeLocationUpdates(locationCallback)
    }
}
