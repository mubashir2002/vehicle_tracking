package com.example.vehicletrackingprototype

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback? = null

    private lateinit var txtLocation: TextView
    private lateinit var txtSpeed: TextView
    private lateinit var spinnerVehicle: Spinner
    private lateinit var btnStartTracking: Button
    private lateinit var btnStopTracking: Button
    private lateinit var btnManageVehicles: Button
    private lateinit var btnTripHistory: Button
    private lateinit var btnFleetDashboard: Button
    private lateinit var btnArmSecurity: Button
    private lateinit var btnLogout: Button

    private var selectedVehicleId: String = ""
    private var tracking = false
    private var securityArmed = false

    private lateinit var userKey: String
    private lateinit var database: DatabaseReference
    private val vehicleList = mutableListOf<Vehicle>()
    private val vehicleIds = mutableListOf<String>()
    private val vehicleNames = mutableListOf<String>()

    private var geofenceCenter: LatLng? = null
    private var geofenceRadiusMeters: Float = 200f
    private var geofenceCircle: Circle? = null
    private var wasInsideGeofence: Boolean? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val SPEED_LIMIT_KMH = 60.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check authentication
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        userKey = prefs.getString("userKey", null) ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Explicitly set database URL to ensure correct connection
        val databaseUrl = "https://vehicletrackingprototype-d8b88-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl).reference

        txtLocation = findViewById(R.id.txtLocation)
        txtSpeed = findViewById(R.id.txtSpeed)
        spinnerVehicle = findViewById(R.id.spinnerVehicle)
        btnStartTracking = findViewById(R.id.btnStartTracking)
        btnStopTracking = findViewById(R.id.btnStopTracking)
        btnManageVehicles = findViewById(R.id.btnManageVehicles)
        btnTripHistory = findViewById(R.id.btnTripHistory)
        btnFleetDashboard = findViewById(R.id.btnFleetDashboard)
        btnArmSecurity = findViewById(R.id.btnArmSecurity)
        btnLogout = findViewById(R.id.btnLogout)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Load user's vehicles
        loadUserVehicles()

        spinnerVehicle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (vehicleIds.isNotEmpty() && position < vehicleIds.size) {
                    selectedVehicleId = vehicleIds[position]
                    Toast.makeText(this@MainActivity, "Selected: ${vehicleNames[position]}", Toast.LENGTH_SHORT).show()
                    loadVehicleGeofence()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnStartTracking.setOnClickListener {
            if (selectedVehicleId.isEmpty()) {
                Toast.makeText(this, "Please add a vehicle first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startTrackingWithPermissionCheck()
        }
        btnStopTracking.setOnClickListener { stopTracking() }

        btnManageVehicles.setOnClickListener {
            startActivity(Intent(this, VehicleManagementActivity::class.java))
        }

        btnTripHistory.setOnClickListener {
            startActivity(Intent(this, TripHistoryActivity::class.java))
        }

        btnFleetDashboard.setOnClickListener {
            startActivity(Intent(this, FleetDashboardActivity::class.java))
        }

        btnArmSecurity.setOnClickListener {
            if (selectedVehicleId.isEmpty()) {
                Toast.makeText(this, "Please select a vehicle first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            securityArmed = !securityArmed
            if (securityArmed) {
                btnArmSecurity.text = "🔒 Disarm Security"
                Toast.makeText(this, "Security armed for selected vehicle", Toast.LENGTH_SHORT).show()
                // Save security status to Firebase
                database.child("vehicles").child(selectedVehicleId)
                    .child("securityArmed").setValue(true)
            } else {
                btnArmSecurity.text = "🔓 Arm Security"
                Toast.makeText(this, "Security disarmed", Toast.LENGTH_SHORT).show()
                database.child("vehicles").child(selectedVehicleId)
                    .child("securityArmed").setValue(false)
                // Clear unauthorized movement alert
                database.child("vehicles").child(selectedVehicleId)
                    .child("unauthorizedMovement").setValue(false)
            }
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadUserVehicles() {
        database.child("users").child(userKey).child("vehicles")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    vehicleList.clear()
                    vehicleIds.clear()
                    vehicleNames.clear()

                    for (child in snapshot.children) {
                        val vehicle = child.getValue(Vehicle::class.java)
                        vehicle?.let {
                            vehicleList.add(it)
                            vehicleIds.add(it.id)
                            vehicleNames.add("${it.name} (${it.licensePlate})")
                        }
                    }

                    if (vehicleNames.isEmpty()) {
                        vehicleNames.add("No vehicles - Add one")
                        btnStartTracking.isEnabled = false
                    } else {
                        btnStartTracking.isEnabled = true
                    }

                    val adapter = ArrayAdapter(this@MainActivity,
                        android.R.layout.simple_spinner_item, vehicleNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerVehicle.adapter = adapter
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity,
                        "Error loading vehicles: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadVehicleGeofence() {
        if (selectedVehicleId.isEmpty()) return

        database.child("vehicles").child(selectedVehicleId).child("geofence")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("centerLat").getValue(Double::class.java)
                    val lng = snapshot.child("centerLng").getValue(Double::class.java)
                    val radius = snapshot.child("radiusMeters").getValue(Float::class.java)

                    if (lat != null && lng != null) {
                        geofenceCenter = LatLng(lat, lng)
                        geofenceRadiusMeters = radius ?: 200f
                        drawGeofenceOnMap()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun drawGeofenceOnMap() {
        geofenceCenter?.let { center ->
            geofenceCircle?.remove()
            geofenceCircle = mMap.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(geofenceRadiusMeters.toDouble())
                    .strokeWidth(2f)
                    .strokeColor(0xFFFF0000.toInt())
                    .fillColor(0x22FF0000)
            )
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Long press on map to set geofence center
        mMap.setOnMapLongClickListener { latLng ->
            if (selectedVehicleId.isEmpty()) {
                Toast.makeText(this, "Please select a vehicle first", Toast.LENGTH_SHORT).show()
                return@setOnMapLongClickListener
            }
            setGeofence(latLng)
        }

        // Move camera to a default position first
        val defaultLocation = LatLng(24.8607, 67.0011) // Default location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
    }

    private fun startTrackingWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        if (tracking) return

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocationOnMap(location)
                    saveLocationToFirebase(location)
                    checkSpeedAndAlerts(location)
                    checkGeofence(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                mainLooper
            )
            tracking = true
            btnStartTracking.isEnabled = false
            btnStopTracking.isEnabled = true
            Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTracking() {
        if (!tracking) return
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        tracking = false
        btnStartTracking.isEnabled = true
        btnStopTracking.isEnabled = false
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateLocationOnMap(location: Location) {
        val pos = LatLng(location.latitude, location.longitude)
        mMap.clear()

        // Draw marker for vehicle
        mMap.addMarker(MarkerOptions().position(pos).title("My Vehicle"))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))

        // Re-draw geofence circle if exists
        drawGeofenceOnMap()

        txtLocation.text = "Lat: %.5f, Lng: %.5f".format(pos.latitude, pos.longitude)

        val speedKmh = location.speed * 3.6f
        txtSpeed.text = "Speed: %.1f km/h".format(speedKmh)
    }

    private fun saveLocationToFirebase(location: Location) {
        if (selectedVehicleId.isEmpty()) return

        val vehicleRef = database.child("vehicles").child(selectedVehicleId)

        val locData = hashMapOf<String, Any>(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "speed" to location.speed,
            "timestamp" to ServerValue.TIMESTAMP
        )

        // Current location
        vehicleRef.child("currentLocation").setValue(locData)

        // History
        vehicleRef.child("history").push().setValue(locData)
    }

    private fun checkSpeedAndAlerts(location: Location) {
        val speedKmh = location.speed * 3.6f

        // Speed limit alert
        if (speedKmh > SPEED_LIMIT_KMH) {
            Toast.makeText(this, "⚠️ Speed limit exceeded! (${speedKmh.toInt()} km/h)", Toast.LENGTH_SHORT).show()

            // Save speed alert to Firebase
            val alertData = mapOf(
                "type" to "speed",
                "speed" to speedKmh,
                "timestamp" to ServerValue.TIMESTAMP
            )
            database.child("vehicles").child(selectedVehicleId)
                .child("alerts").push().setValue(alertData)
        }

        // Unauthorized movement detection
        if (securityArmed && speedKmh > 2.0f) {
            Toast.makeText(this, "🚨 Unauthorized movement detected!", Toast.LENGTH_LONG).show()
            database.child("vehicles").child(selectedVehicleId)
                .child("unauthorizedMovement").setValue(true)

            // Save alert
            val alertData = mapOf(
                "type" to "unauthorized_movement",
                "timestamp" to ServerValue.TIMESTAMP
            )
            database.child("vehicles").child(selectedVehicleId)
                .child("alerts").push().setValue(alertData)
        }
    }

    private fun setGeofence(center: LatLng) {
        geofenceCenter = center
        wasInsideGeofence = null
        Toast.makeText(this, "Geofence set (200m radius)", Toast.LENGTH_SHORT).show()

        // Store in Firebase
        val gfRef = database.child("vehicles").child(selectedVehicleId).child("geofence")

        val data = mapOf(
            "centerLat" to center.latitude,
            "centerLng" to center.longitude,
            "radiusMeters" to geofenceRadiusMeters
        )
        gfRef.setValue(data)

        // Redraw on map
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(center).title("Geofence Center"))
        geofenceCircle = mMap.addCircle(
            CircleOptions()
                .center(center)
                .radius(geofenceRadiusMeters.toDouble())
                .strokeWidth(2f)
                .strokeColor(0xFFFF0000.toInt())
                .fillColor(0x22FF0000)
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 15f))
    }

    private fun checkGeofence(location: Location) {
        val center = geofenceCenter ?: return

        val locCurrent = Location("").apply {
            latitude = location.latitude
            longitude = location.longitude
        }
        val locCenter = Location("").apply {
            latitude = center.latitude
            longitude = center.longitude
        }

        val distance = locCurrent.distanceTo(locCenter)
        val inside = distance <= geofenceRadiusMeters

        if (wasInsideGeofence == null) {
            wasInsideGeofence = inside
            return
        }

        if (wasInsideGeofence == true && !inside) {
            Toast.makeText(this, "🚨 Geofence EXIT detected!", Toast.LENGTH_LONG).show()
            // Save geofence alert
            val alertData = mapOf(
                "type" to "geofence_exit",
                "timestamp" to ServerValue.TIMESTAMP
            )
            database.child("vehicles").child(selectedVehicleId)
                .child("alerts").push().setValue(alertData)
        } else if (wasInsideGeofence == false && inside) {
            Toast.makeText(this, "✅ Geofence ENTRY detected", Toast.LENGTH_SHORT).show()
        }

        wasInsideGeofence = inside
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking()
            } else {
                Toast.makeText(this, "Location permission is required for tracking", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserVehicles()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (tracking) {
            stopTracking()
        }
    }
}