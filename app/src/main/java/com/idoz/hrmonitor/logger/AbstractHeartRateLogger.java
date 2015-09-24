package com.idoz.hrmonitor.logger;

import com.idoz.hrmonitor.HeartRateDao;
import com.idoz.hrmonitor.dao.HeartRateCsvDao;
import com.idoz.hrmonitor.dao.HeartRateRowMapper;

import java.io.File;

/**
 * Created by izilberberg on 9/18/15.
 */
public abstract class AbstractHeartRateLogger implements HeartRateLogger {

  private final static String TAG = AbstractHeartRateLogger.class.getSimpleName();

  HeartRateDao heartRateDao;
  private String username = "NA";
  protected boolean logging = false;

  public AbstractHeartRateLogger(final File targetDir) {
    heartRateDao = createDao(targetDir);
  }

  @Override
  public void startLogging(final String username) {
    logging = true;
  }

  @Override
  public void stopLogging() {
    logging = false;
  }

  @Override
  public int onHeartRateChange(final int newHeartRate) {
    return 0;
  }

  private HeartRateDao createDao(final File targetDir) {
    return new HeartRateCsvDao(targetDir, getMapper(), getHeartRateLoggerFilenamePrefix());
  }

  public void setHeartRateDao(final HeartRateDao heartRateDao)  {
    this.heartRateDao = heartRateDao;
  }

  public String getUsername() {
    return username;
  }

  @Override
  public void setUsername(String username) {
    this.username = username;
  }

  public boolean isLogging() {
    return logging;
  }

  abstract HeartRateRowMapper getMapper();

  abstract String getHeartRateLoggerFilenamePrefix();
}
