package com.example.vehicletrackingprototype

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*

class TripHistoryActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var spinnerVehicle: Spinner
    private lateinit var btnPlayTrip: Button
    private lateinit var btnStopReplay: Button
    private lateinit var txtTripInfo: TextView
    private lateinit var progressReplay: ProgressBar

    private lateinit var userKey: String
    private lateinit var database: DatabaseReference

    private val vehicleList = mutableListOf<Vehicle>()
    private val vehicleIds = mutableListOf<String>()
    private val vehicleNames = mutableListOf<String>()

    private var historyPoints = mutableListOf<LocationHistory>()
    private var replayThread: Thread? = null
    private var isReplaying = false
    private var currentMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_history)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        userKey = prefs.getString("userKey", null) ?: run {
            finish()
            return
        }

        // Explicitly set database URL
        val databaseUrl = "https://vehicletrackingprototype-d8b88-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl).reference

        spinnerVehicle = findViewById(R.id.spinnerVehicleHistory)
        btnPlayTrip = findViewById(R.id.btnPlayTrip)
        btnStopReplay = findViewById(R.id.btnStopReplay)
        txtTripInfo = findViewById(R.id.txtTripInfo)
        progressReplay = findViewById(R.id.progressReplay)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapHistory) as SupportMapFragment
        mapFragment.getMapAsync(this)

        loadUserVehicles()

        spinnerVehicle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (vehicleIds.isNotEmpty()) {
                    loadTripHistory(vehicleIds[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnPlayTrip.setOnClickListener {
            if (historyPoints.isNotEmpty()) {
                startReplay()
            } else {
                Toast.makeText(this, "No trip data to replay", Toast.LENGTH_SHORT).show()
            }
        }

        btnStopReplay.setOnClickListener {
            stopReplay()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val defaultLocation = LatLng(24.8607, 67.0011)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
    }

    private fun loadUserVehicles() {
        database.child("users").child(userKey).child("vehicles")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    vehicleList.clear()
                    vehicleIds.clear()
                    vehicleNames.clear()

                    for (child in snapshot.children) {
                        val vehicle = child.getValue(Vehicle::class.java)
                        vehicle?.let {
                            vehicleList.add(it)
                            vehicleIds.add(it.id)
                            vehicleNames.add(it.name)
                        }
                    }

                    if (vehicleNames.isEmpty()) {
                        vehicleNames.add("No vehicles")
                        Toast.makeText(this@TripHistoryActivity,
                            "Please add vehicles first", Toast.LENGTH_SHORT).show()
                    }

                    val adapter = ArrayAdapter(this@TripHistoryActivity,
                        android.R.layout.simple_spinner_item, vehicleNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerVehicle.adapter = adapter
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@TripHistoryActivity,
                        "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadTripHistory(vehicleId: String) {
        progressReplay.visibility = View.VISIBLE

        database.child("vehicles").child(vehicleId).child("history")
            .orderByChild("timestamp")
            .limitToLast(100)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    historyPoints.clear()

                    for (child in snapshot.children) {
                        val lat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                        val lng = child.child("longitude").getValue(Double::class.java) ?: 0.0
                        val speed = child.child("speed").getValue(Float::class.java) ?: 0f
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L

                        historyPoints.add(LocationHistory(lat, lng, speed, timestamp))
                    }

                    progressReplay.visibility = View.GONE

                    if (historyPoints.isEmpty()) {
                        txtTripInfo.text = "No trip history available"
                        mMap.clear()
                    } else {
                        txtTripInfo.text = "Trip points: ${historyPoints.size}"
                        drawTripPath()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressReplay.visibility = View.GONE
                    Toast.makeText(this@TripHistoryActivity,
                        "Error loading history: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun drawTripPath() {
        mMap.clear()

        if (historyPoints.isEmpty()) return

        val polylineOptions = PolylineOptions()
            .color(Color.BLUE)
            .width(5f)

        for (point in historyPoints) {
            polylineOptions.add(LatLng(point.latitude, point.longitude))
        }

        mMap.addPolyline(polylineOptions)

        // Add start marker
        val startPoint = historyPoints.first()
        mMap.addMarker(
            MarkerOptions()
                .position(LatLng(startPoint.latitude, startPoint.longitude))
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        // Add end marker
        val endPoint = historyPoints.last()
        mMap.addMarker(
            MarkerOptions()
                .position(LatLng(endPoint.latitude, endPoint.longitude))
                .title("End")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        // Center camera on the path
        val bounds = LatLngBounds.Builder()
        historyPoints.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
    }

    private fun startReplay() {
        if (isReplaying) return
        isReplaying = true
        btnPlayTrip.isEnabled = false
        btnStopReplay.isEnabled = true

        replayThread = Thread {
            for (i in historyPoints.indices) {
                if (!isReplaying) break

                val point = historyPoints[i]
                val position = LatLng(point.latitude, point.longitude)
                val speedKmh = point.speed * 3.6f

                runOnUiThread {
                    currentMarker?.remove()
                    currentMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title("Vehicle")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(position))
                    txtTripInfo.text = "Point ${i + 1}/${historyPoints.size} | Speed: %.1f km/h".format(speedKmh)
                }

                Thread.sleep(500) // 0.5 second between points
            }

            runOnUiThread {
                isReplaying = false
                btnPlayTrip.isEnabled = true
                btnStopReplay.isEnabled = false
                txtTripInfo.text = "Replay complete"
            }
        }
        replayThread?.start()
    }

    private fun stopReplay() {
        isReplaying = false
        replayThread?.interrupt()
        btnPlayTrip.isEnabled = true
        btnStopReplay.isEnabled = false
        txtTripInfo.text = "Replay stopped"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReplay()
    }
}
