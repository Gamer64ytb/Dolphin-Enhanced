package org.dolphinemu.dolphinemu.adapters;

import android.content.Context;

import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.dolphinemu.dolphinemu.ui.platform.Platform;
import org.dolphinemu.dolphinemu.ui.platform.PlatformGamesFragment;

public class PlatformPagerAdapter extends FragmentPagerAdapter
{
	private Context mContext;

	private final static String[] TAB_NAMES = {"NGC", "Wii", "Ware"};

	public PlatformPagerAdapter(FragmentManager fm, Context context)
	{
		super(fm);
		mContext = context;
	}

	@Override
	public Fragment getItem(int position)
	{
		return PlatformGamesFragment.newInstance(Platform.fromPosition(position));
	}

	@Override
	public int getCount()
	{
		return TAB_NAMES.length;
	}

	@Override
	public CharSequence getPageTitle(int position)
	{
		return TAB_NAMES[position];
	}
}
