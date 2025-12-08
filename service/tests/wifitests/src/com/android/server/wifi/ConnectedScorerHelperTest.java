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

package com.android.server.wifi;

import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;
import static com.android.server.wifi.Clock.INVALID_TIMESTAMP_MS;
import static com.android.server.wifi.ConnectedScorerHelper.MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS;
import static com.android.server.wifi.ConnectedScorerHelper.NUD_THROTTLE_MS;
import static com.android.server.wifi.ConnectedScorerHelper.TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE;
import static com.android.server.wifi.ConnectedScorer.WIFI_TRANSITION_SCORE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.ip.IpClientManager;
import android.net.wifi.WifiInfo;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

/**
 * Unit tests for {@link com.android.server.wifi.ConnectedScorerHelper}.
 */
@SmallTest
public class ConnectedScorerHelperTest extends WifiBaseTest {
    private static final long TEST_TIMESTAMP_MS = 12345678L;
    private static final int TEST_ENTRY_RSSI = -60;
    private static final int TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND = 10;
    private static final int LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS = 6;
    private static final long LOW_CONNECTED_SCORE_SCAN_PERIOD_MS =
            LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS * 1000L;

    @Mock ScoringParams mMockScoringParams;
    @Mock WifiGlobals mMockWifiGlobals;
    @Mock WifiConnectivityManager mMockWifiConnectivityManager;
    @Mock WifiInfo mMockWifiInfo;
    @Mock IpClientManager mMockIpClientManager;
    private ConnectedScorerHelper mConnectedScorerHelper;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mConnectedScorerHelper = new ConnectedScorerHelper(mMockScoringParams, mMockWifiGlobals,
                mMockWifiConnectivityManager);
        when(mMockScoringParams.getEntryRssi(anyInt())).thenReturn(TEST_ENTRY_RSSI);
        when(mMockScoringParams.getYippeeSkippyPacketsPerSecond())
                .thenReturn(TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND);
        when(mMockScoringParams.getNudKnob()).thenReturn(5);
        when(mMockWifiGlobals.getWifiLowConnectedScoreScanPeriodSeconds())
                .thenReturn(LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS);
    }

    @Test
    public void adjustScore_highPreviousScoreHighCurrentScore_returnCurrentScore() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE + 10);

        int currentScore = WIFI_TRANSITION_SCORE + 20;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI,
                INVALID_TIMESTAMP_MS,
                Instant.now().toEpochMilli(),
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(currentScore, adjustScore);
    }

    @Test
    public void adjustScore_lowPreviousScoreLowCurrentScore_returnCurrentScore() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE - 10);

        int currentScore = WIFI_TRANSITION_SCORE - 20;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI,
                INVALID_TIMESTAMP_MS,
                Instant.now().toEpochMilli(),
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(currentScore, adjustScore);
    }

    @Test
    public void adjustScore_decreasePassTransitionScoreHighPacketSpeed_returnHigherScore() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE + 10);
        when(mMockWifiInfo.getSuccessfulTxPacketsPerSecond())
                .thenReturn((double) TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND + 1);
        when(mMockWifiInfo.getSuccessfulRxPacketsPerSecond())
                .thenReturn((double) TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND + 1);

        int currentScore = WIFI_TRANSITION_SCORE - 10;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI + 1,
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS,
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(WIFI_TRANSITION_SCORE + 1, adjustScore);
    }

    @Test
    public void adjustScore_decreasePassTransitionScoreHighCurrentRssi_returnHigherScorer() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE + 10);
        when(mMockWifiInfo.getSuccessfulTxPacketsPerSecond())
                .thenReturn((double) TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND - 1);

        int currentScore = WIFI_TRANSITION_SCORE - 10;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI + 1,
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS,
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(WIFI_TRANSITION_SCORE + 1, adjustScore);
    }

    @Test
    public void adjustScore_decreasePassTransitionScoreHighPreviousRssi_returnHigherScore() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE + 10);
        when(mMockWifiInfo.getRssi()).thenReturn(TEST_ENTRY_RSSI + 1);
        when(mMockWifiInfo.getSuccessfulTxPacketsPerSecond())
                .thenReturn((double) TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND - 1);

        int currentScore = WIFI_TRANSITION_SCORE - 10;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI - 1,
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + 1,
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(WIFI_TRANSITION_SCORE + 1, adjustScore);
    }

    @Test
    public void adjustScore_scoreDecreasePassTransitionScoreWithLowRssis_returnCurrentScore() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE + 10);
        when(mMockWifiInfo.getRssi()).thenReturn(TEST_ENTRY_RSSI - 1);
        when(mMockWifiInfo.getSuccessfulTxPacketsPerSecond())
                .thenReturn((double) TEST_YIPPEE_SKIPPY_PACKETS_PER_SECOND - 1);

        int currentScore = WIFI_TRANSITION_SCORE - 10;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI - 1,
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + 1,
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(currentScore, adjustScore);
    }

    @Test
    public void adjustScore_scoreIncreasePassTransitionScoreSlowly_returnCurrentScore() {
        when(mMockWifiInfo.getScore()).thenReturn(WIFI_TRANSITION_SCORE - 10);

        int currentScore = WIFI_TRANSITION_SCORE + 10;
        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI,
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS,
                WIFI_TRANSITION_SCORE,
                currentScore);

        assertEquals(currentScore, adjustScore);
    }

    @Test
    public void adjustScore_scoreIncreasePassTransitionScoreQuickly_returnPreviousScore() {
        int previousScore = WIFI_TRANSITION_SCORE - 10;
        when(mMockWifiInfo.getScore()).thenReturn(previousScore);

        int adjustScore = mConnectedScorerHelper.adjustScore(mMockWifiInfo,
                TEST_ENTRY_RSSI,
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS - 1,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE + 10);

        assertEquals(previousScore, adjustScore);
    }

    @Test
    public void triggerScanIfNeeded_shouldTriggerScanAndEnoughTimePassed_returnTrue() {
        assertTrue(mConnectedScorerHelper.triggerScanIfNeeded(
                TEST_TIMESTAMP_MS - LOW_CONNECTED_SCORE_SCAN_PERIOD_MS - 1,
                TEST_TIMESTAMP_MS,
                true));
        verify(mMockWifiConnectivityManager).forceConnectivityScan(eq(WIFI_WORK_SOURCE));
    }

    @Test
    public void triggerScanIfNeeded_shouldTriggerScanAndInvalidPreviousScanTime_returnTrue() {
        assertTrue(mConnectedScorerHelper.triggerScanIfNeeded(
                INVALID_TIMESTAMP_MS,
                INVALID_TIMESTAMP_MS + 1,
                true));
    }

    @Test
    public void triggerScanIfNeeded_notEnoughTimePassed_returnFalse() {
        assertFalse(mConnectedScorerHelper.triggerScanIfNeeded(
                TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + 1,
                true));

        assertFalse(mConnectedScorerHelper.triggerScanIfNeeded(
                TEST_TIMESTAMP_MS - LOW_CONNECTED_SCORE_SCAN_PERIOD_MS,
                TEST_TIMESTAMP_MS,
                true));
    }

    @Test
    public void triggerScanIfNeeded_shouldTriggerScanFalse_returnFalse() {
        assertFalse(mConnectedScorerHelper.triggerScanIfNeeded(
                TEST_TIMESTAMP_MS - LOW_CONNECTED_SCORE_SCAN_PERIOD_MS - 1,
                TEST_TIMESTAMP_MS,
                false));
    }

    @Test
    public void shouldCheckNud_zeroNubKnob_returnFalse() {
        when(mMockScoringParams.getNudKnob()).thenReturn(0);

        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE - 20));
    }

    @Test
    public void shouldCheckNud_invalidLastNudRequestTimeHighAdjustedScore_returnFalse() {
        assertFalse(mConnectedScorerHelper.shouldCheckNud(INVALID_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE));

        assertFalse(mConnectedScorerHelper.shouldCheckNud(INVALID_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE + 1));
    }

    @Test
    public void shouldCheckNud_invalidLastNudRequestTimeLowAdjustedScore_returnTrue() {
        assertTrue(mConnectedScorerHelper.shouldCheckNud(INVALID_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE - 1));
    }

    @Test
    public void shouldCheckNud_longEnoughFromLastNudRequest_nextNudBreachScoreIsTransitionScore() {
        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE * 5,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE));

        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE * 5,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE + 1));

        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE * 5,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 10,
                WIFI_TRANSITION_SCORE - 1));
    }

    @Test
    public void shouldCheckNud_lastNudScoreIsTransitionScore_nudBreachScoreIsTransitionScoreToo() {
        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE));

        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE + 1));

        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1));
    }

    @Test
    public void shouldCheckNud_theHigherNudKnob_theEasierToCheckNud() {
        when(mMockScoringParams.getNudKnob()).thenReturn(10);

        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 2));
        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 11));
    }

    @Test
    public void shouldCheckNud_theLowerNudKnob_theHarderToCheckNud() {
        when(mMockScoringParams.getNudKnob()).thenReturn(1);

        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 2));
        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 11));
    }

    @Test
    public void shouldCheckNud_theLongerFromLastNudRequest_theEasierToCheckNud() {
        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE * 4,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 2));
        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE * 4,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 11));
    }

    @Test
    public void shouldCheckNud_theShorterFromLastNudRequest_theHarderToCheckNud() {
        assertFalse(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + 1,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 2));
        assertTrue(mConnectedScorerHelper.shouldCheckNud(TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + 1,
                WIFI_TRANSITION_SCORE,
                WIFI_TRANSITION_SCORE - 1,
                WIFI_TRANSITION_SCORE - 11));
    }

    @Test
    public void checkNudIfNeeded_enoughTimePassed_returnTrue() {
        assertTrue(mConnectedScorerHelper.checkNudIfNeeded(mMockIpClientManager, TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + NUD_THROTTLE_MS));
        verify(mMockIpClientManager).confirmConfiguration();
    }

    @Test
    public void checkNudIfNeeded_notEnoughTimePassed_returnFalse() {
        assertFalse(mConnectedScorerHelper.checkNudIfNeeded(mMockIpClientManager, TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + NUD_THROTTLE_MS - 1));
        verify(mMockIpClientManager, never()).confirmConfiguration();
    }

    @Test
    public void checkNudIfNeeded_nullIpClientManager_returnFalse() {
        assertFalse(mConnectedScorerHelper.checkNudIfNeeded(null, TEST_TIMESTAMP_MS,
                TEST_TIMESTAMP_MS + NUD_THROTTLE_MS));
        verify(mMockIpClientManager, never()).confirmConfiguration();
    }
}
