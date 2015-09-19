package com.idoz.hrmonitor.logger;

/**
 * Created by izilberberg on 9/6/15.
 */
public interface HeartRateLogger {

  int onHeartRateChange(final int newHeartRate);

  void setUsername(String username);

  void startLogging(String username);

  void stopLogging();
}
