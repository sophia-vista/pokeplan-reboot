package app.pokeplan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.pokeplan.ui.gear.SensorFeed

/**
 * The motion and pressure sensors for the Pokégear's Compass, Level and Barometer, read through `SensorManager`. A listener is registered
 * only when a tool asks (at the normal UI rate) and the returned function unregisters it, so nothing listens while no tool is open.
 */
class AndroidSensorFeed(context: Context) : SensorFeed {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private fun sensor(type: Int): Sensor? = try { manager?.getDefaultSensor(type) } catch (e: Exception) { null }

    private fun listen(sensors: List<Sensor>, onEvent: (SensorEvent) -> Unit): () -> Unit {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onEvent(event)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensors.forEach { manager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        return { manager?.unregisterListener(listener) }
    }

    override fun orientation(onReading: (FloatArray, FloatArray) -> Unit): (() -> Unit)? {
        val accel = sensor(Sensor.TYPE_ACCELEROMETER) ?: return null
        val mag = sensor(Sensor.TYPE_MAGNETIC_FIELD) ?: return null
        var gravity: FloatArray? = null
        var field: FloatArray? = null
        return listen(listOf(accel, mag)) { e ->
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity = e.values.copyOf(3) else field = e.values.copyOf(3)
            val g = gravity; val m = field
            if (g != null && m != null) onReading(g, m)
        }
    }

    override fun gravity(onReading: (FloatArray) -> Unit): (() -> Unit)? {
        val accel = sensor(Sensor.TYPE_ACCELEROMETER) ?: return null
        return listen(listOf(accel)) { e -> onReading(e.values.copyOf(3)) }
    }

    override fun pressure(onReading: (Float) -> Unit): (() -> Unit)? {
        val baro = sensor(Sensor.TYPE_PRESSURE) ?: return null
        return listen(listOf(baro)) { e -> onReading(e.values[0]) }
    }
}
