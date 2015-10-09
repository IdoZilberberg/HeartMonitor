package com.idoz.hrmonitor.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.idoz.hrmonitor.AudioTrackPlayer;
import com.idoz.hrmonitor.R;
import com.idoz.hrmonitor.logger.HeartRateAggregatorLogger;
import com.idoz.hrmonitor.logger.HeartRateFullLogger;
import com.idoz.hrmonitor.logger.HeartRateLogger;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by izilberberg on 9/19/15.
 */
public class DataCollectorService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener{

  private final static String TAG = DataCollectorService.class.getSimpleName();
  private List<HeartRateLogger> loggers;

  // const
  private static final int minIntervalBetweenAlertsInSeconds = 10;
  public static final int MIN_HR_NOT_SET = -1;
  public static final int MAX_HR_NOT_SET = 999;
  private static final String MAX_HR_NOT_SET_STR = Integer.toString(MAX_HR_NOT_SET);
  private static final String MIN_HR_NOT_SET_STR = Integer.toString(MIN_HR_NOT_SET);


  // state
  private boolean isLogging = false;
  private boolean playAlertIfOutsideHrRange;
  private int maxHeartRate = MAX_HR_NOT_SET, minHeartRate = MIN_HR_NOT_SET;
  private boolean alertsTemporarilyMuted = false;
  private boolean isBackToHrRange = true;
  private int thresholdAboveMaxHeartRate = 5;
  private int lastHeartRate = 0;
  private Handler handler;
  private AudioTrackPlayer audioTrackPlayer = null;

  // prefs
  private SharedPreferences SP;

  private void createLoggers() {
    if (loggers != null) {
      return;
    }
    loggers = new LinkedList<>();
    loggers.add(new HeartRateFullLogger(this.getExternalFilesDir("hrlogs")));
    loggers.add(new HeartRateAggregatorLogger(this.getExternalFilesDir("hrlogs")));
  }


  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(TAG, "Starting service...");
    handler = new Handler();
    audioTrackPlayer = new AudioTrackPlayer();
    createLoggers();
    registerReceiver(hrDeviceBroadcastReceiver, new IntentFilter(DeviceListenerService.ACTION_DATA_AVAILABLE));
    initPrefs();
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    cleanup();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.i(TAG, ">>>>> UpdatePrefs(): key=" + key);
    if (getString(R.string.setting_alert_outside_hr_range).equals(key)) {
      playAlertIfOutsideHrRange = sharedPreferences.getBoolean(getString(R.string.setting_alert_outside_hr_range), false);
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
    playAlertIfOutsideHrRange = SP.getBoolean(getString(R.string.setting_alert_outside_hr_range), false);
    maxHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_max_hr), MAX_HR_NOT_SET_STR));
    minHeartRate = Integer.parseInt(SP.getString(getString(R.string.setting_min_hr), MIN_HR_NOT_SET_STR));

    SP.registerOnSharedPreferenceChangeListener(this);
  }


  private void cleanup() {
    Log.i(TAG, "Cleaning up...");
    unregisterReceiver(hrDeviceBroadcastReceiver);
    stopLogging();
    stopSelf();
  }

  public void startLogging(final String username) {
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
    public DataCollectorService getService() {
      return DataCollectorService.this;
    }
  }

  private final IBinder binder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent) {
    Log.i(TAG, "Binding HR Logger service");
    return binder;
  }

  public void onReceiveHeartRateData(final int heartRate) {
    Log.d(TAG, ">> Received HR data: " + heartRate);
    setIsBackToNormalHrRange(lastHeartRate, heartRate);
    for (HeartRateLogger logger : loggers) {
      logger.onHeartRateChange(heartRate);
    }
    playHeartRateAlert(heartRate);
    lastHeartRate = heartRate;
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
      Log.d(TAG, "Don't play");
      return;
    }

    if (isBackToHrRange) {
      play(AudioTrackPlayer.HrAudioEnum.NORMAL);
      isBackToHrRange = false;
      return;
    }
    if (alertsTemporarilyMuted) {
      return;
    }
    if (newHeartRate > maxHeartRate + thresholdAboveMaxHeartRate) {
      Log.d(TAG, "Play HIHI");
      play(AudioTrackPlayer.HrAudioEnum.HIHI);
      muteAlertsForXSeconds(minIntervalBetweenAlertsInSeconds);
      return;
    }
    if (newHeartRate > maxHeartRate) {
      play(AudioTrackPlayer.HrAudioEnum.HI);
      muteAlertsForXSeconds(minIntervalBetweenAlertsInSeconds);
      return;
    }
    if (newHeartRate < minHeartRate) {
      play(AudioTrackPlayer.HrAudioEnum.LO);
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

  private void play(final AudioTrackPlayer.HrAudioEnum audioToPlay) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        Log.d(TAG, "Play sound in thread: " + Thread.currentThread().getName());
        audioTrackPlayer.play(audioToPlay);
      }
    });

  }


  private final BroadcastReceiver hrDeviceBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      final String action = intent.getAction();
      if (!DeviceListenerService.ACTION_DATA_AVAILABLE.equals(action)) {
        return;
      }
      final String heartRateStr = intent.getStringExtra(DeviceListenerService.EXTRA_DATA);
      try {
        final int heartRate = Integer.parseInt(heartRateStr);
        onReceiveHeartRateData(heartRate);
      } catch (NumberFormatException e) {
        return; // ignore silently
      }
    }
  };


}
