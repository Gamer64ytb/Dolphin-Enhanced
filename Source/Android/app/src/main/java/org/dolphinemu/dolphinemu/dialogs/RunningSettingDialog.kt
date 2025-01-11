package org.dolphinemu.dolphinemu.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.get
import org.dolphinemu.dolphinemu.overlay.InputOverlay
import org.dolphinemu.dolphinemu.utils.Rumble

class RunningSettingDialog : DialogFragment() {
    inner class SettingsItem {
        var setting: Int
            private set
        var name: String
            private set
        var type: Int
            private set
        var value: Int

        constructor(setting: Int, nameId: Int, type: Int, value: Int) {
            this.setting = setting
            name = getString(nameId)
            this.type = type
            this.value = value
        }

        constructor(setting: Int, name: String, type: Int, value: Int) {
            this.setting = setting
            this.name = name
            this.type = type
            this.value = value
        }
    }

    abstract inner class SettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
            findViews(itemView)
        }

        protected abstract fun findViews(root: View)

        abstract fun bind(item: SettingsItem)

        abstract override fun onClick(clicked: View)
    }

    inner class ButtonSettingViewHolder(itemView: View) : SettingViewHolder(itemView) {
        var item: SettingsItem? = null
        private var name: TextView? = null

        override fun findViews(root: View) {
            name = root.findViewById(R.id.text_setting_name)
        }

        override fun bind(item: SettingsItem) {
            this.item = item
            name!!.text = item.name
        }

        override fun onClick(clicked: View) {
            val activity = NativeLibrary.getEmulationContext() as EmulationActivity
            when (item!!.setting) {
                SETTING_LOAD_SUBMENU -> loadSubMenu(
                    item!!.value
                )

                SETTING_TAKE_SCREENSHOT -> {
                    NativeLibrary.SaveScreenShot()
                    dismiss()
                }

                SETTING_EDIT_BUTTONS -> {
                    activity.editControlsPlacement()
                    dismiss()
                }

                SETTING_TOGGLE_BUTTONS -> {
                    activity.toggleControls()
                    dismiss()
                }

                SETTING_QUICK_SAVE -> {
                    NativeLibrary.SaveState(0, false)
                    dismiss()
                }

                SETTING_QUICK_LOAD -> {
                    NativeLibrary.LoadState(0)
                    dismiss()
                }

                SETTING_STATE_SAVES -> {
                    activity.showStateSaves()
                    dismiss()
                }

                SETTING_ADJUST_CONTROLS -> {
                    activity.adjustControls()
                    dismiss()
                }

                SETTING_CHOOSE_CONTROLLER -> {
                    activity.chooseController()
                    dismiss()
                }

                SETTING_JOYSTICK_EMULATION -> {
                    activity.showJoystickSettings()
                    dismiss()
                }

                SETTING_CHANGE_DISC -> {
                    activity.changeDisc()
                    dismiss()
                }

                SETTING_SENSOR_EMULATION -> {
                    activity.showSensorSettings()
                    dismiss()
                }

                SETTING_EXIT_GAME -> {
                    activity.exitEmulation()
                    dismiss()
                }
            }
        }
    }

    inner class CheckBoxSettingViewHolder(itemView: View) : SettingViewHolder(itemView),
        CompoundButton.OnCheckedChangeListener {
        var item: SettingsItem? = null
        private var textSettingName: TextView? = null
        private var switchWidget: MaterialSwitch? = null

        override fun findViews(root: View) {
            textSettingName = root.findViewById(R.id.text_setting_name)
            switchWidget = root.findViewById(R.id.switch_widget)
            switchWidget!!.setOnCheckedChangeListener(this)
        }

        override fun bind(item: SettingsItem) {
            this.item = item
            textSettingName!!.text = item.name
            switchWidget!!.isChecked = this.item!!.value > 0
        }

        override fun onClick(clicked: View) {
            switchWidget!!.toggle()
            item!!.value = if (switchWidget!!.isChecked) 1 else 0
        }

        override fun onCheckedChanged(view: CompoundButton, isChecked: Boolean) {
            item!!.value = if (isChecked) 1 else 0
        }
    }

    inner class RadioButtonSettingViewHolder(itemView: View) : SettingViewHolder(itemView),
        RadioGroup.OnCheckedChangeListener {
        var item: SettingsItem? = null
        private var textSettingName: TextView? = null
        private var radioGroup: RadioGroup? = null

        override fun findViews(root: View) {
            textSettingName = root.findViewById(R.id.text_setting_name)
            radioGroup = root.findViewById(R.id.radio_group)
            radioGroup!!.setOnCheckedChangeListener(this)
        }

        override fun bind(item: SettingsItem) {
            val checkIds = intArrayOf(R.id.radio0, R.id.radio1, R.id.radio2)
            var index = item.value
            if (index < 0 || index >= checkIds.size) index = 0

            this.item = item
            textSettingName!!.text = item.name
            radioGroup!!.check(checkIds[index])

            if (item.setting == SETTING_TOUCH_POINTER) {
                val radio0 = radioGroup!!.findViewById<RadioButton>(R.id.radio0)
                radio0.setText(R.string.off)

                val radio1 = radioGroup!!.findViewById<RadioButton>(R.id.radio1)
                radio1.setText(R.string.touch_ir_click)

                val radio2 = radioGroup!!.findViewById<RadioButton>(R.id.radio2)
                radio2.setText(R.string.touch_ir_stick)
            }
        }

        override fun onClick(clicked: View) {
        }

        override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
            when (checkedId) {
                R.id.radio0 -> item!!.value = 0
                R.id.radio1 -> item!!.value = 1
                R.id.radio2 -> item!!.value = 2
                else -> item!!.value = 0
            }
        }
    }

    inner class SeekBarSettingViewHolder(itemView: View) : SettingViewHolder(itemView) {
        var item: SettingsItem? = null
        private var textSettingName: TextView? = null
        private var textSettingValue: TextView? = null
        private var seekBar: SeekBar? = null

        override fun findViews(root: View) {
            textSettingName = root.findViewById(R.id.text_setting_name)
            textSettingValue = root.findViewById(R.id.text_setting_value)
            seekBar = root.findViewById(R.id.seekbar)
            seekBar!!.progress = 99
        }

        override fun bind(item: SettingsItem) {
            this.item = item
            textSettingName!!.text = item.name
            when (item.setting) {
                SETTING_OVERCLOCK_PERCENT -> seekBar!!.max =
                    400

                SETTING_DISPLAY_SCALE -> seekBar!!.max =
                    200

                SETTING_IR_PITCH, SETTING_IR_YAW, SETTING_IR_VERTICAL_OFFSET -> seekBar!!.max =
                    50

                else -> seekBar!!.max = 10
            }
            seekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                    var progress = progress
                    if (seekBar.max > 99) {
                        progress = (progress / 5) * 5
                        textSettingValue!!.text = "$progress%"
                    } else {
                        textSettingValue!!.text = progress.toString()
                    }
                    this@SeekBarSettingViewHolder.item!!.value = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                }
            })
            seekBar!!.progress = item.value
        }

        override fun onClick(clicked: View) {
        }
    }

    inner class SettingsAdapter : RecyclerView.Adapter<SettingViewHolder>() {
        private var rumble = 0
        private var touchPointer = 0
        private var irRecenter = 0
        private var joystickRelative = 0
        private var runningSettings: IntArray? = null
        private var settings: ArrayList<SettingsItem>? = null

        fun loadMainMenu() {
            settings = ArrayList()
            settings!!.add(
                SettingsItem(
                    SETTING_LOAD_SUBMENU,
                    R.string.preferences_settings,
                    TYPE_BUTTON,
                    MENU_SETTINGS
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_TAKE_SCREENSHOT,
                    R.string.emulation_screenshot,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_EDIT_BUTTONS,
                    R.string.emulation_edit_layout,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_TOGGLE_BUTTONS,
                    R.string.emulation_toggle_controls,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_QUICK_SAVE,
                    R.string.emulation_quicksave,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_QUICK_LOAD,
                    R.string.emulation_quickload,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_STATE_SAVES,
                    R.string.state_saves,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_ADJUST_CONTROLS,
                    R.string.emulation_control_adjust,
                    TYPE_BUTTON,
                    0
                )
            )
            if (!get()!!.isGameCubeGame) {
                settings!!.add(
                    SettingsItem(
                        SETTING_CHOOSE_CONTROLLER,
                        R.string.emulation_choose_controller,
                        TYPE_BUTTON,
                        0
                    )
                )
                settings!!.add(
                    SettingsItem(
                        SETTING_JOYSTICK_EMULATION,
                        R.string.emulation_joystick_settings,
                        TYPE_BUTTON,
                        0
                    )
                )
            }
            // TODO: Seems like Change Disc is broken even from original MMJ build, so keep it hidden for now
            /* settings!!.add(
                SettingsItem(
                    SETTING_CHANGE_DISC,
                    R.string.emulation_change_disc,
                    TYPE_BUTTON,
                    0
                )
            ) */
            settings!!.add(
                SettingsItem(
                    SETTING_SENSOR_EMULATION,
                    R.string.emulation_sensor_settings,
                    TYPE_BUTTON,
                    0
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_EXIT_GAME,
                    R.string.emulation_exit,
                    TYPE_BUTTON,
                    0
                )
            )
            notifyDataSetChanged()
        }

        fun loadSettingsMenu() {
            var i = 0
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            runningSettings = NativeLibrary.getRunningSettings()
            settings = ArrayList()

            rumble = if (prefs.getBoolean(EmulationActivity.RUMBLE_PREF_KEY, true)) 1 else 0
            settings!!.add(
                SettingsItem(
                    SETTING_PHONE_RUMBLE, R.string.emulation_control_rumble,
                    TYPE_CHECKBOX, rumble
                )
            )

            if (!get()!!.isGameCubeGame) {
                touchPointer = prefs.getInt(InputOverlay.POINTER_PREF_KEY, 0)
                settings!!.add(
                    SettingsItem(
                        SETTING_TOUCH_POINTER,
                        R.string.touch_screen_pointer,
                        TYPE_RADIO_GROUP,
                        touchPointer
                    )
                )

                val gameId = get()!!.selectedGameId
                val prefId = if (gameId!!.length > 3) gameId.substring(0, 3) else gameId
                irRecenter = if (prefs.getBoolean(
                        InputOverlay.RECENTER_PREF_KEY + "_" + prefId,
                        false
                    )
                ) 1 else 0
                settings!!.add(
                    SettingsItem(
                        SETTING_TOUCH_POINTER_RECENTER,
                        R.string.touch_screen_pointer_recenter,
                        TYPE_CHECKBOX,
                        irRecenter
                    )
                )
            }

            joystickRelative = if (prefs.getBoolean(InputOverlay.RELATIVE_PREF_KEY, true)) 1 else 0
            settings!!.add(
                SettingsItem(
                    SETTING_JOYSTICK_RELATIVE,
                    R.string.joystick_relative_center,
                    TYPE_CHECKBOX,
                    joystickRelative
                )
            )

            // gfx
            settings!!.add(
                SettingsItem(
                    SETTING_SHOW_FPS, R.string.show_fps,
                    TYPE_CHECKBOX, runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_SKIP_EFB,
                    R.string.skip_efb_access,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_EFB_TEXTURE, R.string.efb_copy_method,
                    TYPE_CHECKBOX, runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_VI_SKIP, R.string.vi_skip,
                    TYPE_CHECKBOX, runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_IGNORE_FORMAT,
                    R.string.ignore_format_changes,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_ARBITRARY_MIPMAP_DETECTION,
                    R.string.arbitrary_mipmap_detection,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_IMMEDIATE_XFB,
                    R.string.immediate_xfb,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_DISPLAY_SCALE,
                    R.string.setting_display_scale,
                    TYPE_SEEK_BAR,
                    runningSettings!![i++]
                )
            )

            // core
            settings!!.add(
                SettingsItem(
                    SETTING_SYNC_ON_SKIP_IDLE,
                    R.string.sync_on_skip_idle,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_OVERCLOCK_ENABLE,
                    R.string.overclock_enable,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_OVERCLOCK_PERCENT,
                    R.string.overclock_title,
                    TYPE_SEEK_BAR,
                    runningSettings!![i++]
                )
            )
            settings!!.add(
                SettingsItem(
                    SETTING_JIT_FOLLOW_BRANCH,
                    R.string.jit_follow_branch,
                    TYPE_CHECKBOX,
                    runningSettings!![i++]
                )
            )

            if (!get()!!.isGameCubeGame) {
                settings!!.add(
                    SettingsItem(
                        SETTING_IR_PITCH,
                        R.string.pitch,
                        TYPE_SEEK_BAR,
                        runningSettings!![i++]
                    )
                )
                settings!!.add(
                    SettingsItem(
                        SETTING_IR_YAW,
                        R.string.yaw, TYPE_SEEK_BAR, runningSettings!![i++]
                    )
                )
                settings!!.add(
                    SettingsItem(
                        SETTING_IR_VERTICAL_OFFSET,
                        R.string.vertical_offset,
                        TYPE_SEEK_BAR,
                        runningSettings!![i++]
                    )
                )
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
            val itemView: View
            val inflater = LayoutInflater.from(parent.context)
            when (viewType) {
                TYPE_CHECKBOX -> {
                    itemView = inflater.inflate(R.layout.list_item_running_switch, parent, false)
                    return CheckBoxSettingViewHolder(itemView)
                }

                TYPE_RADIO_GROUP -> {
                    itemView = inflater.inflate(R.layout.list_item_running_radio3, parent, false)
                    return RadioButtonSettingViewHolder(itemView)
                }

                TYPE_SEEK_BAR -> {
                    itemView = inflater.inflate(R.layout.list_item_running_seekbar, parent, false)
                    return SeekBarSettingViewHolder(itemView)
                }

                TYPE_BUTTON -> {
                    itemView = inflater.inflate(R.layout.list_item_running_button, parent, false)
                    return ButtonSettingViewHolder(itemView)
                }

                else -> throw IllegalArgumentException("Invalid view type")
            }
        }

        override fun getItemCount(): Int {
            return settings!!.size
        }

        override fun getItemViewType(position: Int): Int {
            return settings!![position].type
        }

        override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
            holder.bind(settings!![position])
        }

        fun saveSettings() {
            // prefs
            val editor = PreferenceManager.getDefaultSharedPreferences(
                context
            ).edit()

            val rumble = settings!![0].value
            if (this.rumble != rumble) {
                editor.putBoolean(EmulationActivity.RUMBLE_PREF_KEY, rumble > 0)
                Rumble.setPhoneRumble(activity, rumble > 0)
            }
            settings!!.removeAt(0)

            if (!get()!!.isGameCubeGame) {
                val pointer = settings!![0].value
                if (touchPointer != pointer) {
                    editor.putInt(InputOverlay.POINTER_PREF_KEY, pointer)
                    get()!!.setTouchPointer(pointer)
                }
                settings!!.removeAt(0)

                val recenter = settings!![0].value
                if (irRecenter != recenter) {
                    val gameId = get()!!.selectedGameId
                    val prefId = if (gameId!!.length > 3) gameId.substring(0, 3) else gameId
                    editor.putBoolean(InputOverlay.RECENTER_PREF_KEY + "_" + prefId, recenter > 0)
                    InputOverlay.sIRRecenter = recenter > 0
                }
                settings!!.removeAt(0)
            }

            val relative = settings!![0].value
            if (joystickRelative != relative) {
                editor.putBoolean(InputOverlay.RELATIVE_PREF_KEY, relative > 0)
                InputOverlay.sJoystickRelative = relative > 0
            }
            settings!!.removeAt(0)

            editor.apply()

            // settings
            var isChanged = false
            val newSettings = IntArray(runningSettings!!.size)
            for (i in runningSettings!!.indices) {
                newSettings[i] = settings!![i].value
                if (newSettings[i] != runningSettings!![i]) {
                    isChanged = true
                }
            }
            // only apply if user changed settings
            if (isChanged) {
                NativeLibrary.setRunningSettings(newSettings)
                // update display scale
                get()!!.updateTouchPointer()
            }
        }
    }

    private var menu = 0
    private var title: TextView? = null
    private var info: TextView? = null
    private var handler: Handler? = null
    private var adapter: SettingsAdapter? = null
    private var dismissListener: DialogInterface.OnDismissListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val contents = requireActivity().layoutInflater
            .inflate(R.layout.dialog_running_settings, null) as ViewGroup

        title = contents.findViewById(R.id.text_title)
        info = contents.findViewById(R.id.text_info)
        handler = Handler(requireActivity().mainLooper)
        setHeapInfo()

        val columns = 1
        val recyclerView = contents.findViewById<RecyclerView>(R.id.list_settings)
        val layoutManager: RecyclerView.LayoutManager = GridLayoutManager(context, columns)
        recyclerView.layoutManager = layoutManager
        adapter = SettingsAdapter()
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL
            )
        )
        builder.setView(contents)
        loadSubMenu(MENU_MAIN)
        return builder.create()
    }

    // display ram usage
    private fun setHeapInfo() {
        val heapsize = Debug.getNativeHeapAllocatedSize() shr 20
        info!!.text = String.format("%dMB", heapsize)
        handler!!.postDelayed({ this.setHeapInfo() }, 1000)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (menu == MENU_SETTINGS) {
            adapter!!.saveSettings()
        }
        if (dismissListener != null) {
            dismissListener!!.onDismiss(dialog)
        }
        handler!!.removeCallbacksAndMessages(null)
    }

    private fun loadSubMenu(menu: Int) {
        if (menu == MENU_MAIN) {
            val activity = NativeLibrary.getEmulationContext() as EmulationActivity
            title!!.text = activity.title
            adapter!!.loadMainMenu()
        } else if (menu == MENU_SETTINGS) {
            title!!.setText(R.string.preferences_settings)
            adapter!!.loadSettingsMenu()
        }
        this.menu = menu
    }

    companion object {
        const val MENU_MAIN = 0
        const val MENU_SETTINGS = 1

        // gfx
        const val SETTING_SHOW_FPS = 10
        const val SETTING_SKIP_EFB = 11
        const val SETTING_EFB_TEXTURE = 12
        const val SETTING_VI_SKIP = 13
        const val SETTING_IGNORE_FORMAT = 14
        const val SETTING_ARBITRARY_MIPMAP_DETECTION = 15
        const val SETTING_IMMEDIATE_XFB = 16
        const val SETTING_DISPLAY_SCALE = 17

        // core
        const val SETTING_SYNC_ON_SKIP_IDLE = 18
        const val SETTING_OVERCLOCK_ENABLE = 19
        const val SETTING_OVERCLOCK_PERCENT = 20
        const val SETTING_JIT_FOLLOW_BRANCH = 21
        const val SETTING_IR_PITCH = 22
        const val SETTING_IR_YAW = 23
        const val SETTING_IR_VERTICAL_OFFSET = 24

        // pref
        const val SETTING_PHONE_RUMBLE = 100
        const val SETTING_TOUCH_POINTER = 101
        const val SETTING_TOUCH_POINTER_RECENTER = 102
        const val SETTING_JOYSTICK_RELATIVE = 103

        // func
        const val SETTING_LOAD_SUBMENU = 200
        const val SETTING_TAKE_SCREENSHOT = 201
        const val SETTING_EDIT_BUTTONS = 202
        const val SETTING_TOGGLE_BUTTONS = 203
        const val SETTING_QUICK_SAVE = 204
        const val SETTING_QUICK_LOAD = 205
        const val SETTING_STATE_SAVES = 206
        const val SETTING_ADJUST_CONTROLS = 207
        const val SETTING_CHOOSE_CONTROLLER = 208
        const val SETTING_JOYSTICK_EMULATION = 209
        const val SETTING_CHANGE_DISC = 210
        const val SETTING_SENSOR_EMULATION = 211
        const val SETTING_EXIT_GAME = 212

        // view type
        const val TYPE_CHECKBOX = 0
        const val TYPE_RADIO_GROUP = 1
        const val TYPE_SEEK_BAR = 2
        const val TYPE_BUTTON = 3

        fun newInstance(): RunningSettingDialog {
            return RunningSettingDialog()
        }
    }
}
