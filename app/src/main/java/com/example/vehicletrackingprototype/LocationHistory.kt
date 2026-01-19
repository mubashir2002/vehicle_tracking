package com.example.vehicletrackingprototype

data class LocationHistory(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var speed: Float = 0f,
    var timestamp: Long = 0
)
