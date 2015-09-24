package com.idoz.hrmonitor.logger;

import android.content.Context;
import android.test.AndroidTestCase;

import com.idoz.hrmonitor.HeartRateDao;
import com.idoz.hrmonitor.model.HeartRateAggregatedRecord;

import net.danlew.android.joda.JodaTimeAndroid;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Created by izilberberg on 9/20/15.
 */
@RunWith(MockitoJUnitRunner.class)
public class HeartRateAggregatorLoggerTest extends AndroidTestCase {

  @Mock
  Context mockContext;

  @Ignore
  @Test
  public void testAggregation() throws Exception {
    JodaTimeAndroid.init(mockContext);
    HeartRateAggregatorLogger classToTest = new HeartRateAggregatorLogger(new File("somedir"));
    HeartRateDao mockDao = mock(HeartRateDao.class);
    classToTest.setHeartRateDao(mockDao);
    classToTest.startLogging("TEST");

    HeartRateAggregatedRecord expectedRecord = new HeartRateAggregatedRecord("TEST", null, 2.0, 3);
    HeartRateAggregatedRecord actualRecord = classToTest.aggregate(Arrays.asList(1, 2, 3));

    assertEquals("Number of samples is different", expectedRecord.getSamples(), actualRecord.getSamples());
    assertEquals("Average HR is different", expectedRecord.getHrAverage(), actualRecord.getHrAverage());


  }
}