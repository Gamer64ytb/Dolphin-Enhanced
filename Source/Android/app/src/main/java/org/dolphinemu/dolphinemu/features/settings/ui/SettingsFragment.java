package org.dolphinemu.dolphinemu.features.settings.ui;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.dolphinemu.dolphinemu.ui.DividerItemDecoration;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class SettingsFragment extends Fragment implements SettingsFragmentView
{
  private static final String ARGUMENT_MENU_TAG = "menu_tag";
  private static final String ARGUMENT_GAME_ID = "game_id";

  private SettingsFragmentPresenter mPresenter;
  private ArrayList<SettingsItem> mSettingsList;
  private SettingsActivity mActivity;
  private SettingsAdapter mAdapter;
	private static final Map<MenuTag, Integer> titles = new HashMap<>();

	static
	{
		// if you added a new submenu section, add the title for that here.
		titles.put(MenuTag.CONFIG, R.string.preferences_settings);
		titles.put(MenuTag.CONFIG_GENERAL, R.string.general_submenu);
		titles.put(MenuTag.CONFIG_INTERFACE, R.string.interface_submenu);
		titles.put(MenuTag.CONFIG_GAME_CUBE, R.string.gamecube_submenu);
		titles.put(MenuTag.CONFIG_WII, R.string.wii_submenu);
		titles.put(MenuTag.WIIMOTE, R.string.grid_menu_wiimote_settings);
		titles.put(MenuTag.WIIMOTE_EXTENSION, R.string.wiimote_extensions);
		titles.put(MenuTag.GCPAD_TYPE, R.string.grid_menu_gcpad_settings);
		titles.put(MenuTag.GRAPHICS, R.string.grid_menu_graphics_settings);
		titles.put(MenuTag.HACKS, R.string.hacks_submenu);
		titles.put(MenuTag.DEBUG, R.string.debug_submenu);
		titles.put(MenuTag.ENHANCEMENTS, R.string.enhancements_submenu);
		titles.put(MenuTag.GCPAD_1, R.string.controller_0);
		titles.put(MenuTag.GCPAD_2, R.string.controller_1);
		titles.put(MenuTag.GCPAD_3, R.string.controller_2);
		titles.put(MenuTag.GCPAD_4, R.string.controller_3);
		titles.put(MenuTag.WIIMOTE_1, R.string.wiimote_4);
		titles.put(MenuTag.WIIMOTE_2, R.string.wiimote_5);
		titles.put(MenuTag.WIIMOTE_3, R.string.wiimote_6);
		titles.put(MenuTag.WIIMOTE_4, R.string.wiimote_7);
		titles.put(MenuTag.WIIMOTE_EXTENSION_1, R.string.wiimote_extension_4);
		titles.put(MenuTag.WIIMOTE_EXTENSION_2, R.string.wiimote_extension_5);
		titles.put(MenuTag.WIIMOTE_EXTENSION_3, R.string.wiimote_extension_6);
		titles.put(MenuTag.WIIMOTE_EXTENSION_4, R.string.wiimote_extension_7);
	}

  public static Fragment newInstance(MenuTag menuTag, String gameId, Bundle extras)
  {
    SettingsFragment fragment = new SettingsFragment();

    Bundle arguments = new Bundle();
    if (extras != null)
    {
      arguments.putAll(extras);
    }

    arguments.putSerializable(ARGUMENT_MENU_TAG, menuTag);
    arguments.putString(ARGUMENT_GAME_ID, gameId);

    fragment.setArguments(arguments);
    return fragment;
  }

  @Override
  public void onAttach(Context context)
  {
    super.onAttach(context);

    mActivity = (SettingsActivity) context;
    if(mPresenter == null)
      mPresenter = new SettingsFragmentPresenter(mActivity);
    mPresenter.onAttach();
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setRetainInstance(true);
    Bundle args = getArguments();
    MenuTag menuTag = (MenuTag) args.getSerializable(ARGUMENT_MENU_TAG);
    String gameId = getArguments().getString(ARGUMENT_GAME_ID);

    mAdapter = new SettingsAdapter(mActivity);
    mPresenter.onCreate(menuTag, gameId, args);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_settings, container, false);
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState)
  {
		Bundle args = getArguments();
		MenuTag menuTag = (MenuTag) args.getSerializable(ARGUMENT_MENU_TAG);

		if (titles.containsKey(menuTag))
		{
			getActivity().setTitle(titles.get(menuTag));
		}

    //LinearLayoutManager manager = new LinearLayoutManager(mActivity);
    RecyclerView recyclerView = view.findViewById(R.id.list_settings);

    GridLayoutManager mgr = new GridLayoutManager(mActivity, 2);
    mgr.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup()
    {
      @Override public int getSpanSize(int position)
      {
        int viewType = mAdapter.getItemViewType(position);
        if (SettingsItem.TYPE_INPUT_BINDING == viewType &&
          Settings.SECTION_BINDINGS.equals(mAdapter.getSettingSection(position)))
          return 1;
        return 2;
      }
    });

    recyclerView.setAdapter(mAdapter);
    recyclerView.setLayoutManager(mgr);
    recyclerView.addItemDecoration(new DividerItemDecoration(requireActivity(), null));

    showSettingsList(mActivity.getSettings());
  }

  @Override
  public void onDetach()
  {
    super.onDetach();
    mActivity = null;

    if (mAdapter != null)
    {
      mAdapter.closeDialog();
    }
  }

  public void showSettingsList(Settings settings)
  {
    if(mSettingsList == null && settings != null)
    {
      mSettingsList = mPresenter.loadSettingsList(settings);
    }

    if(mSettingsList != null)
    {
      mAdapter.setSettings(mSettingsList);
    }
  }

  public void closeDialog()
  {
    mAdapter.closeDialog();
  }
}
