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

package com.idoz.hrmonitor;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.idoz.hrmonitor.service.DeviceListenerService;

import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceSelectionActivity extends ListActivity {
  private final static String TAG = DeviceSelectionActivity.class.getSimpleName();
  private LeDeviceListAdapter mLeDeviceListAdapter;
  private BluetoothAdapter mBluetoothAdapter;
  private boolean mScanning;
  private Handler mHandler;

  private static final int REQUEST_ENABLE_BT = 1;
  // Stops scanning after 10 seconds.
  private static final long SCAN_PERIOD = 10000;
  private DeviceListenerService deviceListenerService;
  private DeviceListenerServiceConnection deviceListenerServiceConnection;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "$$$ onCreate");
    //getSupportActionBar().setTitle(R.string.title_devices);
    mHandler = new Handler();

    // Use this check to determine whether BLE is supported on the device.  Then you can
    // selectively disable BLE-related features.
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      Toast.makeText(this, R.string.warn_ble_not_supported, Toast.LENGTH_SHORT).show();
      finish();
    }

    // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
    // BluetoothAdapter through BluetoothManager.
    final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    mBluetoothAdapter = bluetoothManager.getAdapter();

    // Checks if Bluetooth is supported on the device.
    if (mBluetoothAdapter == null) {
      Toast.makeText(this, R.string.warn_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "$$$ onDestroy");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.device_scan, menu);
    if (!mScanning) {
      menu.findItem(R.id.menu_stop).setVisible(false);
      menu.findItem(R.id.menu_scan).setVisible(true);
      menu.findItem(R.id.menu_refresh).setActionView(null);
    } else {
      menu.findItem(R.id.menu_stop).setVisible(true);
      menu.findItem(R.id.menu_scan).setVisible(false);
      menu.findItem(R.id.menu_refresh).setActionView(
              R.layout.actionbar_indeterminate_progress);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_scan:
        mLeDeviceListAdapter.clear();
        scanLeDevice(true);
        break;
      case R.id.menu_stop:
        scanLeDevice(false);
        break;
    }
    return true;
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.i(TAG, "$$$ onPause");
    tryUnbindDeviceListenerService();
    scanLeDevice(false);
    mLeDeviceListAdapter.clear();
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.i(TAG, "$$$ onResume");
    // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
    // fire an intent to display a dialog asking the user to grant permission to enable it.
    if (!mBluetoothAdapter.isEnabled()) {
      finishDueToBluetoothDisabled();
    }
    tryBindDeviceListenerService();
    // Initializes list view adapter.
    mLeDeviceListAdapter = new LeDeviceListAdapter();
    setListAdapter(mLeDeviceListAdapter);
    scanLeDevice(true);
  }

  private void finishDueToBluetoothDisabled() {
    Toast.makeText(this, "Enable bluetooth to scan for devices", Toast.LENGTH_SHORT).show();
    finish();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // User chose not to enable Bluetooth.
    if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
      finish();
      return;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
    if (device == null) return;
//    final Intent intent = new Intent(this, DeviceControlActivity.class);
//    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
//    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
    if (mScanning) {
      mBluetoothAdapter.stopLeScan(mLeScanCallback);
      mScanning = false;
    }

    onActiveDeviceSelected(device);
  }

  private void onActiveDeviceSelected(BluetoothDevice device) {
    Toast.makeText(this, "Selected device " + device, Toast.LENGTH_SHORT).show();
    updateActiveDeviceInDeviceListenerService(device);
    updateActiveDeviceInPreferences(device);

    finish();

  }

  private void updateActiveDeviceInDeviceListenerService(final BluetoothDevice device) {
    deviceListenerService.updateActiveDevice(device);
    // TODO
  }

  private void updateActiveDeviceInPreferences(final BluetoothDevice device) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(getString(R.string.setting_active_device), device.getName());
    editor.apply();
  }

  private void scanLeDevice(final boolean enable) {
    if (enable) {
      Log.i(TAG, "$$$ Scanning for devices...");
      // Stops scanning after a pre-defined scan period.
      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          mScanning = false;
          mBluetoothAdapter.stopLeScan(mLeScanCallback);
          invalidateOptionsMenu();
        }
      }, SCAN_PERIOD);

      mScanning = true;
      mBluetoothAdapter.startLeScan(mLeScanCallback);
    } else {
      Log.i(TAG, "Stopped scanning for devices.");
      mScanning = false;
      mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }
    invalidateOptionsMenu();
  }

  // Adapter for holding devices found through scanning.
  private class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> mLeDevices;
    private LayoutInflater mInflator;

    public LeDeviceListAdapter() {
      super();
      mLeDevices = new ArrayList<>();
      mInflator = DeviceSelectionActivity.this.getLayoutInflater();
    }

    public void addDevice(BluetoothDevice device) {
      if (!mLeDevices.contains(device)) {
        mLeDevices.add(device);
      }
    }

    public BluetoothDevice getDevice(int position) {
      return mLeDevices.get(position);
    }

    public void clear() {
      mLeDevices.clear();
    }

    @Override
    public int getCount() {
      return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
      return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
      return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
      ViewHolder viewHolder;
      // General ListView optimization code.
      if (view == null) {
        view = mInflator.inflate(R.layout.listitem_device, null);
        viewHolder = new ViewHolder();
        viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
        viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
        view.setTag(viewHolder);
      } else {
        viewHolder = (ViewHolder) view.getTag();
      }

      BluetoothDevice device = mLeDevices.get(i);
      final String deviceName = device.getName();
      if (deviceName != null && deviceName.length() > 0)
        viewHolder.deviceName.setText(deviceName);
      else
        viewHolder.deviceName.setText(R.string.unknown_device);
      viewHolder.deviceAddress.setText(device.getAddress());

      return view;
    }
  }

  // Device scan callback.
  private BluetoothAdapter.LeScanCallback mLeScanCallback =
          new BluetoothAdapter.LeScanCallback() {

            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  mLeDeviceListAdapter.addDevice(device);
                  mLeDeviceListAdapter.notifyDataSetChanged();
                }
              });
            }
          };

  static class ViewHolder {
    TextView deviceName;
    TextView deviceAddress;
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

  private class DeviceListenerServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      DeviceListenerService.LocalBinder binder = (DeviceListenerService.LocalBinder) service;
      deviceListenerService = binder.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      deviceListenerService = null;
    }
  }

}