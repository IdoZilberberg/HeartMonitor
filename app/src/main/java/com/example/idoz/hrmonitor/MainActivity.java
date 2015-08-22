package com.example.idoz.hrmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.example.idoz.hrmonitor.ble.HRSensorServiceConnection;
import com.example.idoz.hrmonitor.ble.HRSensorService;

import static com.example.idoz.hrmonitor.ConnectionState.*;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final static String TAG = MainActivity.class.getSimpleName();

  private static final int MIN_HR_NOT_SET = -1;
  private static final String MIN_HR_NOT_SET_STR = Integer.toString(MIN_HR_NOT_SET);
  private static final int MAX_HR_NOT_SET = 999;
  private static final String MAX_HR_NOT_SET_STR = Integer.toString(MAX_HR_NOT_SET);
  private ConnectionState connectionState = DISCONNECTED;
  private int lastHeartRate = 0;

  private TextView heartRateText;
  private TextView usernameText;
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;
  private SharedPreferences SP;
  private boolean serviceBound = false;
  private boolean isHRrSensorBroadcastReceiverRegistered = false;
  private final HRSensorServiceConnection hrSensorServiceConnection = new HRSensorServiceConnection();
  private final IntentFilter hrSensorServiceIntentFilter = createHrSensorIntentFilters();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    heartRateText = (TextView) findViewById(R.id.heartRateText);
    usernameText = (TextView) findViewById(R.id.usernameText);
    initPrefs();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);

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
    unregisterHRSensorBroadcastReceiver();
  }

  private void unregisterHRSensorBroadcastReceiver() {
    Log.i(TAG, "unregisterReceiver()");
    if( isHRrSensorBroadcastReceiverRegistered ) {
      unregisterReceiver(hrSensorBroadcastReceiver);
      isHRrSensorBroadcastReceiverRegistered = false;
    }
  }


  @Override
  protected void onResume() {
    Log.i(TAG, "*** onResume()");
    super.onResume();
    registerHRSensorBroadcastReceiver();
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "*** onDestroy()");
    super.onDestroy();
    tryUnbindService();
    SP.unregisterOnSharedPreferenceChangeListener(this);
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
    setHeartRatePending();
  }

  private void registerHRSensorBroadcastReceiver() {
    Log.i(TAG, "registerReceiver");
    if( !isHRrSensorBroadcastReceiverRegistered ) {
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

  private void setHeartRatePending() {
    heartRateText.setText(R.string.heartrate_pending);
    lastHeartRate = 0;
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
    invalidateOptionsMenu();

  }

  private void disconnectHRSensor() {
    Log.i(TAG, "disconnectHRSensor()");
    connectionState = DISCONNECTING;
    tryUnbindService();
    connectionState = DISCONNECTED;
    invalidateOptionsMenu();
  }

  private void setHeartRateUnknown() {
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
        Log.i(TAG, "HR sensor connected");
        invalidateOptionsMenu();
      } else if (HRSensorService.ACTION_GATT_DISCONNECTED.equals(action)) {
        connectionState = DISCONNECTED;
        Log.i(TAG, "HR sensor disconnected");
        invalidateOptionsMenu();
      } else if (HRSensorService.STATUS_HR_NOT_SUPPORTED.equals(action)) {
        Toast.makeText(getBaseContext(), "Heart Rate not supported!", Toast.LENGTH_LONG).show();
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
      heartRateText.setText(R.string.heartrate_error);
      lastHeartRate = 0;
    }

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
