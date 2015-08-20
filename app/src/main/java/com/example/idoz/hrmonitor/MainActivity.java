package com.example.idoz.hrmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.example.idoz.hrmonitor.ble.BleServiceConnection;
import com.example.idoz.hrmonitor.ble.HRSensorService;

import static com.example.idoz.hrmonitor.ConnectionState.*;


public class MainActivity extends AppCompatActivity {

  private final static String TAG = MainActivity.class.getSimpleName();
  private ConnectionState connectionState = DISCONNECTED;
  private int lastHeartRate = 0;
  private TextView heartRateText;
  private HRSensorService hrSensorBroadcastService;
  private boolean serviceBound = false;
  private final BleServiceConnection bleServiceConnection = new BleServiceConnection();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    heartRateText = (TextView) findViewById(R.id.heartRateText);
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
    int id = item.getItemId();
    if (id == R.id.menu_connect) {
      connectHRSensor();
      return true;
    }
    if (id == R.id.menu_disconnect) {
      disconnectHRSensor();

      return true;
    }
    return super.onOptionsItemSelected(item);
  }


  @Override
  protected void onPause() {
    super.onPause();
    disconnectHRSensor();
    unregisterReceiver(hrSensorBroadcastReceiver);
  }

  @Override
  protected void onResume() {
    super.onResume();
    registerThisAsHrSensorReceiver();

  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    tryUnbindService();
    hrSensorBroadcastService = null;
  }

  private void registerThisAsHrSensorReceiver() {
    registerReceiver(hrSensorBroadcastReceiver, hrSensorIntentFilters());
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

  private void tryBindService(Intent hrSensorServiceIntent) {
    if( !serviceBound ) {
      bindService(hrSensorServiceIntent, bleServiceConnection, BIND_AUTO_CREATE);
      serviceBound = true;
    }
    setHeartRatePending();
  }

  private void setHeartRatePending() {
    heartRateText.setText(R.string.heartrate_pending);
    lastHeartRate = 0;
  }

  private void disconnectHRSensor() {
    Log.i(TAG, "disconnectHRSensor()");
    connectionState = DISCONNECTING;
    tryUnbindService();
    connectionState = DISCONNECTED;
    invalidateOptionsMenu();
  }

  private void tryUnbindService() {
    if( serviceBound ) {
      unbindService(bleServiceConnection);
      serviceBound = false;
      setHeartRateUnknown();
    }
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
      } else if(HRSensorService.STATUS_HR_NOT_SUPPORTED.equals(action)) {
        Toast.makeText(getBaseContext(), "Heart Rate not supported!", Toast.LENGTH_LONG).show();
      } else if (HRSensorService.ACTION_DATA_AVAILABLE.equals(action)) {
        refreshHeartRateText(intent.getStringExtra(HRSensorService.EXTRA_DATA));
      }
    }
  };

  private void refreshHeartRateText(String data) {
    if (data != null) {
      try {
        final int newHeartRate = Integer.parseInt(data);
        if( newHeartRate > lastHeartRate ) {
          heartRateText.setTextColor(Color.RED);
        } else  {
          heartRateText.setTextColor(Color.BLUE);
        }
        heartRateText.setText(data);
        lastHeartRate = newHeartRate;
      } catch (NumberFormatException e) {
        heartRateText.setText(R.string.heartrate_error);
        lastHeartRate = 0;
      }

    }
  }


  private static IntentFilter hrSensorIntentFilters() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(HRSensorService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(HRSensorService.ACTION_GATT_DISCONNECTED);
//    intentFilter.addAction(HRSensorService.ACTION_GATT_SERVICES_DISCOVERED);
    intentFilter.addAction(HRSensorService.ACTION_DATA_AVAILABLE);
    return intentFilter;
  }

}
