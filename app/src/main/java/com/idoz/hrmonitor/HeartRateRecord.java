package com.idoz.hrmonitor;

import org.joda.time.DateTime;

/**
 * Created by izilberberg on 8/25/15.
 */
public class HeartRateRecord {

  private final String user;
  private final DateTime date;
  private final int heartRate;

  public HeartRateRecord(String user, DateTime date, int heartRate) {
    this.user = user;
    this.date = date;
    this.heartRate = heartRate;
  }

  public String getUser() {
    return user;
  }

  public DateTime getDate() {
    return date;
  }

  public int getHeartRate() {
    return heartRate;
  }
}
