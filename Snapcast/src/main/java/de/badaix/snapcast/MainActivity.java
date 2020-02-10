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

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import de.badaix.snapcast.control.RemoteControl;
import de.badaix.snapcast.control.json.Client;
import de.badaix.snapcast.control.json.Group;
import de.badaix.snapcast.control.json.ServerStatus;
import de.badaix.snapcast.control.json.Stream;
import de.badaix.snapcast.control.json.Volume;
import de.badaix.snapcast.utils.NsdHelper;
import de.badaix.snapcast.utils.Settings;
import uk.org.jaggard.snapcast.AdDetails;

public class MainActivity extends AppCompatActivity implements GroupItem.GroupItemListener, RemoteControl.RemoteControlListener, SnapService.LogListener, NsdHelper.NsdHelperListener {

    private static final int CLIENT_PROPERTIES_REQUEST = 1;
    private static final int GROUP_PROPERTIES_REQUEST = 2;
    public static final String TAG = "Main";
    private static final String SERVICE_NAME = "Snapcast";// #2";
    private boolean clientBound;
    private MenuItem miClientStartStop;
    private boolean serverBound;
    private MenuItem miServerStartStop;
    private MenuItem miSettings;
    private String host = "";
    private int port = 1704;
    private int controlPort = 1705;
    private String spotifyUsername = "";
    private String spotifyPassword = "";
    private RemoteControl remoteControl;
    private ServerStatus serverStatus;
    private SnapClientService snapClientService;
    private SnapServerService snapServerService;
    private GroupListFragment groupListFragment;
    private Snackbar warningSamplerateSnackbar;
    private int nativeSampleRate;
    private CoordinatorLayout coordinatorLayout;
    private Button btnConnect;
    private boolean batchActive;
    private final NsdHelper nsdHelper = new NsdHelper();

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection clientConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've clientBound to LocalService, cast the IBinder and get LocalService instance
            SnapService.LocalBinder binder = (SnapService.LocalBinder) service;
            snapClientService = (SnapClientService) binder.getService();
            snapClientService.setLogListener(MainActivity.this);
            snapClientService.setStartListener(MainActivity.this::onPlayerStart);
            snapClientService.setStopListener(MainActivity.this::onPlayerStop);
            clientBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            clientBound = false;
        }
    };

    private ServiceConnection serverConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've clientBound to LocalService, cast the IBinder and get LocalService instance
            SnapService.LocalBinder binder = (SnapService.LocalBinder) service;
            snapServerService = (SnapServerService) binder.getService();
            snapServerService.setLogListener(MainActivity.this);
            snapServerService.setStartListener(MainActivity.this::onServerStart);
            snapServerService.setStopListener(MainActivity.this::onServerStop);
            serverBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serverBound = false;
        }
    };
    private final AtomicInteger backgroundProcesses = new AtomicInteger();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MobileAds.initialize(this, AdDetails.APP_ID);

        setContentView(R.layout.activity_main);

        for (int rate : new int[]{8000, 11025, 16000, 22050, 44100, 48000}) {  // add the rates you wish to check against
            Log.d(TAG, "Samplerate: " + rate);
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                Log.d(TAG, "Samplerate: " + rate + ", buffer: " + bufferSize);
            }
        }

        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            String rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            nativeSampleRate = Integer.valueOf(rate);
//            String size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
//            tvInfo.setText("Sample rate: " + rate + ", buffer size: " + size);
        }

        Toolbar serverToolbar = (Toolbar) findViewById(R.id.server_toolbar);
        serverToolbar.setTitle(R.string.local_server);
        serverToolbar.inflateMenu(R.menu.menu_snapcast_server);
        serverToolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);
        miServerStartStop = serverToolbar.getMenu().findItem(R.id.action_server_start_stop);

        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.myCoordinatorLayout);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnConnect.setVisibility(View.GONE);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.

        groupListFragment = (GroupListFragment) getSupportFragmentManager().findFragmentById(R.id.groupListFragment);
        groupListFragment.setHideOffline(new Settings(this).getBoolean("hide_offline", false));

        setActionbarSubtitle("Host: no Snapserver found");

        final RelativeLayout adContainer = findViewById(R.id.adView);
        AdView mAdView = new AdView(this);
        mAdView.setAdSize(AdSize.LARGE_BANNER);
        mAdView.setAdUnitId(AdDetails.AD_UNIT_ID);
        adContainer.addView(mAdView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_snapcast, menu);
        miClientStartStop = menu.findItem(R.id.action_play_stop);
        miSettings = menu.findItem(R.id.action_settings);
//        miRefresh = menu.findItem(R.id.action_refresh);
        updateStartStopMenuItem();
        boolean isChecked = new Settings(this).getBoolean("hide_offline", false);
        MenuItem menuItem = menu.findItem(R.id.action_hide_offline);
        menuItem.setChecked(isChecked);

        if (remoteControl != null) {
            updateMenuItems(remoteControl.isConnected());
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            ServerDialogFragment serverDialogFragment = new ServerDialogFragment();
            final Settings settings = new Settings(this);
            serverDialogFragment.setHost(settings.getHost(), settings.getStreamPort(), settings.getControlPort());
            serverDialogFragment.setAutoStart(new Settings(this).isAutostart());
            serverDialogFragment.setListener(new ServerDialogFragment.ServerDialogListener() {
                @Override
                public void onHostChanged(String host, int streamPort, int controlPort) {
                    setHost(host, streamPort, controlPort);
                    startRemoteControl();
                }

                @Override
                public void onAutoStartChanged(boolean autoStart) {
                    settings.setAutostart(autoStart);
                }
            });
            serverDialogFragment.show(getSupportFragmentManager(), "serverDialogFragment");
//            NsdHelper.getInstance(this).startListening("_snapcast._tcp.", SERVICE_NAME, this);
            return true;
        } else if (id == R.id.action_local_server_settings) {
            openLocalServerSettings();
            return true;
        } else if (id == R.id.action_play_stop) {
            if (clientBound && snapClientService.isRunning()) {
                stopSnapClient();
            } else {
                item.setEnabled(false); //While starting
                startSnapClient();
            }
            return true;
        } else if (id == R.id.action_server_start_stop) {
            if (serverBound && snapServerService.isRunning()) {
                stopSnapServer();
            } else {
                item.setEnabled(false); //While starting
                if (!startSnapServer()) {
                    item.setEnabled(true); //Enable again as start button if it didn't start.
                }
            }
            return true;
        } else if (id == R.id.action_hide_offline) {
            item.setChecked(!item.isChecked());
            new Settings(this).put("hide_offline", item.isChecked());
            groupListFragment.setHideOffline(item.isChecked());
            return true;
        } else if (id == R.id.action_refresh) {
            startRemoteControl();
            remoteControl.getServerStatus();
        } else if (id == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void openLocalServerSettings() {
        LocalServerSettingsDialogFragment serverDialogFragment = new LocalServerSettingsDialogFragment();
        serverDialogFragment.setSpotifyCredentials(spotifyUsername, spotifyPassword);
        serverDialogFragment.setSpotifyCredentialsListener((username, password) -> setSpotifyCredentials(username, password));
        serverDialogFragment.show(getSupportFragmentManager(), "localServerDialogFragment");
    }

    private void setSpotifyCredentials(String username, String password) {
        this.spotifyUsername = username;
        this.spotifyPassword = password;
        new Settings(this).setSpotifyCredentials(username, password);
    }

    private void updateStartStopMenuItem() {
        MainActivity.this.runOnUiThread(() -> {

            if (clientBound && snapClientService.isRunning()) {
                Log.d(TAG, "updateStartStopMenuItem: ic_media_stop");
                miClientStartStop.setIcon(R.drawable.ic_media_stop);
            } else {
                Log.d(TAG, "updateStartStopMenuItem: ic_media_play");
                miClientStartStop.setIcon(R.drawable.ic_media_play);
            }
            miClientStartStop.setEnabled(true);

            if (serverBound && snapServerService.isRunning()) {
                Log.d(TAG, "updateStartStopMenuItem: server ic_media_stop");
                miServerStartStop.setIcon(R.drawable.ic_media_stop);
                miServerStartStop.setTitle(R.string.action_server_stop);
            } else {
                Log.d(TAG, "updateStartStopMenuItem: server ic_media_play");
                miServerStartStop.setIcon(R.drawable.ic_media_play);
                miServerStartStop.setTitle(R.string.action_server_start);
            }
            miServerStartStop.setEnabled(true);
        });
    }

    private boolean startSnapServer() {
        if (TextUtils.isEmpty(spotifyUsername) || TextUtils.isEmpty(spotifyPassword)) {
            openLocalServerSettings();
            return false;
        }

        Intent i = new Intent(this, SnapServerService.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(SnapServerService.SPOTIFY_USERNAME, spotifyUsername);
        i.putExtra(SnapServerService.SPOTIFY_PASSWORD, spotifyPassword);
        i.setAction(SnapService.ACTION_START);

        addBackgroundProcess();
        startService(i);

        if (clientUsingLocalhost() || clientIsEmpty()) {
            //If it is locvalhost already we just start the remote control and this is the easiest method.
            changeClientToLocalhost(null, 0);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.change_client_title)
                    .setMessage(R.string.change_client_text)
                    .setPositiveButton(android.R.string.ok, this::changeClientToLocalhost)
                    .setCancelable(true)
                    .show();
        }

        return true;
    }

    private boolean clientUsingLocalhost() {
        return host != null && host.equals("localhost");
    }

    private boolean clientIsEmpty() {
        return TextUtils.isEmpty(host);
    }

    private void changeClientToLocalhost(DialogInterface ignore1, int ignore2) {
        setHost("localhost", 1704, 1705);
        new Thread(() -> {
            //Give the local server a second to start before we try to connect to it.
            //TODO: Handle this async properly by waiting for the server to start.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
            }
            startRemoteControl();
        }).start();
    }

    private void addBackgroundProcess() {
        backgroundProcesses.incrementAndGet();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startSnapClient() {
        if (TextUtils.isEmpty(host))
            return;

        Intent i = new Intent(this, SnapClientService.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(SnapClientService.EXTRA_HOST, host);
        i.putExtra(SnapClientService.EXTRA_PORT, port);
        i.setAction(SnapClientService.ACTION_START);

        addBackgroundProcess();
        startService(i);
    }

    private void stopSnapClient() {
        if (clientBound)
            snapClientService.stopService();

        removeBackgroundService();
    }

    private void stopSnapServer() {
        if (serverBound) {
            if (host.equals("localhost")) {
                stopSnapClient();
            }
            snapServerService.stopService();
        }

        removeBackgroundService();
    }

    private void removeBackgroundService() {
        if (backgroundProcesses.decrementAndGet() <= 0)
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startRemoteControl() {
        if (remoteControl == null)
            remoteControl = new RemoteControl(this);
        if (!TextUtils.isEmpty(host))
            remoteControl.connect(host, controlPort);
    }

    private void stopRemoteControl() {
        if ((remoteControl != null) && (remoteControl.isConnected()))
            remoteControl.disconnect();
        remoteControl = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        startRemoteControl();
    }

    @Override
    public void onStart() {
        super.onStart();

        Settings settings = new Settings(this);

        if (TextUtils.isEmpty(settings.getHost()))
            nsdHelper.startListening("_snapcast._tcp.", SERVICE_NAME, this, this);
        else
            setHost(settings.getHost(), settings.getStreamPort(), settings.getControlPort());

        setSpotifyCredentialsFromSettings();

        Intent intent = new Intent(this, SnapClientService.class);
        bindService(intent, clientConnection, Context.BIND_AUTO_CREATE);

        Intent serverIntent = new Intent(this, SnapServerService.class);
        bindService(serverIntent, serverConnection, Context.BIND_AUTO_CREATE);
    }

    private void setSpotifyCredentialsFromSettings() {
        Settings settings = new Settings(this);
        setSpotifyCredentials(settings.getSpotifyUsername(), settings.getSpotifyPassword());
    }

    @Override
    public void onDestroy() {
        stopRemoteControl();
        super.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();

        nsdHelper.stopListening();
// Unbind from the service
        if (clientBound) {
            unbindService(clientConnection);
            clientBound = false;
        }

        if (serverBound) {
            unbindService(serverConnection);
            serverBound = false;
        }
    }

    private void onServerStart(SnapService snapService) {
        Log.d(TAG, "onServerStart");
        updateStartStopMenuItem();
    }

    private void onServerStop(SnapService snapService) {
        Log.d(TAG, "onServerStop");
        updateStartStopMenuItem();
    }

    private void onPlayerStart(SnapService snapclientService) {
        Log.d(TAG, "onPlayerStart");
        updateStartStopMenuItem();
    }

    private void onPlayerStop(SnapService snapclientService) {
        Log.d(TAG, "onPlayerStop");
        updateStartStopMenuItem();
        if (warningSamplerateSnackbar != null)
            warningSamplerateSnackbar.dismiss();
    }

    @Override
    public void onLog(SnapService snapclientService, String timestamp, String logClass, String msg) {
        Log.d(TAG, "[" + logClass + "] " + msg);
        if ("Notice".equals(logClass)) {
            if (msg.startsWith("sampleformat")) {
                msg = msg.substring(msg.indexOf(":") + 2);
                Log.d(TAG, "sampleformat: " + msg);
                if (msg.indexOf(':') > 0) {
                    int samplerate = Integer.valueOf(msg.substring(0, msg.indexOf(':')));

                    if (warningSamplerateSnackbar != null)
                        warningSamplerateSnackbar.dismiss();

                    if ((nativeSampleRate != 0) && (nativeSampleRate != samplerate)) {
                        warningSamplerateSnackbar = Snackbar.make(coordinatorLayout,
                                getString(R.string.wrong_sample_rate, samplerate, nativeSampleRate), Snackbar.LENGTH_INDEFINITE);
                        warningSamplerateSnackbar.show();
                    } else if (nativeSampleRate == 0) {
                        warningSamplerateSnackbar = Snackbar.make(coordinatorLayout,
                                getString(R.string.unknown_sample_rate), Snackbar.LENGTH_LONG);
                        warningSamplerateSnackbar.show();
                    }
                }
            }
        } else if ("err".equals(logClass) || "Emerg".equals(logClass) || "Alert".equals(logClass) || "Crit".equals(logClass) || "Err".equals(logClass)) {
            if (msg != null && msg.contains("Error reading config: parse error - unexpected end of input")) {
            } else {
                if (warningSamplerateSnackbar != null)
                    warningSamplerateSnackbar.dismiss();
                warningSamplerateSnackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout),
                        msg, Snackbar.LENGTH_LONG);
                warningSamplerateSnackbar.show();
            }
        }
    }

    @Override
    public void onError(SnapService snapclientService, String msg, Exception exception) {
        updateStartStopMenuItem();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED) {
            return;
        }
        if (requestCode == CLIENT_PROPERTIES_REQUEST) {
            Client client;
            try {
                client = new Client(new JSONObject(data.getStringExtra("client")));
            } catch (JSONException e) {
                Log.wtf(TAG, "Creating new client", e);
                return;
            }

            Client clientOriginal;
            try {
                clientOriginal = new Client(new JSONObject(data.getStringExtra("clientOriginal")));
            } catch (JSONException e) {
                Log.wtf(TAG, "Creating new original client", e);
                return;
            }
            Log.d(TAG, "new name: " + client.getConfig().getName() + ", old name: " + clientOriginal.getConfig().getName());
            if (!client.getConfig().getName().equals(clientOriginal.getConfig().getName()))
                remoteControl.setName(client, client.getConfig().getName());
            Log.d(TAG, "new latency: " + client.getConfig().getLatency() + ", old latency: " + clientOriginal.getConfig().getLatency());
            if (client.getConfig().getLatency() != clientOriginal.getConfig().getLatency())
                remoteControl.setLatency(client, client.getConfig().getLatency());
            serverStatus.updateClient(client);
            groupListFragment.updateServer(MainActivity.this.serverStatus);
        } else if (requestCode == GROUP_PROPERTIES_REQUEST) {
            String groupId = data.getStringExtra("group");
            boolean changed = false;
            if (data.hasExtra("clients")) {
                ArrayList<String> clients = data.getStringArrayListExtra("clients");
                remoteControl.setClients(groupId, clients);
            }
            if (data.hasExtra("stream")) {
                String streamId = data.getStringExtra("stream");
                remoteControl.setStream(groupId, streamId);
                onStreamChanged(RemoteControl.RPCEvent.response, groupId, streamId);
            }
        }
    }


    private void setActionbarSubtitle(final String subtitle) {
        MainActivity.this.runOnUiThread(() -> {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null)
                actionBar.setSubtitle(subtitle);
        });
    }

    private void setHost(final String host, final int streamPort, final int controlPort) {
        if (TextUtils.isEmpty(host))
            return;

        this.host = host;
        this.port = streamPort;
        this.controlPort = controlPort;
        new Settings(this).setHost(host, streamPort, controlPort);
    }

    private void updateMenuItems(final boolean connected) {
        this.runOnUiThread(() -> {
            if (connected) {
                if (miSettings != null)
                    miSettings.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                if (miClientStartStop != null)
                    miClientStartStop.setVisible(true);
//                    if (miRefresh != null)
//                        miRefresh.setVisible(true);
            } else {
                if (miSettings != null)
                    miSettings.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                if (miClientStartStop != null)
                    miClientStartStop.setVisible(false);
//                    if (miRefresh != null)
//                        miRefresh.setVisible(false);
            }
        });
    }

    @Override
    public void onResolved(NsdHelper nsdHelper, NsdServiceInfo serviceInfo) {
        Log.d(TAG, "resolved: " + serviceInfo);
        setHost(serviceInfo.getHost().getCanonicalHostName(), serviceInfo.getPort(), serviceInfo.getPort() + 1);
        startRemoteControl();
        nsdHelper.stopListening();
    }


    @Override
    public void onGroupVolumeChanged(GroupItem groupItem) {
        remoteControl.setGroupVolume(groupItem.getGroup());
    }

    @Override
    public void onMute(GroupItem groupItem, boolean mute) {
        remoteControl.setGroupMuted(groupItem.getGroup(), mute);
    }

    @Override
    public void onVolumeChanged(GroupItem groupItem, ClientItem clientItem, int percent, boolean mute) {
        remoteControl.setVolume(clientItem.getClient(), percent, mute);
    }

    @Override
    public void onDeleteClicked(GroupItem groupItem, final ClientItem clientItem) {
        final Client client = clientItem.getClient();
        client.setDeleted(true);

        serverStatus.updateClient(client);
        groupListFragment.updateServer(serverStatus);
        Snackbar mySnackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout),
                getString(R.string.client_deleted, client.getVisibleName()),
                Snackbar.LENGTH_SHORT);
        mySnackbar.setAction(R.string.undo_string, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                client.setDeleted(false);
                serverStatus.updateClient(client);
                groupListFragment.updateServer(serverStatus);
            }
        });
        mySnackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                super.onDismissed(snackbar, event);
                if (event != DISMISS_EVENT_ACTION) {
                    remoteControl.delete(client);
                    serverStatus.removeClient(client);
                }
            }
        });
        mySnackbar.show();

    }

    @Override
    public void onClientPropertiesClicked(GroupItem groupItem, ClientItem clientItem) {
        Intent intent = new Intent(this, ClientSettingsActivity.class);
        intent.putExtra("client", clientItem.getClient().toJson().toString());
        intent.setFlags(0);
        startActivityForResult(intent, CLIENT_PROPERTIES_REQUEST);
    }

    @Override
    public void onPropertiesClicked(GroupItem groupItem) {
        Intent intent = new Intent(this, GroupSettingsActivity.class);
        intent.putExtra("serverStatus", serverStatus.toJson().toString());
        intent.putExtra("group", groupItem.getGroup().toJson().toString());
        intent.setFlags(0);
        startActivityForResult(intent, GROUP_PROPERTIES_REQUEST);
    }

    @Override
    public void onConnected(RemoteControl remoteControl) {
        setActionbarSubtitle(remoteControl.getHost());
        remoteControl.getServerStatus();
        updateMenuItems(true);
    }

    @Override
    public void onConnecting(RemoteControl remoteControl) {
        setActionbarSubtitle("connecting: " + remoteControl.getHost());
    }

    @Override
    public void onDisconnected(RemoteControl remoteControl, Exception e) {
        Log.d(TAG, "onDisconnected");
        serverStatus = new ServerStatus();
        groupListFragment.updateServer(serverStatus);
        if (e != null) {
            if (e instanceof UnknownHostException)
                setActionbarSubtitle("error: unknown host");
            else
                setActionbarSubtitle("error: " + e.getMessage());
        } else {
            setActionbarSubtitle("not connected");
        }
        updateMenuItems(false);
    }


    @Override
    public void onBatchStart() {
        batchActive = true;
    }


    @Override
    public void onBatchEnd() {
        batchActive = false;
        groupListFragment.updateServer(serverStatus);
    }



/*
    @Override
    public void onClientEvent(RemoteControl remoteControl, RemoteControl.RpcEvent rpcEvent, Client client, RemoteControl.ClientEvent event) {
        Log.d(TAG, "onClientEvent: " + event.toString());
        /// update only in case of notifications
        if (rpcEvent == RemoteControl.RpcEvent.response)
            return;

        serverStatus.updateClient(client);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onServerUpdate(RemoteControl remoteControl, RemoteControl.RpcEvent rpcEvent, ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onStreamUpdate(RemoteControl remoteControl, RemoteControl.RpcEvent rpcEvent, Stream stream) {
        serverStatus.updateStream(stream);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onGroupUpdate(RemoteControl remoteControl, RemoteControl.RpcEvent rpcEvent, Group group) {
        serverStatus.updateGroup(group);
        groupListFragment.updateServer(serverStatus);
    }
*/

    @Override
    public void onConnect(Client client) {
        serverStatus.getClient(client.getId());
        if (client == null) {
            remoteControl.getServerStatus();
            return;
        }
        client.setConnected(true);
        serverStatus.updateClient(client);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onDisconnect(String clientId) {
        Client client = serverStatus.getClient(clientId);
        if (client == null) {
            remoteControl.getServerStatus();
            return;
        }
        client.setConnected(false);
        serverStatus.updateClient(client);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onUpdate(Client client) {
        serverStatus.updateClient(client);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onVolumeChanged(RemoteControl.RPCEvent event, String clientId, Volume volume) {
        if (event == RemoteControl.RPCEvent.response)
            return;
        Client client = serverStatus.getClient(clientId);
        if (client == null) {
            remoteControl.getServerStatus();
            return;
        }
        client.setVolume(volume);
        if (!batchActive)
            groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onLatencyChanged(RemoteControl.RPCEvent event, String clientId, long latency) {
        Client client = serverStatus.getClient(clientId);
        if (client == null) {
            remoteControl.getServerStatus();
            return;
        }
        client.getConfig().setLatency((int) latency);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onNameChanged(RemoteControl.RPCEvent event, String clientId, String name) {
        Client client = serverStatus.getClient(clientId);
        if (client == null) {
            remoteControl.getServerStatus();
            return;
        }
        client.getConfig().setName(name);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onUpdate(Group group) {
        serverStatus.updateGroup(group);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onMute(RemoteControl.RPCEvent event, String groupId, boolean mute) {
        Group g = serverStatus.getGroup(groupId);
        if (g == null) {
            remoteControl.getServerStatus();
            return;
        }
        g.setMuted(mute);
        serverStatus.updateGroup(g);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onStreamChanged(RemoteControl.RPCEvent event, String groupId, String streamId) {
        Group g = serverStatus.getGroup(groupId);
        if (g == null) {
            remoteControl.getServerStatus();
            return;
        }
        g.setStreamId(streamId);
        serverStatus.updateGroup(g);
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onUpdate(ServerStatus server) {
        this.serverStatus = server;
        groupListFragment.updateServer(serverStatus);
    }

    @Override
    public void onUpdate(String streamId, Stream stream) {
        serverStatus.updateStream(stream);
        groupListFragment.updateServer(serverStatus);
    }
}

