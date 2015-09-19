package com.idoz.hrmonitor.dao;

import com.idoz.hrmonitor.model.HeartRateFullRecord;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by izilberberg on 9/12/15.
 */
public class HeartRateFullCsvRowMapper implements HeartRateRowMapper<HeartRateFullRecord> {

  private final static DateTimeFormatter fmtDate = DateTimeFormat.forPattern("yyyy/MM/dd");
  private final static DateTimeFormatter fmtDateHHMM = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm");
  private final static DateTimeFormatter fmtFull = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm:ss");

  public String mapRow(final HeartRateFullRecord heartRateFullRecord) {

    StringBuffer sb = new StringBuffer();
    sb
            .append(heartRateFullRecord.getUser()).append(",")
            .append(fmtDate.print(heartRateFullRecord.getDate())).append(",")
            .append(fmtDateHHMM.print(heartRateFullRecord.getDate())).append(",")
            .append(fmtFull.print(heartRateFullRecord.getDate())).append(",")
            .append(heartRateFullRecord.getHeartRate());
    return sb.toString();
  }


}
