package com.idoz.hrmonitor.dao;

import android.os.Environment;
import android.util.Log;

import com.idoz.hrmonitor.HeartRateDao;
import com.idoz.hrmonitor.model.HeartRateRecord;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;

/**
 * Created by izilberberg on 8/25/15.
 */
public class HeartRateCsvDao implements HeartRateDao {

  private final static String TAG = HeartRateCsvDao.class.getSimpleName();
  private final static DateTimeFormatter fmtDateCompact = DateTimeFormat.forPattern("yyyyMMdd");
  private final File targetDir;
  final HeartRateRowMapper mapper;
  private final String filenamePrefix;

  public HeartRateCsvDao(final File targetDir, final HeartRateRowMapper mapper, final String filenamePrefix) {
    this.targetDir = targetDir;
    this.mapper = mapper;
    this.filenamePrefix = filenamePrefix;
  }

  @Override
  public int save(HeartRateRecord heartRateRecord) {
    return save(Arrays.asList(new HeartRateRecord[] { heartRateRecord}));
  }

  @Override
  public int save(List<? extends HeartRateRecord> heartRateRecords) {
    final String filename = filenamePrefix + fmtDateCompact.print(new DateTime()) + ".csv";
    if (!externalStorageAvailable()) {
      Log.e(TAG, "External storage unavailable! cannot write data");
      return -1;
    }
    if (heartRateRecords == null || heartRateRecords.size() == 0) {
      Log.i(TAG, "No records collected, nothing to save to file");
      return 0;
    }
    int recordsWritten = 0;

    try {
      //final File outputFile = new File(Environment.getExternalStorageDirectory(), filenamePrefix);
      if (targetDir==null || (!targetDir.exists() && !targetDir.mkdirs())) {
        Log.w(TAG, "Cannot create directory " + targetDir.getAbsolutePath());
        return -1;
      }
      final File outputFile = new File(targetDir, filename);
      if (!outputFile.exists()) {
        outputFile.createNewFile();
        Log.i(TAG, "Created new file " + outputFile.getAbsolutePath());
      }
      final FileOutputStream fos = new FileOutputStream(outputFile, true); //.openFileOutput(filenamePrefix, Context.MODE_PRIVATE | Context.MODE_APPEND);
      final OutputStreamWriter writer = new OutputStreamWriter(fos);
      Log.i(TAG, "Starting to write " + heartRateRecords.size() + " records to file " + outputFile.getAbsolutePath());
      for (final HeartRateRecord record : heartRateRecords) {
        writer.append(mapper.mapRow(record)).append("\n");
        ++recordsWritten;
      }
      writer.flush();
      writer.close();
      fos.close();
      Log.i(TAG, "Successfully wrote " + recordsWritten + " records to file " + outputFile.getAbsolutePath());
    } catch (IOException e) {
      Log.w(TAG, "Failed to save heart rate data to file " + filename + ". Reason: " + e.getMessage());
      e.printStackTrace();
      return -1;
    }
    return recordsWritten;

  }

  private boolean externalStorageAvailable() {
    final String state = Environment.getExternalStorageState();
    return Environment.MEDIA_MOUNTED.equals(state);
  }

}
