/*
 *     This file is part of snapcast
 *     Copyright (C) 2014-2018  Johannes Pohl
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.badaix.snapcast.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by johannes on 21.02.16.
 */
public class Settings {
    private final Context ctx;

    public Settings(Context context) {
        this.ctx = context;
    }

    private SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public Settings put(String key, String value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        editor.apply();
        return this;
    }

    public Settings put(String key, boolean value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putBoolean(key, value);
        editor.apply();
        return this;
    }

    public Settings put(String key, int value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(key, value);
        editor.apply();
        return this;
    }

    public String getString(String key, String defaultValue) {
        return getPrefs().getString(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getPrefs().getBoolean(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return getPrefs().getInt(key, defaultValue);
    }

    public String getHost() {
        return getString("host", "");
    }

    public int getStreamPort() {
        return getInt("streamPort", 1704);
    }

    public int getControlPort() {
        return getInt("controlPort", getStreamPort() + 1);
    }

    public boolean isAutostart() {
        return getBoolean("autoStart", false);
    }

    public void setAutostart(boolean autoStart) {
        put("autoStart", autoStart);
    }

    public void setHost(String host, int streamPort, int controlPort) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString("host", host);
        editor.putInt("streamPort", streamPort);
        editor.putInt("controlPort", controlPort);
        editor.apply();
    }

    public String getSpotifyUsername() {
        return getString("spotifyUsername", "");
    }

    public String getSpotifyPassword() {
        return getString("spotifyPassword", "");
    }

    public void setSpotifyCredentials(String username, String password) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString("spotifyUsername", username);
        editor.putString("spotifyPassword", password);
        editor.apply();
    }
}
