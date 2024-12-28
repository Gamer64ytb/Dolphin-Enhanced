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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
import org.dolphinemu.dolphinemu.features.settings.ui.viewholder.CheckBoxSettingViewHolder
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

class SettingsAdapter(private val mActivity: SettingsActivity) :
    RecyclerView.Adapter<SettingViewHolder?>(),
    DialogInterface.OnClickListener, OnSeekBarChangeListener {
    private var mSettings: ArrayList<SettingsItem>? = null

    private var mClickedItem: SettingsItem? = null
    private var mClickedPosition: Int
    private var mSeekbarProgress = 0

    private var mDialog: AlertDialog? = null
    private var mTextSliderValue: TextInputEditText? = null
    private var mTextInputLayout: TextInputLayout? = null

    init {
        mClickedPosition = -1
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
                view = inflater.inflate(R.layout.list_item_setting_checkbox, parent, false)
                return CheckBoxSettingViewHolder(
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
        holder.bind(mSettings!![position])
    }

    override fun getItemCount(): Int {
        return if (mSettings != null) {
            mSettings!!.size
        } else {
            0
        }
    }

    override fun getItemViewType(position: Int): Int {
        return mSettings!![position].type
    }

    fun getSettingSection(position: Int): String {
        return mSettings!![position].section
    }

    fun setSettings(settings: ArrayList<SettingsItem>?) {
        mSettings = settings
        notifyDataSetChanged()
    }

    fun onBooleanClick(item: CheckBoxSetting, position: Int, checked: Boolean) {
        val setting = item.setChecked(checked)
        if (setting != null) {
            mActivity.putSetting(setting)
        }
        mActivity.setSettingChanged()
    }

    fun onSingleChoiceClick(item: SingleChoiceSetting, position: Int) {
        mClickedItem = item
        mClickedPosition = position

        val value = getSelectionForSingleChoiceValue(item)
        val builder = AlertDialog.Builder(mActivity)
        builder.setTitle(item.nameId)
        builder.setSingleChoiceItems(item.choicesId, value, this)
        mDialog = builder.show()
    }

    fun onStringSingleChoiceClick(item: StringSingleChoiceSetting, position: Int) {
        mClickedItem = item
        mClickedPosition = position

        val builder = AlertDialog.Builder(mActivity)
        builder.setTitle(item.nameId)
        builder.setSingleChoiceItems(item.choicesId, item.selectValueIndex, this)
        mDialog = builder.show()
    }

    fun onSeekbarClick(item: SliderSetting, position: Int, progress: Int) {
        val setting = item.setSelectedValue(progress)
        if (setting != null) {
            mActivity.putSetting(setting)
        }
        mActivity.setSettingChanged()
    }

    fun onSliderClick(item: SliderSetting, position: Int) {
        mClickedItem = item
        mClickedPosition = position
        mSeekbarProgress = item.selectedValue
        val builder = AlertDialog.Builder(mActivity)

        val inflater = LayoutInflater.from(mActivity)
        val view = inflater.inflate(R.layout.dialog_seekbar, null)

        builder.setTitle(item.nameId)
        builder.setView(view)
        builder.setPositiveButton(android.R.string.ok, this)
        mDialog = builder.show()

        mTextInputLayout = view.findViewById(R.id.text_input)
        mTextSliderValue = view.findViewById(R.id.text_value)
        mTextSliderValue!!.setText(mSeekbarProgress.toString())
        mTextInputLayout!!.suffixText = item.units

        val seekbar = view.findViewById<SeekBar>(R.id.seekbar)
        seekbar.max = item.max
        seekbar.progress = mSeekbarProgress
        seekbar.keyProgressIncrement = 5

        mTextSliderValue!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                var textValue: Int? = null
                try {
                    textValue = s.toString().toInt()
                } catch (ignored: NumberFormatException) {
                }
                // workaround to maintain SDK 24 support
                // we just use a 0 instead of seekbar.getMin()
                if (textValue == null || textValue < 0 || textValue > seekbar.max) {
                    mTextInputLayout!!.setError("Inappropriate value")
                } else {
                    mTextInputLayout!!.setError(null)
                    seekbar.progress = textValue
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })

        seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mSeekbarProgress = progress
                if (mTextSliderValue!!.getText().toString() != progress.toString()) {
                    mTextSliderValue!!.setText(((progress / 5) * 5).toString())
                    mTextSliderValue!!.setSelection(mTextSliderValue!!.length())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
    }

    fun onSubmenuClick(item: SubmenuSetting) {
        mActivity.loadSubMenu(item.menuKey)
    }

    private fun getFormatString(resId: Int, arg: String): Spanned {
        val unspanned = String.format(mActivity.getString(resId), arg)
        val spanned = Html.fromHtml(unspanned, Html.FROM_HTML_MODE_LEGACY)
        return spanned
    }

    fun onInputBindingClick(item: InputBindingSetting, position: Int) {
        mClickedItem = item
        mClickedPosition = position

        val dialog = MotionAlertDialog(mActivity, item)
        dialog.setTitle(R.string.input_binding)
        dialog.setMessage(
            getFormatString(
                if (item is RumbleBindingSetting) R.string.input_rumble_description else R.string.input_binding_description,
                mActivity.getString(item.nameId)
            )
        )
        dialog.setButton(
            AlertDialog.BUTTON_NEGATIVE, mActivity.getString(android.R.string.cancel),
            this
        )
        dialog.setButton(
            AlertDialog.BUTTON_NEUTRAL, mActivity.getString(R.string.clear)
        ) { _: DialogInterface?, _: Int ->
            val preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity)
            item.clearValue()
        }
        dialog.setOnDismissListener {
            val setting = StringSetting(item.key, item.section, item.value)
            notifyItemChanged(position)
            mActivity.putSetting(setting)
            mActivity.setSettingChanged()
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (mClickedItem is SingleChoiceSetting) {
            val scSetting = mClickedItem as SingleChoiceSetting

            val value = getValueForSingleChoiceSelection(scSetting, which)
            if (scSetting.selectedValue != value) mActivity.setSettingChanged()

            val menuTag = scSetting.menuTag
            if (menuTag != null) {
                if (menuTag.isGCPadMenu) {
                    mActivity.onGcPadSettingChanged(menuTag, value)
                }

                if (menuTag.isWiimoteMenu) {
                    mActivity.onWiimoteSettingChanged(menuTag, value)
                }

                if (menuTag.isWiimoteExtensionMenu) {
                    mActivity.onExtensionSettingChanged(menuTag, value)
                }
            }

            // Get the backing Setting, which may be null (if for example it was missing from the file)
            val setting = scSetting.setSelectedValue(value)
            if (setting != null) {
                mActivity.putSetting(setting)
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
        } else if (mClickedItem is StringSingleChoiceSetting) {
            val scSetting = mClickedItem as StringSingleChoiceSetting
            val value = scSetting.getValueAt(which)
            if (scSetting.selectedValue != value) mActivity.setSettingChanged()

            val setting = scSetting.setSelectedValue(value)
            if (setting != null) {
                mActivity.putSetting(setting)
            }

            closeDialog()
        } else if (mClickedItem is SliderSetting) {
            val sliderSetting = mClickedItem as SliderSetting
            if (sliderSetting.selectedValue != mSeekbarProgress) mActivity.setSettingChanged()

            val setting = sliderSetting.setSelectedValue(mSeekbarProgress)
            if (setting != null) {
                mActivity.putSetting(setting)
            }

            closeDialog()
        }

        mClickedItem = null
        mSeekbarProgress = -1
    }

    fun closeDialog() {
        if (mDialog != null) {
            if (mClickedPosition != -1) {
                notifyItemChanged(mClickedPosition)
                mClickedPosition = -1
            }
            mDialog!!.dismiss()
            mDialog = null
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        mSeekbarProgress = if (seekBar.max > 99) (progress / 5) * 5 else progress
        mTextSliderValue!!.setText(mSeekbarProgress.toString())
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
    }

    private fun getValueForSingleChoiceSelection(item: SingleChoiceSetting, which: Int): Int {
        val valuesId = item.valuesId

        if (valuesId > 0) {
            val valuesArray = mActivity.resources.getIntArray(valuesId)
            return valuesArray[which]
        } else {
            return which
        }
    }

    private fun getSelectionForSingleChoiceValue(item: SingleChoiceSetting): Int {
        val value = item.selectedValue
        val valuesId = item.valuesId

        if (valuesId > 0) {
            val valuesArray = mActivity.resources.getIntArray(valuesId)
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

        mActivity.putSetting(gfxBackend!!)
    }

    private fun putExtensionSetting(which: Int, wiimoteNumber: Int, isGame: Boolean) {
        if (!isGame) {
            val extension = StringSetting(
                SettingsFile.KEY_WIIMOTE_EXTENSION,
                Settings.SECTION_WIIMOTE + wiimoteNumber,
                mActivity.resources.getStringArray(R.array.wiimoteExtensionsEntries)[which]
            )
            mActivity.putSetting(extension)
        } else {
            val extension =
                StringSetting(
                    SettingsFile.KEY_WIIMOTE_EXTENSION + wiimoteNumber,
                    Settings.SECTION_CONTROLS, mActivity.resources
                        .getStringArray(R.array.wiimoteExtensionsEntries)[which]
                )
            mActivity.putSetting(extension)
        }
    }
}
