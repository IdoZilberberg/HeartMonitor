package com.idoz.hrmonitor.dao;

import com.idoz.hrmonitor.model.HeartRateRecord;

/**
 * Created by izilberberg on 9/18/15.
 */
public interface HeartRateRowMapper<T extends HeartRateRecord> {
        String mapRow(T record);
}
