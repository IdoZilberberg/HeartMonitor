package com.idoz.hrmonitor.logger;

/**
 * Created by izilberberg on 9/6/15.
 */
public interface HeartRateLogger {

  int onHeartRateChange(final int oldHeartRate, final int newHeartRate);

  void enable();

  void disable();
}
