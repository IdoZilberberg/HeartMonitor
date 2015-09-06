package com.example.idoz.hrmonitor;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

/**
 * Created by izilberberg on 8/25/15.
 */
public class HeartRateCsvDao implements HeartRateDao {

  private final static String TAG = HeartRateCsvDao.class.getSimpleName();
  private final static DateTimeFormatter fmtDateCompact = DateTimeFormat.forPattern("yyyyMMdd");
  private final static DateTimeFormatter fmtDate = DateTimeFormat.forPattern("yyyy/MM/dd");
  private final static DateTimeFormatter fmtDateHHMM = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm");
  private final static DateTimeFormatter fmtFull = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm:ss");
  private final HeartRateRecordToFileMapper mapper = new HeartRateRecordToFileMapper();
  private final Context context;
  private final String filenamePrefix;
  private final int maxHrCutoff, minHrCutoff;

  public HeartRateCsvDao(final Context context, final String filenamePrefix, final int maxHrCutoff, final int minHrCutoff) {
    this.context = context;
    this.filenamePrefix = filenamePrefix;
    this.maxHrCutoff = maxHrCutoff;
    this.minHrCutoff = minHrCutoff;
  }

  @Override
  public int saveHeartRateRecords(List<HeartRateRecord> heartRateRecords) {
    final String filename = filenamePrefix + fmtDateCompact.print(new DateTime()) + ".csv";
    if (!externalStorageAvailable()) {
      Log.w(TAG, "External storage unavailable! cannot write data");
      return -1;
    }
    if (heartRateRecords == null || heartRateRecords.size() == 0) {
      Log.i(TAG, "No records collected, nothing to save to file");
      return 0;
    }
    int recordsWritten = 0;

    try {
      //final File outputFile = new File(Environment.getExternalStorageDirectory(), filenamePrefix);
      final File dir = context.getExternalFilesDir("hrlogs");
      if (dir==null || (!dir.exists() && !dir.mkdirs())) {
        Log.w(TAG, "Cannot create directory " + dir.getAbsolutePath());
        return -1;
      }
      final File outputFile = new File(dir, filename);
      if (!outputFile.exists()) {
        outputFile.createNewFile();
        Log.i(TAG, "Created new file " + outputFile.getAbsolutePath());
      }
      final FileOutputStream fos = new FileOutputStream(outputFile, true); //.openFileOutput(filenamePrefix, Context.MODE_PRIVATE | Context.MODE_APPEND);
      final OutputStreamWriter writer = new OutputStreamWriter(fos);
      Log.i(TAG, "Starting to write " + heartRateRecords.size() + " records to file " + outputFile.getAbsolutePath());
      for (final HeartRateRecord record : heartRateRecords) {
        if (outsideCutoff(record.getHeartRate())) {
          continue;
        }
        writer.append(mapper.mapRow(record));
        ++recordsWritten;
      }
      writer.flush();
      writer.close();
      fos.close();
      Log.i(TAG, "Successfully wrote " + recordsWritten + " records to file " + outputFile.getAbsolutePath());
    } catch (IOException e) {
      Toast.makeText(context, "Failed to save to file!", Toast.LENGTH_LONG);
      Log.w(TAG, "Failed to save heart rate data to file " + filename + ". Reason: " + e.getMessage());
      e.printStackTrace();
      return -1;
    }
    return recordsWritten;

  }

  private boolean outsideCutoff(final int heartRate) {
    return heartRate < minHrCutoff || heartRate > maxHrCutoff;
  }

  private boolean externalStorageAvailable() {
    final String state = Environment.getExternalStorageState();
    return Environment.MEDIA_MOUNTED.equals(state);
  }

  private class HeartRateRecordToFileMapper {
    public String mapRow(final HeartRateRecord heartRateRecord) {

      StringBuffer sb = new StringBuffer();
      sb
              .append(heartRateRecord.getUser()).append(",")
              .append(fmtDate.print(heartRateRecord.getDate())).append(",")
              .append(fmtDateHHMM.print(heartRateRecord.getDate())).append(",")
              .append(fmtFull.print(heartRateRecord.getDate())).append(",")
              .append(heartRateRecord.getHeartRate()).append("\n");
      return sb.toString();
    }
  }
}
