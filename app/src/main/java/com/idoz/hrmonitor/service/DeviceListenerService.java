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

package com.idoz.hrmonitor.service;

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
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.idoz.hrmonitor.SampleGattAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 * <p>
 * Copied as-is from BluetoothLeGatt sample
 */
public class DeviceListenerService extends Service {

  private final static String TAG = DeviceListenerService.class.getSimpleName();
  private BluetoothAdapter bluetoothAdapter;

  private final static String deviceAddressConst = "00:22:D0:76:1C:E9"; // my device!
  private String deviceAddress = deviceAddressConst;
  private BluetoothGatt deviceCommunicator;
  private Handler handler;

  BluetoothGattCharacteristic heartRateCharacteristic = null;
  public static final String STATUS_HR_NOT_SUPPORTED =
          "com.idoz.hrmonitor.ble.STATUS_HR_NOT_SUPPORTED";
  public final static String ACTION_GATT_CONNECTED =
          "com.idoz.hrmonitor.ble.ACTION_GATT_CONNECTED";
  public final static String ACTION_GATT_DISCONNECTED =
          "com.idoz.hrmonitor.ble.ACTION_GATT_DISCONNECTED";
  public final static String ACTION_DATA_AVAILABLE =
          "com.idoz.hrmonitor.ble.ACTION_DATA_AVAILABLE";

  public final static String EXTRA_DATA =
          "com.idoz.hrmonitor.ble.EXTRA_DATA";

  public final static UUID UUID_HEART_RATE_MEASUREMENT =
          UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

  public void updateActiveDevice(final BluetoothDevice device) {
    if( deviceAddress.equals(device.getAddress()))  {
      Log.d(TAG, "Same active device selected, no need to reconnect");
      return;
    }
    Log.i(TAG, "Switching from device " + deviceAddress + " to device " + device.getAddress());
    deviceAddress = device.getAddress();
    disconnectFromDevice();
    connectToDevice();

  }

  public class LocalBinder extends Binder {
    public DeviceListenerService getService() {
      return DeviceListenerService.this;
    }
  }
  private final IBinder binder = new LocalBinder();

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    handler = new Handler();
    connectToDevice();
//    return super.onStartCommand(intent, flags, startId);
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    cleanup();
  }

  private void cleanup() {
    disconnectFromDevice();
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.i(TAG, "Binding Device Listener service");
    return binder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
//    disconnectFromDevice();
    return super.onUnbind(intent);
  }

  public boolean connectToDevice() {
    if (!initBluetooth()) {
      return false;
    }

    // Previously connected device.  Try to reconnect.
    if (deviceCommunicator != null) {
      Log.d(TAG, "Trying to use an existing deviceCommunicator for connection.");
      if (deviceCommunicator.connect()) {
        return true;
      } else {
        return false;
      }
    }

    final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
    if (device == null) {
      Log.w(TAG, "Device not found.  Unable to connectToDevice.");
      return false;
    }
    // We want to directly connect to the device, so we are setting the autoConnect
    // parameter to false.
    Log.i(TAG, "Trying to connect to heartrate device...");
    deviceCommunicator = device.connectGatt(this, false, deviceConnectionStateCallback);
    return true;
  }

  private Runnable onServiceDiscoveryTimeout = new Runnable() {
    @Override
    public void run() {
      Log.w(TAG, "Service discovery timed out!");
      deviceCommunicator = null;
      broadcastAction(ACTION_GATT_DISCONNECTED);
    }
  };
  // Implements callback methods for GATT events that the app cares about.
  // For example, connection change and services discovered.
  private final BluetoothGattCallback deviceConnectionStateCallback = new BluetoothGattCallback() {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      switch (newState) {
        case BluetoothProfile.STATE_CONNECTED:
          Log.i(TAG, "Connected to heartrate device, starting service discovery.");
          handler.removeCallbacks(onServiceDiscoveryTimeout);
          handler.postDelayed(onServiceDiscoveryTimeout, 5000);
          if (deviceCommunicator == null) {
            connectToDevice();
          }
          deviceCommunicator.discoverServices();
          break;
        case BluetoothProfile.STATE_DISCONNECTED:
          Log.i(TAG, "Disconnected from heartrate device.");
          tryClose();
          break;
      }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        handler.removeCallbacks(onServiceDiscoveryTimeout);
        Log.i(TAG, "Finished discovering services, enabling heart rate broadcast...");
        locateHeartRateCharacteristic(gatt.getServices());
        setCharacteristicNotification(heartRateCharacteristic, true);
        broadcastAction(ACTION_GATT_CONNECTED);
      } else {
        Log.w(TAG, "Failed to discover services, cannot broadcast heart rate! State: " + status);
        broadcastAction(ACTION_GATT_DISCONNECTED);
      }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.v(TAG, "deviceConnectionStateCallback - read characteristic " + characteristic);
        broadcastData(ACTION_DATA_AVAILABLE, characteristic);
      }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
      Log.v(TAG, "deviceConnectionStateCallback - changed characteristic " + characteristic);
      broadcastData(ACTION_DATA_AVAILABLE, characteristic);
    }
  };

  /**
   * Disconnects an existing connection or cancel a pending connection. The disconnection result
   * is reported asynchronously through the
   * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
   * callback.
   */
  public void disconnectFromDevice() {
    tryClose();
  }

  private void locateHeartRateCharacteristic(List<BluetoothGattService> services) {
    heartRateCharacteristic = null;
    for (BluetoothGattService service : services) {
      if (SampleGattAttributes.HEART_RATE_SERVICE.equals(service.getUuid().toString())) {
        heartRateCharacteristic = service.getCharacteristic(UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT));
        break;
      }
    }
    if (heartRateCharacteristic == null) {
      Log.e(TAG, "Device does not support Heart Rate characteristic!");
      disconnectFromDevice();
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
      int format;
      if ((flag & 0x01) != 0) {
        format = BluetoothGattCharacteristic.FORMAT_UINT16;
      } else {
        format = BluetoothGattCharacteristic.FORMAT_UINT8;
      }
      final int heartRate = characteristic.getIntValue(format, 1);
      Log.v(TAG, String.format("Received heart rate: %d", heartRate));
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

  private boolean initBluetooth() {
    if (bluetoothAdapter != null) {
      return true;
    }
    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    bluetoothAdapter = bluetoothManager.getAdapter();
    return bluetoothAdapter != null;
  }

  /**
   * After using a given BLE device, the app must call this method to ensure resources are
   * released properly.
   */
  private void tryClose() {
    Log.i(TAG, "tryClose(): closing connection to heartrate device");
    if (deviceCommunicator == null) {
      return;
    }
    heartRateCharacteristic = null;
    deviceCommunicator.close();
    deviceCommunicator.disconnect();
    deviceCommunicator = null;
    broadcastAction(ACTION_GATT_DISCONNECTED);
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
}
