package com.example.publisherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MainActivity : AppCompatActivity() {

    private lateinit var mqttClient: MqttClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var isPublishing = false
    private val mqttBrokerUri = "tcp://broker.sundaebytestt.com:1883" // MQTT connection to the broker
    private val topic = "assignment/location"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val studentIdInput = findViewById<EditText>(R.id.studentIdInput)
        val startButton = findViewById<Button>(R.id.startPublishing)
        val stopButton = findViewById<Button>(R.id.stopPublishing)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        connectToMqttBroker()

        startButton.setOnClickListener {
            val studentId = studentIdInput.text.toString()
            if (studentId.isNotEmpty()) {
                startPublishing(studentId)
            } else {
                studentIdInput.error = "Please enter your Student ID"
            }
        }

        stopButton.setOnClickListener {
            stopPublishing()
        }
    }

    private fun connectToMqttBroker() {
        try {
            mqttClient = MqttClient(mqttBrokerUri, MqttClient.generateClientId(), MemoryPersistence())
            val options = MqttConnectOptions()
            options.isCleanSession = true
            mqttClient.connect(options)
            Log.d("MQTT", "Connected to MQTT broker")
        } catch (e: MqttException) {
            Log.e("MQTT", "Error connecting to MQTT broker: ${e.message}")
        }
    }

    private fun startPublishing(studentId: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        isPublishing = true

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isPublishing) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val message = """
                            {
                                "student_id": "$studentId",
                                "latitude": ${location.latitude},
                                "longitude": ${location.longitude},
                                "speed": ${location.speed}
                            }
                        """.trimIndent()
                        publishMessage(message)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun stopPublishing() {
        isPublishing = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun publishMessage(payload: String) {
        try {
            val message = MqttMessage(payload.toByteArray())
            mqttClient.publish(topic, message)
            Log.d("MQTT", "Message published: $payload")
        } catch (e: MqttException) {
            Log.e("MQTT", "Error publishing message: ${e.message}")
        }
    }
}