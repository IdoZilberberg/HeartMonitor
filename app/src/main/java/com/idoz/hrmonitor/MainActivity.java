package com.idoz.hrmonitor;

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
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.idoz.hrmonitor.AudioTrackPlayer.HrAudioEnum;
import com.idoz.hrmonitor.logger.HeartRateFullLogger;
import com.idoz.hrmonitor.logger.HeartRateLogger;

import static com.idoz.hrmonitor.ConnectionState.*;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final static String TAG = MainActivity.class.getSimpleName();

  // consts
  private static final int MIN_HR_NOT_SET = -1;
  private static final String MIN_HR_NOT_SET_STR = Integer.toString(MIN_HR_NOT_SET);
  private static final int MAX_HR_NOT_SET = 999;
  private static final String MAX_HR_NOT_SET_STR = Integer.toString(MAX_HR_NOT_SET);
  private static final int REQUEST_ENABLE_BT = 1;
  private static final long connectionTimeoutDelayMillis = 4000L;
  private static final long delayOnReconnectAfterDisconnectMillis = 5000L;
  private static final int minIntervalBetweenAlertsInSeconds = 10;
  private static final int NOTIFICATION_MAIN = 1;
  private static final IntentFilter hrSensorServiceIntentFilter = createHrSensorIntentFilters();

  // state (mutable)
  private ConnectionState hrSensorConnectionState = DISCONNECTED;
  private boolean bluetoothEnabled = false;
  private boolean serviceBound = false;
  private boolean autoReconnect = true;
  private boolean isBluetoothOnOffStateReceiverRegistered = false;
  private boolean isHRrSensorBroadcastReceiverRegistered = false;
  private int lastHeartRate = 0;
  private Handler handler;
  private boolean alertsTemporarilyMuted = false;
  private boolean isBackToHrRange = true;
  private boolean hrMockingActive = false;

  // view elements
  private Toolbar toolbar;
  private TextView heartRateText;
  private TextView usernameText;
  private ImageView hrSensorConnectedIndicatorImageView;
  private ProgressBar heartRateMemoryDataProgressBar;
  private ToggleButton loggingToggleButton;
  private ToggleButton orientationToggleButton;
  private ToggleButton mockToggleButton;

  // prefs
  private SharedPreferences SP;
  private String username = "NA";
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;
  private boolean playAlertIfOutsideHrRange;


  // connections
  private HRSensorService hrSensorService;
  private HrSensorServiceConnection hrSensorServiceConnection;
  private BroadcastReceiver bluetoothOnOffStateReceiver;
  private HeartRateLogger heartRateLogger;
  private AudioTrackPlayer audioTrackPlayer;
  private int thresholdAboveMaxHeartRate = 5;

  final Runnable onCreateMockHrData = new Runnable() {
    @Override
    public void run() {
      onReceiveHeartRateData((int) (Math.random() * (maxHeartRate - minHeartRate + 40) + minHeartRate - 15));
      if (hrMockingActive) {
        handler.postDelayed(onCreateMockHrData, 1000);
      } else {
        handler.removeCallbacks(onCreateMockHrData);
      }
    }
  };


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "*** onCreate()");
    warnIfNoBluetoothCapability();
    initPrefs();
    populateUiVariables();
    addToNotificationBar();
    createBluetoothOnOffStateReceiver();
    Intent i = new Intent(this, HRSensorService.class);
    startService(i);

    createHeartRateLogger();
//    registerReceiver(bluetoothOnOffStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    handler = new Handler();
    audioTrackPlayer = new AudioTrackPlayer();
    hrSensorServiceConnection = new HrSensorServiceConnection();
  }


  @Override
  protected void onStart() {
    super.onStart();
    Log.i(TAG, "*** onStart()");
    //checkBluetoothEnabled();
    checkBluetoothEnabled();
    if (!bluetoothEnabled) {
      return;
    }
    tryRegisterBluetoothOnOffStateReceiver();

    if (autoReconnect) {
      userClickedConnectHrSensor();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.i(TAG, "*** onResume()");
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    tryBindHRSensorService();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i(TAG, "*** onNewIntent()");
    if( intent.getBooleanExtra("EXIT", false) ) {
      Log.i(TAG, "Requested to finish from notification");
      shutdown();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    Log.i(TAG, "*** onStop()");
    tryUnregisterBluetoothOnOffStateReceiver();
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "*** onDestroy()");

    super.onDestroy();
  }

  private void shutdown() {
    Log.i(TAG, "*** shutdown()");
    stopLogging();
    removeFromNotificationBar();
    userClickedDisconnectHrSensor();
    SP.unregisterOnSharedPreferenceChangeListener(this);
    tryUnregisterBluetoothOnOffStateReceiver();
    tryUnbindHRSensorService();
    Intent i = new Intent(this, HRSensorService.class);
    stopService(i);
    finish();
  }

  private void addToNotificationBar() {
    final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    final Intent resultIntent = new Intent(this, MainActivity.class);
    //final Intent resultIntentExit = new Intent(this, MainActivity.class).putExtra("EXIT", true);
    final PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    //final PendingIntent resultPendingIntentExit = PendingIntent.getActivity(this, 0, resultIntentExit, PendingIntent.FLAG_UPDATE_CURRENT);
    final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.heart_512)
            .setContentTitle("Heart Rate Monitor")
            .setContentText(">> status tbd <<")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true) // prevent removal
            .addAction(R.drawable.heart_notification_128, "Show", resultPendingIntent);
            //.addAction(R.drawable.abc_btn_radio_material, "Exit", resultPendingIntentExit);

    notificationBuilder.setContentIntent(resultPendingIntent);
    Log.i(TAG, "=== add notification ===");
    notificationManager.notify(NOTIFICATION_MAIN, notificationBuilder.build());
  }

  private void tryRegisterBluetoothOnOffStateReceiver() {
    createBluetoothOnOffStateReceiver();
    if (!isBluetoothOnOffStateReceiverRegistered) {
      registerReceiver(bluetoothOnOffStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
      isBluetoothOnOffStateReceiverRegistered = true;
      Log.d(TAG, "Registered Bluetooth ON/OFF state receiver");
    }
  }

  private void tryUnregisterBluetoothOnOffStateReceiver() {
    if (isBluetoothOnOffStateReceiverRegistered) {
      unregisterReceiver(bluetoothOnOffStateReceiver);
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

    playAlertIfOutsideHrRange = SP.getBoolean(getString(R.string.setting_alert_outside_hr_range), false);
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
    if (getString(R.string.setting_alert_outside_hr_range).equals(key)) {
      playAlertIfOutsideHrRange = sharedPreferences.getBoolean(key, false);
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
      case R.id.menu_exit:
        shutdown();
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }


  private void createHeartRateLogger() {
    heartRateLogger = new HeartRateFullLogger(this, username);
  }

  private void populateUiVariables() {
    setContentView(R.layout.activity_main);
    toolbar = (Toolbar) findViewById(R.id.toolbar);
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
    loggingToggleButton = (ToggleButton) findViewById(R.id.loggingToggleButton);
    loggingToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
          startLogging();
          play(HrAudioEnum.HI);
        } else {
          stopLogging();
          play(HrAudioEnum.NORMAL);
        }
      }
    });
    orientationToggleButton = (ToggleButton) findViewById(R.id.orientationToggleButton);
    orientationToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switchToPortraitOrientation(isChecked);
      }
    });
    mockToggleButton = (ToggleButton) findViewById(R.id.mockToggleButton);
    mockToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        toggleHrMocking(isChecked);
        if (isChecked) {
          play(HrAudioEnum.HIHI);
        } else  {
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

  private void startLogging() {
    if (heartRateLogger != null) {
      heartRateLogger.enable();
    }
  }

  private void stopLogging() {
    if (heartRateLogger != null) {
      heartRateLogger.disable();
    }
    heartRateMemoryDataProgressBar.setProgress(0);
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


  private void tryUnregisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "tryUnregisterHRSensorBroadcastReceiver()");
    if (isHRrSensorBroadcastReceiverRegistered) {
      unregisterReceiver(hrSensorBroadcastReceiver);
      isHRrSensorBroadcastReceiverRegistered = false;
    }
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
    if (hrSensorConnectionState == CONNECTED) {
      hrSensorConnectedIndicatorImageView.setVisibility(View.VISIBLE);
    } else {
      hrSensorConnectedIndicatorImageView.setVisibility(View.INVISIBLE);
    }
  }

  private void tryRegisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "tryRegisterHRSensorBroadcastReceiver()");
    if (!isHRrSensorBroadcastReceiverRegistered) {
      registerReceiver(hrSensorBroadcastReceiver, hrSensorServiceIntentFilter);
      isHRrSensorBroadcastReceiverRegistered = true;
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
    if (!serviceBound) {
      boolean bindResult = bindService(
              new Intent(this, HRSensorService.class), hrSensorServiceConnection, BIND_AUTO_CREATE);
      Log.i(TAG, "Bind to HR Sensor service success? " + bindResult);
    }
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

  private void tryUnbindHRSensorService() {
    if (serviceBound) {
      tryUnregisterHRSensorBroadcastReceiver();
      Log.i(TAG, "Unbinding from HR Sensor service...");
      unbindService(hrSensorServiceConnection);
      hrSensorService = null;
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
    setIsBackToNormalHrRange(lastHeartRate, newHeartRate);
    playHeartRateAlert(newHeartRate);
    int progress = heartRateLogger.onHeartRateChange(lastHeartRate, newHeartRate);
    refreshHeartRateUi(newHeartRate);
    heartRateMemoryDataProgressBar.setProgress(progress);

    lastHeartRate = newHeartRate;
  }

  private void setIsBackToNormalHrRange(final int oldHeartRate, final int newHeartRate) {
    if ((oldHeartRate > maxHeartRate || oldHeartRate < minHeartRate) &&
            (newHeartRate <= maxHeartRate && newHeartRate >= minHeartRate)) {
      isBackToHrRange = true;
      alertsTemporarilyMuted = false;
    } else {
      isBackToHrRange = false;
    }
  }

  private void playHeartRateAlert(final int newHeartRate) {
    if (!playAlertIfOutsideHrRange) {
      return;
    }

    if (isBackToHrRange) {
      play(HrAudioEnum.NORMAL);
      isBackToHrRange = false;
      return;
    }
    if (alertsTemporarilyMuted) {
      return;
    }
    if (newHeartRate > maxHeartRate + thresholdAboveMaxHeartRate) {
      play(HrAudioEnum.HIHI);
      muteAlertsForXSeconds(minIntervalBetweenAlertsInSeconds);
      return;
    }
    if (newHeartRate > maxHeartRate) {
      play(HrAudioEnum.HI);
      muteAlertsForXSeconds(minIntervalBetweenAlertsInSeconds);
      return;
    }
    if (newHeartRate < minHeartRate) {
      play(HrAudioEnum.LO);
      muteAlertsForXSeconds(minIntervalBetweenAlertsInSeconds);
    }
  }

  private void muteAlertsForXSeconds(int minIntervalBetweenAlertsInSeconds) {
    alertsTemporarilyMuted = true;
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        Log.d(TAG, "muteAlertsForXSeconds().run() in thread: " + Thread.currentThread().getName());
        alertsTemporarilyMuted = false;
      }
    }, minIntervalBetweenAlertsInSeconds * 1000);
  }


  private void refreshHeartRateUi(int newHeartRate) {
    heartRateText.setTextColor(calculateHeartRateTextColor(newHeartRate));
    if (hrMockingActive) {
      heartRateText.setTypeface(null, Typeface.ITALIC);
    } else {
      heartRateText.setTypeface(null, Typeface.NORMAL);
    }
    heartRateText.setText(Integer.toString(newHeartRate));
  }

  private void setHeartRatePending() {

    heartRateText.setTextColor(Color.BLACK);
    heartRateText.setText(R.string.heartrate_pending);
    lastHeartRate = 0;
  }

  private void setHeartRateUnknown() {
    heartRateText.setTextColor(Color.BLACK);
    heartRateText.setText(R.string.heartrate_unknown);
    lastHeartRate = 0;
  }

  private void setHeartRateError() {
    heartRateText.setTextColor(Color.BLACK);
    heartRateText.setText(R.string.heartrate_error);
    lastHeartRate = 0;
  }


  private int calculateHeartRateTextColor(final int newHR) {
    if (newHR > maxHeartRate) {
      return Color.RED;
    }
    if (newHR < minHeartRate) {
      return Color.BLACK;
    }
//    if (newHR > oldHR) {
//      return Color.MAGENTA;
//    }
    return Color.rgb(0, 128, 0);
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
  private final BroadcastReceiver hrSensorBroadcastReceiver = new BroadcastReceiver() {
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

  private class HrSensorServiceConnection implements ServiceConnection  {
    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      HRSensorService.LocalBinder binder = (HRSensorService.LocalBinder) service;
      hrSensorService = binder.getService();
      tryRegisterHRSensorBroadcastReceiver();
      serviceBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      setHeartRateUnknown();
      serviceBound = false;
    }

  }

}


