package com.idoz.hrmonitor.logger;

import android.util.Log;

import com.idoz.hrmonitor.dao.HeartRateFullCsvRowMapper;
import com.idoz.hrmonitor.dao.HeartRateRowMapper;
import com.idoz.hrmonitor.model.HeartRateFullRecord;

import org.joda.time.DateTime;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by izilberberg on 9/6/15.
 */
public class HeartRateFullLogger extends AbstractHeartRateLogger {

  private final static String TAG = HeartRateFullLogger.class.getSimpleName();
  private final int maxHeartRateRecordsInMemory = 10;
  private final List<HeartRateFullRecord> heartRateFullRecords;


  private final static String heartRateLoggerFilenamePrefix = "heartRate_";

  public HeartRateFullLogger(final File targetDir) {
    super(targetDir);
    heartRateFullRecords = new LinkedList<>();
  }


  @Override
  public int onHeartRateChange(final int newHeartRate) {
    //Log.d(TAG, ">> Got new HR: " + newHeartRate);
    if (!isLogging()) {
      return 0;
    }

    if (heartRateFullRecords.size() >= maxHeartRateRecordsInMemory) {
      flush();
    }
    heartRateFullRecords.add(new HeartRateFullRecord(getUsername(), new DateTime(), newHeartRate));
    final int percentMemoryFull = (int) ((heartRateFullRecords.size() / (double) maxHeartRateRecordsInMemory) * 100.0);
    Log.d(TAG, "Memory is " + percentMemoryFull + "% full.");
    return percentMemoryFull;
  }

  @Override
  String getHeartRateLoggerFilenamePrefix() {
    return heartRateLoggerFilenamePrefix;
  }

  @Override
  public void startLogging(String username) {
    super.startLogging(username);
    Log.i(TAG, "Logging started for " + username);
  }

  @Override
  public void stopLogging() {
    final int count = flush();
    Log.i(TAG, "Logging stopped, saved " + count + " heartRateFullRecords. -1 denotes error.");
    super.stopLogging();
  }

  private int flush() {
    final int count = heartRateDao.save(heartRateFullRecords);
    heartRateFullRecords.clear();

    return count;
  }

  @Override
  HeartRateRowMapper getMapper() {
    return new HeartRateFullCsvRowMapper();
  }
}
