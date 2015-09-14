package com.idoz.hrmonitor.handlers;

import android.content.Context;
import android.util.Log;

import com.idoz.hrmonitor.HeartRateDao;
import com.idoz.hrmonitor.HeartRateRecord;
import com.idoz.hrmonitor.dao.HeartRateCsvDao;

import org.joda.time.DateTime;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by izilberberg on 9/6/15.
 */
public class HeartRateFullLogger implements HeartRateObserver {

  private final static String TAG = HeartRateFullLogger.class.getSimpleName();
  private final int maxHeartRateRecordsInMemory = 10;
  private final HeartRateDao heartRateDao;
  private final List<HeartRateRecord> heartRateRecords;
  private String username;
  private boolean isLogging;

  private final static String heartRateLoggerFilenamePrefix = "heartRate_";
  private static final int maxHrCutoff = 200; // omit outlier values
  private static final int minHrCutoff = 30; // omit outlier values


  public HeartRateFullLogger(final Context context, final String username) {
    this.username = username;
    heartRateRecords = new LinkedList<>();
    this.heartRateDao = createDao(context);
  }

  private HeartRateDao createDao(final Context context) {
    return new HeartRateCsvDao(context, heartRateLoggerFilenamePrefix, maxHrCutoff, minHrCutoff);
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public int onHeartRateChange(final int oldHeartRate, final int newHeartRate) {
    if (!isLogging) {
      return 0;
    }

    if (heartRateRecords.size() >= maxHeartRateRecordsInMemory) {
      flushHeartRateMemoryToStorage();
    }
    heartRateRecords.add(new HeartRateRecord(username, new DateTime(), newHeartRate));
    final int percentMemoryFull = (int)((heartRateRecords.size() / (double)maxHeartRateRecordsInMemory) * 100.0);
    Log.i(TAG, "Memory is " + percentMemoryFull + "% full.");
    return percentMemoryFull;
  }


  @Override
  public void enable() {
    isLogging = true;
    Log.i(TAG, "Logging started");

  }

  @Override
  public void disable() {
    isLogging = false;

    final int count = flushHeartRateMemoryToStorage();
    Log.i(TAG, "Logging stopped, saved " + count + " heartRateRecords. -1 denotes error.");

  }

  private int flushHeartRateMemoryToStorage() {
    final int count = heartRateDao.saveHeartRateRecords(heartRateRecords);
    heartRateRecords.clear();

    return count;
  }

}
