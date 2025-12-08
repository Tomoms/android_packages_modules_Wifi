/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi.ml_connected_scorer;

import static com.android.server.wifi.ml_connected_scorer.Constants.POLLING_DELAY_MS;
import static com.android.server.wifi.ml_connected_scorer.Constants.POLLING_INTERVAL_MS;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCORE_LOW_RSSI_THR_DBM;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiUsabilityStatsEntry;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.ml_connected_scorer.RandomForestModule}.
 */
@SmallTest
public final class MlConnectedScorerHelperTest {
    private static final String TEST_BSSID_1 = "test_bssid_1";
    private static final int TEST_FREQUENCY_1 = 5000;
    private static final String TEST_BSSID_2 = "test_bssid_2";
    private static final int TEST_FREQUENCY_2 = 6000;
    private static final long TIME_STAMP_MS = 1234567L;
    private static final double TEST_TOTAL_TX_BAD_DIFF = 1000;
    private static final double TEST_TOTAL_TX_SUCCESS_DIFF = 2000;
    private MlConnectedScorerHelper mHelper;

    @Mock WifiUsabilityStatsEntry mMockStatsEntry1;
    @Mock WifiUsabilityStatsEntry mMockStatsEntry2;
    @Mock WifiInfo mMockWifiInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mHelper = new MlConnectedScorerHelper();
        when(mMockWifiInfo.getBSSID()).thenReturn(TEST_BSSID_1);
        when(mMockWifiInfo.getFrequency()).thenReturn(TEST_FREQUENCY_1);
    }

    @Test
    public void isSameBssidAndFreq_invalidBssid_returnTrue() {
        assertTrue(mHelper.isSameBssidAndFreq(null, TEST_FREQUENCY_1, mMockWifiInfo));
    }

    @Test
    public void isSameBssidAndFreq_invalidFrequency_returnTrue() {
        assertTrue(mHelper.isSameBssidAndFreq(TEST_BSSID_1, -1, mMockWifiInfo));
    }

    @Test
    public void isSameBssidAndFreq_totallySame_returnTrue() {
        assertTrue(mHelper.isSameBssidAndFreq(TEST_BSSID_1, TEST_FREQUENCY_1, mMockWifiInfo));
    }

    @Test
    public void isSameBssidAndFreq_notSameBssid_returnFalse() {
        assertFalse(mHelper.isSameBssidAndFreq(TEST_BSSID_2, TEST_FREQUENCY_1, mMockWifiInfo));
    }

    @Test
    public void isSameBssidAndFreq_notSameFrequency_returnFalse() {
        assertFalse(mHelper.isSameBssidAndFreq(TEST_BSSID_1, TEST_FREQUENCY_2, mMockWifiInfo));
    }

    @Test
    public void isTimeStampGapTooLarge_returnTrue() {
        when(mMockStatsEntry1.getTimeStampMillis()).thenReturn(TIME_STAMP_MS);
        when(mMockStatsEntry2.getTimeStampMillis()).thenReturn(
                TIME_STAMP_MS + POLLING_INTERVAL_MS + POLLING_DELAY_MS + 1);

        assertTrue(mHelper.isTimeStampGapTooLarge(mMockStatsEntry1, mMockStatsEntry2));
    }

    @Test
    public void isTimeStampGapTooLarge_returnFalse1() {
        when(mMockStatsEntry1.getTimeStampMillis()).thenReturn(TIME_STAMP_MS);
        when(mMockStatsEntry2.getTimeStampMillis()).thenReturn(
                TIME_STAMP_MS + POLLING_INTERVAL_MS + POLLING_DELAY_MS);

        assertFalse(mHelper.isTimeStampGapTooLarge(mMockStatsEntry1, mMockStatsEntry2));
    }

    @Test
    public void isTimeStampGapTooLarge_returnFalse2() {
        when(mMockStatsEntry1.getTimeStampMillis()).thenReturn(TIME_STAMP_MS);
        when(mMockStatsEntry2.getTimeStampMillis()).thenReturn(TIME_STAMP_MS + 1);

        assertFalse(mHelper.isTimeStampGapTooLarge(mMockStatsEntry1, mMockStatsEntry2));
    }

    @Test
    public void isLinkQualityBad_lowRssi_returnTrue1() {
        assertTrue(mHelper.isLinkQualityBad(TEST_TOTAL_TX_BAD_DIFF, TEST_TOTAL_TX_SUCCESS_DIFF,
                SCORE_LOW_RSSI_THR_DBM));
    }

    @Test
    public void isLinkQualityBad_lowRssi_returnTrue2() {
        assertTrue(mHelper.isLinkQualityBad(TEST_TOTAL_TX_BAD_DIFF, TEST_TOTAL_TX_SUCCESS_DIFF,
                SCORE_LOW_RSSI_THR_DBM - 1));
    }
}
