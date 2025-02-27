package com.xavi.ejaccelerometro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.xavi.ejaccelerometro.databinding.ActivityMainBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometre: Sensor
    private lateinit var binding: ActivityMainBinding
    private var contX = 0
    private var contY = 0
    private var contZ = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Utilitzem bindings per facilitar accés als elements gràfics
        // recorda activar-los a build.gradle.kts afegint:
        // viewBinding { enable = true }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // escoltar variacions dels sensors
        sensorManager = getSystemService(
            Context.SENSOR_SERVICE) as SensorManager
        accelerometre = sensorManager.getDefaultSensor(
            Sensor.TYPE_LINEAR_ACCELERATION) as Sensor
        sensorManager.registerListener(this, accelerometre,
            SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // emprem les dades del sensor
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // 1g = 9,8 m/s² , què és un valor força alt.
        // Al fer *10 ens acostem als 100, que és el valor màxim per defecte de la ProgressBar
        binding.progressBar3.progress = abs(x*10.0).toInt()
        binding.progressBar2.progress = abs(y*10.0).toInt()
        binding.progressBar.progress = abs(z*10.0).toInt()

        if (abs(z*10.0).toInt() <= 90 && abs(z*10.0).toInt() >= 80){
            contZ += 1
            binding.contTerc.text = ""+contZ
            Log.i("prob",""+abs(z*10.0).toInt())
        }
        if (abs(x*10.0).toInt() >= 100){
            contX += 1
            binding.contPrim.text = ""+contX
        }
        if (abs(y*10.0).toInt() >= 100){
            contY += 1
            binding.contSeg.text = ""+contY
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        Log.i("prob",""+accuracy)

    }
}