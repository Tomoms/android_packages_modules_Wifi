/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiInfo;

import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Unit tests for {@link com.android.server.wifi.VelocityBasedConnectedScorer}.
 */
@SmallTest
public class VelocityBasedConnectedScorerTest extends WifiBaseTest {
    private static final int WIFI_LOW_CONNECTED_SCORE_THRESHOLD_TO_TRIGGER_SCAN_FOR_MBB = 55;
    private static final int TEST_ADJUST_SCORE = 70;
    private static final long TEST_TIME_STAMP_MS = 12345678L;

    class FakeClock extends Clock {
        long mWallClockMillis = 1500000000000L;
        int mStepMillis = 3001;

        @Override
        public long getWallClockMillis() {
            mWallClockMillis += mStepMillis;
            return mWallClockMillis;
        }
    }

    FakeClock mClock;
    VelocityBasedConnectedScorer mVelocityBasedConnectedScorer;
    VelocityBasedConnectedScorer mVelocityBasedConnectedScorerWithMockHelper;
    ScanDetailCache mScanDetailCache;
    WifiInfo mWifiInfo;
    int mRssiExitThreshold2GHz;
    int mRssiExitThreshold5GHz;
    @Mock Context mContext;
    @Mock WifiGlobals mMockWifiGlobals;
    @Mock ConnectedScorerHelper mMockConnectedScorerHelper;
    @Mock WifiConnectivityManager mMockWifiConnectivityManager;
    @Spy private MockResources mResources = new MockResources();

    private int setupIntegerResource(int resourceName, int value) {
        doReturn(value).when(mResources).getInteger(resourceName);
        return value;
    }

    /**
     * Sets up resource values for testing
     *
     * See frameworks/base/core/res/res/values/config.xml
     */
    private void setUpResources(Resources resources) {
        mRssiExitThreshold2GHz = setupIntegerResource(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_24GHz, -83);
        mRssiExitThreshold5GHz = setupIntegerResource(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_5GHz, -80);
    }

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setUpResources(mResources);
        mWifiInfo = new WifiInfo();
        mWifiInfo.setFrequency(2412);
        when(mContext.getResources()).thenReturn(mResources);
        mClock = new FakeClock();
        when(mMockWifiGlobals.getWifiLowConnectedScoreThresholdToTriggerScanForMbb()).thenReturn(
                WIFI_LOW_CONNECTED_SCORE_THRESHOLD_TO_TRIGGER_SCAN_FOR_MBB);
        ScoringParams scoringParams = new ScoringParams(mContext);
        mVelocityBasedConnectedScorer = new VelocityBasedConnectedScorer(scoringParams,
                mMockWifiGlobals,
                new ConnectedScorerHelper(scoringParams, mMockWifiGlobals,
                        mMockWifiConnectivityManager));
        mVelocityBasedConnectedScorerWithMockHelper = new VelocityBasedConnectedScorer(
            scoringParams, mMockWifiGlobals, mMockConnectedScorerHelper);
    }

    /**
     *
     * Low RSSI, but some data is moving and error rate is low.
     *
     * Expect a score above threshold.
     */
    @Test
    public void allowLowRssiIfErrorRateIsLowAndSomeDataIsMoving() throws Exception {
        mWifiInfo.setRssi(mRssiExitThreshold2GHz - 2);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setSuccessfulTxPacketsPerSecond(2.1); // proportional to pps
        mWifiInfo.setLostTxPacketsPerSecond(.5);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(2.1);
        ConnectedScoreResult scoreResult = null;
        for (int i = 0; i < 10; i++) {
            scoreResult = mVelocityBasedConnectedScorer.generateScoreResult(mWifiInfo, null,
                    mClock.getWallClockMillis(), true);
        }
        assertTrue(scoreResult.score() > ConnectedScorer.WIFI_TRANSITION_SCORE);
        assertTrue(scoreResult.adjustedScore() > ConnectedScorer.WIFI_TRANSITION_SCORE);
        assertTrue(scoreResult.isWifiUsable());

        // If we reset, should be below threshold after the first input
        mVelocityBasedConnectedScorer.reset();
        scoreResult = mVelocityBasedConnectedScorer.generateScoreResult(mWifiInfo, null,
                mClock.getWallClockMillis(), true);
        assertTrue(scoreResult.score() < ConnectedScorer.WIFI_TRANSITION_SCORE);
        assertTrue(scoreResult.adjustedScore() < ConnectedScorer.WIFI_TRANSITION_SCORE);
        assertFalse(scoreResult.isWifiUsable());
    }

    /**
     *
     * Low RSSI, and almost no data is moving.
     *
     * Expect a score below threshold.
     */
    @Test
    public void disallowLowRssiIfDataIsNotMoving() throws Exception {
        mWifiInfo.setRssi(mRssiExitThreshold2GHz - 1);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setSuccessfulTxPacketsPerSecond(.1); // proportional to pps
        mWifiInfo.setLostTxPacketsPerSecond(0);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(.1);
        ConnectedScoreResult scoreResult = null;
        for (int i = 0; i < 10; i++) {
            scoreResult = mVelocityBasedConnectedScorer.generateScoreResult(mWifiInfo, null,
                    mClock.getWallClockMillis(), true);
        }
        assertTrue(scoreResult.score() < ConnectedScorer.WIFI_TRANSITION_SCORE);
        assertTrue(scoreResult.adjustedScore() < ConnectedScorer.WIFI_TRANSITION_SCORE);
        assertFalse(scoreResult.isWifiUsable());
    }

    /**
     *
     * Low RSSI, and almost no data is moving in secondary mode.
     *
     * Expect a score below secondary threshold.
     */
    @Test
    public void disallowLowRssiIfDataIsNotMovingInSecondaryMode() throws Exception {
        mWifiInfo.setRssi(mRssiExitThreshold2GHz - 1);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setSuccessfulTxPacketsPerSecond(.1); // proportional to pps
        mWifiInfo.setLostTxPacketsPerSecond(0);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(.1);
        ConnectedScoreResult scoreResult = null;
        for (int i = 0; i < 10; i++) {
            scoreResult = mVelocityBasedConnectedScorer.generateScoreResult(mWifiInfo, null,
                mClock.getWallClockMillis(), false);
        }
        assertTrue(scoreResult.score() < ConnectedScorer.WIFI_SECONDARY_TRANSITION_SCORE);
        assertTrue(scoreResult.adjustedScore() < ConnectedScorer.WIFI_SECONDARY_TRANSITION_SCORE);
        assertFalse(scoreResult.isWifiUsable());
    }

    @Test
    public void generateScoreResult_highScore_shouldTriggerScanTrue() {
        when(mMockConnectedScorerHelper.adjustScore(any(WifiInfo.class), anyDouble(), anyLong(),
                anyLong(), anyInt(), anyInt())).thenReturn(
                        WIFI_LOW_CONNECTED_SCORE_THRESHOLD_TO_TRIGGER_SCAN_FOR_MBB - 1);

        assertTrue(mVelocityBasedConnectedScorerWithMockHelper.generateScoreResult(mWifiInfo, null,
                mClock.getWallClockMillis(), false).shouldTriggerScan());
    }

    @Test
    public void generateScoreResult_scoreAtThreshold_shouldTriggerScanFalse() {
        when(mMockConnectedScorerHelper.adjustScore(any(WifiInfo.class), anyDouble(), anyLong(),
                anyLong(), anyInt(), anyInt())).thenReturn(
                        WIFI_LOW_CONNECTED_SCORE_THRESHOLD_TO_TRIGGER_SCAN_FOR_MBB);

        assertFalse(mVelocityBasedConnectedScorerWithMockHelper.generateScoreResult(mWifiInfo, null,
                mClock.getWallClockMillis(), false).shouldTriggerScan());
    }

    @Test
    public void generateScoreResult_lowScore_shouldTriggerScanFalse() {
        when(mMockConnectedScorerHelper.adjustScore(any(WifiInfo.class), anyDouble(), anyLong(),
                anyLong(), anyInt(), anyInt())).thenReturn(
                        WIFI_LOW_CONNECTED_SCORE_THRESHOLD_TO_TRIGGER_SCAN_FOR_MBB + 1);

        assertFalse(mVelocityBasedConnectedScorerWithMockHelper.generateScoreResult(mWifiInfo, null,
                mClock.getWallClockMillis(), false).shouldTriggerScan());
    }

    @Test
    public void generateScoreResult_shouldCheckNud() {
        when(mMockConnectedScorerHelper.shouldCheckNud(
                anyLong(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(mMockConnectedScorerHelper.adjustScore(any(WifiInfo.class), anyDouble(), anyLong(),
                anyLong(), anyInt(), anyInt())).thenReturn(TEST_ADJUST_SCORE);

        assertTrue(mVelocityBasedConnectedScorerWithMockHelper.generateScoreResult(mWifiInfo, null,
                TEST_TIME_STAMP_MS, true).shouldCheckNud());
        assertEquals(TEST_TIME_STAMP_MS,
                mVelocityBasedConnectedScorerWithMockHelper.mLastNudCheckTimeMs);
        assertEquals(TEST_ADJUST_SCORE,
                mVelocityBasedConnectedScorerWithMockHelper.mLastNudCheckScore);
    }

    @Test
    public void generateScoreResult_shouldNotCheckNud() {
        when(mMockConnectedScorerHelper.shouldCheckNud(
                anyLong(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(false);
        when(mMockConnectedScorerHelper.adjustScore(any(WifiInfo.class), anyDouble(), anyLong(),
                anyLong(), anyInt(), anyInt())).thenReturn(TEST_ADJUST_SCORE);

        assertFalse(mVelocityBasedConnectedScorerWithMockHelper.generateScoreResult(mWifiInfo, null,
                TEST_TIME_STAMP_MS, true).shouldCheckNud());
        assertNotEquals(TEST_TIME_STAMP_MS,
                mVelocityBasedConnectedScorerWithMockHelper.mLastNudCheckTimeMs);
        assertNotEquals(TEST_ADJUST_SCORE,
                mVelocityBasedConnectedScorerWithMockHelper.mLastNudCheckScore);
    }
}
