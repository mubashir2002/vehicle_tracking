package com.example.vehicletrackingprototype

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*

class FleetDashboardActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var recyclerFleet: RecyclerView
    private lateinit var txtTotalVehicles: TextView
    private lateinit var txtActiveVehicles: TextView
    private lateinit var txtAlerts: TextView
    private lateinit var progressLoading: ProgressBar

    private lateinit var userKey: String
    private lateinit var database: DatabaseReference

    private val fleetVehicles = mutableListOf<FleetVehicleStatus>()
    private lateinit var adapter: FleetAdapter
    private val vehicleMarkers = mutableMapOf<String, Marker>()

    data class FleetVehicleStatus(
        val vehicle: Vehicle,
        var latitude: Double = 0.0,
        var longitude: Double = 0.0,
        var speed: Float = 0f,
        var isActive: Boolean = false,
        var hasAlert: Boolean = false,
        var alertMessage: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fleet_dashboard)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        userKey = prefs.getString("userKey", null) ?: run {
            finish()
            return
        }

        // Explicitly set database URL
        val databaseUrl = "https://vehicletrackingprototype-d8b88-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl).reference

        recyclerFleet = findViewById(R.id.recyclerFleet)
        txtTotalVehicles = findViewById(R.id.txtTotalVehicles)
        txtActiveVehicles = findViewById(R.id.txtActiveVehicles)
        txtAlerts = findViewById(R.id.txtAlerts)
        progressLoading = findViewById(R.id.progressLoading)

        adapter = FleetAdapter(fleetVehicles) { vehicleStatus ->
            focusOnVehicle(vehicleStatus)
        }
        recyclerFleet.layoutManager = LinearLayoutManager(this)
        recyclerFleet.adapter = adapter

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFleet) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val defaultLocation = LatLng(24.8607, 67.0011)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        loadFleetData()
    }

    private fun loadFleetData() {
        progressLoading.visibility = View.VISIBLE

        // Load user's vehicles
        database.child("users").child(userKey).child("vehicles")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    fleetVehicles.clear()

                    for (child in snapshot.children) {
                        val vehicle = child.getValue(Vehicle::class.java)
                        vehicle?.let {
                            fleetVehicles.add(FleetVehicleStatus(it))
                            loadVehicleStatus(it)
                        }
                    }

                    updateDashboardStats()
                    progressLoading.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    progressLoading.visibility = View.GONE
                    Toast.makeText(this@FleetDashboardActivity,
                        "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadVehicleStatus(vehicle: Vehicle) {
        // Listen for real-time location updates
        database.child("vehicles").child(vehicle.id).child("currentLocation")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val speed = snapshot.child("speed").getValue(Float::class.java) ?: 0f
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    // Check if vehicle is active (updated in last 5 minutes)
                    val isActive = System.currentTimeMillis() - timestamp < 5 * 60 * 1000

                    // Update fleet status
                    val index = fleetVehicles.indexOfFirst { it.vehicle.id == vehicle.id }
                    if (index >= 0) {
                        fleetVehicles[index].apply {
                            this.latitude = lat
                            this.longitude = lng
                            this.speed = speed
                            this.isActive = isActive
                        }

                        // Check for speed alert
                        if (speed * 3.6f > 60) {
                            fleetVehicles[index].hasAlert = true
                            fleetVehicles[index].alertMessage = "Speeding!"
                        }

                        adapter.notifyItemChanged(index)
                        updateVehicleMarker(fleetVehicles[index])
                        updateDashboardStats()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        // Listen for unauthorized movement alerts
        database.child("vehicles").child(vehicle.id).child("unauthorizedMovement")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val hasUnauthorized = snapshot.getValue(Boolean::class.java) ?: false

                    val index = fleetVehicles.indexOfFirst { it.vehicle.id == vehicle.id }
                    if (index >= 0 && hasUnauthorized) {
                        fleetVehicles[index].hasAlert = true
                        fleetVehicles[index].alertMessage = "Unauthorized movement!"
                        adapter.notifyItemChanged(index)
                        updateDashboardStats()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateVehicleMarker(status: FleetVehicleStatus) {
        if (status.latitude == 0.0 && status.longitude == 0.0) return

        val position = LatLng(status.latitude, status.longitude)

        // Remove existing marker
        vehicleMarkers[status.vehicle.id]?.remove()

        // Add new marker with color based on status
        val markerColor = when {
            status.hasAlert -> BitmapDescriptorFactory.HUE_RED
            status.isActive -> BitmapDescriptorFactory.HUE_GREEN
            else -> BitmapDescriptorFactory.HUE_YELLOW
        }

        val marker = mMap.addMarker(
            MarkerOptions()
                .position(position)
                .title(status.vehicle.name)
                .snippet("Speed: %.1f km/h".format(status.speed * 3.6f))
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
        )

        marker?.let { vehicleMarkers[status.vehicle.id] = it }
    }

    private fun updateDashboardStats() {
        val total = fleetVehicles.size
        val active = fleetVehicles.count { it.isActive }
        val alerts = fleetVehicles.count { it.hasAlert }

        txtTotalVehicles.text = "Total: $total"
        txtActiveVehicles.text = "Active: $active"
        txtAlerts.text = "Alerts: $alerts"
    }

    private fun focusOnVehicle(status: FleetVehicleStatus) {
        if (status.latitude != 0.0 && status.longitude != 0.0) {
            val position = LatLng(status.latitude, status.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
            vehicleMarkers[status.vehicle.id]?.showInfoWindow()
        } else {
            Toast.makeText(this, "No location data for this vehicle", Toast.LENGTH_SHORT).show()
        }
    }

    // Fleet Adapter
    inner class FleetAdapter(
        private val vehicles: List<FleetVehicleStatus>,
        private val onItemClick: (FleetVehicleStatus) -> Unit
    ) : RecyclerView.Adapter<FleetAdapter.FleetViewHolder>() {

        inner class FleetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtFleetVehicleName)
            val txtStatus: TextView = view.findViewById(R.id.txtFleetStatus)
            val txtSpeed: TextView = view.findViewById(R.id.txtFleetSpeed)
            val viewIndicator: View = view.findViewById(R.id.viewStatusIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FleetViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_fleet_vehicle, parent, false)
            return FleetViewHolder(view)
        }

        override fun onBindViewHolder(holder: FleetViewHolder, position: Int) {
            val status = vehicles[position]
            holder.txtName.text = status.vehicle.name
            holder.txtSpeed.text = "${String.format("%.0f", status.speed * 3.6f)}"

            when {
                status.hasAlert -> {
                    holder.txtStatus.text = "● ${status.alertMessage}"
                    holder.txtStatus.setTextColor(Color.RED)
                    holder.viewIndicator.setBackgroundResource(R.drawable.circle_red)
                }
                status.isActive -> {
                    holder.txtStatus.text = "● Active"
                    holder.txtStatus.setTextColor(Color.parseColor("#4CAF50"))
                    holder.viewIndicator.setBackgroundResource(R.drawable.circle_green)
                }
                else -> {
                    holder.txtStatus.text = "● Inactive"
                    holder.txtStatus.setTextColor(Color.GRAY)
                    holder.viewIndicator.setBackgroundResource(R.drawable.circle_gray)
                }
            }

            holder.itemView.setOnClickListener { onItemClick(status) }
        }

        override fun getItemCount() = vehicles.size
    }
}
