package org.dolphinemu.dolphinemu.features.settings.ui

import android.content.DialogInterface
import android.preference.PreferenceManager
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.slider.Slider
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.dialogs.MotionAlertDialog
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.CheckBoxSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.RumbleBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SingleChoiceSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.StringSingleChoiceSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SubmenuSetting
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.SwitchSettingViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.HeaderViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.InputBindingSettingViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.RumbleBindingViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.SeekbarViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.SettingViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.SingleChoiceViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.SliderViewHolder
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.SubmenuViewHolder
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile
import org.dolphinemu.dolphinemu.utils.Log

class SettingsAdapter(private val activity: SettingsActivity) :
    RecyclerView.Adapter<SettingViewHolder?>(),
    DialogInterface.OnClickListener, OnSeekBarChangeListener {
    private var settings: ArrayList<SettingsItem>? = null

    private var clickedItem: SettingsItem? = null
    private var clickedPosition: Int
    private var seekbarProgress = 0

    private var dialog: AlertDialog? = null
    private var inputDialog: MotionAlertDialog? = null
    private var textSliderValue: TextInputEditText? = null
    private var textInputLayout: TextInputLayout? = null

    init {
        clickedPosition = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val view: View
        val inflater = LayoutInflater.from(parent.context)

        when (viewType) {
            SettingsItem.TYPE_HEADER -> {
                view = inflater.inflate(R.layout.list_item_settings_header, parent, false)
                return HeaderViewHolder(
                    view,
                    this
                )
            }

            SettingsItem.TYPE_CHECKBOX -> {
                view = inflater.inflate(R.layout.list_item_setting_switch, parent, false)
                return SwitchSettingViewHolder(
                    view,
                    this
                )
            }

            SettingsItem.TYPE_STRING_SINGLE_CHOICE, SettingsItem.TYPE_SINGLE_CHOICE -> {
                view = inflater.inflate(R.layout.list_item_setting, parent, false)
                return SingleChoiceViewHolder(view, this)
            }

            SettingsItem.TYPE_SLIDER -> {
                view = inflater.inflate(R.layout.list_item_setting, parent, false)
                return SliderViewHolder(view, this)
            }

            SettingsItem.TYPE_SUBMENU -> {
                view = inflater.inflate(R.layout.list_item_setting, parent, false)
                return SubmenuViewHolder(view, this)
            }

            SettingsItem.TYPE_INPUT_BINDING -> {
                view = inflater.inflate(R.layout.list_item_setting, parent, false)
                return InputBindingSettingViewHolder(view, this)
            }

            SettingsItem.TYPE_RUMBLE_BINDING -> {
                view = inflater.inflate(R.layout.list_item_setting, parent, false)
                return RumbleBindingViewHolder(view, this)
            }

            SettingsItem.TYPE_SEEKBAR -> {
                view = inflater.inflate(R.layout.list_item_setting_seekbar, parent, false)
                return SeekbarViewHolder(view, this)
            }

            else -> {
                Log.error("[SettingsAdapter] Invalid view type: $viewType")
                return null!!
            }
        }
    }

    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        holder.bind(settings!![position])
    }

    override fun getItemCount(): Int {
        return if (settings != null) {
            settings!!.size
        } else {
            0
        }
    }

    override fun getItemViewType(position: Int): Int {
        return settings!![position].type
    }

    fun getSettingSection(position: Int): String {
        return settings!![position].section
    }

    fun setSettings(settings: ArrayList<SettingsItem>?) {
        this.settings = settings
        notifyDataSetChanged()
    }

    fun onBooleanClick(item: CheckBoxSetting, position: Int, checked: Boolean) {
        val setting = item.setChecked(checked)
        if (setting != null) {
            activity.putSetting(setting)
        }
        activity.setSettingChanged()
    }

    fun onSingleChoiceClick(item: SingleChoiceSetting, position: Int) {
        clickedItem = item
        clickedPosition = position

        val value = getSelectionForSingleChoiceValue(item)
        val builder = MaterialAlertDialogBuilder(activity)
        builder.setTitle(item.nameId)
        builder.setSingleChoiceItems(item.choicesId, value, this)
        dialog = builder.show()
    }

    fun onStringSingleChoiceClick(item: StringSingleChoiceSetting, position: Int) {
        clickedItem = item
        clickedPosition = position

        val builder = MaterialAlertDialogBuilder(activity)
        builder.setTitle(item.nameId)
        builder.setSingleChoiceItems(item.choicesId, item.selectValueIndex, this)
        dialog = builder.show()
    }

    fun onSeekbarClick(item: SliderSetting, position: Int, progress: Int) {
        val setting = item.setSelectedValue(progress)
        if (setting != null) {
            activity.putSetting(setting)
        }
        activity.setSettingChanged()
    }

    fun onSliderClick(item: SliderSetting, position: Int) {
        clickedItem = item
        clickedPosition = position
        seekbarProgress = item.selectedValue

        val builder = MaterialAlertDialogBuilder(activity)
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_sliders, null)
        val slider = view.findViewById<Slider>(R.id.slider)

        builder.setTitle(item.nameId)
        builder.setView(view)
        builder.setPositiveButton(android.R.string.ok, this)
        builder.setNeutralButton(R.string.slider_default) { dialog: DialogInterface?, which: Int ->
            slider.value = item.getDefaultValue().toFloat()
            seekbarProgress = item.getDefaultValue()
            onClick(dialog!!, which)
        }
        dialog = builder.show()

        textInputLayout = view.findViewById(R.id.text_input)
        textSliderValue = view.findViewById(R.id.text_value)
        textSliderValue!!.setText(seekbarProgress.toString())
        textInputLayout!!.suffixText = item.units

        slider.valueFrom = 0f
        slider.valueTo = item.max.toFloat()
        slider.value = seekbarProgress.toFloat()

        // 128 avoids 5 step skipping on MEM2 Size setting
        slider.stepSize = when {
            item.max > 128 -> 5f
            else -> 1f
        }

        textSliderValue!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val textValue = s.toString().toIntOrNull()
                // workaround to maintain SDK 24 support
                // we just use textValue < 0 instead of textValue < seekbar.getMin()
                if (textValue == null || textValue < 0 || textValue > item.max) {
                    textInputLayout!!.error = activity.getString(R.string.invalid_value)
                } else {
                    textInputLayout!!.error = null
                    seekbarProgress = textValue
                    if (slider.value.toInt() != textValue) {
                        slider.value = textValue.toFloat()
                    }
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        slider.addOnChangeListener { _, value, _ ->
            val progress = value.toInt()
            seekbarProgress = progress
            if (textSliderValue!!.text.toString() != progress.toString()) {
                textSliderValue!!.setText(progress.toString())
                textSliderValue!!.setSelection(textSliderValue!!.length())
            }
        }
    }

    fun onSubmenuClick(item: SubmenuSetting) {
        activity.loadSubMenu(item.menuKey)
    }

    private fun getFormatString(resId: Int, arg: String): Spanned {
        val unspanned = String.format(activity.getString(resId), arg)
        val spanned = Html.fromHtml(unspanned, Html.FROM_HTML_MODE_LEGACY)
        return spanned
    }

    fun onInputBindingClick(item: InputBindingSetting, position: Int) {
        clickedItem = item
        clickedPosition = position

        inputDialog = MotionAlertDialog(activity, item).apply {
            setTitle(R.string.input_binding)
            setMessage(
                getFormatString(
                    if (item is RumbleBindingSetting)
                        R.string.input_rumble_description
                    else
                        R.string.input_binding_description,
                    activity.getString(item.nameId)
                )
            )
            setButton(DialogInterface.BUTTON_NEGATIVE,
                activity.getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            setButton(DialogInterface.BUTTON_NEUTRAL,
                activity.getString(R.string.clear)) { _, _ ->
                item.clearValue()
            }
            setOnDismissListener {
                val setting = StringSetting(item.key, item.section, item.value)
                notifyItemChanged(position)
                activity.putSetting(setting)
                activity.setSettingChanged()
            }
            setCanceledOnTouchOutside(false)
        }
        inputDialog?.show()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (clickedItem is SingleChoiceSetting) {
            val scSetting = clickedItem as SingleChoiceSetting

            val value = getValueForSingleChoiceSelection(scSetting, which)
            if (scSetting.selectedValue != value) activity.setSettingChanged()

            val menuTag = scSetting.menuTag
            if (menuTag != null) {
                if (menuTag.isGCPadMenu) {
                    activity.onGcPadSettingChanged(menuTag, value)
                }

                if (menuTag.isWiimoteMenu) {
                    activity.onWiimoteSettingChanged(menuTag, value)
                }

                if (menuTag.isWiimoteExtensionMenu) {
                    activity.onExtensionSettingChanged(menuTag, value)
                }
            }

            // Get the backing Setting, which may be null (if for example it was missing from the file)
            val setting = scSetting.setSelectedValue(value)
            if (setting != null) {
                activity.putSetting(setting)
            } else {
                if (scSetting.key == SettingsFile.KEY_VIDEO_BACKEND_INDEX) {
                    putVideoBackendSetting(which)
                } else if (scSetting.key == SettingsFile.KEY_WIIMOTE_EXTENSION) {
                    putExtensionSetting(
                        which, Character.getNumericValue(
                            scSetting.section[scSetting.section.length - 1]
                        ), false
                    )
                } else if (scSetting.key.contains(SettingsFile.KEY_WIIMOTE_EXTENSION) &&
                    scSetting.section == Settings.SECTION_CONTROLS
                ) {
                    putExtensionSetting(
                        which, Character
                            .getNumericValue(scSetting.key[scSetting.key.length - 1]),
                        true
                    )
                }
            }

            closeDialog()
        } else if (clickedItem is StringSingleChoiceSetting) {
            val scSetting = clickedItem as StringSingleChoiceSetting
            val value = scSetting.getValueAt(which)
            if (scSetting.selectedValue != value) activity.setSettingChanged()

            val setting = scSetting.setSelectedValue(value)
            if (setting != null) {
                activity.putSetting(setting)
            }

            closeDialog()
        } else if (clickedItem is SliderSetting) {
            val sliderSetting = clickedItem as SliderSetting
            if (sliderSetting.selectedValue != seekbarProgress) activity.setSettingChanged()

            val setting = sliderSetting.setSelectedValue(seekbarProgress)
            if (setting != null) {
                activity.putSetting(setting)
            }

            closeDialog()
        }

        clickedItem = null
        seekbarProgress = -1
    }

    fun closeDialog() {
        if (dialog != null) {
            if (clickedPosition != -1) {
                notifyItemChanged(clickedPosition)
                clickedPosition = -1
            }
            dialog!!.dismiss()
            dialog = null
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        seekbarProgress = if (seekBar.max > 99) (progress / 5) * 5 else progress
        textSliderValue!!.setText(seekbarProgress.toString())
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
    }

    private fun getValueForSingleChoiceSelection(item: SingleChoiceSetting, which: Int): Int {
        val valuesId = item.valuesId

        if (valuesId > 0) {
            val valuesArray = activity.resources.getIntArray(valuesId)
            return valuesArray[which]
        } else {
            return which
        }
    }

    private fun getSelectionForSingleChoiceValue(item: SingleChoiceSetting): Int {
        val value = item.selectedValue
        val valuesId = item.valuesId

        if (valuesId > 0) {
            val valuesArray = activity.resources.getIntArray(valuesId)
            for (index in valuesArray.indices) {
                val current = valuesArray[index]
                if (current == value) {
                    return index
                }
            }
        } else {
            return value
        }

        return -1
    }

    private fun putVideoBackendSetting(which: Int) {
        var gfxBackend: StringSetting? = null
        when (which) {
            0 -> gfxBackend =
                StringSetting(SettingsFile.KEY_VIDEO_BACKEND, Settings.SECTION_INI_CORE, "OGL")

            1 -> gfxBackend = StringSetting(
                SettingsFile.KEY_VIDEO_BACKEND, Settings.SECTION_INI_CORE,
                "Vulkan"
            )

            2 -> gfxBackend = StringSetting(
                SettingsFile.KEY_VIDEO_BACKEND, Settings.SECTION_INI_CORE,
                "Software Renderer"
            )

            3 -> gfxBackend = StringSetting(
                SettingsFile.KEY_VIDEO_BACKEND, Settings.SECTION_INI_CORE,
                "Null"
            )
        }

        activity.putSetting(gfxBackend!!)
    }

    private fun putExtensionSetting(which: Int, wiimoteNumber: Int, isGame: Boolean) {
        if (!isGame) {
            val extension = StringSetting(
                SettingsFile.KEY_WIIMOTE_EXTENSION,
                Settings.SECTION_WIIMOTE + wiimoteNumber,
                activity.resources.getStringArray(R.array.wiimoteExtensionsEntries)[which]
            )
            activity.putSetting(extension)
        } else {
            val extension =
                StringSetting(
                    SettingsFile.KEY_WIIMOTE_EXTENSION + wiimoteNumber,
                    Settings.SECTION_CONTROLS, activity.resources
                        .getStringArray(R.array.wiimoteExtensionsEntries)[which]
                )
            activity.putSetting(extension)
        }
    }
}
