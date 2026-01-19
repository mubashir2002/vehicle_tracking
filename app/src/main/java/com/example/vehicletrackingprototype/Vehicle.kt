package com.example.vehicletrackingprototype

data class Vehicle(
    var id: String = "",
    var name: String = "",
    var licensePlate: String = "",
    var ownerId: String = "",
    var createdAt: Long = 0
)
