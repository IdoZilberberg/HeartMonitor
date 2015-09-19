package com.idoz.hrmonitor.logger;

import android.content.Context;
import android.util.Log;

import com.idoz.hrmonitor.dao.HeartRateFullCsvRowMapper;
import com.idoz.hrmonitor.dao.HeartRateRowMapper;
import com.idoz.hrmonitor.model.HeartRateFullRecord;

import org.joda.time.DateTime;

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

  public HeartRateFullLogger(final Context context, final String username) {
    super(context, username);
    heartRateFullRecords = new LinkedList<>();
  }


  @Override
  public int onHeartRateChange(final int oldHeartRate, final int newHeartRate) {
    if (!isLogging()) {
      return 0;
    }

    if (heartRateFullRecords.size() >= maxHeartRateRecordsInMemory) {
      flush();
    }
    heartRateFullRecords.add(new HeartRateFullRecord(getUsername(), new DateTime(), newHeartRate));
    final int percentMemoryFull = (int)((heartRateFullRecords.size() / (double)maxHeartRateRecordsInMemory) * 100.0);
    Log.i(TAG, "Memory is " + percentMemoryFull + "% full.");
    return percentMemoryFull;
  }

  @Override
  String getHeartRateLoggerFilenamePrefix() {
    return heartRateLoggerFilenamePrefix;
  }

  @Override
  public void disable() {
    super.disable();

    final int count = flush();
    Log.i(TAG, "Logging stopped, saved " + count + " heartRateFullRecords. -1 denotes error.");

  }

  @Override
  public int flush() {
    final int count = heartRateDao.save(heartRateFullRecords);
    heartRateFullRecords.clear();

    return count;
  }

  @Override
  HeartRateRowMapper getMapper() {
    return new HeartRateFullCsvRowMapper();
  }
}
