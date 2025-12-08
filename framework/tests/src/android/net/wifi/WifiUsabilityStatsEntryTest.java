/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.wifi;

import static android.net.wifi.WifiUsabilityStatsEntry.SCORER_TYPE_ML;
import static android.net.wifi.WifiUsabilityStatsEntry.SCORER_TYPE_VELOCITY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.validateMockitoUsage;

import android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats;
import android.net.wifi.WifiUsabilityStatsEntry.PacketStats;
import android.net.wifi.WifiUsabilityStatsEntry.PeerInfo;
import android.net.wifi.WifiUsabilityStatsEntry.RadioStats;
import android.net.wifi.WifiUsabilityStatsEntry.RateStats;
import android.net.wifi.WifiUsabilityStatsEntry.ScanResultWithSameFreq;
import android.os.Parcel;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.NoSuchElementException;


/**
 * Unit tests for {@link android.net.wifi.WifiUsabilityStatsEntry}.
 */
@SmallTest
public class WifiUsabilityStatsEntryTest {
    private static final int TEST_INTERNAL_SCORE = 50;

    /**
     * Setup before tests.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Clean up after tests.
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Verify parcel read/write for Wifi usability stats result.
     */
    @Test
    public void verifyStatsResultWriteAndThenRead() throws Exception {
        WifiUsabilityStatsEntry writeResult = createResult();
        WifiUsabilityStatsEntry readResult = parcelWriteRead(writeResult);
        assertWifiUsabilityStatsEntryEquals(writeResult, readResult);
    }

    /**
     * Verify WifiUsabilityStatsEntry#TimeSliceDutyCycleInPercent.
     */
    @Test
    public void getTimeSliceDutyCycleInPercent() throws Exception {
        ContentionTimeStats[] contentionTimeStats = new ContentionTimeStats[4];
        contentionTimeStats[0] = new ContentionTimeStats(1, 2, 3, 4);
        contentionTimeStats[1] = new ContentionTimeStats(5, 6, 7, 8);
        contentionTimeStats[2] = new ContentionTimeStats(9, 10, 11, 12);
        contentionTimeStats[3] = new ContentionTimeStats(13, 14, 15, 16);
        PacketStats[] packetStats = new PacketStats[4];
        packetStats[0] = new PacketStats(1, 2, 3, 4);
        packetStats[1] = new PacketStats(5, 6, 7, 8);
        packetStats[2] = new PacketStats(9, 10, 11, 12);
        packetStats[3] = new PacketStats(13, 14, 15, 16);
        RateStats[] rateStats = new RateStats[2];
        rateStats[0] = new RateStats(1, 3, 4, 7, 9, 11, 13, 15, 17);
        rateStats[1] = new RateStats(2, 2, 3, 8, 10, 12, 14, 16, 18);

        RadioStats[] radioStats = new RadioStats[2];
        radioStats[0] = new RadioStats(0, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        radioStats[1] = new RadioStats(1, 20, 21, 22, 23, 24, 25, 26, 27, 28, new int[] {1, 2, 3});
        PeerInfo[] peerInfo = new PeerInfo[1];
        peerInfo[0] = new PeerInfo(1, 50, rateStats);
        ScanResultWithSameFreq[] scanResultsWithSameFreq2G = new ScanResultWithSameFreq[1];
        scanResultsWithSameFreq2G[0] = new ScanResultWithSameFreq(100, -50, 2412);
        ScanResultWithSameFreq[] scanResultsWithSameFreq5G = new ScanResultWithSameFreq[1];
        scanResultsWithSameFreq5G[0] = new ScanResultWithSameFreq(100, -50, 5500);

        SparseArray<WifiUsabilityStatsEntry.LinkStats> linkStats = new SparseArray<>();
        linkStats.put(0, new WifiUsabilityStatsEntry.LinkStats(0,
                WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN, 0, -50, 2412, -50, 0, 0, 0,
                300, 200, 188, 2, 2, 100, 300, 100, 100, 200,
                contentionTimeStats, rateStats, packetStats, peerInfo, scanResultsWithSameFreq2G));
        linkStats.put(1, new WifiUsabilityStatsEntry.LinkStats(1,
                WifiUsabilityStatsEntry.LINK_STATE_UNKNOWN, 0, -40, 5500, -40, 1, 0, 0,
                860, 600, 388, 2, 2, 200, 400, 100, 150, 300,
                contentionTimeStats, rateStats, packetStats, peerInfo, scanResultsWithSameFreq5G));

        WifiUsabilityStatsEntry usabilityStatsEntry = new WifiUsabilityStatsEntry(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                32, contentionTimeStats, rateStats, radioStats, 100, true,
                true, true, 23, 24, 25, true, linkStats, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
                false, 36, false, 37, 38, 39, 40, 41, TEST_INTERNAL_SCORE, SCORER_TYPE_ML);
        assertEquals(32, usabilityStatsEntry.getTimeSliceDutyCycleInPercent());

        WifiUsabilityStatsEntry usabilityStatsEntryWithInvalidDutyCycleValue =
                new WifiUsabilityStatsEntry(
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                        21, 22, -1, contentionTimeStats, rateStats, radioStats, 101, true, true,
                        true, 23, 24, 25, true, linkStats, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
                        true, 36, true, 37, 38, 39, 40, 41, TEST_INTERNAL_SCORE, SCORER_TYPE_ML);
        try {
            usabilityStatsEntryWithInvalidDutyCycleValue.getTimeSliceDutyCycleInPercent();
            fail();
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    /**
     * Write the provided {@link WifiUsabilityStatsEntry} to a parcel and deserialize it.
     */
    private static WifiUsabilityStatsEntry parcelWriteRead(
            WifiUsabilityStatsEntry writeResult) throws Exception {
        Parcel parcel = Parcel.obtain();
        writeResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        return WifiUsabilityStatsEntry.CREATOR.createFromParcel(parcel);
    }

    private static WifiUsabilityStatsEntry createResult() {
        ContentionTimeStats[] contentionTimeStats = new ContentionTimeStats[4];
        contentionTimeStats[0] = new ContentionTimeStats(1, 2, 3, 4);
        contentionTimeStats[1] = new ContentionTimeStats(5, 6, 7, 8);
        contentionTimeStats[2] = new ContentionTimeStats(9, 10, 11, 12);
        contentionTimeStats[3] = new ContentionTimeStats(13, 14, 15, 16);
        PacketStats[] packetStats = new PacketStats[4];
        packetStats[0] = new PacketStats(1, 2, 3, 4);
        packetStats[1] = new PacketStats(5, 6, 7, 8);
        packetStats[2] = new PacketStats(9, 10, 11, 12);
        packetStats[3] = new PacketStats(13, 14, 15, 16);
        RateStats[] rateStats = new RateStats[2];
        rateStats[0] = new RateStats(1, 3, 4, 7, 9, 11, 13, 15, 17);
        rateStats[1] = new RateStats(2, 2, 3, 8, 10, 12, 14, 16, 18);

        RadioStats[] radioStats = new RadioStats[2];
        radioStats[0] = new RadioStats(0, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        radioStats[1] = new RadioStats(1, 20, 21, 22, 23, 24, 25, 26, 27, 28, new int[] {1, 2, 3});
        PeerInfo[] peerInfo = new PeerInfo[1];
        peerInfo[0] = new PeerInfo(1, 50, rateStats);
        ScanResultWithSameFreq[] scanResultsWithSameFreq2G = new ScanResultWithSameFreq[1];
        scanResultsWithSameFreq2G[0] = new ScanResultWithSameFreq(100, -50, 2412);
        ScanResultWithSameFreq[] scanResultsWithSameFreq5G = new ScanResultWithSameFreq[1];
        scanResultsWithSameFreq5G[0] = new ScanResultWithSameFreq(100, -50, 5500);
        SparseArray<WifiUsabilityStatsEntry.LinkStats> linkStats = new SparseArray<>();
        linkStats.put(0, new WifiUsabilityStatsEntry.LinkStats(3,
                WifiUsabilityStatsEntry.LINK_STATE_IN_USE, 0, -50, 2412, -50, 0, 0, 0, 300,
                200, 188, 2, 2, 100, 300, 100, 100, 200,
                contentionTimeStats, rateStats, packetStats, peerInfo, scanResultsWithSameFreq2G));
        linkStats.put(1, new WifiUsabilityStatsEntry.LinkStats(8,
                WifiUsabilityStatsEntry.LINK_STATE_IN_USE, 0, -40, 5500, -40, 1, 0, 0, 860,
                600, 388, 2, 2, 200, 400, 100, 150, 300,
                contentionTimeStats, rateStats, packetStats, peerInfo, scanResultsWithSameFreq5G));

        return new WifiUsabilityStatsEntry(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                50, contentionTimeStats, rateStats, radioStats, 102, true,
                true, true, 23, 24, 25, true, linkStats, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
                true, 36, false, 37, 38, 39, 40, 41, TEST_INTERNAL_SCORE, SCORER_TYPE_ML
        );
    }

    private static void assertWifiUsabilityStatsEntryEquals(
            WifiUsabilityStatsEntry expected,
            WifiUsabilityStatsEntry actual) {
        assertEquals(expected.getTimeStampMillis(), actual.getTimeStampMillis());
        assertEquals(expected.getRssi(), actual.getRssi());
        assertEquals(expected.getLinkSpeedMbps(), actual.getLinkSpeedMbps());
        assertEquals(expected.getTotalTxSuccess(), actual.getTotalTxSuccess());
        assertEquals(expected.getTotalTxRetries(), actual.getTotalTxRetries());
        assertEquals(expected.getTotalTxBad(), actual.getTotalTxBad());
        assertEquals(expected.getTotalRxSuccess(), actual.getTotalRxSuccess());
        assertEquals(expected.getTotalCcaBusyFreqTimeMillis(),
                actual.getTotalCcaBusyFreqTimeMillis());
        assertEquals(expected.getTotalRadioOnFreqTimeMillis(),
                actual.getTotalRadioOnFreqTimeMillis());
        assertEquals(expected.getWifiLinkLayerRadioStats().size(),
                actual.getWifiLinkLayerRadioStats().size());
        for (int i = 0; i < expected.getWifiLinkLayerRadioStats().size(); i++) {
            RadioStats expectedRadioStats = expected.getWifiLinkLayerRadioStats().get(i);
            RadioStats actualRadioStats = actual.getWifiLinkLayerRadioStats().get(i);
            assertEquals(expectedRadioStats.getRadioId(),
                    actualRadioStats.getRadioId());
            assertEquals(expectedRadioStats.getTotalRadioOnTimeMillis(),
                    actualRadioStats.getTotalRadioOnTimeMillis());
            assertEquals(expectedRadioStats.getTotalRadioTxTimeMillis(),
                    actualRadioStats.getTotalRadioTxTimeMillis());
            assertEquals(expectedRadioStats.getTotalRadioRxTimeMillis(),
                    actualRadioStats.getTotalRadioRxTimeMillis());
            assertEquals(expectedRadioStats.getTotalScanTimeMillis(),
                    actualRadioStats.getTotalScanTimeMillis());
            assertEquals(expectedRadioStats.getTotalNanScanTimeMillis(),
                    actualRadioStats.getTotalNanScanTimeMillis());
            assertEquals(expectedRadioStats.getTotalBackgroundScanTimeMillis(),
                    actualRadioStats.getTotalBackgroundScanTimeMillis());
            assertEquals(expectedRadioStats.getTotalRoamScanTimeMillis(),
                    actualRadioStats.getTotalRoamScanTimeMillis());
            assertEquals(expectedRadioStats.getTotalPnoScanTimeMillis(),
                    actualRadioStats.getTotalPnoScanTimeMillis());
            assertEquals(expectedRadioStats.getTotalHotspot2ScanTimeMillis(),
                    actualRadioStats.getTotalHotspot2ScanTimeMillis());
        }
        assertEquals(expected.getTotalRadioOnTimeMillis(), actual.getTotalRadioOnTimeMillis());
        assertEquals(expected.getTotalRadioTxTimeMillis(), actual.getTotalRadioTxTimeMillis());
        assertEquals(expected.getTotalRadioRxTimeMillis(), actual.getTotalRadioRxTimeMillis());
        assertEquals(expected.getTotalScanTimeMillis(), actual.getTotalScanTimeMillis());
        assertEquals(expected.getTotalNanScanTimeMillis(), actual.getTotalNanScanTimeMillis());
        assertEquals(expected.getTotalBackgroundScanTimeMillis(),
                actual.getTotalBackgroundScanTimeMillis());
        assertEquals(expected.getTotalRoamScanTimeMillis(), actual.getTotalRoamScanTimeMillis());
        assertEquals(expected.getTotalPnoScanTimeMillis(), actual.getTotalPnoScanTimeMillis());
        assertEquals(expected.getTotalHotspot2ScanTimeMillis(),
                actual.getTotalHotspot2ScanTimeMillis());
        assertEquals(expected.getTotalCcaBusyFreqTimeMillis(),
                actual.getTotalCcaBusyFreqTimeMillis());
        assertEquals(expected.getTotalRadioOnFreqTimeMillis(),
                actual.getTotalRadioOnFreqTimeMillis());
        assertEquals(expected.getTotalBeaconRx(), actual.getTotalBeaconRx());
        assertEquals(expected.getProbeStatusSinceLastUpdate(),
                actual.getProbeStatusSinceLastUpdate());
        assertEquals(expected.getProbeElapsedTimeSinceLastUpdateMillis(),
                actual.getProbeElapsedTimeSinceLastUpdateMillis());
        assertEquals(expected.getProbeMcsRateSinceLastUpdate(),
                actual.getProbeMcsRateSinceLastUpdate());
        assertEquals(expected.getRxLinkSpeedMbps(), actual.getRxLinkSpeedMbps());
        assertEquals(expected.getTimeSliceDutyCycleInPercent(),
                actual.getTimeSliceDutyCycleInPercent());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionTimeMinMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionTimeMinMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionTimeMaxMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionTimeMaxMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionTimeAvgMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionTimeAvgMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionNumSamples(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                        .getContentionNumSamples());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionTimeMinMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionTimeMinMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionTimeMaxMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionTimeMaxMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionTimeAvgMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionTimeAvgMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionNumSamples(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                        .getContentionNumSamples());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionTimeMinMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionTimeMinMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionTimeMaxMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionTimeMaxMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionTimeAvgMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionTimeAvgMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionNumSamples(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                        .getContentionNumSamples());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionTimeMinMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionTimeMinMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionTimeMaxMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionTimeMaxMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionTimeAvgMicros(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionTimeAvgMicros());
        assertEquals(
                expected.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionNumSamples(),
                actual.getContentionTimeStats(WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                        .getContentionNumSamples());
        for (int i = 0; i < expected.getRateStats().size(); i++) {
            RateStats expectedStats = expected.getRateStats().get(i);
            RateStats actualStats = actual.getRateStats().get(i);
            assertEquals(expectedStats.getPreamble(), actualStats.getPreamble());
            assertEquals(expectedStats.getNumberOfSpatialStreams(),
                    actualStats.getNumberOfSpatialStreams());
            assertEquals(expectedStats.getBandwidthInMhz(), actualStats.getBandwidthInMhz());
            assertEquals(expectedStats.getRateMcsIdx(), actualStats.getRateMcsIdx());
            assertEquals(expectedStats.getBitRateInKbps(), actualStats.getBitRateInKbps());
            assertEquals(expectedStats.getTxMpdu(), actualStats.getTxMpdu());
            assertEquals(expectedStats.getRxMpdu(), actualStats.getRxMpdu());
            assertEquals(expectedStats.getMpduLost(), actualStats.getMpduLost());
            assertEquals(expectedStats.getRetries(), actualStats.getRetries());
        }
        assertEquals(expected.getChannelUtilizationRatio(), actual.getChannelUtilizationRatio());
        assertEquals(expected.isThroughputSufficient(), actual.isThroughputSufficient());
        assertEquals(expected.isWifiScoringEnabled(), actual.isWifiScoringEnabled());
        assertEquals(expected.isCellularDataAvailable(), actual.isCellularDataAvailable());
        assertEquals(expected.getCellularDataNetworkType(), actual.getCellularDataNetworkType());
        assertEquals(expected.getCellularSignalStrengthDbm(),
                actual.getCellularSignalStrengthDbm());
        assertEquals(expected.getCellularSignalStrengthDb(), actual.getCellularSignalStrengthDb());
        assertEquals(expected.isSameRegisteredCell(), actual.isSameRegisteredCell());

        // validate link specific stats
        assertArrayEquals(expected.getLinkIds(), actual.getLinkIds());
        int[] links = actual.getLinkIds();
        if (links != null) {
            for (int link : links) {
                assertEquals(expected.getRssi(link), actual.getRssi(link));
                assertEquals(expected.getRadioId(link), actual.getRadioId(link));
                assertEquals(expected.getFrequencyMhz(link), actual.getFrequencyMhz(link));
                assertEquals(expected.getRssiMgmt(link), actual.getRssiMgmt(link));
                assertEquals(expected.getChannelWidth(link), actual.getChannelWidth(link));
                assertEquals(expected.getCenterFreqFirstSegment(link),
                        actual.getCenterFreqFirstSegment(link));
                assertEquals(expected.getCenterFreqSecondSegment(link),
                        actual.getCenterFreqSecondSegment(link));
                assertEquals(expected.getTxLinkSpeedMbps(link),
                        actual.getTxLinkSpeedMbps(link));
                assertEquals(expected.getRxLinkSpeedMbps(link),
                        actual.getRxLinkSpeedMbps(link));
                assertEquals(expected.getTotalBeaconRx(link),
                        actual.getTotalBeaconRx(link));
                assertEquals(expected.getTimeSliceDutyCycleInPercent(link),
                        actual.getTimeSliceDutyCycleInPercent(link));
                assertEquals(expected.getTotalRxSuccess(link),
                        actual.getTotalRxSuccess(link));
                assertEquals(expected.getTotalTxSuccess(link),
                        actual.getTotalTxSuccess(link));
                assertEquals(expected.getTotalTxBad(link), actual.getTotalTxBad(link));
                assertEquals(expected.getTotalTxRetries(link),
                        actual.getTotalTxRetries(link));
                assertEquals(expected.getTotalCcaBusyFreqTimeMillis(link),
                        actual.getTotalCcaBusyFreqTimeMillis(link));
                assertEquals(expected.getTotalRadioOnFreqTimeMillis(link),
                        actual.getTotalRadioOnFreqTimeMillis(link));
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionTimeMinMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionTimeMinMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionTimeMaxMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionTimeMaxMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionTimeAvgMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionTimeAvgMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionNumSamples(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getContentionNumSamples());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionTimeMinMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionTimeMinMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionTimeMaxMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionTimeMaxMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionTimeAvgMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionTimeAvgMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionNumSamples(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getContentionNumSamples());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionTimeMinMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionTimeMinMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionTimeMaxMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionTimeMaxMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionTimeAvgMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionTimeAvgMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionNumSamples(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getContentionNumSamples());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionTimeMinMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionTimeMinMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionTimeMaxMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionTimeMaxMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionTimeAvgMicros(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionTimeAvgMicros());
                assertEquals(
                        expected.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionNumSamples(),
                        actual.getContentionTimeStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getContentionNumSamples());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getTxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getTxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getTxRetries(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getTxRetries());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getTxBad(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getTxBad());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getRxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE)
                                .getRxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getTxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getTxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getTxRetries(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getTxRetries());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getTxBad(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getTxBad());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getRxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BK)
                                .getRxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getTxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getTxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getTxRetries(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getTxRetries());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getTxBad(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getTxBad());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getRxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VI)
                                .getRxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getTxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getTxSuccess());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getTxRetries(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getTxRetries());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getTxBad(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getTxBad());
                assertEquals(
                        expected.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getRxSuccess(),
                        actual.getPacketStats(link,
                                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_VO)
                                .getRxSuccess());

                for (int j = 0; j < expected.getRateStats(link).size(); j++) {
                    RateStats expectedStats = expected.getRateStats(link).get(j);
                    RateStats actualStats = actual.getRateStats(link).get(j);
                    assertEquals(expectedStats.getPreamble(), actualStats.getPreamble());
                    assertEquals(expectedStats.getNumberOfSpatialStreams(),
                            actualStats.getNumberOfSpatialStreams());
                    assertEquals(expectedStats.getBandwidthInMhz(),
                            actualStats.getBandwidthInMhz());
                    assertEquals(expectedStats.getRateMcsIdx(), actualStats.getRateMcsIdx());
                    assertEquals(expectedStats.getBitRateInKbps(), actualStats.getBitRateInKbps());
                    assertEquals(expectedStats.getTxMpdu(), actualStats.getTxMpdu());
                    assertEquals(expectedStats.getRxMpdu(), actualStats.getRxMpdu());
                    assertEquals(expectedStats.getMpduLost(), actualStats.getMpduLost());
                    assertEquals(expectedStats.getRetries(), actualStats.getRetries());
                }
                for (int j = 0; j < expected.getPeerInfo(link).size(); j++) {
                    PeerInfo expectedStats = expected.getPeerInfo(link).get(j);
                    PeerInfo actualStats = actual.getPeerInfo(link).get(j);
                    assertEquals(expectedStats.getStaCount(), actualStats.getStaCount());
                    assertEquals(expectedStats.getChanUtil(), actualStats.getChanUtil());
                }
                for (int j = 0; j < expected.getScanResultsWithSameFreq(link).length; j++) {
                    assertEquals(expected.getScanResultsWithSameFreq(link)[j]
                                .getScanResultTimestampMicros(),
                                actual.getScanResultsWithSameFreq(link)[j]
                                .getScanResultTimestampMicros());
                    assertEquals(expected.getScanResultsWithSameFreq(link)[j].getRssi(),
                                actual.getScanResultsWithSameFreq(link)[j].getRssi());
                    assertEquals(expected.getScanResultsWithSameFreq(link)[j].getFrequency(),
                                actual.getScanResultsWithSameFreq(link)[j].getFrequency());
                }
            }
        }
        assertEquals(expected.getWifiLinkCount(), actual.getWifiLinkCount());
        assertEquals(expected.isNetworkCapabilitiesDownstreamSufficient(),
                actual.isNetworkCapabilitiesDownstreamSufficient());
        assertEquals(expected.isNetworkCapabilitiesUpstreamSufficient(),
                actual.isNetworkCapabilitiesUpstreamSufficient());
        assertEquals(expected.isThroughputPredictorDownstreamSufficient(),
                actual.isThroughputPredictorDownstreamSufficient());
        assertEquals(expected.isThroughputPredictorUpstreamSufficient(),
                actual.isThroughputPredictorUpstreamSufficient());
        assertEquals(expected.isBluetoothConnected(), actual.isBluetoothConnected());
        assertEquals(expected.getStatusDataStall(), actual.getStatusDataStall());
        assertEquals(expected.getInternalScore(), actual.getInternalScore());
        assertEquals(expected.getInternalScorerType(), actual.getInternalScorerType());
    }

    /**
     * Verify invalid linkId for WifiUsabilityStatsEntry.getXX(linkId) API's.
     */
    @Test
    public void verifyInvalidLinkIdForGetApis() throws Exception {

        SparseArray<WifiUsabilityStatsEntry.LinkStats> linkStats = new SparseArray<>();
        linkStats.put(0, new WifiUsabilityStatsEntry.LinkStats(0,
                WifiUsabilityStatsEntry.LINK_STATE_IN_USE, 0, -50, 2412, -50, 0, 0, 0, 300,
                200, 188, 2, 2, 100, 300, 100, 100, 200,
                null, null, null, null, null));

        WifiUsabilityStatsEntry usabilityStatsEntry = new WifiUsabilityStatsEntry(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                32, null, null, null, 100, true, true, true, 23, 24, 25, true, linkStats, 26, 27,
                28, 29, 30, 31, 32, 33, 34, 35, true, 36, true, 37, 38, 39, 40, 41,
                TEST_INTERNAL_SCORE, SCORER_TYPE_ML);

        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getLinkState(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getRssi(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTxLinkSpeedMbps(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getContentionTimeStats(MloLink.INVALID_MLO_LINK_ID,
                        WifiUsabilityStatsEntry.WME_ACCESS_CATEGORY_BE));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getRateStats(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getRadioId(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getRxLinkSpeedMbps(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalTxBad(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalBeaconRx(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalRxSuccess(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalTxSuccess(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalTxRetries(MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTimeSliceDutyCycleInPercent(
                        MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalCcaBusyFreqTimeMillis(
                        MloLink.INVALID_MLO_LINK_ID));
        assertThrows("linkId is invalid - " + MloLink.INVALID_MLO_LINK_ID,
                NoSuchElementException.class,
                () -> usabilityStatsEntry.getTotalRadioOnFreqTimeMillis(
                        MloLink.INVALID_MLO_LINK_ID));
    }

    @Test
    public void testDefaultBuilder() {
        WifiUsabilityStatsEntry entry = new WifiUsabilityStatsEntry.Builder().build();
        assertEquals(0, entry.getTimeStampMillis());
        assertEquals(WifiInfo.INVALID_RSSI, entry.getRssi());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, entry.getLinkSpeedMbps());
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, entry.getCellularDataNetworkType());
        assertFalse(entry.isThroughputSufficient());
    }

    @Test
    public void testBuilderWithSingleField() {
        long timestamp = 123456789L;
        WifiUsabilityStatsEntry entry = new WifiUsabilityStatsEntry.Builder()
                .setTimeStampMillis(timestamp)
                .build();

        assertEquals(timestamp, entry.getTimeStampMillis());
        assertEquals(WifiInfo.INVALID_RSSI, entry.getRssi());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, entry.getLinkSpeedMbps());
    }

    @Test
    public void testBuilderWithMultipleFields() {
        long timestamp = 987654321L;
        int rssi = -50;
        int linkSpeed = 866;
        boolean isThroughputSufficient = true;
        int cellularDataNetworkType = TelephonyManager.NETWORK_TYPE_LTE;

        WifiUsabilityStatsEntry entry = new WifiUsabilityStatsEntry.Builder()
                .setTimeStampMillis(timestamp)
                .setRssi(rssi)
                .setLinkSpeedMbps(linkSpeed)
                .setIsThroughputSufficient(isThroughputSufficient)
                .setCellularDataNetworkType(cellularDataNetworkType)
                .build();

        assertEquals(timestamp, entry.getTimeStampMillis());
        assertEquals(rssi, entry.getRssi());
        assertEquals(linkSpeed, entry.getLinkSpeedMbps());
        assertTrue(entry.isThroughputSufficient());
        assertEquals(cellularDataNetworkType, entry.getCellularDataNetworkType());
    }

    @Test
    public void testAllBuilderMethodsAndBuild() {
        // Sample data for the builder
        long testTimeStampMillis = 1000L;
        int testRssi = -60;
        int testLinkSpeedMbps = 150;
        long testTotalTxSuccess = 10000;
        long testTotalTxRetries = 100;
        long testTotalTxBad = 5;
        long testTotalRxSuccess = 20000;
        long testTotalRadioOnTimeMillis = 5000;
        long testTotalRadioTxTimeMillis = 1500;
        long testTotalRadioRxTimeMillis = 2000;
        long testTotalScanTimeMillis = 100;
        long testTotalNanScanTimeMillis = 10;
        long testTotalBackgroundScanTimeMillis = 20;
        long testTotalRoamScanTimeMillis = 5;
        long testTotalPnoScanTimeMillis = 5;
        long testTotalHotspot2ScanTimeMillis = 5;
        long testTotalCcaBusyFreqTimeMillis = 200;
        long testTotalRadioOnFreqTimeMillis = 2500;
        long testTotalBeaconRx = 500;
        int testProbeStatusSinceLastUpdate = WifiUsabilityStatsEntry.PROBE_STATUS_SUCCESS;
        int testProbeElapsedTimeSinceLastUpdateMillis = 50;
        int testProbeMcsRateSinceLastUpdate = 10;
        int testRxLinkSpeedMbps = 130;
        int testTimeSliceDutyCycleInPercent = 90;
        ContentionTimeStats[] testContentionTimeStats = new ContentionTimeStats[4];
        testContentionTimeStats[0] = new ContentionTimeStats(100, 200, 150, 500);
        RateStats[] testRateStats = new RateStats[1];
        testRateStats[0] = new RateStats(
            WifiUsabilityStatsEntry.WIFI_PREAMBLE_HE,
            WifiUsabilityStatsEntry.WIFI_SPATIAL_STREAMS_TWO,
            WifiUsabilityStatsEntry.WIFI_BANDWIDTH_80_MHZ,
            5,
            1000,
            100,
            200,
            5,
            10);
        RadioStats[] testRadioStats = new RadioStats[1];
        testRadioStats[0] = new RadioStats(1, 1000, 500, 500, 10, 5, 5, 0, 0, 0);
        int testChannelUtilizationRatio = 128;
        boolean testIsThroughputSufficient = true;
        boolean testIsWifiScoringEnabled = true;
        boolean testIsCellularDataAvailable = false;
        int testCellularDataNetworkType = TelephonyManager.NETWORK_TYPE_LTE;
        int testCellularSignalStrengthDbm = -90;
        int testCellularSignalStrengthDb = 20;
        boolean testIsSameRegisteredCell = true;
        SparseArray<WifiUsabilityStatsEntry.LinkStats> testLinkStats = new SparseArray<>();
        testLinkStats.put(0, new WifiUsabilityStatsEntry.LinkStats(0,
                WifiUsabilityStatsEntry.LINK_STATE_NOT_IN_USE, 1, -55, 5180, -60,
                WifiUsabilityStatsEntry.WIFI_BANDWIDTH_80_MHZ, 5180, 0, 150, 130, 5000, 50, 2,
                8000, 250, 90, 100, 1200, null, null, null, null, null));
        int testWifiLinkCount = 1;
        int testMloMode = WifiManager.MLO_MODE_LOW_LATENCY;
        long testTxTransmittedBytes = 12345L;
        long testRxTransmittedBytes = 67890L;
        int testLabelBadEventCount = 3;
        int testWifiFrameworkState = WifiManager.WIFI_STATE_ENABLED;
        int testIsNetworkCapabilitiesDownstreamSufficient = 1;
        int testIsNetworkCapabilitiesUpstreamSufficient = 1;
        int testIsThroughputPredictorDownstreamSufficient = 1;
        int testIsThroughputPredictorUpstreamSufficient = 1;
        boolean testIsBluetoothConnected = true;
        int testUwbAdapterState = 1;
        boolean testIsLowLatencyActivated = true;
        int testMaxSupportedTxLinkSpeed = 1200;
        int testMaxSupportedRxLinkSpeed = 1000;
        int testVoipMode = 1;
        int testThreadDeviceRole = 1;
        int testStatusDataStall = 1;
        int testInternalScore = 85;
        int testInternalScorerType = WifiUsabilityStatsEntry.SCORER_TYPE_ML;

        // Build the object using the builder
        WifiUsabilityStatsEntry statsEntry = new WifiUsabilityStatsEntry.Builder()
                .setTimeStampMillis(testTimeStampMillis)
                .setRssi(testRssi)
                .setLinkSpeedMbps(testLinkSpeedMbps)
                .setTotalTxSuccess(testTotalTxSuccess)
                .setTotalTxRetries(testTotalTxRetries)
                .setTotalTxBad(testTotalTxBad)
                .setTotalRxSuccess(testTotalRxSuccess)
                .setTotalRadioOnTimeMillis(testTotalRadioOnTimeMillis)
                .setTotalRadioTxTimeMillis(testTotalRadioTxTimeMillis)
                .setTotalRadioRxTimeMillis(testTotalRadioRxTimeMillis)
                .setTotalScanTimeMillis(testTotalScanTimeMillis)
                .setTotalNanScanTimeMillis(testTotalNanScanTimeMillis)
                .setTotalBackgroundScanTimeMillis(testTotalBackgroundScanTimeMillis)
                .setTotalRoamScanTimeMillis(testTotalRoamScanTimeMillis)
                .setTotalPnoScanTimeMillis(testTotalPnoScanTimeMillis)
                .setTotalHotspot2ScanTimeMillis(testTotalHotspot2ScanTimeMillis)
                .setTotalCcaBusyFreqTimeMillis(testTotalCcaBusyFreqTimeMillis)
                .setTotalRadioOnFreqTimeMillis(testTotalRadioOnFreqTimeMillis)
                .setTotalBeaconRx(testTotalBeaconRx)
                .setProbeStatusSinceLastUpdate(testProbeStatusSinceLastUpdate)
                .setProbeElapsedTimeSinceLastUpdateMillis(testProbeElapsedTimeSinceLastUpdateMillis)
                .setProbeMcsRateSinceLastUpdate(testProbeMcsRateSinceLastUpdate)
                .setRxLinkSpeedMbps(testRxLinkSpeedMbps)
                .setTimeSliceDutyCycleInPercent(testTimeSliceDutyCycleInPercent)
                .setContentionTimeStats(testContentionTimeStats)
                .setRateStats(testRateStats)
                .setWifiLinkLayerRadioStats(testRadioStats)
                .setChannelUtilizationRatio(testChannelUtilizationRatio)
                .setIsThroughputSufficient(testIsThroughputSufficient)
                .setIsWifiScoringEnabled(testIsWifiScoringEnabled)
                .setIsCellularDataAvailable(testIsCellularDataAvailable)
                .setCellularDataNetworkType(testCellularDataNetworkType)
                .setCellularSignalStrengthDbm(testCellularSignalStrengthDbm)
                .setCellularSignalStrengthDb(testCellularSignalStrengthDb)
                .setIsSameRegisteredCell(testIsSameRegisteredCell)
                .setLinkStats(testLinkStats)
                .setWifiLinkCount(testWifiLinkCount)
                .setMloMode(testMloMode)
                .setTxTransmittedBytes(testTxTransmittedBytes)
                .setRxTransmittedBytes(testRxTransmittedBytes)
                .setLabelBadEventCount(testLabelBadEventCount)
                .setWifiFrameworkState(testWifiFrameworkState)
                .setIsNetworkCapabilitiesDownstreamSufficient(
                    testIsNetworkCapabilitiesDownstreamSufficient)
                .setIsNetworkCapabilitiesUpstreamSufficient(
                    testIsNetworkCapabilitiesUpstreamSufficient)
                .setIsThroughputPredictorDownstreamSufficient(
                    testIsThroughputPredictorDownstreamSufficient)
                .setIsThroughputPredictorUpstreamSufficient(
                    testIsThroughputPredictorUpstreamSufficient)
                .setIsBluetoothConnected(testIsBluetoothConnected)
                .setUwbAdapterState(testUwbAdapterState)
                .setIsLowLatencyActivated(testIsLowLatencyActivated)
                .setMaxSupportedTxLinkSpeed(testMaxSupportedTxLinkSpeed)
                .setMaxSupportedRxLinkSpeed(testMaxSupportedRxLinkSpeed)
                .setVoipMode(testVoipMode)
                .setThreadDeviceRole(testThreadDeviceRole)
                .setStatusDataStall(testStatusDataStall)
                .setInternalScore(testInternalScore)
                .setInternalScorerType(testInternalScorerType)
                .build();

        // Verify that the built object contains the correct values
        assertEquals(testTimeStampMillis, statsEntry.getTimeStampMillis());
        assertEquals(testRssi, statsEntry.getRssi());
        assertEquals(testLinkSpeedMbps, statsEntry.getLinkSpeedMbps());
        assertEquals(testTotalTxSuccess, statsEntry.getTotalTxSuccess());
        assertEquals(testTotalTxRetries, statsEntry.getTotalTxRetries());
        assertEquals(testTotalTxBad, statsEntry.getTotalTxBad());
        assertEquals(testTotalRxSuccess, statsEntry.getTotalRxSuccess());
        assertEquals(testTotalRadioOnTimeMillis, statsEntry.getTotalRadioOnTimeMillis());
        assertEquals(testTotalRadioTxTimeMillis, statsEntry.getTotalRadioTxTimeMillis());
        assertEquals(testTotalRadioRxTimeMillis, statsEntry.getTotalRadioRxTimeMillis());
        assertEquals(testTotalScanTimeMillis, statsEntry.getTotalScanTimeMillis());
        assertEquals(testTotalNanScanTimeMillis, statsEntry.getTotalNanScanTimeMillis());
        assertEquals(testTotalBackgroundScanTimeMillis,
                statsEntry.getTotalBackgroundScanTimeMillis());
        assertEquals(testTotalRoamScanTimeMillis, statsEntry.getTotalRoamScanTimeMillis());
        assertEquals(testTotalPnoScanTimeMillis, statsEntry.getTotalPnoScanTimeMillis());
        assertEquals(testTotalHotspot2ScanTimeMillis, statsEntry.getTotalHotspot2ScanTimeMillis());
        assertEquals(testTotalCcaBusyFreqTimeMillis, statsEntry.getTotalCcaBusyFreqTimeMillis());
        assertEquals(testTotalRadioOnFreqTimeMillis, statsEntry.getTotalRadioOnFreqTimeMillis());
        assertEquals(testTotalBeaconRx, statsEntry.getTotalBeaconRx());
        assertEquals(testProbeStatusSinceLastUpdate, statsEntry.getProbeStatusSinceLastUpdate());
        assertEquals(testProbeElapsedTimeSinceLastUpdateMillis,
                statsEntry.getProbeElapsedTimeSinceLastUpdateMillis());
        assertEquals(testProbeMcsRateSinceLastUpdate, statsEntry.getProbeMcsRateSinceLastUpdate());
        assertEquals(testRxLinkSpeedMbps, statsEntry.getRxLinkSpeedMbps());
        assertEquals(testTimeSliceDutyCycleInPercent, statsEntry.getTimeSliceDutyCycleInPercent());
        assertNotNull(statsEntry.getContentionTimeStats(0));
        assertFalse(statsEntry.getRateStats().isEmpty());
        assertFalse(statsEntry.getWifiLinkLayerRadioStats().isEmpty());
        assertEquals(testChannelUtilizationRatio, statsEntry.getChannelUtilizationRatio());
        assertEquals(testIsThroughputSufficient, statsEntry.isThroughputSufficient());
        assertEquals(testIsWifiScoringEnabled, statsEntry.isWifiScoringEnabled());
        assertEquals(testIsCellularDataAvailable, statsEntry.isCellularDataAvailable());
        assertEquals(testCellularDataNetworkType, statsEntry.getCellularDataNetworkType());
        assertEquals(testCellularSignalStrengthDbm, statsEntry.getCellularSignalStrengthDbm());
        assertEquals(testCellularSignalStrengthDb, statsEntry.getCellularSignalStrengthDb());
        assertEquals(testIsSameRegisteredCell, statsEntry.isSameRegisteredCell());
        assertNotNull(statsEntry.getLinkIds());
        assertEquals(testWifiLinkCount, statsEntry.getWifiLinkCount());
        assertEquals(testMloMode, statsEntry.getMloMode());
        assertEquals(testTxTransmittedBytes, statsEntry.getTxTransmittedBytes());
        assertEquals(testRxTransmittedBytes, statsEntry.getRxTransmittedBytes());
        assertEquals(testLabelBadEventCount, statsEntry.getLabelBadEventCount());
        assertEquals(testWifiFrameworkState, statsEntry.getWifiFrameworkState());
        assertEquals(testIsNetworkCapabilitiesDownstreamSufficient,
                statsEntry.isNetworkCapabilitiesDownstreamSufficient());
        assertEquals(testIsNetworkCapabilitiesUpstreamSufficient,
                statsEntry.isNetworkCapabilitiesUpstreamSufficient());
        assertEquals(testIsThroughputPredictorDownstreamSufficient,
                statsEntry.isThroughputPredictorDownstreamSufficient());
        assertEquals(testIsThroughputPredictorUpstreamSufficient,
                statsEntry.isThroughputPredictorUpstreamSufficient());
        assertEquals(testIsBluetoothConnected, statsEntry.isBluetoothConnected());
        assertEquals(testUwbAdapterState, statsEntry.getUwbAdapterState());
        assertEquals(testIsLowLatencyActivated, statsEntry.getLowLatencyModeState());
        assertEquals(testMaxSupportedTxLinkSpeed, statsEntry.getMaxSupportedTxLinkSpeed());
        assertEquals(testMaxSupportedRxLinkSpeed, statsEntry.getMaxSupportedRxLinkSpeed());
        assertEquals(testVoipMode, statsEntry.getVoipMode());
        assertEquals(testThreadDeviceRole, statsEntry.getThreadDeviceRole());
        assertEquals(testStatusDataStall, statsEntry.getStatusDataStall());
        assertEquals(testInternalScore, statsEntry.getInternalScore());
        assertEquals(testInternalScorerType, statsEntry.getInternalScorerType());
    }

    @Test
    public void setInternalScore() {
        WifiUsabilityStatsEntry usabilityStatsEntry = new WifiUsabilityStatsEntry(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                32, null, null, null, 100, true, true, true, 23, 24, 25, true, null, 26, 27,
                28, 29, 30, 31, 32, 33, 34, 35, true, 36, true, 37, 38, 39, 40, 41,
                TEST_INTERNAL_SCORE, SCORER_TYPE_ML);
        assertEquals(TEST_INTERNAL_SCORE, usabilityStatsEntry.getInternalScore());

        usabilityStatsEntry.setInternalScore(TEST_INTERNAL_SCORE + 1);

        assertEquals(TEST_INTERNAL_SCORE + 1, usabilityStatsEntry.getInternalScore());
    }

    @Test
    public void setInternalScorerType() {
        WifiUsabilityStatsEntry usabilityStatsEntry = new WifiUsabilityStatsEntry(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                32, null, null, null, 100, true, true, true, 23, 24, 25, true, null, 26, 27,
                28, 29, 30, 31, 32, 33, 34, 35, true, 36, true, 37, 38, 39, 40, 41,
                TEST_INTERNAL_SCORE, SCORER_TYPE_ML);
        assertEquals(SCORER_TYPE_ML, usabilityStatsEntry.getInternalScorerType());

        usabilityStatsEntry.setInternalScorerType(SCORER_TYPE_VELOCITY);

        assertEquals(SCORER_TYPE_VELOCITY, usabilityStatsEntry.getInternalScorerType());
    }
}
