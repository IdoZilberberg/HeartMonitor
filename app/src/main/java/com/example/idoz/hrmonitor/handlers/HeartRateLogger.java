package com.example.idoz.hrmonitor.handlers;

import android.util.Log;
import android.widget.Toast;

import com.example.idoz.hrmonitor.HeartRateDao;
import com.example.idoz.hrmonitor.HeartRateRecord;

import org.joda.time.DateTime;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by izilberberg on 9/6/15.
 */
public class HeartRateLogger {

  private final static String TAG = HeartRateLogger.class.getSimpleName();
  private final int maxHeartRateRecordsInMemory = 10;
  private final HeartRateDao heartRateDao;
  private final List<HeartRateRecord> heartRateRecords;
  private boolean isLogging;

  public HeartRateLogger(final HeartRateDao heartRateDao) {
    heartRateRecords = new LinkedList<>();
    this.heartRateDao = heartRateDao;
  }

  /**
   *
   * @return  pct [0-100] of how full the memory is
   */
  public int logHeartRate(final String username, final int newHeartRate) {
    if (!isLogging) {
      return 0;
    }

    if (heartRateRecords.size() >= maxHeartRateRecordsInMemory) {
      flushHeartRateMemoryToStorage();
    }
    heartRateRecords.add(new HeartRateRecord(username, new DateTime(), newHeartRate));
    final int percentMemoryfull = (int)((heartRateRecords.size() / (double)maxHeartRateRecordsInMemory) * 100.0);
    Log.i(TAG, "Memory is " + percentMemoryfull + "% full.");
    return percentMemoryfull;
  }


  public void startLogging() {
    isLogging = true;
    Log.i(TAG, "Logging started");

  }

  public void stopLogging() {
    isLogging = false;

    final int count = flushHeartRateMemoryToStorage();
    Log.i(TAG, "Logging stopped, saved " + count + " heartRateRecords. -1 denotes error.");

  }

  private int flushHeartRateMemoryToStorage() {
    final int count = heartRateDao.saveHeartRateRecords(heartRateRecords);
    heartRateRecords.clear();

    return count;
  }

}
