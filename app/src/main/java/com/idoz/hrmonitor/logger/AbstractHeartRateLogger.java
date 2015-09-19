package com.idoz.hrmonitor.logger;

import android.content.Context;
import android.util.Log;

import com.idoz.hrmonitor.HeartRateDao;
import com.idoz.hrmonitor.dao.HeartRateCsvDao;
import com.idoz.hrmonitor.dao.HeartRateRowMapper;

/**
 * Created by izilberberg on 9/18/15.
 */
public abstract class AbstractHeartRateLogger implements HeartRateLogger {

  private final static String TAG = AbstractHeartRateLogger.class.getSimpleName();

  final HeartRateDao heartRateDao;
  private final String username;
  protected boolean logging = false;

  public AbstractHeartRateLogger(final Context context, final String username) {
    this.username = username;
    heartRateDao = createDao(context);
  }

  @Override
  public void enable() {
    logging = true;
    Log.i(TAG, "Logging started");
  }

  @Override
  public void disable() {
    logging = false;
  }

  @Override
  public int onHeartRateChange(int oldHeartRate, int newHeartRate) {
    return 0;
  }

  private HeartRateDao createDao(final Context context) {
    return new HeartRateCsvDao(context, getMapper(), getHeartRateLoggerFilenamePrefix());
  }

  public String getUsername() {
    return username;
  }

  public boolean isLogging() {
    return logging;
  }

  abstract int flush();

  abstract HeartRateRowMapper getMapper();

  abstract String getHeartRateLoggerFilenamePrefix();
}
