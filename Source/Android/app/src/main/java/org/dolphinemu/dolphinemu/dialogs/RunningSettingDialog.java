package org.dolphinemu.dolphinemu.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;
import org.dolphinemu.dolphinemu.overlay.InputOverlay;
import org.dolphinemu.dolphinemu.ui.DividerItemDecoration;
import org.dolphinemu.dolphinemu.utils.Rumble;

import java.util.ArrayList;

public class RunningSettingDialog extends DialogFragment
{
	public static final int MENU_MAIN = 0;
	public static final int MENU_SETTINGS = 1;

  public class SettingsItem
  {
    // gfx
    public static final int SETTING_SHOW_FPS = 0;
    public static final int SETTING_SKIP_EFB = 1;
    public static final int SETTING_EFB_TEXTURE = 2;
    public static final int SETTING_VI_SKIP = 3;
    public static final int SETTING_IGNORE_FORMAT = 4;
    public static final int SETTING_ARBITRARY_MIPMAP_DETECTION = 5;
    public static final int SETTING_IMMEDIATE_XFB = 6;
    public static final int SETTING_DISPLAY_SCALE = 7;
    // core
    public static final int SETTING_SYNC_ON_SKIP_IDLE = 8;
    public static final int SETTING_OVERCLOCK_ENABLE = 9;
    public static final int SETTING_OVERCLOCK_PERCENT = 10;
    public static final int SETTING_JIT_FOLLOW_BRANCH = 11;
    public static final int SETTING_IR_PITCH = 12;
    public static final int SETTING_IR_YAW = 13;
    public static final int SETTING_IR_VERTICAL_OFFSET = 14;
    // pref
    public static final int SETTING_PHONE_RUMBLE = 100;
    public static final int SETTING_TOUCH_POINTER = 101;
    public static final int SETTING_TOUCH_POINTER_RECENTER = 102;
    public static final int SETTING_JOYSTICK_RELATIVE = 103;
    // func
    public static final int SETTING_LOAD_SUBMENU = 200;
    public static final int SETTING_TAKE_SCREENSHOT = 201;
    public static final int SETTING_EDIT_BUTTONS = 202;
    public static final int SETTING_TOGGLE_BUTTONS = 203;
    public static final int SETTING_QUICKSAVE = 204;
    public static final int SETTING_QUICKLOAD = 205;
    public static final int SETTING_STATESAVES = 206;
    public static final int SETTING_ADJUST_SCALE = 207;
    public static final int SETTING_CHOOSE_CONTROLLER = 208;
    public static final int SETTING_JOYSTICK_EMULATION = 209;
    public static final int SETTING_CHANGE_DISC = 210;
    public static final int SETTING_SENSOR_EMULATION = 211;
    public static final int SETTING_EXIT_GAME = 212;
    // view type
    public static final int TYPE_CHECKBOX = 0;
    public static final int TYPE_RADIO_GROUP = 1;
    public static final int TYPE_SEEK_BAR = 2;
    public static final int TYPE_BUTTON = 3;

    private int mSetting;
    private String mName;
    private int mType;
    private int mValue;

    public SettingsItem(int setting, int nameId, int type, int value)
    {
      mSetting = setting;
      mName = getString(nameId);
      mType = type;
      mValue = value;
    }

    public SettingsItem(int setting, String name, int type, int value)
    {
      mSetting = setting;
      mName = name;
      mType = type;
      mValue = value;
    }

    public int getType()
    {
      return mType;
    }

    public int getSetting()
    {
      return mSetting;
    }

    public String getName()
    {
      return mName;
    }

    public int getValue()
    {
      return mValue;
    }

    public void setValue(int value)
    {
      mValue = value;
    }
  }

  public abstract class SettingViewHolder extends RecyclerView.ViewHolder
    implements View.OnClickListener
  {
    public SettingViewHolder(View itemView)
    {
      super(itemView);
      itemView.setOnClickListener(this);
      findViews(itemView);
    }

    protected abstract void findViews(View root);

    public abstract void bind(SettingsItem item);

    public abstract void onClick(View clicked);
  }

  public final class ButtonSettingViewHolder extends SettingViewHolder
  {
	SettingsItem mItem;
    private TextView mName;

    public ButtonSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
	  mName = root.findViewById(R.id.text_setting_name);
    }

    @Override
    public void bind(SettingsItem item)
    {
	  mItem = item;
	  mName.setText(item.getName());
    }

    @Override
    public void onClick(View clicked)
    {
	  EmulationActivity activity = (EmulationActivity)NativeLibrary.getEmulationContext();
      switch (mItem.getSetting())
      {
	    case SettingsItem.SETTING_LOAD_SUBMENU:
		  loadSubMenu(mItem.getValue());
          break;
        case SettingsItem.SETTING_TAKE_SCREENSHOT:
          NativeLibrary.SaveScreenShot();
          dismiss();
          break;
		case SettingsItem.SETTING_EDIT_BUTTONS:
		  activity.editControlsPlacement();
          dismiss();
          break;
		case SettingsItem.SETTING_TOGGLE_BUTTONS:
          activity.toggleControls();
          dismiss();
          break;
		case SettingsItem.SETTING_QUICKSAVE:
		  NativeLibrary.SaveState(0, false);
          dismiss();
          break;
		case SettingsItem.SETTING_QUICKLOAD:
		  NativeLibrary.LoadState(0);
          dismiss();
          break;
		case SettingsItem.SETTING_STATESAVES:
		  activity.showStateSaves();
          dismiss();
          break;
		case SettingsItem.SETTING_ADJUST_SCALE:
		  activity.adjustScale();
          dismiss();
          break;
		case SettingsItem.SETTING_CHOOSE_CONTROLLER:
		  activity.chooseController();
          dismiss();
          break;
		case SettingsItem.SETTING_JOYSTICK_EMULATION:
		  activity.showJoystickSettings();
          dismiss();
          break;
		case SettingsItem.SETTING_CHANGE_DISC:
		  activity.changeDisc();
          dismiss();
          break;
		case SettingsItem.SETTING_SENSOR_EMULATION:
		  activity.showSensorSettings();
          dismiss();
          break;
		case SettingsItem.SETTING_EXIT_GAME:
		  activity.exitEmulation();
          dismiss();
          break;
      }
    }
  }

  public final class CheckBoxSettingViewHolder extends SettingViewHolder
    implements CompoundButton.OnCheckedChangeListener
  {
    SettingsItem mItem;
    private TextView mTextSettingName;
    private CheckBox mCheckbox;

    public CheckBoxSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mTextSettingName = root.findViewById(R.id.text_setting_name);
      mCheckbox = root.findViewById(R.id.checkbox);
      mCheckbox.setOnCheckedChangeListener(this);
    }

    @Override
    public void bind(SettingsItem item)
    {
      mItem = item;
      mTextSettingName.setText(item.getName());
      mCheckbox.setChecked(mItem.getValue() > 0);
    }

    @Override
    public void onClick(View clicked)
    {
      mCheckbox.toggle();
      mItem.setValue(mCheckbox.isChecked() ? 1 : 0);
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked)
    {
      mItem.setValue(isChecked ? 1 : 0);
    }
  }

  public final class RadioButtonSettingViewHolder extends SettingViewHolder
    implements RadioGroup.OnCheckedChangeListener
  {
    SettingsItem mItem;
    private TextView mTextSettingName;
    private RadioGroup mRadioGroup;

    public RadioButtonSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mTextSettingName = root.findViewById(R.id.text_setting_name);
      mRadioGroup = root.findViewById(R.id.radio_group);
      mRadioGroup.setOnCheckedChangeListener(this);
    }

    @Override
    public void bind(SettingsItem item)
    {
      int checkIds[] = {R.id.radio0, R.id.radio1, R.id.radio2};
      int index = item.getValue();
      if(index < 0 || index >= checkIds.length)
        index = 0;

      mItem = item;
      mTextSettingName.setText(item.getName());
      mRadioGroup.check(checkIds[index]);

      if(item.getSetting() == SettingsItem.SETTING_TOUCH_POINTER)
      {
        RadioButton radio0 = mRadioGroup.findViewById(R.id.radio0);
        radio0.setText(R.string.off);

        RadioButton radio1 = mRadioGroup.findViewById(R.id.radio1);
        radio1.setText(R.string.touch_ir_click);

        RadioButton radio2 = mRadioGroup.findViewById(R.id.radio2);
        radio2.setText(R.string.touch_ir_stick);
      }
    }

    @Override
    public void onClick(View clicked)
    {
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId)
    {
      switch (checkedId)
      {
        case R.id.radio0:
          mItem.setValue(0);
          break;
        case R.id.radio1:
          mItem.setValue(1);
          break;
        case R.id.radio2:
          mItem.setValue(2);
          break;
        default:
          mItem.setValue(0);
          break;
      }
    }
  }

  public final class SeekBarSettingViewHolder extends SettingViewHolder
  {
    SettingsItem mItem;
    private TextView mTextSettingName;
    private TextView mTextSettingValue;
    private SeekBar mSeekBar;

    public SeekBarSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mTextSettingName = root.findViewById(R.id.text_setting_name);
      mTextSettingValue = root.findViewById(R.id.text_setting_value);
      mSeekBar = root.findViewById(R.id.seekbar);
      mSeekBar.setProgress(99);
    }

    @Override
    public void bind(SettingsItem item)
    {
      mItem = item;
      mTextSettingName.setText(item.getName());
      switch (item.getSetting())
      {
        case SettingsItem.SETTING_OVERCLOCK_PERCENT:
          mSeekBar.setMax(300);
          break;
        case SettingsItem.SETTING_DISPLAY_SCALE:
          mSeekBar.setMax(200);
          break;
        case SettingsItem.SETTING_IR_PITCH:
        case SettingsItem.SETTING_IR_YAW:
        case SettingsItem.SETTING_IR_VERTICAL_OFFSET:
          mSeekBar.setMax(50);
          break;
        default:
          mSeekBar.setMax(10);
          break;
      }
      mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
      {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean b)
        {
          if (seekBar.getMax() > 99)
          {
            progress = (progress / 5) * 5;
            mTextSettingValue.setText(progress + "%");
          }
          else
          {
            mTextSettingValue.setText(String.valueOf(progress));
          }
          mItem.setValue(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar)
        {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar)
        {
        }
      });
      mSeekBar.setProgress(item.getValue());
    }

    @Override
    public void onClick(View clicked)
    {
    }
  }

  public class SettingsAdapter extends RecyclerView.Adapter<SettingViewHolder>
  {
    private int mRumble;
    private int mTouchPointer;
    private int mIRRecenter;
    private int mJoystickRelative;
    private int[] mRunningSettings;
    private ArrayList<SettingsItem> mSettings;

    public void loadMainMenu()
    {
	  EmulationActivity activity = (EmulationActivity)NativeLibrary.getEmulationContext();

      mSettings = new ArrayList<>();
      mSettings.add(new SettingsItem(SettingsItem.SETTING_LOAD_SUBMENU, R.string.preferences_settings, SettingsItem.TYPE_BUTTON, MENU_SETTINGS));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_TAKE_SCREENSHOT, R.string.emulation_screenshot, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_EDIT_BUTTONS, R.string.emulation_edit_layout, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_TOGGLE_BUTTONS, R.string.emulation_toggle_controls, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_QUICKSAVE, R.string.emulation_quicksave, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_QUICKLOAD, R.string.emulation_quickload, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_STATESAVES, R.string.state_saves, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_ADJUST_SCALE, R.string.emulation_control_scale, SettingsItem.TYPE_BUTTON, 0));
      if (!activity.isGameCubeGame())
      {
		mSettings.add(new SettingsItem(SettingsItem.SETTING_CHOOSE_CONTROLLER, R.string.emulation_choose_controller, SettingsItem.TYPE_BUTTON, 0));
        mSettings.add(new SettingsItem(SettingsItem.SETTING_JOYSTICK_EMULATION, R.string.emulation_joystick_settings, SettingsItem.TYPE_BUTTON, 0));
      }
      mSettings.add(new SettingsItem(SettingsItem.SETTING_CHANGE_DISC, R.string.emulation_change_disc, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SENSOR_EMULATION, R.string.emulation_sensor_settings, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_EXIT_GAME, R.string.emulation_exit, SettingsItem.TYPE_BUTTON, 0));
      notifyDataSetChanged();
    }

    public void loadSettingsMenu()
    {
      int i = 0;
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      mRunningSettings = NativeLibrary.getRunningSettings();
      mSettings = new ArrayList<>();

      mRumble = prefs.getBoolean(EmulationActivity.RUMBLE_PREF_KEY, true) ? 1 : 0;
      mSettings.add(new SettingsItem(SettingsItem.SETTING_PHONE_RUMBLE, R.string.emulation_control_rumble,
        SettingsItem.TYPE_CHECKBOX, mRumble));

      if (!EmulationActivity.get().isGameCubeGame())
      {
        mTouchPointer = prefs.getInt(InputOverlay.POINTER_PREF_KEY, 0);
        mSettings.add(new SettingsItem(SettingsItem.SETTING_TOUCH_POINTER,
          R.string.touch_screen_pointer, SettingsItem.TYPE_RADIO_GROUP, mTouchPointer));

        String gameId = EmulationActivity.get().getSelectedGameId();
        String prefId = gameId.length() > 3 ? gameId.substring(0, 3) : gameId;
        mIRRecenter = prefs.getBoolean(InputOverlay.RECENTER_PREF_KEY + "_" + prefId, false) ? 1 : 0;
        mSettings.add(new SettingsItem(SettingsItem.SETTING_TOUCH_POINTER_RECENTER,
          R.string.touch_screen_pointer_recenter, SettingsItem.TYPE_CHECKBOX, mIRRecenter));
      }

      mJoystickRelative = prefs.getBoolean(InputOverlay.RELATIVE_PREF_KEY, true) ? 1 : 0;
      mSettings.add(new SettingsItem(SettingsItem.SETTING_JOYSTICK_RELATIVE,
        R.string.joystick_relative_center, SettingsItem.TYPE_CHECKBOX, mJoystickRelative));

      // gfx
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SHOW_FPS, R.string.show_fps,
        SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SKIP_EFB,
        R.string.skip_efb_access, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_EFB_TEXTURE, R.string.efb_copy_method,
        SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_VI_SKIP, R.string.vi_skip,
        SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_IGNORE_FORMAT,
        R.string.ignore_format_changes, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_ARBITRARY_MIPMAP_DETECTION,
        R.string.arbitrary_mipmap_detection, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_IMMEDIATE_XFB,
        R.string.immediate_xfb, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_DISPLAY_SCALE,
        R.string.setting_display_scale, SettingsItem.TYPE_SEEK_BAR, mRunningSettings[i++]));

      // core
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SYNC_ON_SKIP_IDLE,
        R.string.sync_on_skip_idle, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_OVERCLOCK_ENABLE,
        R.string.overclock_enable, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_OVERCLOCK_PERCENT,
        R.string.overclock_title, SettingsItem.TYPE_SEEK_BAR, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_JIT_FOLLOW_BRANCH,
        R.string.jit_follow_branch, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));

      if (!EmulationActivity.get().isGameCubeGame())
      {
        mSettings.add(new SettingsItem(SettingsItem.SETTING_IR_PITCH,
          R.string.pitch, SettingsItem.TYPE_SEEK_BAR, mRunningSettings[i++]));
        mSettings.add(new SettingsItem(SettingsItem.SETTING_IR_YAW,
          R.string.yaw, SettingsItem.TYPE_SEEK_BAR, mRunningSettings[i++]));
        mSettings.add(new SettingsItem(SettingsItem.SETTING_IR_VERTICAL_OFFSET,
          R.string.vertical_offset, SettingsItem.TYPE_SEEK_BAR, mRunningSettings[i++]));
      }
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SettingViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
      View itemView;
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      switch (viewType)
      {
        case SettingsItem.TYPE_CHECKBOX:
          itemView = inflater.inflate(R.layout.list_item_running_checkbox, parent, false);
          return new CheckBoxSettingViewHolder(itemView);
        case SettingsItem.TYPE_RADIO_GROUP:
          itemView = inflater.inflate(R.layout.list_item_running_radio3, parent, false);
          return new RadioButtonSettingViewHolder(itemView);
        case SettingsItem.TYPE_SEEK_BAR:
          itemView = inflater.inflate(R.layout.list_item_running_seekbar, parent, false);
          return new SeekBarSettingViewHolder(itemView);
		case SettingsItem.TYPE_BUTTON:
          itemView = inflater.inflate(R.layout.list_item_running_button, parent, false);
          return new ButtonSettingViewHolder(itemView);
      }
      return null;
    }

    @Override
    public int getItemCount()
    {
      return mSettings.size();
    }

    @Override
    public int getItemViewType(int position)
    {
      return mSettings.get(position).getType();
    }

    @Override
    public void onBindViewHolder(@NonNull SettingViewHolder holder, int position)
    {
      holder.bind(mSettings.get(position));
    }

    public void saveSettings()
    {
	  // don't constantly save settings
      if (mRunningSettings == null)
      {
		return;
      }

      // prefs
      SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

      int rumble = mSettings.get(0).getValue();
      if (mRumble != rumble)
      {
        editor.putBoolean(EmulationActivity.RUMBLE_PREF_KEY, rumble > 0);
        Rumble.setPhoneRumble(getActivity(), rumble > 0);
      }
      mSettings.remove(0);

      if (!EmulationActivity.get().isGameCubeGame())
      {
        int pointer = mSettings.get(0).getValue();
        if(mTouchPointer != pointer)
        {
          editor.putInt(InputOverlay.POINTER_PREF_KEY, pointer);
          EmulationActivity.get().setTouchPointer(pointer);
        }
        mSettings.remove(0);

        int recenter = mSettings.get(0).getValue();
        if(mIRRecenter != recenter)
        {
          String gameId = EmulationActivity.get().getSelectedGameId();
          String prefId = gameId.length() > 3 ? gameId.substring(0, 3) : gameId;
          editor.putBoolean(InputOverlay.RECENTER_PREF_KEY + "_" + prefId, recenter > 0);
          InputOverlay.sIRRecenter = recenter > 0;
        }
        mSettings.remove(0);
      }

      int relative = mSettings.get(0).getValue();
      if (mJoystickRelative != relative)
      {
        editor.putBoolean(InputOverlay.RELATIVE_PREF_KEY, relative > 0);
        InputOverlay.sJoystickRelative = relative > 0;
      }
      mSettings.remove(0);

      editor.apply();

      // settings
      boolean isChanged = false;
      int[] newSettings = new int[mRunningSettings.length];
      for (int i = 0; i < mRunningSettings.length; ++i)
      {
        newSettings[i] = mSettings.get(i).getValue();
        if (newSettings[i] != mRunningSettings[i])
        {
          isChanged = true;
        }
      }
      // only apply if user changed settings
      if (isChanged)
      {
        NativeLibrary.setRunningSettings(newSettings);
        // update display scale
        EmulationActivity.get().updateTouchPointer();
      }
    }
  }

  public static RunningSettingDialog newInstance()
  {
    return new RunningSettingDialog();
  }

  private int mMenu;
  private TextView mTitle;
  private TextView mInfo;
  private Handler mHandler;
  private SettingsAdapter mAdapter;
  private DialogInterface.OnDismissListener mDismissListener;

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    ViewGroup contents = (ViewGroup) getActivity().getLayoutInflater()
      .inflate(R.layout.dialog_running_settings, null);

    mTitle = contents.findViewById(R.id.text_title);
    mInfo = contents.findViewById(R.id.text_info);
    mHandler = new Handler(getActivity().getMainLooper());
    setHeapInfo();

    int columns = 1;
    RecyclerView recyclerView = contents.findViewById(R.id.list_settings);
    RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), columns);
    recyclerView.setLayoutManager(layoutManager);
    mAdapter = new SettingsAdapter();
    recyclerView.setAdapter(mAdapter);
    recyclerView.addItemDecoration(new DividerItemDecoration(requireActivity(), null));
    builder.setView(contents);
    loadSubMenu(MENU_MAIN);
    return builder.create();
  }

	// display ram usage
	public void setHeapInfo()
	{
	  long heapsize = Debug.getNativeHeapAllocatedSize() >> 20;
      mInfo.setText(String.format("%dMB", heapsize));
      mHandler.postDelayed(this::setHeapInfo, 1000);
	}

	public void setOnDismissListener(DialogInterface.OnDismissListener listener)
	{
	  mDismissListener = listener;
	}

  @Override
  public void onDismiss(DialogInterface dialog)
  {
    super.onDismiss(dialog);
    if (mMenu == MENU_SETTINGS)
    {
	  mAdapter.saveSettings();
    }
    if (mDismissListener != null)
    {
	  mDismissListener.onDismiss(dialog);
    }
    mHandler.removeCallbacksAndMessages(null);
  }

	private void loadSubMenu(int menu)
	{
	  if (menu == MENU_MAIN)
	  {
	    EmulationActivity activity = (EmulationActivity)NativeLibrary.getEmulationContext();
        mTitle.setText(activity.getTitle());
        mAdapter.loadMainMenu();
      }
      else if (menu == MENU_SETTINGS)
      {
        mTitle.setText(R.string.preferences_settings);
        mAdapter.loadSettingsMenu();
      }
      mMenu = menu;
	}
}
