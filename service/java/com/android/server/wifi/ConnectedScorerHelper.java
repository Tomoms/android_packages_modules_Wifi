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

import android.net.ip.IpClientManager;
import android.net.wifi.WifiInfo;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Helper class for all the scorer to score WiFi connection.
 */
public class ConnectedScorerHelper {
    @VisibleForTesting
    static final long MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS = 9000;
    @VisibleForTesting
    static final long NUD_THROTTLE_MS = 5000;
    @VisibleForTesting
    static final long TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE = 30000;

    private final ScoringParams mScoringParams;
    private final WifiGlobals mWifiGlobals;
    private final WifiConnectivityManager mWifiConnectivityManager;

    ConnectedScorerHelper(ScoringParams scoringParams, WifiGlobals wifiGlobals,
            WifiConnectivityManager wifiConnectivityManager) {
        mScoringParams = scoringParams;
        mWifiGlobals = wifiGlobals;
        mWifiConnectivityManager = wifiConnectivityManager;
    }

   /**
    * Adjust the score from VelocityBasedConnectedScorer.
    *
    * @param wifiInfo used to adjust the score
    * @param filteredRssi kalman-filtered rssi used to adjust the score
    * @param lastDownwardBreachTimeMs last downward breach time in milliseconds
    * @param currentTimeMs current time in milliseconds
    * @param transitionScore the transition score of the scorer
    * @param score the score which need to be adjusted.
    * @return adjusted score
    */
    public int adjustScore(WifiInfo wifiInfo, double filteredRssi,
            long lastDownwardBreachTimeMs, long currentTimeMs, int transitionScore, int score) {
        int adjustedScore = score;
        if (wifiInfo.getScore() > transitionScore
                && adjustedScore <= transitionScore
                && wifiInfo.getSuccessfulTxPacketsPerSecond()
                        >= mScoringParams.getYippeeSkippyPacketsPerSecond()
                && wifiInfo.getSuccessfulRxPacketsPerSecond()
                        >= mScoringParams.getYippeeSkippyPacketsPerSecond()) {
            adjustedScore = transitionScore + 1;
        }

        if (wifiInfo.getScore() > transitionScore && adjustedScore <= transitionScore) {
            // We don't want to trigger a downward breach unless the rssi is
            // below the entry threshold.  There is noise in the measured rssi, and
            // the kalman-filtered rssi is affected by the trend, so check them both.
            // TODO(b/74613347) skip this if there are other indications to support the low score
            int entry = mScoringParams.getEntryRssi(wifiInfo.getFrequency());
            if (filteredRssi >= entry || wifiInfo.getRssi() >= entry) {
                // Stay a notch above the transition score to reduce ambiguity.
                adjustedScore = transitionScore + 1;
            }
        }

        if (wifiInfo.getScore() < transitionScore && adjustedScore >= transitionScore) {
            // Staying at below transition score for a certain period of time
            // to prevent going back to wifi network again in a short time.
            long elapsedTimeMs = currentTimeMs - lastDownwardBreachTimeMs;
            if (elapsedTimeMs < MIN_TIME_TO_KEEP_BELOW_TRANSITION_SCORE_MS) {
                adjustedScore = wifiInfo.getScore();
            }
        }
        return adjustedScore;
    }

   /**
    * Trigger Wi-Fi scan if needed.
    *
    * @param lastScanTimeMs last triggered scan time in milliseconds
    * @param currentTimeMs current time in milliseconds
    * @param shouldTriggerScan whether the scorer request to trigger Wi-Fi scan
    * @return true if Wi-Fi scan is triggered.
    */
    public boolean triggerScanIfNeeded(long lastScanTimeMs, long currentTimeMs,
            boolean shouldTriggerScan) {
        if (shouldTriggerScan && enoughTimePassedSinceLastScan(lastScanTimeMs, currentTimeMs)) {
            mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
            return true;
        }
        return false;
    }

    private boolean enoughTimePassedSinceLastScan(long lastScanTimeMs,
            long currentTimeMs) {
        return lastScanTimeMs == INVALID_TIMESTAMP_MS
            || currentTimeMs - lastScanTimeMs
                    > (mWifiGlobals.getWifiLowConnectedScoreScanPeriodSeconds() * 1000L);
    }

    /**
     * Return whether to check NUD. Used by {@link VelocityBasedConnectedScorer}.
     *
     * @param lastNudRequestTimeMs last time in milliseconds to request NUD check
     * @param currentTimeMs current time in milliseconds
     * @param transitionScore the transition score of the scorer
     * @param lastNudRequestScore last score to request NUD check
     * @param adjustedScore the adjusted scorer reported by the scorer
     * @return true if NUD should be checked.
     */
    public boolean shouldCheckNud(long lastNudRequestTimeMs, long currentTimeMs,
            int transitionScore, int lastNudRequestScore, int adjustedScore) {
        int nud = mScoringParams.getNudKnob();
        if (nud == 0) {
            return false;
        }

        if (lastNudRequestTimeMs == INVALID_TIMESTAMP_MS) {
            lastNudRequestScore = transitionScore;
        }

        // nextNudBreach is the bar the score needs to cross before we ask for NUD
        double nextNudBreach;
        double quotient = (double) (currentTimeMs - lastNudRequestTimeMs)
                / TIME_INTERVAL_TO_CALCULATE_NUD_CHECK_SCORE;
        // nud is between 1 and 10 at this point
        double deltaLevel = 11 - nud;
        // If we were below threshold the last time we checked, then compute a new bar
        // that starts down from there and decays exponentially back up to the steady-state
        // bar. If 5 time constants have passed, we are 99% of the way there, so skip the math.
        if (lastNudRequestScore < ConnectedScorer.WIFI_TRANSITION_SCORE && quotient < 5.0) {
            double a = Math.exp(-quotient);
            nextNudBreach = a * (lastNudRequestScore - deltaLevel) + (1.0 - a) * transitionScore;
        } else {
            nextNudBreach = transitionScore;
        }
        if (adjustedScore >= nextNudBreach) {
            return false;
        }
        return true;
    }

    /**
     * Check NUD if needed. Used by {@link WifiScoreReport}.
     *
     * @param ipClientManager used to check NUD
     * @param lastNudCheckTimeMs last NUD check time in milliseconds
     * @param currentTimeMs current time in milliseconds
     * @return true if NUD is checked.
     */
    public boolean checkNudIfNeeded(IpClientManager ipClientManager, long lastNudCheckTimeMs,
            long currentTimeMs) {
        // Don't ever ask back-to-back - allow at least 5 seconds for the last one to finish.
        if (currentTimeMs - lastNudCheckTimeMs < NUD_THROTTLE_MS) {
            return false;
        }

        if (ipClientManager != null) {
            ipClientManager.confirmConfiguration();
            return true;
        }
        return false;
    }
}
