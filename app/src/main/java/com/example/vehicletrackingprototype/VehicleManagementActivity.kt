package com.example.vehicletrackingprototype

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class VehicleManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddVehicle: Button
    private lateinit var txtNoVehicles: LinearLayout

    private val vehicleList = mutableListOf<Vehicle>()
    private lateinit var adapter: VehicleAdapter
    private lateinit var userKey: String
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_management)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        userKey = prefs.getString("userKey", null) ?: run {
            Toast.makeText(this, "Error: User not logged in. Please login again.", Toast.LENGTH_LONG).show()
            android.util.Log.e("VehicleManagement", "UserKey is null! User not logged in.")
            finish()
            return
        }

        android.util.Log.d("VehicleManagement", "onCreate: userKey = $userKey")
        
        // Explicitly set database URL to ensure correct connection
        val databaseUrl = "https://vehicletrackingprototype-d8b88-default-rtdb.asia-southeast1.firebasedatabase.app/"
        database = FirebaseDatabase.getInstance(databaseUrl).reference
        android.util.Log.d("VehicleManagement", "Using database URL: $databaseUrl")

        recyclerView = findViewById(R.id.recyclerVehicles)
        btnAddVehicle = findViewById(R.id.btnAddVehicle)
        txtNoVehicles = findViewById(R.id.txtNoVehicles)

        adapter = VehicleAdapter(vehicleList) { vehicle ->
            showDeleteDialog(vehicle)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnAddVehicle.setOnClickListener {
            showAddVehicleDialog()
        }

        loadVehicles()
    }

    private fun loadVehicles() {
        val vehiclesPath = "users/$userKey/vehicles"
        android.util.Log.d("VehicleManagement", "loadVehicles: Listening to path: $vehiclesPath")
        
        database.child("users").child(userKey).child("vehicles")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    android.util.Log.d("VehicleManagement", "onDataChange: snapshot exists=${snapshot.exists()}, children count=${snapshot.childrenCount}")
                    
                    vehicleList.clear()
                    var count = 0
                    for (child in snapshot.children) {
                        android.util.Log.d("VehicleManagement", "Processing child: ${child.key}")
                        val vehicle = child.getValue(Vehicle::class.java)
                        if (vehicle != null) {
                            android.util.Log.d("VehicleManagement", "✅ Loaded vehicle: ${vehicle.name} (${vehicle.licensePlate})")
                            vehicleList.add(vehicle)
                            count++
                        } else {
                            android.util.Log.w("VehicleManagement", "⚠️ Failed to parse vehicle from child: ${child.key}")
                        }
                    }
                    
                    android.util.Log.d("VehicleManagement", "Total vehicles loaded: $count")
                    adapter.notifyDataSetChanged()
                    txtNoVehicles.visibility = if (vehicleList.isEmpty()) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("VehicleManagement", "❌ Error loading vehicles", error.toException())
                    Toast.makeText(this@VehicleManagementActivity,
                        "❌ Error loading vehicles: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun showAddVehicleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_vehicle, null)
        val edtVehicleName = dialogView.findViewById<EditText>(R.id.edtVehicleName)
        val edtLicensePlate = dialogView.findViewById<EditText>(R.id.edtLicensePlate)

        AlertDialog.Builder(this)
            .setTitle("Add New Vehicle")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = edtVehicleName.text.toString().trim()
                val plate = edtLicensePlate.text.toString().trim()

                if (name.isEmpty() || plate.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                addVehicle(name, plate)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addVehicle(name: String, licensePlate: String) {
        android.util.Log.d("VehicleManagement", "addVehicle called: name=$name, plate=$licensePlate, userKey=$userKey")
        
        val vehicleRef = database.child("users").child(userKey).child("vehicles").push()
        val vehicleId = vehicleRef.key
        
        if (vehicleId == null) {
            android.util.Log.e("VehicleManagement", "Failed to generate vehicle ID")
            Toast.makeText(this, "Error: Could not generate vehicle ID", Toast.LENGTH_LONG).show()
            return
        }
        
        android.util.Log.d("VehicleManagement", "Generated vehicleId: $vehicleId")
        android.util.Log.d("VehicleManagement", "Firebase path: users/$userKey/vehicles/$vehicleId")

        val vehicle = Vehicle(
            id = vehicleId,
            name = name,
            licensePlate = licensePlate,
            ownerId = userKey,
            createdAt = System.currentTimeMillis()
        )
        
        android.util.Log.d("VehicleManagement", "Vehicle object created: $vehicle")

        vehicleRef.setValue(vehicle)
            .addOnSuccessListener {
                android.util.Log.d("VehicleManagement", "✅ Vehicle saved to Firebase successfully!")
                Toast.makeText(this, "✅ Vehicle added: $name", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("VehicleManagement", "❌ Failed to save vehicle", exception)
                Toast.makeText(this, "❌ Failed to add vehicle: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showDeleteDialog(vehicle: Vehicle) {
        AlertDialog.Builder(this)
            .setTitle("Delete Vehicle")
            .setMessage("Are you sure you want to delete ${vehicle.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteVehicle(vehicle)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteVehicle(vehicle: Vehicle) {
        database.child("users").child(userKey).child("vehicles").child(vehicle.id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Vehicle deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // RecyclerView Adapter
    inner class VehicleAdapter(
        private val vehicles: List<Vehicle>,
        private val onDeleteClick: (Vehicle) -> Unit
    ) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

        inner class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtVehicleName)
            val txtPlate: TextView = view.findViewById(R.id.txtLicensePlate)
            val btnDelete: View = view.findViewById(R.id.btnDeleteVehicle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_vehicle, parent, false)
            return VehicleViewHolder(view)
        }

        override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
            val vehicle = vehicles[position]
            holder.txtName.text = vehicle.name
            holder.txtPlate.text = vehicle.licensePlate
            holder.btnDelete.setOnClickListener { onDeleteClick(vehicle) }
        }

        override fun getItemCount() = vehicles.size
    }
}
