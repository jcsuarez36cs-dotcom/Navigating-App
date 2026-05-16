package com.example.navigatingapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.navigatingapp.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityMainBinding
    private var locationManager: LocationManager? = null
    private var currentPdfDescriptor: ParcelFileDescriptor? = null

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult

        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val destination = File(cacheDir, "selected_map.pdf")
        contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        renderFirstPage(destination)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        binding.uploadPdfButton.setOnClickListener {
            documentPicker.launch(arrayOf("application/pdf"))
        }

        binding.enableGpsButton.setOnClickListener {
            ensureLocationPermissionAndStart()
        }
    }

    private fun ensureLocationPermissionAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
            return
        }

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        val manager = locationManager ?: return
        val hasFinePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFinePermission && !hasCoarsePermission) return

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Toast.makeText(this, R.string.enable_location_services, Toast.LENGTH_LONG).show()
                return
            }
        }

        manager.requestLocationUpdates(provider, 2000L, 1f, this)
        Toast.makeText(this, getString(R.string.location_updates_started, provider), Toast.LENGTH_SHORT)
            .show()

        manager.getLastKnownLocation(provider)?.let { updateLocationUi(it) }
    }

    private fun renderFirstPage(file: File) {
        currentPdfDescriptor?.close()
        currentPdfDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        val renderer = PdfRenderer(currentPdfDescriptor!!)
        if (renderer.pageCount == 0) {
            renderer.close()
            return
        }

        val page = renderer.openPage(0)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        binding.pdfImageView.setImageBitmap(bitmap)
        binding.loadedFileLabel.text = getString(R.string.loaded_file_name, file.name)
        page.close()
        renderer.close()
    }

    private fun updateLocationUi(location: Location) {
        binding.locationText.text = getString(
            R.string.location_template,
            location.latitude,
            location.longitude,
            location.accuracy
        )
    }

    override fun onLocationChanged(location: Location) {
        updateLocationUi(location)
    }

    override fun onStop() {
        super.onStop()
        locationManager?.removeUpdates(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        currentPdfDescriptor?.close()
    }
}
