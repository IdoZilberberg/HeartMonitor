package com.idoz.hrmonitor;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.idoz.hrmonitor.logger.HeartRateAggregatorLogger;
import com.idoz.hrmonitor.logger.HeartRateFullLogger;
import com.idoz.hrmonitor.logger.HeartRateLogger;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by izilberberg on 9/19/15.
 */
public class HrLoggerService extends Service {

  private final static String TAG = HrLoggerService.class.getSimpleName();
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
    registerReceiver(hrSensorBroadcastReceiver, new IntentFilter(HRSensorService.ACTION_DATA_AVAILABLE));
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    cleanup();
  }

  private void cleanup() {
    Log.i(TAG, "Cleaning up...");
    unregisterReceiver(hrSensorBroadcastReceiver);
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

  public void updatePreferences(final SharedPreferences sharedPreferences) {
    Log.d(TAG, "updatePreferences(): " + sharedPreferences);
    playAlertIfOutsideHrRange = sharedPreferences.getBoolean(getString(R.string.setting_alert_outside_hr_range), false);
    maxHeartRate = Integer.parseInt(sharedPreferences.getString(getString(R.string.setting_max_hr), MAX_HR_NOT_SET_STR));
    minHeartRate = Integer.parseInt(sharedPreferences.getString(getString(R.string.setting_min_hr), MIN_HR_NOT_SET_STR));

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
    return binder;
  }

  void onReceiveHeartRateData(final int heartRate) {
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


  private final BroadcastReceiver hrSensorBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      final String action = intent.getAction();
      if (!HRSensorService.ACTION_DATA_AVAILABLE.equals(action)) {
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
