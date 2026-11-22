package app.pokeplan.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.pokeplan.ui.gear.SensorFeed

class AndroidSensorFeed(private val activity: ComponentActivity) : SensorFeed {
    private val manager = activity.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var waiting: ((Boolean) -> Unit)? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> waiting?.invoke(granted); waiting = null }

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

    override fun rotation(onReading: (FloatArray) -> Unit): (() -> Unit)? {
        val gyro = sensor(Sensor.TYPE_GYROSCOPE) ?: return null
        return listen(listOf(gyro)) { e -> onReading(e.values.copyOf(3)) }
    }

    override fun steps(onReading: (Long) -> Unit, onUnavailable: (String) -> Unit): (() -> Unit)? {
        val counter = sensor(Sensor.TYPE_STEP_COUNTER)
        if (counter == null) { onUnavailable("This phone has no step counter."); return {} }
        var stop: (() -> Unit)? = null
        var cancelled = false
        fun start() { if (!cancelled) stop = listen(listOf(counter)) { e -> onReading(e.values[0].toLong()) } }
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (!needed || activity.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) start()
        else {
            waiting = { ok -> if (ok) start() else onUnavailable("Physical activity permission was not given. You can allow it in the phone's settings for PokéLife.") }
            permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return { cancelled = true; stop?.invoke() }
    }
}
