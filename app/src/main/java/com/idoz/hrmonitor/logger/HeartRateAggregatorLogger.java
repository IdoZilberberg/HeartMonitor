package com.idoz.hrmonitor.logger;

import android.content.Context;
import android.util.Log;

import com.idoz.hrmonitor.dao.HeartRateAggregatedCsvRowMapper;
import com.idoz.hrmonitor.dao.HeartRateRowMapper;
import com.idoz.hrmonitor.model.HeartRateAggregatedRecord;

import org.joda.time.DateTime;

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

  private final List<Integer> heartRateHistory;

  public HeartRateAggregatorLogger(final Context context) {
    super(context);
    heartRateHistory = new LinkedList<>();
  }

  @Override
  public int onHeartRateChange(final int newHeartRate) {
    Log.d(TAG, ">> Got new HR: " + newHeartRate);
    if (!isLogging()) {
      return 0;
    }



    if ( heartRateHistory.size() >= AGG_COUNT) {
      flush();
    }
    heartRateHistory.add(newHeartRate);
    final int percentMemoryFull = (int)((heartRateHistory.size() / (double)AGG_COUNT) * 100.0);
    Log.i(TAG, "Memory is " + percentMemoryFull + "% full.");
    return percentMemoryFull;
  }

  @Override
  String getHeartRateLoggerFilenamePrefix() {
    return heartRateLoggerFilenamePrefix;
  }

  private int flush() {
    HeartRateAggregatedRecord record = aggregate(heartRateHistory);
    final int count = heartRateDao.save(record);
    heartRateHistory.clear();

    return count;
  }

  private HeartRateAggregatedRecord aggregate(List<Integer> heartRateHistory) {
    HeartRateAggregatedRecord aggregatedRecord = new HeartRateAggregatedRecord(getUsername(), new DateTime(), 123.3);

    return aggregatedRecord;
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
