package com.idoz.hrmonitor;

import android.app.Application;

import net.danlew.android.joda.JodaTimeAndroid;

/**
 * Created by izilberberg on 8/26/15.
 */
public class HeartMonitorApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    JodaTimeAndroid.init(this);
  }
}
