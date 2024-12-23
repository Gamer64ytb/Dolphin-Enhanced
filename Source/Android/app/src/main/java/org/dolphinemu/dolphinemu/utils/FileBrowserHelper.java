package org.dolphinemu.dolphinemu.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.dolphinemu.dolphinemu.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FileBrowserHelper
{
  public static final HashSet<String> GAME_EXTENSIONS = new HashSet<>(Arrays.asList(
          "gcm", "tgc", "iso", "ciso", "gcz", "wbfs", "wia", "rvz", "wad", "dol", "elf"));

  public static final HashSet<String> GAME_LIKE_EXTENSIONS = new HashSet<>(GAME_EXTENSIONS);

  static
  {
    GAME_LIKE_EXTENSIONS.add("dff");
  }

  public static void runAfterExtensionCheck(Context context, Uri uri, Set<String> validExtensions,
                                            Runnable runnable)
  {
    String extension = null;

    String path = uri.getLastPathSegment();
    if (path != null)
      extension = getExtension(new File(path).getName(), false);

    if (extension == null)
      extension = getExtension(ContentHandler.getDisplayName(uri), false);

    if (extension != null && validExtensions.contains(extension))
    {
      runnable.run();
      return;
    }

    String message;
    if (extension == null)
    {
      message = context.getString(R.string.no_file_extension);
    }
    else
    {
      int messageId = validExtensions.size() == 1 ?
              R.string.wrong_file_extension_single : R.string.wrong_file_extension_multiple;

      message = context.getString(messageId, extension,
              setToSortedDelimitedString(validExtensions));
    }

    new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> runnable.run())
            .setNegativeButton(android.R.string.no, null)
            .setCancelable(false)
            .show();
  }

  @Nullable
  public static String getExtension(@Nullable String fileName, boolean includeDot)
  {
    if (fileName == null)
      return null;

    int dotIndex = fileName.lastIndexOf(".");
    if (dotIndex == -1)
      return null;
    return fileName.substring(dotIndex + (includeDot ? 0 : 1));
  }

  public static String setToSortedDelimitedString(Set<String> set)
  {
    ArrayList<String> list = new ArrayList<>(set);
    Collections.sort(list);
    return join(", ", list);
  }

  // TODO: Replace this with String.join once we can use Java 8
  private static String join(CharSequence delimiter, Iterable<? extends CharSequence> elements)
  {
    StringBuilder sb = new StringBuilder();

    boolean first = true;
    for (CharSequence element : elements)
    {
      if (!first)
        sb.append(delimiter);
      first = false;
      sb.append(element);
    }

    return sb.toString();
  }
}
