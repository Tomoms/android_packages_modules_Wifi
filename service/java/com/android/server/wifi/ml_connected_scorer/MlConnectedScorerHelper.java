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
import static com.android.server.wifi.ml_connected_scorer.Flags.LINK_SPEED_LOW_MBPS;
import static com.android.server.wifi.ml_connected_scorer.Flags.LINK_SPEED_VERY_LOW_MBPS;
import static com.android.server.wifi.ml_connected_scorer.Flags.RSSI_THRESHOLD_NO_HYSTERESIS_NETWORK_STATUS_CHANGE_DBM;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCORE_BREACHING_RSSI_THRESHOLD;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCORE_LOW_RSSI_THR_DBM;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCORE_LOW_TX_BAD_THR;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCORE_LOW_TX_SUCCESS_TO_BAD_RATIO_THR;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.util.Log;

/** Class to store a buffer of wifi stats and call classifier. */
public class MlConnectedScorerHelper {
    private boolean mVerboseLoggingEnabled = false;

    /** Return true if the BSSID and frequence are the same. */
    public boolean isSameBssidAndFreq(String previousBssid, int previousFrequency,
            WifiInfo wifiInfo) {
        if (previousBssid == null || previousFrequency == -1) {
            return true;
        }
        String currentBssid = wifiInfo.getBSSID();
        int currentFrequency = wifiInfo.getFrequency();
        if (previousBssid.equals(currentBssid) && (previousFrequency == currentFrequency)) {
            return true;
        }
        if (mVerboseLoggingEnabled) {
            Log.d(Constants.TAG, "previousBssid=" + previousBssid + " currentBssid=" + currentBssid
                    + " previousFrequency=" + previousFrequency
                    + " currentFrequency=" + currentFrequency);
        }
        return false;
    }

    /** Return true if the time stamp gap between the two stats are too large. */
    public boolean isTimeStampGapTooLarge(WifiUsabilityStatsEntry previousStats,
            WifiUsabilityStatsEntry stats) {
        long interval = stats.getTimeStampMillis()
                - previousStats.getTimeStampMillis();
        return interval > (POLLING_INTERVAL_MS + POLLING_DELAY_MS);
    }

    /** Return true is the link quality is bad. */
    public boolean isLinkQualityBad(double totalTxBadDiff, double totalTxSuccessDiff, int rssi) {
        if (rssi <= SCORE_LOW_RSSI_THR_DBM) {
            return true;
        }
        return totalTxBadDiff * SCORE_LOW_TX_SUCCESS_TO_BAD_RATIO_THR >= totalTxSuccessDiff
                && rssi <= SCORE_BREACHING_RSSI_THRESHOLD
                && totalTxBadDiff >= SCORE_LOW_TX_BAD_THR;
    }

    /** Return true if the RSSI is very low and link speed is low. */
    public boolean isRssiVeryLowAndLinkSpeedLow(WifiUsabilityStatsEntry stats) {
        boolean isTxLinkSpeedLow =
                stats.getLinkSpeedMbps() <= LINK_SPEED_LOW_MBPS && stats.getLinkSpeedMbps() > 0;
        boolean isRxLinkSpeedLow =
                stats.getRxLinkSpeedMbps() <= LINK_SPEED_LOW_MBPS && stats.getRxLinkSpeedMbps() > 0;
        boolean isRssiVeryLow = stats.getRssi()
                <= RSSI_THRESHOLD_NO_HYSTERESIS_NETWORK_STATUS_CHANGE_DBM;
        return isRssiVeryLow && (isTxLinkSpeedLow || isRxLinkSpeedLow);
    }

    /** Return true if the RSSI is low and link speed is very low. */
    public boolean isRssiLowAndLinkSpeedVeryLow(WifiUsabilityStatsEntry stats) {
        boolean isTxLinkSpeedVeryLow = stats.getLinkSpeedMbps() <= LINK_SPEED_VERY_LOW_MBPS
                && stats.getLinkSpeedMbps() > 0;
        boolean isRxLinkSpeedVeryLow = stats.getRxLinkSpeedMbps() <= LINK_SPEED_VERY_LOW_MBPS
                && stats.getRxLinkSpeedMbps() > 0;
        boolean isRssiLow = stats.getRssi() < SCORE_BREACHING_RSSI_THRESHOLD;
        return isRssiLow && (isTxLinkSpeedVeryLow || isRxLinkSpeedVeryLow);
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean enable) {
        mVerboseLoggingEnabled = enable;
    }
}
