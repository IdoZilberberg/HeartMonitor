package com.idoz.hrmonitor.logger;

import android.util.Log;

import com.idoz.hrmonitor.dao.HeartRateAggregatedCsvRowMapper;
import com.idoz.hrmonitor.dao.HeartRateRowMapper;
import com.idoz.hrmonitor.model.HeartRateAggregatedRecord;

import org.joda.time.DateTime;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by izilberberg on 9/18/15.
 * Aggregated logger - collect data for one minute, calc median, percentiles, then write it
 */
public class HeartRateAggregatorLogger extends AbstractHeartRateLogger {

  private final static String TAG = HeartRateAggregatorLogger.class.getSimpleName();

  private final static String heartRateLoggerFilenamePrefix = "heartRateAgg_";
  private static final int AGG_COUNT = 60;

  // state
  private final List<Integer> heartRateBuffer;

  public HeartRateAggregatorLogger(final File targetDir) {
    super(targetDir);
    heartRateBuffer = new LinkedList<>();
  }

  @Override
  public int onHeartRateChange(final int newHeartRate) {
    //Log.d(TAG, ">> Got new HR: " + newHeartRate);
    if (!isLogging()) {
      return 0;
    }

    if (heartRateBuffer.size() >= AGG_COUNT) {
      flush();
    }

    heartRateBuffer.add(newHeartRate);
    final int percentMemoryFull = (int) ((heartRateBuffer.size() / (double) AGG_COUNT) * 100.0);
    Log.i(TAG, "Memory is " + percentMemoryFull + "% full.");
    return percentMemoryFull;
  }

  @Override
  String getHeartRateLoggerFilenamePrefix() {
    return heartRateLoggerFilenamePrefix;
  }

  private int flush() {
    HeartRateAggregatedRecord record = aggregate(heartRateBuffer);
    final int count = heartRateDao.save(record);
    heartRateBuffer.clear();
    Log.i(TAG, "Logged average HR " + record.getHrAverage() + " from " + record.getSamples() + " samples.");
    return count;
  }

  HeartRateAggregatedRecord aggregate(final List<Integer> buf) {
    int total=0;
    for (Integer hrValue : buf) {
      total+=hrValue;
    }
    double hrAverage = (buf==null || buf.size()==0) ? 0.0 : total / buf.size();
    return new HeartRateAggregatedRecord(getUsername(), new DateTime(), hrAverage, buf.size());
  }

  @Override
  HeartRateRowMapper getMapper() {
    return new HeartRateAggregatedCsvRowMapper();
  }

  @Override
  public void startLogging(String username) {
    super.startLogging(username);
    Log.i(TAG, "Logging started for " + username);
  }

  @Override
  public void stopLogging() {
    super.stopLogging();
    Log.i(TAG, "Logging stopped");
  }
}
