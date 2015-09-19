package com.idoz.hrmonitor;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.idoz.hrmonitor.logger.HeartRateAggregatorLogger;
import com.idoz.hrmonitor.logger.HeartRateFullLogger;
import com.idoz.hrmonitor.logger.HeartRateLogger;

import java.util.LinkedList;
import java.util.List;

import static com.idoz.hrmonitor.ConnectionState.CONNECTED;
import static com.idoz.hrmonitor.ConnectionState.CONNECTING;
import static com.idoz.hrmonitor.ConnectionState.DISCONNECTED;

/**
 * Created by izilberberg on 9/19/15.
 */
public class HrLoggerService extends Service {

  private final static String TAG = HrLoggerService.class.getSimpleName();
  private List<HeartRateLogger> loggers;
  private Handler handler;
  private boolean isLogging = false;

  private void createLoggers() {
    if (loggers != null) {
      return;
    }
    loggers = new LinkedList<>();
    loggers.add(new HeartRateFullLogger(this));
    loggers.add(new HeartRateAggregatorLogger(this));
  }


  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(TAG, "Starting service...");
    createLoggers();
    handler = new Handler();
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    cleanup();
    super.onDestroy();
  }

  private void cleanup() {
    Log.i(TAG, "Cleaning up...");
    stopLogging();
  }

  public void startLogging(final String username) {
    isLogging = true;
    Log.i(TAG, "++++ Start Logging ++++");
    for (HeartRateLogger logger : loggers) {
      logger.startLogging(username);
    }
  }

  public void stopLogging() {
    isLogging = false;
    Log.i(TAG, "++++ Stop Logging ++++");
    for (HeartRateLogger logger : loggers) {
      logger.stopLogging();
    }
  }



  public class LocalBinder extends Binder {
    HrLoggerService getService() {
      return HrLoggerService.this;
    }
  }

  private final IBinder binder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent) {
    Log.i(TAG, "Binding HR Logger service");
    handler = new Handler();
    return binder;
  }

  private void onReceiveHeartRateData(final int heartRate) {
    for (HeartRateLogger logger : loggers) {
      logger.onHeartRateChange(heartRate);
    }
  }

  private final BroadcastReceiver hrSensorBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      final String action = intent.getAction();
      if(!HRSensorService.ACTION_DATA_AVAILABLE.equals(action)) {
        return;
      }
      final String heartRateStr = intent.getStringExtra(HRSensorService.EXTRA_DATA);
      try {
        final int heartRate = Integer.parseInt(heartRateStr);
        onReceiveHeartRateData(heartRate);
      } catch (NumberFormatException e) {
        return; // ignore silently
      }
    }
  };


}
