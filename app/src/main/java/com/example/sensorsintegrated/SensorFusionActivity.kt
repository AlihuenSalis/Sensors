package com.example.sensorsintegrated

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.sensorsintegrated.databinding.ActivitySensorFusionBinding
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SensorFusionActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySensorFusionBinding

    private var mSensorManager: SensorManager? = null

    val EPSILON = 0.000000001f
    private val NS2S: Float = 1.0f / 1000000000.0f
    private var timestamp: Float = 0f
    private var initState: Boolean = true

    private val fuseTimer = Timer()
    private val TIME_CONSTANT: Long = 10

    // angular speeds from gyro
    private var gyro: FloatArray = FloatArray(3)

    // rotation matrix from gyro data
    private var gyroMatrix: FloatArray = FloatArray(9)

    // orientation angles from gyro matrix
    private var gyroOrientation: FloatArray = FloatArray(3)

    // magnetic field vector
    private var magnet: FloatArray = FloatArray(3)

    // accelerometer vector
    private var accel: FloatArray = FloatArray(3)

    // orientation angles from accel and magnet
    private var accMagOrientation: FloatArray = FloatArray(3)

    // final orientation angles from sensor fusion
    private var fusedOrientation: FloatArray = FloatArray(3)

    // accelerometer and magnetometer based rotation matrix
    private var rotationMatrix: FloatArray = FloatArray(9)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorFusionBinding.inflate(layoutInflater)
        setContentView(binding.root)


        gyroOrientation[0] = 0.0f
        gyroOrientation[1] = 0.0f
        gyroOrientation[2] = 0.0f

        // initialise gyroMatrix with identity matrix
        gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f
        gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f
        gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f

        // get sensorManager and initialise sensor listeners
        mSensorManager = this.getSystemService(SENSOR_SERVICE) as SensorManager
        initListeners()

        // wait for one second until gyroscope and magnetometer/accelerometer
        // data is initialised then scedule the complementary filter task
        fuseTimer.scheduleAtFixedRate(
            calculateFusedOrientationTask(),
            3000, TIME_CONSTANT
        )
    }

    // initialisation of the sensor listeners
    fun initListeners() {
        mSensorManager!!.registerListener(
            this,
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        mSensorManager!!.registerListener(
            this,
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        mSensorManager!!.registerListener(
            this,
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // copy new accelerometer data into accel array
                    // then calculate new orientation
                    System.arraycopy(event.values, 0, accel, 0, 3)
                    calculateAccMagOrientation()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    // process gyro data
                    gyroFunction(event)
                }
                Sensor.TYPE_MAGNETIC_FIELD ->{
                    // copy new magnetometer data into magnet array
                    System.arraycopy(event.values, 0, magnet, 0, 3)
                }
            }
        }
    }

    // get the absolute orientation from the accelerometer and magnetometer.
    // get the accelerometer/magnetometer based orientation
    private fun calculateAccMagOrientation() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // nothing to do
    }

    private fun getRotationVectorFromGyro(
        gyroValues: FloatArray,
        deltaRotationVector: FloatArray,
        timeFactor: Float
    ) {
        val normValues = FloatArray(3)

        // Calculate the angular speed of the sample
        val omegaMagnitude =
            sqrt((gyroValues[0] * gyroValues[0] + gyroValues[1] * gyroValues[1] + gyroValues[2] * gyroValues[2]).toDouble())
                .toFloat()

        // Normalize the rotation vector if it's big enough to get the axis
        if (omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude
            normValues[1] = gyroValues[1] / omegaMagnitude
            normValues[2] = gyroValues[2] / omegaMagnitude
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        val thetaOverTwo = omegaMagnitude * timeFactor
        val sinThetaOverTwo = sin(thetaOverTwo.toDouble()).toFloat()
        val cosThetaOverTwo = cos(thetaOverTwo.toDouble()).toFloat()
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0]
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1]
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2]
        deltaRotationVector[3] = cosThetaOverTwo
    }

    fun gyroFunction(event: SensorEvent) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
//        if (accMagOrientation == null) return

        // initialisation of the gyroscope based rotation matrix
        if (initState) {
            var initMatrix: FloatArray? = FloatArray(9)
            initMatrix = getRotationMatrixFromOrientation(accMagOrientation)
            val test = FloatArray(3)
            SensorManager.getOrientation(initMatrix, test)
            gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix) // gyroMatrix is the total orientation calculated from all hitherto processed gyroscope measurements
            initState = false
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        val deltaVector = FloatArray(4)
        if (timestamp != 0f) {
            val dT = (event.timestamp - timestamp) * NS2S
            System.arraycopy(event.values, 0, gyro, 0, 3)
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f)
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp.toFloat()

        // convert rotation vector into rotation matrix
        val deltaMatrix = FloatArray(9)  //deltaMatrix holds the last rotation interval which needs to be applied to the gyroMatrix in the next step
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector)

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix)

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation(gyroMatrix, gyroOrientation)
    }

    private fun getRotationMatrixFromOrientation(o: FloatArray): FloatArray {
        val xM = FloatArray(9)
        val yM = FloatArray(9)
        val zM = FloatArray(9)
        val sinX = sin(o[1].toDouble()).toFloat()
        val cosX = cos(o[1].toDouble()).toFloat()
        val sinY = sin(o[2].toDouble()).toFloat()
        val cosY = cos(o[2].toDouble()).toFloat()
        val sinZ = sin(o[0].toDouble()).toFloat()
        val cosZ = cos(o[0].toDouble()).toFloat()

        // rotation about x-axis (pitch)
        xM[0] = 1.0f
        xM[1] = 0.0f
        xM[2] = 0.0f
        xM[3] = 0.0f
        xM[4] = cosX
        xM[5] = sinX
        xM[6] = 0.0f
        xM[7] = -sinX
        xM[8] = cosX

        // rotation about y-axis (roll)
        yM[0] = cosY
        yM[1] = 0.0f
        yM[2] = sinY
        yM[3] = 0.0f
        yM[4] = 1.0f
        yM[5] = 0.0f
        yM[6] = -sinY
        yM[7] = 0.0f
        yM[8] = cosY

        // rotation about z-axis (azimuth)
        zM[0] = cosZ
        zM[1] = sinZ
        zM[2] = 0.0f
        zM[3] = -sinZ
        zM[4] = cosZ
        zM[5] = 0.0f
        zM[6] = 0.0f
        zM[7] = 0.0f
        zM[8] = 1.0f

        // rotation order is y, x, z (roll, pitch, azimuth)
        var resultMatrix: FloatArray = matrixMultiplication(xM, yM)
        resultMatrix = matrixMultiplication(zM, resultMatrix)
        return resultMatrix
    }

    // This is the function for the matrix multiplication:
    private fun matrixMultiplication(A: FloatArray, B: FloatArray): FloatArray {
        val result = FloatArray(9)
        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6]
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7]
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8]
        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6]
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7]
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8]
        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6]
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7]
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8]
        return result
    }


    inner class calculateFusedOrientationTask : TimerTask() {
        private var filterCoefficient = 0.85f

        override fun run() {
            val oneMinusCoeff: Float = 1.0f - filterCoefficient
            fusedOrientation[0] = filterCoefficient * gyroOrientation[0]
            + oneMinusCoeff * accMagOrientation[0]
            fusedOrientation[1] = filterCoefficient * gyroOrientation[1]
            + oneMinusCoeff * accMagOrientation[1]
            fusedOrientation[2] = filterCoefficient * gyroOrientation[2]
            + oneMinusCoeff * accMagOrientation[2]

            // overwrite gyro matrix and orientation with fused orientation
            // to comensate gyro drift
            gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation)
            System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3)
        }
    }
}