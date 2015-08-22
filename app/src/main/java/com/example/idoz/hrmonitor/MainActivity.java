package com.example.idoz.hrmonitor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.idoz.hrmonitor.ble.HRSensorServiceConnection;
import com.example.idoz.hrmonitor.ble.HRSensorService;

import static com.example.idoz.hrmonitor.ConnectionState.*;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final static String TAG = MainActivity.class.getSimpleName();

  // consts
  private static final int MIN_HR_NOT_SET = -1;
  private static final String MIN_HR_NOT_SET_STR = Integer.toString(MIN_HR_NOT_SET);
  private static final int MAX_HR_NOT_SET = 999;
  private static final String MAX_HR_NOT_SET_STR = Integer.toString(MAX_HR_NOT_SET);
  private static final int REQUEST_ENABLE_BT = 1;

  private static final IntentFilter hrSensorServiceIntentFilter = createHrSensorIntentFilters();

  // state (mutable)
  private ConnectionState connectionState = DISCONNECTED;
  private boolean bluetoothEnabled = false;
  private boolean serviceBound = false;
  private boolean isHRrSensorBroadcastReceiverRegistered = false;
  private int lastHeartRate = 0;

  // view elements
  private TextView heartRateText;
  private TextView usernameText;
  private ImageView btConnectedIndicatorImageView;

  // prefs
  private SharedPreferences SP;
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;

  private HRSensorServiceConnection hrSensorServiceConnection;
  private BroadcastReceiver bluetoothStateReceiver;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    initUi();
    verifyBluetoothCapability();
    initPrefs();
    initExternals();
  }

  private void initExternals() {

    hrSensorServiceConnection = new HRSensorServiceConnection();
    bluetoothStateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
            bluetoothEnabled = true;
          } else {
            bluetoothEnabled = false;
          }
          refreshUi();
        }
      }
    };

  }

  private void initUi() {
    setContentView(R.layout.activity_main);
    heartRateText = (TextView) findViewById(R.id.heartRateText);
    usernameText = (TextView) findViewById(R.id.usernameText);
    btConnectedIndicatorImageView = (ImageView) findViewById(R.id.btConnectedIndicatorImage);
    refreshUi();
  }

  private void verifyBluetoothCapability() {
    // Use this check to determine whether BLE is supported on the device.  Then you can
    // selectively disable BLE-related features.
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
      finish();
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
      return;
    }

    registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);

    if( !bluetoothEnabled ) {
      menu.findItem(R.id.menu_connect).setVisible(true);
      menu.findItem(R.id.menu_connect).setEnabled(false);
      menu.findItem(R.id.menu_disconnect).setVisible(false);
      return true;
    }

    if (CONNECTED == connectionState) {
      menu.findItem(R.id.menu_connect).setVisible(false);
      menu.findItem(R.id.menu_disconnect).setVisible(true);
    } else {
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
        connectHRSensor();
        return true;
      case R.id.menu_disconnect:
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
  protected void onPause() {
    Log.i(TAG, "*** onPause()");
    super.onPause();
    disconnectHRSensor();
    //unregisterHRSensorBroadcastReceiver();
  }

  private void unregisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "unregisterReceiver()");
    if (isHRrSensorBroadcastReceiverRegistered) {
      unregisterReceiver(hrSensorBroadcastReceiver);
      isHRrSensorBroadcastReceiverRegistered = false;
    }
  }


  @Override
  protected void onResume() {
    Log.i(TAG, "*** onResume()");
    super.onResume();
    // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
    // fire an intent to display a dialog asking the user to grant permission to enable it.
    verifyBluetoothEnabled();

    connectHRSensor();
    //registerHRSensorBroadcastReceiver();
  }

  private void verifyBluetoothEnabled() {

    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    } else  {
      bluetoothEnabled = true;
      refreshUi();
    }
  }

  /*
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
  */


  @Override
  protected void onDestroy() {
    Log.i(TAG, "*** onDestroy()");
    super.onDestroy();
    tryUnbindService();
    SP.unregisterOnSharedPreferenceChangeListener(this);
  }

  private void refreshUi()  {
    setBluetoothConnectionIndicator();
    invalidateOptionsMenu();
  }

  private void setBluetoothConnectionIndicator() {
    if( connectionState == CONNECTED ) {
      btConnectedIndicatorImageView.setVisibility(View.VISIBLE);
    } else  {
      btConnectedIndicatorImageView.setVisibility(View.INVISIBLE);
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
  }

  private void initPrefs() {
    SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    String username = SP.getString(getString(R.string.setting_username), getString(R.string.default_username));
    maxHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_max_hr), MAX_HR_NOT_SET_STR));
    minHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_min_hr), MIN_HR_NOT_SET_STR));
    usernameText.setText(username);
    SP.registerOnSharedPreferenceChangeListener(this);
  }

  private void tryBindService(Intent hrSensorServiceIntent) {
    if (!serviceBound) {
      bindHRSensorServiceConnection(hrSensorServiceIntent);
      registerHRSensorBroadcastReceiver();
      serviceBound = true;
    }
  }

  private void registerHRSensorBroadcastReceiver() {
    Log.i(TAG, "registerReceiver");
    if (!isHRrSensorBroadcastReceiverRegistered) {
      registerReceiver(hrSensorBroadcastReceiver, hrSensorServiceIntentFilter);
      isHRrSensorBroadcastReceiverRegistered = true;
    }
  }

  private void bindHRSensorServiceConnection(Intent hrSensorServiceIntent) {
    Log.i(TAG, "bindService()");
    bindService(hrSensorServiceIntent, hrSensorServiceConnection, BIND_AUTO_CREATE);
  }

  private void tryUnbindService() {
    if (serviceBound) {
      unregisterHRSensorBroadcastReceiver();
      unbindHRSensorServiceConnection();
      serviceBound = false;
      setHeartRateUnknown();
    }
    connectionState = DISCONNECTED;
  }

  private void unbindHRSensorServiceConnection() {
    Log.i(TAG, "unbindService()");
    unbindService(hrSensorServiceConnection);
  }

  private void connectHRSensor() {
    Log.i(TAG, "connectHRSensor()");
    if (CONNECTED == connectionState) {
      return;
    }

    connectionState = CONNECTING;
    Intent hrSensorServiceIntent = new Intent(this, HRSensorService.class);
    tryBindService(hrSensorServiceIntent);
    connectionState = CONNECTED;
    refreshUi();

  }

  private void disconnectHRSensor() {
    Log.i(TAG, "disconnectHRSensor()");
    connectionState = DISCONNECTING;
    tryUnbindService();
    connectionState = DISCONNECTED;
    refreshUi();
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

  // Handles various events fired by the Service.
  // ACTION_GATT_CONNECTED: connected to a HR sensor
  // ACTION_GATT_DISCONNECTED: disconnected from HR sensor
  // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
  //                        or notification operations.
  private final BroadcastReceiver hrSensorBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      final String action = intent.getAction();
      if (HRSensorService.ACTION_GATT_CONNECTED.equals(action)) {
        connectionState = CONNECTED;
        Log.i(TAG, ">>> HR sensor connected");
        setHeartRatePending();
        refreshUi();
      } else if (HRSensorService.ACTION_GATT_DISCONNECTED.equals(action)) {
        connectionState = DISCONNECTED;
        Log.i(TAG, ">>> HR sensor disconnected");
        setHeartRateUnknown();
        refreshUi();
      } else if (HRSensorService.STATUS_HR_NOT_SUPPORTED.equals(action)) {
        Toast.makeText(getBaseContext(), "Heart Rate not supported!", Toast.LENGTH_LONG).show();
        setHeartRateError();
      } else if (HRSensorService.ACTION_DATA_AVAILABLE.equals(action)) {
        refreshHeartRateText(intent.getStringExtra(HRSensorService.EXTRA_DATA));
      }
    }
  };

  private void refreshHeartRateText(final String data) {
    if (data == null) {
      return;
    }
    try {
      final int newHeartRate = Integer.parseInt(data);
      heartRateText.setTextColor(calculateHeartRateTextColor(newHeartRate, lastHeartRate));
      heartRateText.setText(data);
      lastHeartRate = newHeartRate;
    } catch (NumberFormatException e) {
      setHeartRateError();
    }

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

  private static IntentFilter createHrSensorIntentFilters() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(HRSensorService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(HRSensorService.ACTION_GATT_DISCONNECTED);
    intentFilter.addAction(HRSensorService.ACTION_DATA_AVAILABLE);
    return intentFilter;
  }

}
