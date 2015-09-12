package com.example.idoz.hrmonitor.dao;

import com.example.idoz.hrmonitor.HeartRateRecord;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by izilberberg on 9/12/15.
 */
public class HeartRateRecordCsvRowMapper {

  private final static DateTimeFormatter fmtDate = DateTimeFormat.forPattern("yyyy/MM/dd");
  private final static DateTimeFormatter fmtDateHHMM = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm");
  private final static DateTimeFormatter fmtFull = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm:ss");

  public String mapRow(final HeartRateRecord heartRateRecord) {

    StringBuffer sb = new StringBuffer();
    sb
            .append(heartRateRecord.getUser()).append(",")
            .append(fmtDate.print(heartRateRecord.getDate())).append(",")
            .append(fmtDateHHMM.print(heartRateRecord.getDate())).append(",")
            .append(fmtFull.print(heartRateRecord.getDate())).append(",")
            .append(heartRateRecord.getHeartRate());
    return sb.toString();
  }


}
