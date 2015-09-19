package com.idoz.hrmonitor.model;

import org.joda.time.DateTime;

/**
 * Created by izilberberg on 9/18/15.
 */
public class HeartRateAggregatedRecord implements HeartRateRecord {
  private final String user;
  private final DateTime date;
  private final double hrAverage;


  public HeartRateAggregatedRecord(String user, DateTime date, double hrAverage) {
    this.user = user;
    this.date = date;
    this.hrAverage = hrAverage;
  }

  public String getUser() {
    return user;
  }

  public DateTime getDate() {
    return date;
  }

  public double getHrAverage() {
    return hrAverage;
  }
}
