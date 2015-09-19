package com.idoz.hrmonitor;

import com.idoz.hrmonitor.model.HeartRateFullRecord;
import com.idoz.hrmonitor.model.HeartRateRecord;

import java.util.List;

/**
 * Created by izilberberg on 8/25/15.
 */
public interface HeartRateDao {

  int save(final List<? extends HeartRateRecord> heartRateRecords);

  int save(final HeartRateRecord heartRateRecord);

}
