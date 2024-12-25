package org.dolphinemu.dolphinemu.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.ConvertActivity;
import org.dolphinemu.dolphinemu.activities.EditorActivity;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity;
import org.dolphinemu.dolphinemu.model.GameFile;
import org.dolphinemu.dolphinemu.services.GameFileCacheService;
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization;

import java.io.File;

// GameBannerRequestHandler is only used there, so let's join it into one file
class GameBannerRequestHandler extends RequestHandler
{
  private final GameFile mGameFile;

  public GameBannerRequestHandler(GameFile gameFile)
  {
    mGameFile = gameFile;
  }

  @Override
  public boolean canHandleRequest(Request data)
  {
    return true;
  }

  @Override
  public Result load(Request request, int networkPolicy)
  {
    int[] vector = mGameFile.getBanner();
    int width = mGameFile.getBannerWidth();
    int height = mGameFile.getBannerHeight();
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(vector, 0, width, 0, 0, width, height);
    return new Result(bitmap, Picasso.LoadedFrom.DISK);
  }
}

public final class GameDetailsDialog extends DialogFragment
{
  private static final String ARG_GAME_PATH = "game_path";

  private String mGameId;

  private static void loadGameBanner(ImageView imageView, GameFile gameFile)
  {
    Picasso picassoInstance = new Picasso.Builder(imageView.getContext())
            .addRequestHandler(new GameBannerRequestHandler(gameFile))
            .build();

    picassoInstance
            .load(Uri.parse("file://" + gameFile.getCoverPath(imageView.getContext())))
            .fit()
            .noFade()
            .noPlaceholder()
            .config(Bitmap.Config.RGB_565)
            .error(R.drawable.no_banner)
            .into(imageView);
  }

  public static GameDetailsDialog newInstance(String gamePath)
  {
    GameDetailsDialog fragment = new GameDetailsDialog();

    Bundle arguments = new Bundle();
    arguments.putString(ARG_GAME_PATH, gamePath);
    fragment.setArguments(arguments);

    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    final GameFile gameFile =
      GameFileCacheService.addOrGet(getArguments().getString(ARG_GAME_PATH));

    mGameId = gameFile.getGameId();

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    ViewGroup contents = (ViewGroup) getActivity().getLayoutInflater()
      .inflate(R.layout.dialog_game_details, null);

    // game title
    TextView textGameTitle = contents.findViewById(R.id.text_game_title);
    textGameTitle.setText(gameFile.getTitle());

    // game filename
    String gameId = gameFile.getGameId();
    if (gameFile.getPlatform() > 0)
    {
      gameId += ", " + gameFile.getTitlePath();
    }
    TextView textGameFilename = contents.findViewById(R.id.text_game_filename);
    textGameFilename.setText(gameId);

    //
    Button buttonConvert = contents.findViewById(R.id.button_convert);
    buttonConvert.setOnClickListener(view ->
    {
      this.dismiss();
      ConvertActivity.launch(getContext(), gameFile.getPath());
    });
    buttonConvert.setEnabled(gameFile.shouldAllowConversion());

    Button buttonDeleteSetting = contents.findViewById(R.id.button_delete_setting);
    buttonDeleteSetting.setOnClickListener(view ->
    {
      this.dismiss();
      this.deleteGameSetting(getContext());
    });
    buttonDeleteSetting.setEnabled(gameSettingExists());

    Button buttonCheatCode = contents.findViewById(R.id.button_cheat_code);
    buttonCheatCode.setOnClickListener(view ->
    {
      this.dismiss();
      EditorActivity.launch(getContext(), gameFile.getPath());
    });

    //
    Button buttonWiimote = contents.findViewById(R.id.button_wiimote_settings);
    buttonWiimote.setOnClickListener(view ->
    {
      this.dismiss();
      SettingsActivity.launch(getContext(), MenuTag.WIIMOTE, gameFile.getGameId());
    });
    buttonWiimote.setEnabled(gameFile.getPlatform() > 0);

    Button buttonGCPad = contents.findViewById(R.id.button_gcpad_settings);
    buttonGCPad.setOnClickListener(view ->
    {
      this.dismiss();
      SettingsActivity.launch(getContext(), MenuTag.GCPAD_TYPE, gameFile.getGameId());
    });

    //
    Button buttonGameSetting = contents.findViewById(R.id.button_game_setting);
    buttonGameSetting.setOnClickListener(view ->
    {
      this.dismiss();
      SettingsActivity.launch(getContext(), MenuTag.CONFIG, gameFile.getGameId());
    });

    Button buttonLaunch = contents.findViewById(R.id.button_quick_load);
    buttonLaunch.setOnClickListener(view ->
    {
      this.dismiss();
      EmulationActivity.launch(getContext(), gameFile, gameFile.getLastSavedState());
    });

    ImageView imageGameScreen = contents.findViewById(R.id.image_game_screen);
    loadGameBanner(imageGameScreen, gameFile);

    builder.setView(contents);
    return builder.create();
  }

  private boolean gameSettingExists()
  {
    String path = DirectoryInitialization.getLocalSettingFile(mGameId);
    File gameSettingsFile = new File(path);
    return gameSettingsFile.exists();
  }

  private void deleteGameSetting(Context context)
  {
    String path = DirectoryInitialization.getLocalSettingFile(mGameId);
    File gameSettingsFile = new File(path);
    if (gameSettingsFile.exists())
    {
      if (gameSettingsFile.delete())
      {
        Toast.makeText(context, "Cleared settings for " + mGameId, Toast.LENGTH_SHORT).show();
      }
      else
      {
        Toast.makeText(context, "Unable to clear settings for " + mGameId, Toast.LENGTH_SHORT)
          .show();
      }
    }
    else
    {
      Toast.makeText(context, "No game settings to delete", Toast.LENGTH_SHORT).show();
    }
  }
}
