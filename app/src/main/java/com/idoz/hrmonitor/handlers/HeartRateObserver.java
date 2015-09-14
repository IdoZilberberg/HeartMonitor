package com.idoz.hrmonitor.handlers;

/**
 * Created by izilberberg on 9/6/15.
 */
public interface HeartRateObserver {

  int onHeartRateChange(final int oldHeartRate, final int newHeartRate);

  void enable();

  void disable();
}
