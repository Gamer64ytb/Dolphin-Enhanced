package org.dolphinemu.dolphinemu.fragments

import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.overlay.InputOverlay
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization.DirectoryInitializationState
import org.dolphinemu.dolphinemu.utils.DirectoryStateReceiver
import org.dolphinemu.dolphinemu.utils.Log

class EmulationFragment : Fragment(), SurfaceHolder.Callback,
    SensorEventListener {
    private var inputOverlay: InputOverlay? = null
    private var emulationState: EmulationState? = null
    private var directoryStateReceiver: DirectoryStateReceiver? = null
    private var emulationActivity: EmulationActivity? = null

    /**
     * Initialize anything that doesn't depend on the layout / views in here.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        emulationActivity = activity as EmulationActivity?

        // So this fragment doesn't restart on configuration changes; i.e. rotation.
        retainInstance = true

        val gamePaths = requireArguments().getStringArray(KEY_GAME_PATHS)
        emulationState = EmulationState(gamePaths)
    }

    /**
     * Initialize the UI and start emulation in here.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val contents = inflater.inflate(R.layout.fragment_emulation, container, false)

        val surfaceView = contents.findViewById<SurfaceView>(R.id.surface_emulation)
        surfaceView.holder.addCallback(this)

        inputOverlay = contents.findViewById(R.id.surface_input_overlay)

        val doneButton = contents.findViewById<Button>(R.id.done_control_config)
        doneButton?.setOnClickListener { v: View? -> stopConfiguringControls() }

        // The new Surface created here will get passed to the native code via onSurfaceChanged.
        return contents
    }

    override fun onResume() {
        super.onResume()
        if (DirectoryInitialization.isReady()) {
            emulationState!!.run(emulationActivity!!.savedState)
        } else {
            setupDolphinDirectoriesThenStartEmulation()
        }
    }

    override fun onPause() {
        if (directoryStateReceiver != null) {
            LocalBroadcastManager.getInstance(emulationActivity!!)
                .unregisterReceiver(directoryStateReceiver!!)
            directoryStateReceiver = null
        }

        emulationState!!.pause()
        super.onPause()
    }

    private fun setupDolphinDirectoriesThenStartEmulation() {
        val statusIntentFilter = IntentFilter(
            DirectoryInitialization.BROADCAST_ACTION
        )

        directoryStateReceiver =
            DirectoryStateReceiver { directoryInitializationState: DirectoryInitializationState ->
              when (directoryInitializationState) {
                  DirectoryInitializationState.DIRECTORIES_INITIALIZED -> {
                    emulationState!!.run(emulationActivity!!.savedState)
                  }
                  DirectoryInitializationState.EXTERNAL_STORAGE_PERMISSION_NEEDED -> {
                    Toast.makeText(context, R.string.write_permission_needed, Toast.LENGTH_SHORT)
                      .show()
                  }
                  DirectoryInitializationState.CANT_FIND_EXTERNAL_STORAGE -> {
                    Toast.makeText(
                      context, R.string.external_storage_not_mounted,
                      Toast.LENGTH_SHORT
                    )
                      .show()
                  }
              }
            }

        // Registers the DirectoryStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(emulationActivity!!).registerReceiver(
            directoryStateReceiver!!,
            statusIntentFilter
        )
        DirectoryInitialization.start(emulationActivity)
    }

    fun refreshInputOverlay() {
        inputOverlay!!.refreshControls()
    }

    fun resetCurrentLayout() {
        inputOverlay!!.resetCurrentLayout()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // We purposely don't do anything here.
        // All work is done in surfaceChanged, which we are guaranteed to get even for surface creation.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        emulationState!!.newSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        emulationState!!.clearSurface()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensorType = event.sensor.type
        if (sensorType == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            val rotationMtx = FloatArray(9)
            val rotationVal = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotationMtx, event.values)
            SensorManager.remapCoordinateSystem(
                rotationMtx,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Y,
                rotationMtx
            )
            SensorManager.getOrientation(rotationMtx, rotationVal)
            inputOverlay!!.onSensorChanged(rotationVal)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        inputOverlay!!.onAccuracyChanged(accuracy)
    }

    fun setTouchPointer(type: Int) {
        inputOverlay!!.setTouchPointer(type)
    }

    fun updateTouchPointer() {
        inputOverlay!!.updateTouchPointer()
    }

    fun stopEmulation() {
        emulationState!!.stop()
    }

    fun startConfiguringControls() {
        requireView().findViewById<View>(R.id.done_control_config).visibility =
            View.VISIBLE
        inputOverlay!!.setIsInEditMode(true)
    }

    fun stopConfiguringControls() {
        requireView().findViewById<View>(R.id.done_control_config).visibility =
            View.GONE
        inputOverlay!!.setIsInEditMode(false)
    }

    val isConfiguringControls: Boolean
        get() = inputOverlay!!.isInEditMode

    private class EmulationState
        (private val mGamePaths: Array<String?>?) {
        private enum class State {
            STOPPED, RUNNING, PAUSED
        }

        private var state: State
        private var surface: Surface? = null
        private var runWhenSurfaceIsValid = false

        private var statePath: String? = null

        init {
            // Starting state is stopped.
            state = State.STOPPED
        }

        @Synchronized
        fun stop() {
            if (state != State.STOPPED) {
                state = State.STOPPED
                NativeLibrary.StopEmulation()
            } else {
                Log.warning("[EmulationFragment] Stop called while already stopped.")
            }
        }

        @Synchronized
        fun pause() {
            if (state == State.RUNNING) {
                state = State.PAUSED
                // Release the surface before pausing, since emulation has to be running for that.
                NativeLibrary.SurfaceDestroyed()
                NativeLibrary.PauseEmulation()
            }
        }

        @Synchronized
        fun run(statePath: String?) {
            this.statePath = statePath

            if (NativeLibrary.IsRunning()) {
                state = State.PAUSED
            }

            // If the surface is set, run now. Otherwise, wait for it to get set.
            if (surface != null) {
                runWithValidSurface()
            } else {
                runWhenSurfaceIsValid = true
            }
        }

        // Surface callbacks
        @Synchronized
        fun newSurface(surface: Surface?) {
            this.surface = surface
            if (runWhenSurfaceIsValid) {
                runWithValidSurface()
            }
        }

        @Synchronized
        fun clearSurface() {
            if (surface == null) {
                Log.warning("[EmulationFragment] clearSurface called, but surface already null.")
            } else {
                surface = null
              when (state) {
                  State.RUNNING -> {
                    NativeLibrary.SurfaceDestroyed()
                    state = State.PAUSED
                  }
                  State.PAUSED -> {
                    Log.warning("[EmulationFragment] Surface cleared while emulation paused.")
                  }
                  else -> {
                    Log.warning("[EmulationFragment] Surface cleared while emulation stopped.")
                  }
              }
            }
        }

        fun runWithValidSurface() {
            runWhenSurfaceIsValid = false
          when (state) {
              State.STOPPED -> {
                Thread({
                  NativeLibrary.SurfaceChanged(surface)
                  NativeLibrary.Run(mGamePaths, statePath)
                }, "NativeEmulation").start()
              }
              State.PAUSED -> {
                NativeLibrary.SurfaceChanged(surface)
                NativeLibrary.UnPauseEmulation()
              }
              else -> {
                Log.warning("[EmulationFragment] Bug, run called while already running.")
              }
          }
            state = State.RUNNING
        }
    }

    companion object {
        private const val KEY_GAME_PATHS = "game_paths"

        fun newInstance(gamePaths: Array<String>?): EmulationFragment {
            val args = Bundle()
            args.putStringArray(KEY_GAME_PATHS, gamePaths)

            val fragment = EmulationFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
