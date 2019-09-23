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

package de.badaix.snapcast;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;

public class AboutActivity extends AppCompatActivity {
    private static final String TAG = "About";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        try {
            getSupportActionBar().setTitle(getString(R.string.about) + " Snapcast");
        } catch (Exception e) {
            Log.wtf(TAG, "Setting title", e);
        }
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            getSupportActionBar().setSubtitle("v" + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.wtf(TAG, "Setting subtitle", e);
        }
        WebView wv = (WebView) findViewById(R.id.webView);
        wv.loadUrl("file:///android_asset/" + this.getText(R.string.about_file));
    }
}
