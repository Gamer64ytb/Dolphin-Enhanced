package org.dolphinemu.dolphinemu.features.settings.ui.viewholder;

import android.view.View;
import android.widget.TextView;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter;

public final class InputBindingSettingViewHolder extends SettingViewHolder
{
  private InputBindingSetting mItem;

  private TextView mTextSettingName;
  private TextView mTextSettingDescription;

  public InputBindingSettingViewHolder(View itemView, SettingsAdapter adapter)
  {
    super(itemView, adapter);
  }

  @Override
  protected void findViews(View root)
  {
    mTextSettingName = root.findViewById(R.id.text_setting_name);
    mTextSettingDescription = root.findViewById(R.id.text_setting_description);
  }

  @Override
  public void bind(SettingsItem item)
  {
    mItem = (InputBindingSetting) item;
    mTextSettingName.setText(mItem.getNameId());
    mTextSettingDescription.setText(mItem.getSettingText());
  }

  @Override
  public void onClick(View clicked)
  {
    getAdapter().onInputBindingClick(mItem, getAdapterPosition());
  }
}
