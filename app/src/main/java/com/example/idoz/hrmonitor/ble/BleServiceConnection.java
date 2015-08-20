package com.example.idoz.hrmonitor.ble;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by izilberberg on 8/6/15.
 * // Code to manage Service lifecycle.
 */
public class BleServiceConnection implements ServiceConnection {

  private final static String TAG = BleServiceConnection.class.getSimpleName();

  private HRSensorService HRSensorService;

  @Override
  public void onServiceConnected(ComponentName componentName, IBinder service) {

    HRSensorService = ((HRSensorService.LocalBinder) service).getService();
//    if (!HRSensorService.initialize()) {
//      Log.e(TAG, "Unable to initialize Bluetooth");
//    }
//    // Automatically connects to the device upon successful start-up initialization.
//    HRSensorService.connectToDevice(mDeviceAddress);
  }

  @Override
  public void onServiceDisconnected(ComponentName componentName) {
    HRSensorService = null;
  }
}
