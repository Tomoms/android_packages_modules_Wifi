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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.net.wifi.WifiUsabilityStatsEntry;
import android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats;
import android.net.wifi.WifiUsabilityStatsEntry.RadioStats;
import android.net.wifi.WifiUsabilityStatsEntry.RateStats;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link com.android.server.wifi.ml_connected_scorer.WifiUsabilityStatsEntryWrapper}
 */
@SmallTest
public final class WifiUsabilityStatsEntryWrapperTest {
    private WifiUsabilityStatsEntryWrapper mWrapper;

    @Before
    public void setUp() {
        mWrapper = new WifiUsabilityStatsEntryWrapper(generateUsabilityStats());
    }

    @Test
    public void setDefaultValuesWithoutRxLinkSpeed_defaultTxTputAboveMinValue() throws Exception {
        mWrapper.setDefaultValues();
        double[] normMeans = FeatureUtils.getNormMeans();

        assertThat(mWrapper.estTxTput).isEqualTo(normMeans[0]);
        assertThat(mWrapper.estTxTput).isAtLeast(0);
        assertThat(mWrapper.getFeaturesAsArray().length).isEqualTo(15);
    }

    @Test
    public void testLowTxTraffic_returnNanEstTxTput() throws Exception {
        WifiUsabilityStatsEntry statsPrev =
                generateUsabilityStats(100, 100, -50, 0, 0, 0, 0, 0, 25, 1000);
        WifiUsabilityStatsEntry statsCurr =
                generateUsabilityStats(100, 100, -50, 0, 0, 0, 10, 10, 125, 1200);
        mWrapper = new WifiUsabilityStatsEntryWrapper(statsCurr);
        mWrapper.setDiffs(statsPrev);

        assertTrue(Double.isNaN(mWrapper.totalTxSuccessDiffPerTxAttempt));
        assertTrue(Double.isNaN(mWrapper.totalTxRetriesDiffPerTxAttempt));
        assertTrue(Double.isNaN(mWrapper.estTxTput));
        assertTrue(Double.isNaN(mWrapper.linkSpeedMbps));
    }

    @Test
    public void testLowRxTraffic_returnNanEstRxTput() throws Exception {
        WifiUsabilityStatsEntry statsPrev =
                generateUsabilityStats(100, 100, -50, 0, 0, 0, 0, 0, 25, 1000);
        WifiUsabilityStatsEntry statsCurr =
                generateUsabilityStats(100, 100, -50, 10, 0, 0, 0, 0, 125, 1200);
        mWrapper = new WifiUsabilityStatsEntryWrapper(statsCurr);
        mWrapper.setDiffs(statsPrev);

        assertTrue(Double.isNaN(mWrapper.estRxTput));
        assertTrue(Double.isNaN(mWrapper.rxLinkSpeedMbps));
    }

    @Test
    public void highTxTraffic_returnNanEstTxRxTput() throws Exception {
        WifiUsabilityStatsEntry statsPrev =
                generateUsabilityStats(100, 100, -50, 0, 0, 0, 0, 0, 25, 1000);
        WifiUsabilityStatsEntry statsCurr =
                generateUsabilityStats(100, 100, -50, 90, 0, 0, 10, 10, 125, 1200);
        mWrapper = new WifiUsabilityStatsEntryWrapper(statsCurr);
        mWrapper.setDiffs(statsPrev);

        assertTrue(Double.isNaN(mWrapper.totalTxSuccessDiffPerTxAttempt));
        assertTrue(Double.isNaN(mWrapper.totalTxRetriesDiffPerTxAttempt));
        assertTrue(Double.isNaN(mWrapper.estRxTput));
        assertTrue(Double.isNaN(mWrapper.rxLinkSpeedMbps));
        assertTrue(Double.isNaN(mWrapper.estTxTput));
        assertTrue(Double.isNaN(mWrapper.linkSpeedMbps));
    }

    @Test
    public void highRxTraffic_returnNanEstTxRxTput() throws Exception {
        WifiUsabilityStatsEntry statsPrev =
                generateUsabilityStats(100, 100, -50, 0, 0, 0, 0, 0, 25, 1000);
        WifiUsabilityStatsEntry statsCurr =
                generateUsabilityStats(100, 100, -50, 59, 0, 0, 90, 10, 125, 1200);
        mWrapper = new WifiUsabilityStatsEntryWrapper(statsCurr);
        mWrapper.setDiffs(statsPrev);

        assertTrue(Double.isNaN(mWrapper.estRxTput));
        assertTrue(Double.isNaN(mWrapper.rxLinkSpeedMbps));
        assertTrue(Double.isNaN(mWrapper.estTxTput));
        assertTrue(Double.isNaN(mWrapper.linkSpeedMbps));
    }

    private static WifiUsabilityStatsEntry generateUsabilityStats() {
        return generateUsabilityStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static WifiUsabilityStatsEntry generateUsabilityStats(
            int txSpeedMbps,
            int rxSpeedMbps,
            int rssi,
            int txSuccess,
            int txRetries,
            int txBad,
            int rxSuccess,
            int rxOnTimeMs,
            int ccaBusyTimeMs,
            int radioOnTimeMs) {
        return new WifiUsabilityStatsEntry(
                1234567L, // long timeStampMillis
                rssi, // int rssi
                txSpeedMbps, // int linkSpeedMbps
                txSuccess, // long totalTxSuccess
                txRetries, // long totalTxRetries
                txBad, // long totalTxBad
                rxSuccess, // long totalRxSuccess
                rxOnTimeMs, // long totalRadioOnTimeMillis
                1000L, // long totalRadioTxTimeMillis
                radioOnTimeMs, // long totalRadioRxTimeMillis
                1000L, // long totalScanTimeMillis
                0, // long totalNanScanTimeMillis
                0, // long totalBackgroundScanTimeMillis
                0, // long totalRoamScanTimeMillis
                0, // long totalPnoScanTimeMillis
                0, // long totalHotspot2ScanTimeMillis
                ccaBusyTimeMs, // long totalCcaBusyFreqTimeMillis
                radioOnTimeMs, // long totalRadioOnFreqTimeMillis
                0, // long totalBeaconRx
                0, // @ProbeStatus int probeStatusSinceLastUpdate
                0, // int probeElapsedTimeSinceLastUpdateMillis
                0, // int probeMcsRateSinceLastUpdate
                rxSpeedMbps, // int rxLinkSpeedMbps
                0, //int timeSliceDutyCycleInPercent,
                new ContentionTimeStats[0], //ContentionTimeStats[] contentionTimeStats,
                new RateStats[0], //RateStats[] rateStats,
                new RadioStats[0], //RadioStats[] radiostats,
                0, //int channelUtilizationRatio,
                true, // boolean isThroughputSufficient
                true, // boolean isWifiScoringEnabled
                true, // boolean isCellularDataAvailable
                0, // @NetworkType int cellularDataNetworkType
                0, // int cellularSignalStrengthDbm
                0, // int cellularSignalStrengthDb,
                false, // boolean isSameRegisteredCell
                new SparseArray<>(), // SparseArray<LinkStats> linkStats
                0, // int wifiLinkCount
                0, // @WifiManager.MloMode int mloMode
                0, //long txTransmittedBytes,
                0, // long rxTransmittedBytes,
                0, // int labelBadEventCount,
                0, //int wifiFrameworkState,
                0, // int isNetworkCapabilitiesDownstreamSufficient,
                0, //int isNetworkCapabilitiesUpstreamSufficient,
                0, //int isThroughputPredictorDownstreamSufficient,
                0, //int isThroughputPredictorUpstreamSufficient,
                false, // boolean isBluetoothConnected,
                0, // int uwbAdapterState,
                false, // boolean isLowLatencyActivated,
                0, // int maxSupportedTxLinkSpeed,
                0, //int maxSupportedRxLinkSpeed,
                0, // int voipMode,
                0, // int threadDeviceRole,
                0, // int statusDataStall,
                0, //int internalScore,
                0); // int internalScorerType
    }
}
