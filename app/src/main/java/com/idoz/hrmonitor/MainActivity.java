package com.idoz.hrmonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.idoz.hrmonitor.AudioTrackPlayer.HrAudioEnum;
import com.idoz.hrmonitor.service.DataCollectorService;
import com.idoz.hrmonitor.service.DeviceListenerService;

import java.util.Arrays;
import java.util.List;

import static com.idoz.hrmonitor.ConnectionState.CONNECTED;
import static com.idoz.hrmonitor.ConnectionState.CONNECTING;
import static com.idoz.hrmonitor.ConnectionState.DISCONNECTED;
import static com.idoz.hrmonitor.ConnectionState.DISCONNECTING;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final static String TAG = MainActivity.class.getSimpleName();

  // consts
  public static final int MIN_HR_NOT_SET = -1;
  public static final int MAX_HR_NOT_SET = 999;
  private static final String MAX_HR_NOT_SET_STR = Integer.toString(MAX_HR_NOT_SET);
  private static final String MIN_HR_NOT_SET_STR = Integer.toString(MIN_HR_NOT_SET);
  private static final int REQUEST_ENABLE_BT = 1;
  private static final long connectionTimeoutDelayMillis = 4000L;
  private static final long delayOnReconnectAfterDisconnectMillis = 5000L;
  private static final int NOTIFICATION_MAIN = 1;
  private static final IntentFilter hrDeviceServiceIntentFilter = createHrDeviceIntentFilters();
  NotificationManager notificationManager;
  Intent notificationIntent;
  PendingIntent contentIntent;

  // state (mutable)
  private ConnectionState hrDeviceConnectionState = DISCONNECTED;
  private boolean bluetoothEnabled = false;
  private boolean autoReconnect = true;
  private boolean isBluetoothOnOffStateReceiverRegistered = false;
  private boolean isHRDeviceBroadcastReceiverRegistered = false;
  private Handler handler;
  private boolean hrMockingActive = false;
  private boolean doubleBackToExitPressedOnce;
  private boolean isReversedOrientation = false;
  private boolean playHrAudioCues;

  private TextView heartRateText;
  private TextView usernameText;
  private ImageView hrDeviceConnectedIndicatorImageView;
  private ProgressBar heartRateMemoryDataProgressBar;
  private ToggleButton mockToggleButton;
  private ToggleButton audioCuesButton;
  private ImageButton toggleHrConnection;

  // For drawer navigation (http://blog.teamtreehouse.com/add-navigation-drawer-android)
  private ActionBarDrawerToggle mDrawerToggle;
  // prefs
  private SharedPreferences SP;

  private String username = "NA";
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;
  // connections
  private DeviceListenerService deviceListenerService;


  private DataCollectorService dataCollectorService;
  private DeviceListenerServiceConnection deviceListenerServiceConnection = null;
  private DataCollectorServiceConnection dataCollectorServiceConnection = null;
  private BroadcastReceiver bluetoothOnOffStateReceiver = null;
  private AudioTrackPlayer audioTrackPlayer = null;

  private Toast exitToast;


  final Runnable onCreateMockHrData = new Runnable() {
    @Override
    public void run() {
      final int mockedHR = (int) (Math.random() * (maxHeartRate - minHeartRate + 30) + minHeartRate - 10);
      onReceiveHeartRateData(mockedHR);
      if (dataCollectorServiceConnection != null) {
        dataCollectorServiceConnection.onReceiveHeartRateData(mockedHR);
      }
      if (hrMockingActive) {
        handler.postDelayed(onCreateMockHrData, 1000);
      } else {
        handler.removeCallbacks(onCreateMockHrData);
      }
    }
  };


  @SuppressLint("ShowToast")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "*** onCreate()");
    warnIfNoBluetoothCapability();
    initPrefs();
    populateUiVariables();

    createBluetoothOnOffStateReceiver();
    startService(new Intent(this, DeviceListenerService.class));
    startService(new Intent(this, DataCollectorService.class));

//    registerReceiver(bluetoothOnOffStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    handler = new Handler();
    audioTrackPlayer = new AudioTrackPlayer();
    exitToast = Toast.makeText(this, "Tap BACK again to exit", Toast.LENGTH_SHORT);

//    deviceListenerServiceConnection = new DeviceListenerServiceConnection();
    //dataCollectorServiceConnection = new DataCollectorServiceConnection();
  }

  @Override
  protected void onStart() {
    super.onStart();
    Log.i(TAG, "*** onStart()");
    checkBluetoothEnabled();
    if (!bluetoothEnabled) {
      return;
    }
    if (autoReconnect) {
      userClickedConnectHrDevice();
    }
  }


  @Override
  protected void onResume() {
    super.onResume();
    Log.i(TAG, "*** onResume()");
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    tryBindDeviceListenerService();
    tryBindDataCollectorService();
    tryRegisterBluetoothOnOffStateReceiver();
    tryRegisterHRDeviceBroadcastReceiver();
    initNotifications();
    updateNotifications();
    refreshActiveDeviceText();
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.i(TAG, "*** onPause()");
    tryUnregisterHRDeviceBroadcastReceiver();
    tryUnregisterBluetoothOnOffStateReceiver();
    tryUnbindDataCollectorService();
    tryUnbindDeviceListenerService();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i(TAG, "*** onNewIntent()");
    if (intent.getBooleanExtra("EXIT", false)) {
      Log.i(TAG, "Requested to finish from notification");
      shutdown();
    }
  }


  @Override
  protected void onStop() {
    super.onStop();
    Log.i(TAG, "*** onStop()");

  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "*** onDestroy()");
    tryUnregisterHRDeviceBroadcastReceiver();
    tryUnregisterBluetoothOnOffStateReceiver();

    if (handler != null) {
      handler.removeCallbacks(resetDoubleBackFlag);
    }
  }

  @Override
  public void onBackPressed() {
    if (doubleBackToExitPressedOnce) {
      exitToast.cancel();
      super.onBackPressed();
      shutdown();
      return;
    }
    this.doubleBackToExitPressedOnce = true;
    exitToast.show();

    handler.postDelayed(resetDoubleBackFlag, 2000);

  }

  private void shutdown() {
    Log.i(TAG, "*** shutdown()");
    dataCollectorService.stopLogging();
    removeFromNotificationBar();
    userClickedDisconnectHrDevice();
    SP.unregisterOnSharedPreferenceChangeListener(this);
    tryUnregisterBluetoothOnOffStateReceiver();
    tryUnbindDeviceListenerService();
    stopService(new Intent(this, DataCollectorService.class));
    stopService(new Intent(this, DeviceListenerService.class));
    finish();
  }

  private void initNotifications() {
    Log.i(TAG, "=== initNotifications() ===");
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
    contentIntent = PendingIntent.getActivity(getApplicationContext(), NOTIFICATION_MAIN,
            notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

  }

  private void updateNotifications() {

    Log.i(TAG, "=== updateNotifications() ===");

    final NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.hrmonitor_logo_white)
            .setContentIntent(contentIntent)
            .setContentTitle("Heartrate Monitor")
            .setContentText("OPEN")
            .setColor(Color.BLUE)
                    //.setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
//            .addAction(R.drawable.heart_512, "Show", contentIntent)
            ;

    notificationManager.notify(NOTIFICATION_MAIN, builder.build());

  }

  private void tryRegisterBluetoothOnOffStateReceiver() {
    if (!isBluetoothOnOffStateReceiverRegistered) {
      createBluetoothOnOffStateReceiver();
      registerReceiver(bluetoothOnOffStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
      isBluetoothOnOffStateReceiverRegistered = true;
      Log.d(TAG, "Registered Bluetooth ON/OFF state receiver");
    }
  }

  private void tryUnregisterBluetoothOnOffStateReceiver() {
    if (isBluetoothOnOffStateReceiverRegistered) {
      unregisterReceiver(bluetoothOnOffStateReceiver);
      bluetoothOnOffStateReceiver = null;
      isBluetoothOnOffStateReceiverRegistered = false;
      Log.d(TAG, "Unregistered Bluetooth ON/OFF state receiver");
    }
  }

  private void removeFromNotificationBar() {
    final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    Log.i(TAG, "=== remove notification ===");
    notificationManager.cancel(NOTIFICATION_MAIN);
  }

  private void initPrefs() {
    SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    username = SP.getString(getString(R.string.setting_username), getString(R.string.default_username));
    maxHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_max_hr), MAX_HR_NOT_SET_STR));
    minHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_min_hr), MIN_HR_NOT_SET_STR));
    playHrAudioCues = SP.getBoolean(getString(R.string.setting_alert_outside_hr_range), false);
    SP.registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.i(TAG, ">>>>> UpdatePrefs(): key=" + key);
    if (getString(R.string.setting_alert_outside_hr_range).equals(key)) {
      playHrAudioCues = SP.getBoolean(getString(R.string.setting_alert_outside_hr_range), false);
      audioCuesButton.setChecked(playHrAudioCues);
    }
    if (getString(R.string.setting_username).equals(key)) {
      usernameText.setText(sharedPreferences.getString(key, "NA"));
      return;
    }
    if (getString(R.string.setting_max_hr).equals(key)) {
      maxHeartRate = Integer.parseInt(sharedPreferences.getString(key, MAX_HR_NOT_SET_STR));
      return;
    }
    if (getString(R.string.setting_min_hr).equals(key)) {
      minHeartRate = Integer.parseInt(sharedPreferences.getString(key, MIN_HR_NOT_SET_STR));
    }
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);

    if (!bluetoothEnabled) {
      toggleHrConnection.setImageResource(R.drawable.bt_disabled);
      toggleHrConnection.setEnabled(false);
      menu.findItem(R.id.menu_connect).setVisible(false);
      menu.findItem(R.id.menu_connect).setEnabled(false);
      menu.findItem(R.id.menu_disconnect).setVisible(false);
      mockToggleButton.setVisibility(View.VISIBLE);
      return true;
    }

    toggleHrConnection.setImageResource(R.drawable.bt_enabled);
    toggleHrConnection.setEnabled(true);
    mockToggleButton.setVisibility(View.INVISIBLE);
    toggleHrMocking(false);

    switch (hrDeviceConnectionState) {
      case CONNECTING:
      case CONNECTED:
        toggleHrConnection.setImageResource(R.drawable.bt_connected);
        menu.findItem(R.id.menu_connect).setVisible(false);
        menu.findItem(R.id.menu_disconnect).setVisible(true);
        break;
      case DISCONNECTING:
      case DISCONNECTED:
        toggleHrConnection.setImageResource(R.drawable.bt_enabled);
        menu.findItem(R.id.menu_connect).setVisible(true);
        menu.findItem(R.id.menu_disconnect).setVisible(false);

    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {


    final int id = item.getItemId();
    switch (id) {
      case R.id.menu_connect:
        userClickedConnectHrDevice();
        return true;
      case R.id.menu_disconnect:

        userClickedDisconnectHrDevice();
        return true;
    }

    if (mDrawerToggle.onOptionsItemSelected(item)) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    mDrawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    mDrawerToggle.onConfigurationChanged(newConfig);
  }

  private void startSettingsActivity() {
    startActivity(new Intent(this, SettingsActivity.class));
  }

  private void toggleHrMocking(final boolean isEnabled) {
    hrMockingActive = isEnabled;
    if (hrMockingActive) {
      handler.post(onCreateMockHrData);
    } else {
      handler.removeCallbacks(onCreateMockHrData);
      setHeartRateUnknown();
    }

  }


  private void populateUiVariables() {
    setContentView(R.layout.activity_main);
    initDrawer();
    heartRateText = (TextView) findViewById(R.id.heartRateText);
    usernameText = (TextView) findViewById(R.id.usernameText);
    usernameText.setText(username);
    hrDeviceConnectedIndicatorImageView = (ImageView) findViewById(R.id.btConnectedIndicatorImage);
    hrDeviceConnectedIndicatorImageView.setImageResource(R.drawable.bt_disabled);
    heartRateMemoryDataProgressBar = (ProgressBar) findViewById(R.id.heartRateMemoryDataProgressBar);
    heartRateMemoryDataProgressBar.setProgress(0);
    heartRateMemoryDataProgressBar.setMax(100);
    heartRateMemoryDataProgressBar.setScaleY(3f); // For UI legibility
    final ToggleButton loggingToggleButton = (ToggleButton) findViewById(R.id.loggingToggleButton);
    loggingToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
          dataCollectorService.startLogging(username);
          play(HrAudioEnum.HI);
        } else {
          dataCollectorService.stopLogging();
          play(HrAudioEnum.NORMAL);
        }
      }
    });
    final ImageButton reverseOrientationButton = (ImageButton) findViewById(R.id.reverseOrientationButton);
    reverseOrientationButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        switchToPortraitOrientation(!isReversedOrientation);
        isReversedOrientation = !isReversedOrientation;
      }
    });
    audioCuesButton = (ToggleButton) findViewById(R.id.audioCuesButton);
    audioCuesButton.setChecked(playHrAudioCues);
    audioCuesButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(getString(R.string.setting_alert_outside_hr_range), isChecked);
        editor.apply();
      }
    });
    final ImageButton showVolumeButton = (ImageButton) findViewById(R.id.showVolumeButton);
    showVolumeButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showVolumeControl();
      }
    });
    mockToggleButton = (ToggleButton) findViewById(R.id.mockToggleButton);
    mockToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        toggleHrMocking(isChecked);
        if (isChecked) {
          play(HrAudioEnum.HIHI);
        } else {
          play(HrAudioEnum.LO);
        }
      }
    });
    toggleHrConnection = (ImageButton) findViewById(R.id.toggleHrConnection);
    toggleHrConnection.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        switch(hrDeviceConnectionState) {
          case CONNECTED:
          case CONNECTING:
            userClickedDisconnectHrDevice(); break;
          case DISCONNECTED:
          case DISCONNECTING:
            userClickedConnectHrDevice(); break;
        }

      }
    });
  }

  private void initDrawer() {
    final DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
    ListView drawerList = (ListView) findViewById(R.id.navList);
    final String activityTitle = getTitle().toString();

    final DrawerItem[] drawerItems = new DrawerItem[]{
            new DrawerItem(getString(R.string.drawer_devices_title), R.drawable.hr_settings_black),
            new DrawerItem(getString(R.string.drawer_settings_title), R.drawable.drawer_settings_icon),
            new DrawerItem("About", R.drawable.ic_about)
    };
    drawerList.setAdapter(new DrawerItemAdapter(this, Arrays.asList(drawerItems)));
    drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        drawerLayout.closeDrawers();
        switch (position) {
          case 0:
            startActivity(new Intent(MainActivity.this, DeviceSelectionActivity.class));
            //Toast.makeText(MainActivity.this, "Device Settings", Toast.LENGTH_SHORT).show();
            break;
          case 1:
            startSettingsActivity();
            break;
        }
      }
    });

    mDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
            R.string.drawer_open, R.string.drawer_close) {

      /** Called when a drawer has settled in a completely open state. */
      public void onDrawerOpened(View drawerView) {
        super.onDrawerOpened(drawerView);
        getSupportActionBar().setTitle(R.string.drawer_open_title);
        invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
      }

      /** Called when a drawer has settled in a completely closed state. */
      public void onDrawerClosed(View view) {
        super.onDrawerClosed(view);
        getSupportActionBar().setTitle(activityTitle);
        invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
      }
    };

    mDrawerToggle.setDrawerIndicatorEnabled(true);
    drawerLayout.setDrawerListener(mDrawerToggle);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);
  }

  private void showVolumeControl() {
    AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
  }

  private void switchToPortraitOrientation(boolean reversePortrait) {
    if (reversePortrait) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
    } else {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
  }

  private void play(final HrAudioEnum audioToPlay) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        Log.d(TAG, "Play sound in thread: " + Thread.currentThread().getName());
        audioTrackPlayer.play(audioToPlay);
      }
    });

  }

  private void refreshUi() {
    refreshHRDeviceConnectionIndicator();
    if (!bluetoothEnabled) {
      setHeartRateUnknown();
    }
    refreshActiveDeviceText();
    invalidateOptionsMenu();
  }

  private void refreshActiveDeviceText() {
    final TextView activeDeviceTv = (TextView)findViewById(R.id.deviceDetailsText);
    activeDeviceTv.setText(SP.getString(getString(R.string.setting_active_device), getString(R.string.device_unknown)));
  }

  private void createBluetoothOnOffStateReceiver() {

    if (bluetoothOnOffStateReceiver != null) {
      return;
    }

    bluetoothOnOffStateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
          switch (bluetoothState) {
            case BluetoothAdapter.STATE_ON:
              bluetoothEnabled = true;
              toggleHrMocking(false);
              Log.i(TAG, ">->-> Bluetooth is ON <-<-<");
              break;
            case BluetoothAdapter.STATE_OFF:
              bluetoothEnabled = false;
              hrDeviceConnectionState = DISCONNECTED;
              Log.i(TAG, ">->-> Bluetooth is OFF <-<-<");
              break;
            default:
              break;
          }
        }
        refreshUi();
      }
    };

  }


  private void checkBluetoothEnabled() {

    Log.i(TAG, "checkBluetoothEnabled()");
    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
    if (bluetoothAdapter == null) {
      bluetoothEnabled = false;
      refreshUi();
      return;
    }

    if (!bluetoothAdapter.isEnabled()) {
      bluetoothEnabled = false;
      startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
    } else {
      bluetoothEnabled = true;
      refreshUi();
    }
  }

  @Override
  // might be redundant because we have a listener on BT state which does this already
  // remove this method and see if it still works
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // User chose not to enable Bluetooth.
    if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
      bluetoothEnabled = false;
      refreshUi();
      return;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void refreshHRDeviceConnectionIndicator() {

    if (!bluetoothEnabled) {
      hrDeviceConnectedIndicatorImageView.setImageResource(R.drawable.bt_disabled);
      return;
    }

    switch (hrDeviceConnectionState) {
      case CONNECTED:
        hrDeviceConnectedIndicatorImageView.setImageResource(R.drawable.bt_connected);
        break;
      case CONNECTING:
        hrDeviceConnectedIndicatorImageView.setImageResource(R.drawable.bt_searching);
        break;
      default:
        hrDeviceConnectedIndicatorImageView.setImageResource(R.drawable.bt_enabled);
        break;
    }
  }

  private void tryRegisterHRDeviceBroadcastReceiver() {
    Log.i(TAG, "tryRegisterHRDeviceBroadcastReceiver()");
    if (!isHRDeviceBroadcastReceiverRegistered) {
      hrDeviceBroadcastReceiver = createHrDeviceBroadcastReceiver();
      registerReceiver(hrDeviceBroadcastReceiver, hrDeviceServiceIntentFilter);
      isHRDeviceBroadcastReceiverRegistered = true;
    }
  }

  private void tryUnregisterHRDeviceBroadcastReceiver() {
    Log.i(TAG, "tryUnregisterHRDeviceBroadcastReceiver()");
    if (isHRDeviceBroadcastReceiverRegistered) {
      unregisterReceiver(hrDeviceBroadcastReceiver);
      hrDeviceBroadcastReceiver = null;
      isHRDeviceBroadcastReceiverRegistered = false;
    }
  }

  private void userClickedConnectHrDevice() {

    autoReconnect = true;
    if (CONNECTED == hrDeviceConnectionState) {
      Log.i(TAG, "userClickedConnectHrDevice(): already connected");
      return;
    }
    if (deviceListenerService != null) {
      hrDeviceConnectionState = CONNECTING;
      Log.i(TAG, "userClickedConnectHrDevice() waiting to connect for " + connectionTimeoutDelayMillis + " millis");
      setHeartRatePending();
      refreshUi();
      handler.removeCallbacks(onConnectionTimeout);
      handler.postDelayed(onConnectionTimeout, connectionTimeoutDelayMillis);
      deviceListenerService.connectToDevice();
    }

  }

  private void tryBindDeviceListenerService() {
    if (deviceListenerServiceConnection == null) {
      deviceListenerServiceConnection = new DeviceListenerServiceConnection();
      boolean bindResult = bindService(
              new Intent(this, DeviceListenerService.class), deviceListenerServiceConnection, BIND_AUTO_CREATE);
      Log.i(TAG, "Bind to HR device listener service success? " + bindResult);
    }
  }

  private void tryUnbindDeviceListenerService() {
    if (deviceListenerServiceConnection != null) {
      Log.i(TAG, "Unbinding from HR device listener service...");
      unbindService(deviceListenerServiceConnection);
      deviceListenerServiceConnection = null;
    }
  }

  private void tryBindDataCollectorService() {
    dataCollectorServiceConnection = new DataCollectorServiceConnection();
    bindService(new Intent(this, DataCollectorService.class), dataCollectorServiceConnection, BIND_AUTO_CREATE);

  }

  private void tryUnbindDataCollectorService() {
    Log.i(TAG, "Unbinding from Data Collector service...");
    unbindService(dataCollectorServiceConnection);
    dataCollectorServiceConnection = null;
  }

  private void userClickedDisconnectHrDevice() {
    Log.i(TAG, "userClickedDisconnectHrDevice()");
    autoReconnect = false;
    if (DISCONNECTED == hrDeviceConnectionState) {
      Log.i(TAG, "userClickedDisconnectHrDevice(): already disconnected.");
      return;
    }
    if (deviceListenerService != null) {
      hrDeviceConnectionState = DISCONNECTING;
//      setHeartRatePending();
      refreshUi();
      deviceListenerService.disconnectFromDevice();
    }
  }

  private void onReceiveHeartRateData(final String data) {
    if (data == null) {
      return;
    }
    final int newHeartRate;
    try {
      newHeartRate = Integer.parseInt(data);
    } catch (NumberFormatException e) {
      setHeartRateError();
      return;
    }
    onReceiveHeartRateData(newHeartRate);
  }

  private void onReceiveHeartRateData(final int newHeartRate) {
    setHeartRateNewValue(newHeartRate);
    heartRateMemoryDataProgressBar.setProgress(0);
  }


  private void setHeartRateNewValue(int newHeartRate) {
    heartRateText.setTextColor(calculateHeartRateTextColor(newHeartRate));
    if (hrMockingActive) {
      heartRateText.setTypeface(null, Typeface.ITALIC);
    } else {
      heartRateText.setTypeface(null, Typeface.NORMAL);
    }
    heartRateText.setText(String.format("%d", newHeartRate));
  }

  private void setHeartRatePending() {

    heartRateText.setTextColor(getResources().getColor(R.color.textColorHrNA));
    heartRateText.setText(R.string.heartrate_pending);
  }

  private void setHeartRateUnknown() {
    heartRateText.setTextColor(getResources().getColor(R.color.textColorHrNA));
    heartRateText.setText(R.string.heartrate_unknown);
  }

  private void setHeartRateError() {
    heartRateText.setTextColor(getResources().getColor(R.color.textColorHrNA));
    heartRateText.setText(R.string.heartrate_error);
  }


  private int calculateHeartRateTextColor(final int newHR) {
    if (newHR > maxHeartRate) {
      return getResources().getColor(R.color.textColorHrTooHigh);
    }
    if (newHR < minHeartRate) {
      return getResources().getColor(R.color.textColorHrTooLow);
    }
//    if (newHR > oldHR) {
//      return Color.MAGENTA;
//    }
    return getResources().getColor(R.color.textColorHrInRange);
  }

  private Runnable reconnectAfterDisconnect = new Runnable() {
    @Override
    public void run() {
      Log.d(TAG, "reconnectAfterDisconnect() in thread: " + Thread.currentThread().getName());
      userClickedConnectHrDevice();
    }
  };
  // Handles various events fired by the Service.
// ACTION_GATT_CONNECTED: connected to a HR device
// ACTION_GATT_DISCONNECTED: disconnected from HR device
// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
//                        or notification operations.
  private BroadcastReceiver hrDeviceBroadcastReceiver;

  @NonNull
  private BroadcastReceiver createHrDeviceBroadcastReceiver() {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {

          case DeviceListenerService.ACTION_GATT_CONNECTED:
            hrDeviceConnectionState = CONNECTED;
            Log.i(TAG, ">>> Received broadcast: HR device connected");
            handler.removeCallbacks(onConnectionTimeout);
            setHeartRatePending();
            refreshUi();
            break;

          case DeviceListenerService.ACTION_GATT_DISCONNECTED:
            hrDeviceConnectionState = DISCONNECTED;
            Log.i(TAG, ">>> Received broadcast: HR device disconnected");
            setHeartRateUnknown();
            refreshUi();
            if (autoReconnect) {
              Log.w(TAG, "Disconnected unintentionally from HR device, trying to reconnect in " + delayOnReconnectAfterDisconnectMillis + " millis...");
              hrDeviceConnectionState = CONNECTING;
              refreshUi();
              handler.removeCallbacks(reconnectAfterDisconnect);
              handler.postDelayed(reconnectAfterDisconnect, 5000);
            }
            break;

          case DeviceListenerService.STATUS_HR_NOT_SUPPORTED:
            Toast.makeText(getBaseContext(), "Heart Rate not supported!", Toast.LENGTH_LONG).show();
            setHeartRateError();
            break;

          case DeviceListenerService.ACTION_DATA_AVAILABLE:
            onReceiveHeartRateData(intent.getStringExtra(DeviceListenerService.EXTRA_DATA));
        }
      }
    };
  }

  private static IntentFilter createHrDeviceIntentFilters() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(DeviceListenerService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(DeviceListenerService.ACTION_GATT_DISCONNECTED);
    intentFilter.addAction(DeviceListenerService.ACTION_DATA_AVAILABLE);
    return intentFilter;
  }

  private void warnIfNoBluetoothCapability() {
    // Use this check to determine whether BLE is supported on the device.
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Toast.makeText(this, R.string.warn_ble_not_supported, Toast.LENGTH_LONG).show();
      return;
    }

    // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
    // BluetoothAdapter through BluetoothManager.
    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

    // Checks if Bluetooth is supported on the device.
    if (bluetoothAdapter == null) {
      Toast.makeText(this, R.string.warn_bluetooth_not_supported, Toast.LENGTH_LONG).show();
    }
  }

  final Runnable onConnectionTimeout = new Runnable() {
    @Override
    public void run() {
      if (hrDeviceConnectionState != CONNECTED) {
        hrDeviceConnectionState = DISCONNECTED;
        Log.w(TAG, "Connection to HR device timed out!");
        setHeartRateUnknown();
        refreshUi();
      }
    }
  };

  private class DeviceListenerServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      DeviceListenerService.LocalBinder binder = (DeviceListenerService.LocalBinder) service;
      deviceListenerService = binder.getService();
      tryRegisterHRDeviceBroadcastReceiver();
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      setHeartRateUnknown();
      deviceListenerService = null;
    }
  }

  private class DataCollectorServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      DataCollectorService.LocalBinder binder = (DataCollectorService.LocalBinder) service;
      dataCollectorService = binder.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      dataCollectorService = null;

    }

    public void onReceiveHeartRateData(final int mockedHR) {
      if (dataCollectorService != null) {
        dataCollectorService.onReceiveHeartRateData(mockedHR);
      }
    }
  }

  private final Runnable resetDoubleBackFlag = new Runnable() {
    @Override
    public void run() {
      doubleBackToExitPressedOnce = false;
    }
  };

  public class DrawerItem {
    public String name;
    public int icon;

    public DrawerItem(String name, int icon) {
      this.name = name;
      this.icon = icon;
    }
  }

  public class DrawerItemAdapter extends ArrayAdapter<DrawerItem> {
    public DrawerItemAdapter(Context context, List<DrawerItem> objects) {
      super(context, 0, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      // Get the data item for this position
      DrawerItem drawerItem = getItem(position);
      // Check if an existing view is being reused, otherwise inflate the view
      if (convertView == null) {
        convertView = LayoutInflater.from(getContext()).inflate(R.layout.drawer_item, parent, false);
      }
      // Lookup view for data population
      TextView tvName = (TextView) convertView.findViewById(R.id.name);
      ImageView tvIcon = (ImageView) convertView.findViewById(R.id.icon);
      // Populate the data into the template view using the data object
      tvName.setText(drawerItem.name);
      tvIcon.setImageResource(drawerItem.icon);
      // Return the completed view to render on screen
      return convertView;
    }
  }
}


