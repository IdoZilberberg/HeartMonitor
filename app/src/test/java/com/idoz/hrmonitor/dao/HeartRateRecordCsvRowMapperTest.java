package com.idoz.hrmonitor.dao;

import com.idoz.hrmonitor.HeartRateDao;
import com.idoz.hrmonitor.HeartRateRecord;

import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Created by izilberberg on 9/12/15.
 */

@RunWith(MockitoJUnitRunner.class)
public class HeartRateRecordCsvRowMapperTest {

  @Mock
  HeartRateDao dao;

  @BeforeClass
  public static void init() {

  }

  @Test
  public void testMapRow() throws Exception {

    when(dao.saveHeartRateRecords(anyList())).thenReturn(1);

    final HeartRateRecord record = new HeartRateRecord(
            "IDO", new DateTime(2015, 12, 31, 12, 59, 48), 123);
    HeartRateRecordCsvRowMapper classUnderTest = new HeartRateRecordCsvRowMapper();
    final String actual = classUnderTest.mapRow(record);
    final String expected = "IDO,2015/12/31,2015/12/31-12:59,2015/12/31-12:59:48,123";
    assertEquals("difference in csv mapper", expected, actual);


  }
}