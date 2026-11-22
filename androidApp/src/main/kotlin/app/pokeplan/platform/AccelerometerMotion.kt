package app.pokeplan.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.pokeplan.ui.MotionSource
import kotlin.math.abs
import kotlin.math.sqrt

class AccelerometerMotion(context: Context) : MotionSource {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var listener: SensorEventListener? = null

    override fun start(onMoved: () -> Unit) {
        stop()
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val armedAt = System.currentTimeMillis() + ARM_DELAY_MILLIS
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (System.currentTimeMillis() < armedAt) return
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                if (abs(g - SensorManager.GRAVITY_EARTH) > THRESHOLD) onMoved()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = l
        manager.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        listener?.let { manager.unregisterListener(it) }
        listener = null
    }

    private companion object {
        const val THRESHOLD = 2.5f
        const val ARM_DELAY_MILLIS = 1_000L
    }
}
