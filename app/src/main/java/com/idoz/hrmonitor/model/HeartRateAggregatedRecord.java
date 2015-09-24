package com.idoz.hrmonitor.model;

import org.joda.time.DateTime;

/**
 * Created by izilberberg on 9/18/15.
 */
public class HeartRateAggregatedRecord implements HeartRateRecord {
  private final String user;
  private final DateTime date;
  private final double hrAverage;
  private final int samples;


  public HeartRateAggregatedRecord(final String user, final DateTime date, final double hrAverage, final int samples) {
    this.user = user;
    this.date = date;
    this.hrAverage = hrAverage;
    this.samples = samples;
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

  public int getSamples() {
    return samples;
  }
}
