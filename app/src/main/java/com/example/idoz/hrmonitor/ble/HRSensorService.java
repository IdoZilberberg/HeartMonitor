/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.idoz.hrmonitor.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 * <p>
 * Copied as-is from BluetoothLeGatt sample
 */
public class HRSensorService extends Service {

  private final static String TAG = HRSensorService.class.getSimpleName();
  private BluetoothManager bluetoothManager;
  private BluetoothAdapter bluetoothAdapter;

  private String deviceAddress;
  private BluetoothGatt deviceCommunicator;
  BluetoothGattCharacteristic heartRateCharacteristic = null;

  private int mConnectionState = STATE_DISCONNECTED;

  private static final int STATE_DISCONNECTED = 0;
  private static final int STATE_CONNECTING = 1;

  private static final int STATE_CONNECTED = 2;

  public static final String STATUS_HR_NOT_SUPPORTED =
          "com.example.bluetooth.le.STATUS_HR_NOT_SUPPORTED";
  public final static String STATUS_NO_BLUETOOTH =
          "com.example.bluetooth.le.STATUS_NO_BLUETOOTH";
  public static final java.lang.String STATUS_NO_BLUETOOTH_LE =
          "com.example.bluetooth.le.STATUS_NO_BLUETOOTH_LE";
  public final static String ACTION_GATT_CONNECTED =
          "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
  public final static String ACTION_GATT_DISCONNECTED =
          "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
  public final static String ACTION_GATT_SERVICES_DISCOVERED =
          "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
  public final static String ACTION_DATA_AVAILABLE =
          "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
  public final static String EXTRA_DATA =
          "com.example.bluetooth.le.EXTRA_DATA";

  public final static String deviceAddressConst = "00:22:D0:76:1C:E9"; // my device!

  public final static UUID UUID_HEART_RATE_MEASUREMENT =
          UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

  public class LocalBinder extends Binder {
    HRSensorService getService() {
      return HRSensorService.this;
    }
  }

  private final IBinder mBinder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent) {
    Log.i(TAG, "Binding HR Sensor service");
    enableBluetooth();
    connectToDevice(deviceAddressConst);
    return mBinder;
  }

  private void enableBluetooth() {
    // Use this check to determine whether BLE is supported on the device.  Then you can
    // selectively disable BLE-related features.
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Log.e(TAG, "BLE not supported!");
      broadcastAction(STATUS_NO_BLUETOOTH_LE);
      return;
    }

    // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
    // BluetoothAdapter through BluetoothManager.
    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    bluetoothAdapter = bluetoothManager.getAdapter();

    // Checks if Bluetooth is supported on the device.
    if (bluetoothAdapter == null) {
      Log.e(TAG, "Bluetooth not supported!");
      broadcastAction(STATUS_NO_BLUETOOTH);
      return;
    }

  }

  @Override
  public boolean onUnbind(Intent intent) {
    // After using a given device, you should make sure that BluetoothGatt.close() is called
    // such that resources are cleaned up properly.  In this particular example, close() is
    // invoked when the UI is disconnected from the Service.
    disconnectFromDevice();
    close();
    return super.onUnbind(intent);
  }


  // Implements callback methods for GATT events that the app cares about.  For example,
  // connection change and services discovered.
  private final BluetoothGattCallback deviceCallback = new BluetoothGattCallback() {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      String intentAction;
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        intentAction = ACTION_GATT_CONNECTED;
        mConnectionState = STATE_CONNECTED;
        broadcastAction(intentAction);
        Log.i(TAG, "deviceCallback - Connected to GATT server.");
        // Attempts to discover services after successful connection.
        Log.i(TAG, "deviceCallback - Attempting to start service discovery:" +
                deviceCommunicator.discoverServices());

      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        intentAction = ACTION_GATT_DISCONNECTED;
        mConnectionState = STATE_DISCONNECTED;
        Log.i(TAG, "Disconnected from GATT server.");
        broadcastAction(intentAction);
      }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.i(TAG, "deviceCallback - finished discovering services, enabling heart rate characteristic broadcast...");
        locateHeartRateCharacteristic(gatt.getServices());
        setCharacteristicNotification(heartRateCharacteristic, true);
      } else {
        Log.w(TAG, "deviceCallback - might have failed to discover services. Status received: " + status);
      }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.v(TAG, "deviceCallback - read characteristic " + characteristic);
        broadcastData(ACTION_DATA_AVAILABLE, characteristic);
      }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
      Log.v(TAG, "deviceCallback - changed characteristic " + characteristic);
      broadcastData(ACTION_DATA_AVAILABLE, characteristic);
    }
  };

  private void locateHeartRateCharacteristic(List<BluetoothGattService> services) {
    heartRateCharacteristic = null;
    for( BluetoothGattService service : services) {
      if( SampleGattAttributes.HEART_RATE_SERVICE.equals(service.getUuid().toString()))  {
        heartRateCharacteristic = service.getCharacteristic(UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT));
        break;
      }
    }
    if( heartRateCharacteristic == null ) {
      Log.e(TAG,"Device does not support Heart Rate characteristic!");
      disconnectFromDevice();
      close();
      broadcastAction(STATUS_HR_NOT_SUPPORTED);
    }
  }

  private void broadcastAction(final String action) {
    final Intent intent = new Intent(action);
    sendBroadcast(intent);
  }

  private void broadcastData(final String action,
                             final BluetoothGattCharacteristic characteristic) {
    final Intent intent = new Intent(action);

    // This is special handling for the Heart Rate Measurement profile.  Data parsing is
    // carried out as per profile specifications:
    // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
    if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
      int flag = characteristic.getProperties();
      int format = -1;
      if ((flag & 0x01) != 0) {
        format = BluetoothGattCharacteristic.FORMAT_UINT16;
        Log.d(TAG, "Heart rate format UINT16.");
      } else {
        format = BluetoothGattCharacteristic.FORMAT_UINT8;
        Log.d(TAG, "Heart rate format UINT8.");
      }
      final int heartRate = characteristic.getIntValue(format, 1);
      Log.d(TAG, String.format("Received heart rate: %d", heartRate));
      intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
    } else {
      // For all other profiles, writes the data formatted in HEX.
      final byte[] data = characteristic.getValue();
      if (data != null && data.length > 0) {
        final StringBuilder stringBuilder = new StringBuilder(data.length);
        for (byte byteChar : data)
          stringBuilder.append(String.format("%02X ", byteChar));
        intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
      }
    }
    sendBroadcast(intent);
  }


  /**
   * Initializes a reference to the local Bluetooth adapter.
   *
   * @return Return true if the initialization is successful.
   */
  public boolean initialize() {
    // For API level 18 and above, get a reference to BluetoothAdapter through
    // BluetoothManager.
    if (bluetoothManager == null) {
      bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
      if (bluetoothManager == null) {
        Log.e(TAG, "Unable to initialize BluetoothManager.");
        return false;
      }
    }

    bluetoothAdapter = bluetoothManager.getAdapter();
    if (bluetoothAdapter == null) {
      Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
      return false;
    }

    return true;
  }

  /**
   * Connects to the GATT server hosted on the Bluetooth LE device.
   *
   * @param address The device address of the destination device.
   * @return Return true if the connection is initiated successfully. The connection result
   * is reported asynchronously through the
   * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
   * callback.
   */
  private boolean connectToDevice(final String address) {
    if (bluetoothAdapter == null || address == null) {
      Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
      return false;
    }

    // Previously connected device.  Try to reconnect.
    if (deviceAddress != null && address.equals(deviceAddress)
            && deviceCommunicator != null) {
      Log.d(TAG, "Trying to use an existing deviceCommunicator for connection.");
      if (deviceCommunicator.connect()) {
        mConnectionState = STATE_CONNECTING;
        return true;
      } else {
        return false;
      }
    }

    final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
    if (device == null) {
      Log.w(TAG, "Device not found.  Unable to connectToDevice.");
      return false;
    }
    // We want to directly connect to the device, so we are setting the autoConnect
    // parameter to false.
    Log.i(TAG, "Connecting to device (GATT server) - from now comms with device are through deviceCallback");
    deviceCommunicator = device.connectGatt(this, false, deviceCallback);
    deviceAddress = address;
    //setCharacteristicNotification(new BluetoothGattCharacteristic(UUID_HEART_RATE_MEASUREMENT, ), true);
    mConnectionState = STATE_CONNECTING;
    return true;
  }

  /**
   * Disconnects an existing connection or cancel a pending connection. The disconnection result
   * is reported asynchronously through the
   * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
   * callback.
   */
  public void disconnectFromDevice() {
    if (bluetoothAdapter == null || deviceCommunicator == null) {
      Log.w(TAG, "BluetoothAdapter not initialized");
      return;
    }
    Log.i(TAG, "Disconnecting from device (GATT server)");
    deviceCommunicator.disconnect();
    heartRateCharacteristic = null;
  }

  /**
   * After using a given BLE device, the app must call this method to ensure resources are
   * released properly.
   */
  private void close() {
    if (deviceCommunicator == null) {
      return;
    }
    deviceCommunicator.close();
    deviceCommunicator = null;
  }

  /**
   * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
   * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
   * callback.
   *
   * @param characteristic The characteristic to read from.
   */
  public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
    if (bluetoothAdapter == null || deviceCommunicator == null) {
      Log.w(TAG, "BluetoothAdapter not initialized");
      return;
    }
    deviceCommunicator.readCharacteristic(characteristic);
  }

  /**
   * Enables or disables notification on a give characteristic.
   *
   * @param characteristic Characteristic to act on.
   * @param enabled        If true, enable notification.  False otherwise.
   */
  private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                            boolean enabled) {
    if (bluetoothAdapter == null || deviceCommunicator == null) {
      Log.w(TAG, "BluetoothAdapter not initialized");
      return;
    }
    deviceCommunicator.setCharacteristicNotification(characteristic, enabled);

    // This is specific to Heart Rate Measurement.
    if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
      BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
              UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
      descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
      deviceCommunicator.writeDescriptor(descriptor);
      Log.i(TAG, "Heart Rate monitoring enabled!");
    }
  }

  /**
   * Retrieves a list of supported GATT services on the connected device. This should be
   * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
   *
   * @return A {@code List} of supported services.
   */
  public List<BluetoothGattService> getSupportedGattServices() {
    if (deviceCommunicator == null) return null;

    return deviceCommunicator.getServices();
  }
}
