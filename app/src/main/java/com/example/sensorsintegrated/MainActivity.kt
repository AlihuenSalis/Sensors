package com.example.sensorsintegrated

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sensorsintegrated.databinding.ActivityMainBinding
import java.text.DecimalFormat
import java.util.*

class MainActivity : Activity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding

    private val MY_PERMISSION_ACCESS_FINE_LOCATION = 1
    var isMph: Boolean = true
    private var speedlimit: String = "10"
    private var limitExceedCount: Int = 0
    private var slimitExceedCount: String? = null
    private var limitExceedTime: String? = null
    private var maxSpeed: Int = 0
    private var sMaxSpeed: String? = null
    private var tEnd: Long = 0
    private var tStart:Long = 0
    private var timeString: String? = null
    private var Name: String? = null
    private var i: Int = 0
    private var flag: Int = 0
    private var accSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private var SM: SensorManager? = null
    private var locationManager: LocationManager? = null
    private var currentSpeed: Float = 0.0f
    private var running: Boolean = false
    private var paused: Boolean = false
    private var start: Long = 0
    private var pausedStart: Long = 0
    private var end: Long = 0

    private var details: MutableList<String> = ArrayList()

    private val SHARED_PREF_NAME = "sensors"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.buttonSensor.setOnClickListener {
            // fetching value from sharedpreference
            val sharedPreferences: SharedPreferences = getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.clear().commit()

            //  Fetching the boolean value form sharedpreferences
            sharedPreferences.getInt("overPitch", 0)
            sharedPreferences.getInt("overYaw", 0)
            sharedPreferences.getInt("overX", 0)
            sharedPreferences.getInt("overY", 0)

            val intent = Intent(this@MainActivity, SensorFusionActivity::class.java)
            startActivity(intent)

        }
    }

//    fun display() {
//        val intent = Intent(this@MainActivity, ViewDatabase::class.java)
//        intent.putExtra("userid", Name)
//        startActivity(intent)
//    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun started(view: View) {

        if (i == 0) {
            binding.button.text = "END"
            binding.button.background = ContextCompat.getDrawable(applicationContext ,R.drawable.back4)
            tStart = System.currentTimeMillis()
            i = 1
        } else {
            binding.button.text = "START"
            binding.button.background = ContextCompat.getDrawable(applicationContext, R.drawable.back3)
            tEnd = System.currentTimeMillis()
            val tDelta = tEnd - tStart
            val elapsedSeconds = tDelta / 1000.0
            val hours = (elapsedSeconds / 3600).toInt()
            val minutes = (elapsedSeconds % 3600 / 60).toInt()
            val seconds = (elapsedSeconds % 60).toInt()
            timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            val elapsed: Long = stop()
            val tseconds = elapsed.toDouble() / 1000000000.0
            val shours = (tseconds / 3600).toInt()
            val sminutes = (tseconds % 3600 / 60).toInt()
            val sseconds = (tseconds % 60).toInt()
            limitExceedTime = String.format("%02d:%02d:%02d", shours, sminutes, sseconds)
            slimitExceedCount = Integer.toString(limitExceedCount)
            sMaxSpeed = Integer.toString(maxSpeed)
            i = 0
            onadd()
            binding.time.text = "Total time: $timeString"
            binding.maxSpeed.text = "Max Speed: $sMaxSpeed"
            binding.limitExceeded.text = "LimitExceededTime: $limitExceedTime"
            binding.limitExceededCount.text = "LimitExceededCount: $slimitExceedCount"
            locationManager!!.removeUpdates(this)
        }
        // Create our Sensor Manager
        SM = getSystemService(SENSOR_SERVICE) as SensorManager


        // Accelerometer Sensor
        accSensor = SM!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = SM!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometerSensor = SM!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Register sensor Listener
        SM!!.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL)
        SM!!.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        SM!!.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL)

//        speedView = findViewById<TextView>(R.id.speedView)
//        speedLimit = findViewById<TextView>(R.id.speedLimit)

        if (!checkPermission()) {
            requestPermission()
        }

        //turn on speedometer using GPS
        turnOnGps()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val result1 = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        val result2 = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.INTERNET)
        return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED &&
                result2 == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE), 1)
    }

    private fun turnOnGps() {
//        if (ContextCompat.checkSelfPermission(
//                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_COARSE_LOCATION,
//                    Manifest.permission.INTERNET),
//                MY_PERMISSION_ACCESS_FINE_LOCATION
//            )
//        }

        if (locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5f, this)
        }

        if (locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager!!.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000,
                5f,
                this
            )
        }
    }

    private fun turnOffGps() { locationManager!!.removeUpdates(this) }

    override fun onLocationChanged(location: Location) {

        val mph: Int = speedlimit.toInt()
        binding.speedLimit.text = "Limit: $mph"
        pause()
        currentSpeed = location.speed * 2.23f

        if (currentSpeed > mph) {
            if (!isRunning()) {
                start()
            } else {
                resume()
            }
            if (flag == 0) {
                limitExceedCount++
                flag = 1
            }
        }
        if (currentSpeed < mph) {
            flag = 0
        }
        if (maxSpeed < currentSpeed) {
            maxSpeed = currentSpeed.toInt()
        }

        binding.speedView.text = "Speed ${DecimalFormat("##").format(currentSpeed)}"
    }

    private fun onadd() {
        details.add("Total Time: $timeString")
        details.add("Max Speed: $sMaxSpeed")
        details.add("LimitExceedTime: $limitExceedTime")
        details.add("LimitExceedCount: $slimitExceedCount")
//        val id: String = mRootReference.push().getKey()
//        mRootReference.child(id).setValue(details)
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        // not in use
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // not in use
    }

    override fun onProviderDisabled(provider: String) {
        super.onProviderDisabled(provider)
        turnOffGps()
    }

    fun start() {
        start = System.nanoTime()
        running = true
        paused = false
        pausedStart = -1
    }

    private fun isRunning(): Boolean { return running }

    private fun isPaused(): Boolean { return paused }

    fun pause(): Long {
        return if (!isRunning()) {
            -1
        } else if (isPaused()) {
            pausedStart - start
        } else {
            pausedStart = System.nanoTime()
            paused = true
            pausedStart - start
        }
    }


    private fun resume() {
        if (isPaused() && isRunning()) {
            start = System.nanoTime() - (pausedStart - start)
            paused = false
        }
    }

    private fun stop(): Long {

        return if (!isRunning()) {
            -1
        } else if (isPaused()) {
            running = false
            paused = false
            pausedStart - start
        } else {
            end = System.nanoTime()
            running = false
            end - start
        }

    }

}