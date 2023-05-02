package org.dolphinemu.dolphinemu.ui.platform;

/**
 * Abstraction for a screen representing a single platform's games.
 */
public interface PlatformGamesView
{
	/**
	 * To be called when the game file cache is updated.
	 */
	void showGames();
}
