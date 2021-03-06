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

package de.badaix.snapcast.control.json;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by johannes on 06.01.16.
 */
public class Volume implements JsonSerialisable {
    private static final String TAG = "Volume";
    private boolean muted;
    private int percent = 100;

    public Volume(JSONObject json) {
        fromJson(json);
    }

    public Volume() {

    }

    public Volume(int percent, boolean muted) {
        this.percent = percent;
        this.muted = muted;
    }

    @Override
    public void fromJson(JSONObject json) {
        try {
            percent = json.getInt("percent");
            muted = json.getBoolean("muted");
        } catch (JSONException e) {
            Log.wtf(TAG, "Getting from JSON", e);
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("percent", percent);
            json.put("muted", muted);
        } catch (JSONException e) {
            Log.wtf(TAG, "Saving to JSON", e);
        }
        return json;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Volume volume = (Volume) o;

        if (muted != volume.muted) return false;
        return percent == volume.percent;

    }

    @Override
    public int hashCode() {
        int result = (muted ? 1 : 0);
        result = 31 * result + percent;
        return result;
    }
}
