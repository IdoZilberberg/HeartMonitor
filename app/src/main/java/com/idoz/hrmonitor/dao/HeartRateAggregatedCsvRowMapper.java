package com.idoz.hrmonitor.dao;

import com.idoz.hrmonitor.model.HeartRateAggregatedRecord;
import com.idoz.hrmonitor.model.HeartRateFullRecord;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by izilberberg on 9/18/15.
 */
public class HeartRateAggregatedCsvRowMapper implements HeartRateRowMapper<HeartRateAggregatedRecord>{

  private final static DateTimeFormatter fmtDate = DateTimeFormat.forPattern("yyyy/MM/dd");
  private final static DateTimeFormatter fmtDateHHMM = DateTimeFormat.forPattern("yyyy/MM/dd-HH:mm");

  public String mapRow(final HeartRateAggregatedRecord heartRateAggregatedRecord) {

    StringBuffer sb = new StringBuffer();
    sb
            .append(heartRateAggregatedRecord.getUser()).append(",")
            .append(fmtDate.print(heartRateAggregatedRecord.getDate())).append(",")
            .append(fmtDateHHMM.print(heartRateAggregatedRecord.getDate())).append(",")
            .append(heartRateAggregatedRecord.getHrAverage());
    return sb.toString();
  }


}
