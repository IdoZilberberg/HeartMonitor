package com.example.idoz.hrmonitor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
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

import com.example.idoz.hrmonitor.AudioTrackPlayer.HrAudioEnum;

import org.joda.time.DateTime;

import java.util.LinkedList;
import java.util.List;

import static com.example.idoz.hrmonitor.ConnectionState.*;


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
  private static final int maxHrCutoff = 200; // omit outlier values
  private static final int minHrCutoff = 30; // omit outlier values

  private static final IntentFilter hrSensorServiceIntentFilter = createHrSensorIntentFilters();

  // state (mutable)
  private ConnectionState hrSensorConnectionState = DISCONNECTED;
  private boolean bluetoothEnabled = false;
  private boolean serviceBound = false;
  private boolean autoReconnect = true;
  private boolean isBluetoothStateReceiverRegistered = false;
  private boolean isHRrSensorBroadcastReceiverRegistered = false;
  private int lastHeartRate = 0;
  private Handler handler;
  private boolean isLogging = false;
  private boolean canPlayAlerts = true;

  // view elements
  private TextView heartRateText;
  private TextView usernameText;
  private ImageView hrSensorConnectedIndicatorImageView;
  private ProgressBar heartRateMemoryDataProgressBar;

  // prefs
  private SharedPreferences SP;
  private String username = "NA";
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;
  private int maxHeartRateRecordsInMemory;
  private boolean alertOutsideHrRange;
  private final static String heartRateLoggerFilenamePrefix = "heartRate_";

  // connections
  private HRSensorService hrSensorService;
  private BroadcastReceiver bluetoothStateReceiver;
  private HeartRateDao dao;
  private AudioTrackPlayer audioTrackPlayer;

  private List<HeartRateRecord> records = new LinkedList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    exitIfNoBluetoothCapability();
    initPrefs();
    populateUiVariables();
    createBluetoothStateReceiver();
    createDao();
    registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    handler = new Handler();
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    audioTrackPlayer = new AudioTrackPlayer();
  }

  private void tryRegisterBluetoothStateReceiver() {
    createBluetoothStateReceiver();
    if (!isBluetoothStateReceiverRegistered) {
      registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
      isBluetoothStateReceiverRegistered = true;
    }
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
    tryRegisterBluetoothStateReceiver();
    tryBindHRSensorService();
    if (autoReconnect) {
      connectHRSensor();
    }
  }


  @Override
  protected void onStop() {
    super.onStop();
    Log.i(TAG, "*** onStop()");
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "*** onDestroy()");
    super.onDestroy();
    stopLogging();
    disconnectHRSensor();
    SP.unregisterOnSharedPreferenceChangeListener(this);
    tryUnregisterBluetoothStateReceiver();
    tryUnbindHRSensorService();
  }

  private void tryUnregisterBluetoothStateReceiver() {
    if (isBluetoothStateReceiverRegistered) {
      unregisterReceiver(bluetoothStateReceiver);
      isBluetoothStateReceiverRegistered = false;
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);

    if (!bluetoothEnabled) {
      menu.findItem(R.id.menu_connect).setVisible(true);
      menu.findItem(R.id.menu_connect).setEnabled(false);
      menu.findItem(R.id.menu_disconnect).setVisible(false);
      return true;
    }

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

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int id = item.getItemId();
    switch (id) {
      case R.id.menu_connect:
        autoReconnect = true;
        connectHRSensor();
        return true;
      case R.id.menu_disconnect:
        autoReconnect = false;
        disconnectHRSensor();
        return true;
      case R.id.menu_settings:
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
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
      alertOutsideHrRange = sharedPreferences.getBoolean(key, false);
    }
  }


  private void createDao() {
    dao = new HeartRateCsvDao(heartRateLoggerFilenamePrefix, maxHrCutoff, minHrCutoff);
  }

  private void populateUiVariables() {
    setContentView(R.layout.activity_main);
    heartRateText = (TextView) findViewById(R.id.heartRateText);
    usernameText = (TextView) findViewById(R.id.usernameText);
    usernameText.setText(username);
    hrSensorConnectedIndicatorImageView = (ImageView) findViewById(R.id.btConnectedIndicatorImage);
    heartRateMemoryDataProgressBar = (ProgressBar) findViewById(R.id.heartRateMemoryDataProgressBar);
    heartRateMemoryDataProgressBar.setProgress(0);
    heartRateMemoryDataProgressBar.setMax(maxHeartRateRecordsInMemory);
    heartRateMemoryDataProgressBar.setScaleY(3f);
    ToggleButton loggingToggleButton = (ToggleButton) findViewById(R.id.loggingToggleButton);
    loggingToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
          startLogging();
          play(HrAudioEnum.HI);
        } else {
          stopLogging();
          play(HrAudioEnum.LO);
        }
      }
    });
  }

  private void play(final HrAudioEnum audioToPlay) {
    audioTrackPlayer.play(getBaseContext(), audioToPlay);
  }

  private void startLogging() {
    isLogging = true;
    Log.i(TAG, "Logging started");
  }

  private void stopLogging() {
    isLogging = false;

    final int count = flushHeartRateMemoryToStorage();
    Log.i(TAG, "Logging stopped, saved " + count + " records. -1 denotes error.");
  }

  private int flushHeartRateMemoryToStorage() {
    final int count = dao.saveHeartRateRecords(this, records);
    if (count == -1) {
      Toast.makeText(this, "Error saving log file", Toast.LENGTH_LONG).show();
    }
    records.clear();
    heartRateMemoryDataProgressBar.setProgress(0);
    return count;
  }


  private void refreshUi() {
    refreshHRSensorConnectionIndicator();
    invalidateOptionsMenu();
  }

  private void createBluetoothStateReceiver() {

    if (bluetoothStateReceiver != null) {
      return;
    }

    bluetoothStateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
          switch (bluetoothState) {
            case BluetoothAdapter.STATE_ON:
              bluetoothEnabled = true;
              Log.i(TAG, ">->-> Bluetooth is ON <-<-<");
              break;
            case BluetoothAdapter.STATE_OFF:
              bluetoothEnabled = false;
              Log.i(TAG, ">->-> Bluetooth is OFF <-<-<");
              break;
            default:
              break;
          }
        }
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

    if (!bluetoothAdapter.isEnabled()) {
      bluetoothEnabled = false;
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
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

  private void initPrefs() {
    SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    username = SP.getString(getString(R.string.setting_username), getString(R.string.default_username));
    maxHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_max_hr), MAX_HR_NOT_SET_STR));
    minHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_min_hr), MIN_HR_NOT_SET_STR));
    maxHeartRateRecordsInMemory = 10; // To put in prefs
    SP.registerOnSharedPreferenceChangeListener(this);
  }

  private void tryRegisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "tryRegisterHRSensorBroadcastReceiver()");
    if (!isHRrSensorBroadcastReceiverRegistered) {
      registerReceiver(hrSensorBroadcastReceiver, hrSensorServiceIntentFilter);
      isHRrSensorBroadcastReceiverRegistered = true;
    }
  }

  private void connectHRSensor() {

    if (CONNECTED == hrSensorConnectionState) {
      Log.i(TAG, "connectHRSensor(): already connected");
      return;
    }
    if (hrSensorService != null) {
      hrSensorConnectionState = CONNECTING;
      Log.i(TAG, "connectHRSensor() waiting to connect for " + connectionTimeoutDelayMillis + " millis");
      setHeartRatePending();
      refreshUi();
      handler.removeCallbacks(onConnectionTimeout);
      handler.postDelayed(onConnectionTimeout, connectionTimeoutDelayMillis);
      hrSensorService.connectToDevice();
    }

  }

  private void tryBindHRSensorService() {
    if (!serviceBound) {
      Intent hrSensorServiceIntent = new Intent(this, HRSensorService.class);
      boolean bindResult = bindService(hrSensorServiceIntent, hrSensorServiceConnection, BIND_AUTO_CREATE);
      Log.i(TAG, "Bind to HR Sensor service success? " + bindResult);
    }
  }

  private void disconnectHRSensor() {
    Log.i(TAG, "disconnectHRSensor()");
    if (DISCONNECTED == hrSensorConnectionState) {
      Log.i(TAG, "disconnectHRSensor(): already disconnected.");
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
    try {
      final int newHeartRate = Integer.parseInt(data);
      checkAgainstHrRange(newHeartRate);
      refreshHeartRateText(newHeartRate);
      lastHeartRate = newHeartRate;
      addRecordIfLogging(newHeartRate);
    } catch (NumberFormatException e) {
      setHeartRateError();
    }

  }

  private void checkAgainstHrRange(final int newHeartRate) {
    if (!canPlayAlerts) {
      return;
    }
    if (newHeartRate > maxHeartRate && alertOutsideHrRange) {
      play(HrAudioEnum.HI);
    }
    if (newHeartRate < minHeartRate && alertOutsideHrRange) {
      play(HrAudioEnum.LO);
    }
  }

  private void addRecordIfLogging(int newHeartRate) {
    if (!isLogging) {
      return;
    }

    if (records.size() >= maxHeartRateRecordsInMemory) {
      flushHeartRateMemoryToStorage();
      //records.remove(0); // cut oldest record
    }
    records.add(new HeartRateRecord(username, new DateTime(), newHeartRate));
    heartRateMemoryDataProgressBar.setProgress(records.size());
  }

  private void refreshHeartRateText(int newHeartRate) {
    heartRateText.setTextColor(calculateHeartRateTextColor(newHeartRate, lastHeartRate));
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


  private int calculateHeartRateTextColor(final int newHR, final int oldHR) {
    if (newHR > maxHeartRate) {
      return Color.RED;
    }
    if (newHR < minHeartRate) {
      return Color.BLACK;
    }
    if (newHR > oldHR) {
      return Color.MAGENTA;
    }
    return Color.BLUE;
  }

  private Runnable reconnectAfterDisconnect = new Runnable() {
    @Override
    public void run() {
      connectHRSensor();
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

  /**
   * Defines callbacks for service binding, passed to bindService()
   */
  private ServiceConnection hrSensorServiceConnection = new ServiceConnection() {

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
  };

  private void exitIfNoBluetoothCapability() {
    // Use this check to determine whether BLE is supported on the device.  Then you can
    // selectively disable BLE-related features.
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
    // BluetoothAdapter through BluetoothManager.
    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

    // Checks if Bluetooth is supported on the device.
    if (bluetoothAdapter == null) {
      Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();

      finish();
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

}


