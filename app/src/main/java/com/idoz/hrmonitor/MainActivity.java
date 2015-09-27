package com.idoz.hrmonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
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
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.idoz.hrmonitor.AudioTrackPlayer.HrAudioEnum;

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
  private static final IntentFilter hrSensorServiceIntentFilter = createHrSensorIntentFilters();
  NotificationManager notificationManager;
  Intent notificationIntent;
  PendingIntent contentIntent;

  // state (mutable)
  private ConnectionState hrSensorConnectionState = DISCONNECTED;
  private boolean bluetoothEnabled = false;
  private boolean autoReconnect = true;
  private boolean isBluetoothOnOffStateReceiverRegistered = false;
  private boolean isHRrSensorBroadcastReceiverRegistered = false;
  private Handler handler;
  private boolean hrMockingActive = false;
  private boolean doubleBackToExitPressedOnce;
  private boolean isReversedOrientation = false;

  private TextView heartRateText;
  private TextView usernameText;
  private ImageView hrSensorConnectedIndicatorImageView;
  private ProgressBar heartRateMemoryDataProgressBar;
  private ToggleButton mockToggleButton;
  private ToggleButton audioCuesButton;
  // prefs
  private SharedPreferences SP;

  private String username = "NA";
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;
  // connections
  private HRSensorService hrSensorService;


  private HrLoggerService hrLoggerService;
  private HrSensorServiceConnection hrSensorServiceConnection = null;
  private HrLoggerServiceConnection hrLoggerServiceConnection = null;
  private BroadcastReceiver bluetoothOnOffStateReceiver = null;
  private AudioTrackPlayer audioTrackPlayer = null;

  private Toast exitToast;


  final Runnable onCreateMockHrData = new Runnable() {
    @Override
    public void run() {
      final int mockedHR = (int) (Math.random() * (maxHeartRate - minHeartRate + 40) + minHeartRate - 15);
      onReceiveHeartRateData(mockedHR);
      hrLoggerServiceConnection.onReceiveHeartRateData(mockedHR);
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
    startService(new Intent(this, HRSensorService.class));
    startService(new Intent(this, HrLoggerService.class));

//    registerReceiver(bluetoothOnOffStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    handler = new Handler();
    audioTrackPlayer = new AudioTrackPlayer();
    exitToast = Toast.makeText(this, "Tap BACK again to exit", Toast.LENGTH_SHORT);

//    hrSensorServiceConnection = new HrSensorServiceConnection();
    //hrLoggerServiceConnection = new HrLoggerServiceConnection();
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
      userClickedConnectHrSensor();
    }
  }


  @Override
  protected void onResume() {
    super.onResume();
    Log.i(TAG, "*** onResume()");
    setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);
    tryBindHRSensorService();
    tryBindHrLoggerService();
    tryRegisterBluetoothOnOffStateReceiver();
    tryRegisterHRSensorBroadcastReceiver();
    initNotifications();
    updateNotifications();
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.i(TAG, "*** onPause()");
    tryUnregisterHRSensorBroadcastReceiver();
    tryUnregisterBluetoothOnOffStateReceiver();
    tryUnbindHrLoggerService();
    tryUnbindHRSensorService();
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
    tryUnregisterHRSensorBroadcastReceiver();
    tryUnregisterBluetoothOnOffStateReceiver();

    if (handler != null) {
      handler.removeCallbacks(resetDoubleBackFlag);
    }
  }

  @Override
  public void onBackPressed() {
    if(doubleBackToExitPressedOnce) {
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
    hrLoggerService.stopLogging();
    removeFromNotificationBar();
    userClickedDisconnectHrSensor();
    SP.unregisterOnSharedPreferenceChangeListener(this);
    tryUnregisterBluetoothOnOffStateReceiver();
    tryUnbindHRSensorService();
    stopService(new Intent(this, HrLoggerService.class));
    stopService(new Intent(this, HRSensorService.class));
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
            .setSmallIcon(R.drawable.bluetooth_128)
            .setContentIntent(contentIntent)
            .setContentTitle("Heart Rate Monitor")
            .setContentText("Connected")
            .setColor(Color.BLUE)
            .setCategory(Notification.CATEGORY_SERVICE)
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

    SP.registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

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
      menu.findItem(R.id.menu_connect).setVisible(false);
      menu.findItem(R.id.menu_connect).setEnabled(false);
      menu.findItem(R.id.menu_disconnect).setVisible(false);
      mockToggleButton.setVisibility(View.VISIBLE);
      return true;
    }

    mockToggleButton.setVisibility(View.GONE);
    toggleHrMocking(false);

    switch (hrSensorConnectionState) {
      case CONNECTING:
      case CONNECTED:
        menu.findItem(R.id.menu_connect).setVisible(false);
        menu.findItem(R.id.menu_disconnect).setVisible(true);
        break;
      case DISCONNECTING:
      case DISCONNECTED:
        menu.findItem(R.id.menu_connect).setVisible(true);
        menu.findItem(R.id.menu_disconnect).setVisible(false);

    }
    return true;
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


  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int id = item.getItemId();
    switch (id) {
      case R.id.menu_connect:
        autoReconnect = true;
        userClickedConnectHrSensor();
        return true;
      case R.id.menu_disconnect:
        autoReconnect = false;
        userClickedDisconnectHrSensor();
        return true;
      case R.id.menu_settings:
        startActivity(new Intent(this, SettingsActivity.class));
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }


  private void populateUiVariables() {
    setContentView(R.layout.activity_main);
    final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    if (toolbar != null) {
      toolbar.setTitle("HeartRate Logger");
      setSupportActionBar(toolbar);
    }
    heartRateText = (TextView) findViewById(R.id.heartRateText);
    usernameText = (TextView) findViewById(R.id.usernameText);
    usernameText.setText(username);
    hrSensorConnectedIndicatorImageView = (ImageView) findViewById(R.id.btConnectedIndicatorImage);
    heartRateMemoryDataProgressBar = (ProgressBar) findViewById(R.id.heartRateMemoryDataProgressBar);
    heartRateMemoryDataProgressBar.setProgress(0);
    heartRateMemoryDataProgressBar.setMax(100);
    heartRateMemoryDataProgressBar.setScaleY(3f); // For UI legibility
    final ToggleButton loggingToggleButton = (ToggleButton) findViewById(R.id.loggingToggleButton);
    loggingToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
          hrLoggerService.startLogging(username);
          play(HrAudioEnum.HI);
        } else {
          hrLoggerService.stopLogging();
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
    audioCuesButton = (ToggleButton)findViewById(R.id.audioCuesButton);
    audioCuesButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(getString(R.string.setting_alert_outside_hr_range), isChecked);
        editor.commit();
        hrLoggerServiceConnection.updatePreferences(PreferenceManager.getDefaultSharedPreferences(MainActivity.this));
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
    refreshHRSensorConnectionIndicator();

    if (!bluetoothEnabled) {
      setHeartRateUnknown();
    }
    invalidateOptionsMenu();
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
              hrSensorConnectionState = DISCONNECTED;
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

  private void refreshHRSensorConnectionIndicator() {

    if(!bluetoothEnabled) {
      hrSensorConnectedIndicatorImageView.setImageResource(R.drawable.bt_disabled);
      return;
    }

    switch(hrSensorConnectionState) {
      case CONNECTED: hrSensorConnectedIndicatorImageView.setImageResource(R.drawable.bt_connected); break;
      case CONNECTING: hrSensorConnectedIndicatorImageView.setImageResource(R.drawable.bt_searching); break;
      default: hrSensorConnectedIndicatorImageView.setImageResource(R.drawable.bt_enabled); break;
    }
  }

  private void tryRegisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "tryRegisterHRSensorBroadcastReceiver()");
    if (!isHRrSensorBroadcastReceiverRegistered) {
      hrSensorBroadcastReceiver = createHrSensorBroadcastReceiver();
      registerReceiver(hrSensorBroadcastReceiver, hrSensorServiceIntentFilter);
      isHRrSensorBroadcastReceiverRegistered = true;
    }
  }

  private void tryUnregisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "tryUnregisterHRSensorBroadcastReceiver()");
    if (isHRrSensorBroadcastReceiverRegistered) {
      unregisterReceiver(hrSensorBroadcastReceiver);
      hrSensorBroadcastReceiver = null;
      isHRrSensorBroadcastReceiverRegistered = false;
    }
  }

  private void userClickedConnectHrSensor() {

    if (CONNECTED == hrSensorConnectionState) {
      Log.i(TAG, "userClickedConnectHrSensor(): already connected");
      return;
    }
    if (hrSensorService != null) {
      hrSensorConnectionState = CONNECTING;
      Log.i(TAG, "userClickedConnectHrSensor() waiting to connect for " + connectionTimeoutDelayMillis + " millis");
      setHeartRatePending();
      refreshUi();
      handler.removeCallbacks(onConnectionTimeout);
      handler.postDelayed(onConnectionTimeout, connectionTimeoutDelayMillis);
      hrSensorService.connectToDevice();
    }

  }

  private void tryBindHRSensorService() {
    if (hrSensorServiceConnection==null) {
      hrSensorServiceConnection = new HrSensorServiceConnection();
      boolean bindResult = bindService(
              new Intent(this, HRSensorService.class), hrSensorServiceConnection, BIND_AUTO_CREATE);
      Log.i(TAG, "Bind to HR Sensor service success? " + bindResult);
    }
  }

  private void tryUnbindHRSensorService() {
    if (hrSensorServiceConnection!=null) {
      Log.i(TAG, "Unbinding from HR Sensor service...");
      unbindService(hrSensorServiceConnection);
      hrSensorServiceConnection = null;
    }
  }

  private void tryBindHrLoggerService() {
    hrLoggerServiceConnection = new HrLoggerServiceConnection();
    bindService(new Intent(this, HrLoggerService.class), hrLoggerServiceConnection, BIND_AUTO_CREATE);
    if(hrLoggerServiceConnection!=null) {
      hrLoggerServiceConnection.updatePreferences(PreferenceManager.getDefaultSharedPreferences(this));
    }

  }

  private void tryUnbindHrLoggerService() {
    Log.i(TAG, "Unbinding from HR Logger service...");
    unbindService(hrLoggerServiceConnection);
    hrLoggerServiceConnection = null;
  }
  private void userClickedDisconnectHrSensor() {
    Log.i(TAG, "userClickedDisconnectHrSensor()");
    if (DISCONNECTED == hrSensorConnectionState) {
      Log.i(TAG, "userClickedDisconnectHrSensor(): already disconnected.");
      return;
    }
    if (hrSensorService != null) {
      hrSensorConnectionState = DISCONNECTING;
//      setHeartRatePending();
//      refreshUi();
      hrSensorService.disconnectFromDevice();
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
    //playHeartRateAlert(newHeartRate);
    //int progress = heartRateLogger.onHeartRateChange(lastHeartRate, newHeartRate);
    setHeartRateNewValue(newHeartRate);
//    heartRateMemoryDataProgressBar.setProgress(progress);
    heartRateMemoryDataProgressBar.setProgress(0);
    //updateNotifications();
  }


  private void setHeartRateNewValue(int newHeartRate) {
    heartRateText.setTextColor(calculateHeartRateTextColor(newHeartRate));
    if (hrMockingActive) {
      heartRateText.setTypeface(null, Typeface.ITALIC);
    } else {
      heartRateText.setTypeface(null, Typeface.NORMAL);
    }
    heartRateText.setText(Integer.toString(newHeartRate));
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
      userClickedConnectHrSensor();
    }
  };
  // Handles various events fired by the Service.
// ACTION_GATT_CONNECTED: connected to a HR sensor
// ACTION_GATT_DISCONNECTED: disconnected from HR sensor
// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
//                        or notification operations.
  private BroadcastReceiver hrSensorBroadcastReceiver;

  @NonNull
  private BroadcastReceiver createHrSensorBroadcastReceiver() {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {

          case HRSensorService.ACTION_GATT_CONNECTED:
            hrSensorConnectionState = CONNECTED;
            Log.i(TAG, ">>> Received broadcast: HR sensor connected");
            handler.removeCallbacks(onConnectionTimeout);
            setHeartRatePending();
            refreshUi();
            break;

          case HRSensorService.ACTION_GATT_DISCONNECTED:
            hrSensorConnectionState = DISCONNECTED;
            Log.i(TAG, ">>> Received broadcast: HR sensor disconnected");
            setHeartRateUnknown();
            refreshUi();
            if (autoReconnect) {
              Log.w(TAG, "Disconnected unintentionally from HR sensor, trying to reconnect in " + delayOnReconnectAfterDisconnectMillis + " millis...");
              hrSensorConnectionState = CONNECTING;
              refreshUi();
              handler.removeCallbacks(reconnectAfterDisconnect);
              handler.postDelayed(reconnectAfterDisconnect, 5000);
            }
            break;

          case HRSensorService.STATUS_HR_NOT_SUPPORTED:
            Toast.makeText(getBaseContext(), "Heart Rate not supported!", Toast.LENGTH_LONG).show();
            setHeartRateError();
            break;

          case HRSensorService.ACTION_DATA_AVAILABLE:
            onReceiveHeartRateData(intent.getStringExtra(HRSensorService.EXTRA_DATA));
        }
      }
    };
  }

  private static IntentFilter createHrSensorIntentFilters() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(HRSensorService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(HRSensorService.ACTION_GATT_DISCONNECTED);
    intentFilter.addAction(HRSensorService.ACTION_DATA_AVAILABLE);
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
      if (hrSensorConnectionState != CONNECTED) {
        hrSensorConnectionState = DISCONNECTED;
        Log.w(TAG, "Connection to HR sensor timed out!");
        setHeartRateUnknown();
        refreshUi();
      }
    }
  };

  private class HrSensorServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      HRSensorService.LocalBinder binder = (HRSensorService.LocalBinder) service;
      hrSensorService = binder.getService();
      tryRegisterHRSensorBroadcastReceiver();
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      setHeartRateUnknown();
      hrSensorService = null;
    }
  }

  private class HrLoggerServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      HrLoggerService.LocalBinder binder = (HrLoggerService.LocalBinder) service;
      hrLoggerService = binder.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      hrLoggerService = null;

    }

    public void updatePreferences(final SharedPreferences sharedPreferences) {
      if(hrLoggerService != null) {
        hrLoggerService.updatePreferences(sharedPreferences);
      }
    }

    public void onReceiveHeartRateData(final int mockedHR) {
      if(hrLoggerService != null) {
        hrLoggerService.onReceiveHeartRateData(mockedHR);
      }
    }
  }

  private final Runnable resetDoubleBackFlag = new Runnable() {
    @Override
    public void run() {
      doubleBackToExitPressedOnce = false;
    }
  };

}


