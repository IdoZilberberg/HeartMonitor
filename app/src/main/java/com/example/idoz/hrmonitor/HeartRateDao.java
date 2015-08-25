package com.example.idoz.hrmonitor;

import android.content.Context;
import java.util.List;

/**
 * Created by izilberberg on 8/25/15.
 */
public interface HeartRateDao {

  int saveHeartRateRecords(final Context context, final List<HeartRateRecord> heartRateRecords);



}
