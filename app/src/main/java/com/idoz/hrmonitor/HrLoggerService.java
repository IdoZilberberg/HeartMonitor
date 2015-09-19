package com.idoz.hrmonitor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by izilberberg on 9/19/15.
 */
public class HrLoggerService extends Service {




  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
