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

import static com.android.server.wifi.ml_connected_scorer.Flags.ENABLE_DEFAULT_MEAN_CCA_BUSY_RATIO;
import static com.android.server.wifi.ml_connected_scorer.Flags.ENABLE_MODEL_WITH_RX_LINK_SPEED_BEACON_RX;
import static com.android.server.wifi.ml_connected_scorer.Flags.ENABLE_SUBTRACT_RX_TIME_FROM_CCA_BUSY_TIME;
import static com.android.server.wifi.ml_connected_scorer.Flags.RX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD;
import static com.android.server.wifi.ml_connected_scorer.Flags.SUCCESS_PKT_COUNT_VERY_HIGH_THRESHOLD;
import static com.android.server.wifi.ml_connected_scorer.Flags.TX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD;

import android.net.wifi.WifiUsabilityStatsEntry;

/** Wrapper around android.net.wifi.WifiUsabilityStatsEntry. */
final class WifiUsabilityStatsEntryWrapper {

    // Diffed cumulative features.
    public double totalRadioOnTimeDiff = 0;
    public double totalTxSuccessDiff = 0;
    public double totalRxSuccessDiff = 0;
    public double totalTxRetriesDiff = 0;
    public double totalRadioRxTimeMsDiff = 0;
    public double totalRadioTxTimeMsDiff = 0;
    public double totalScanTimeMsDiff = 0;
    public double totalRoamScanTimeMsDiff = 0;
    public double totalRadioOnFreqTimeMsDiff = 0;
    public double totalCcaBusyFreqTimeMsDiff = 0;
    public double totalTxBad = 0;
    public double totalBeaconRx = 0;

    // Non-diffed features.
    public double linkSpeedMbps = 0;
    public double rxLinkSpeedMbps = 0;
    public double rssi = 0;

    // Derived features.
    public double totalRadioOnTimeDiffPerAttempt = 0;
    public double totalTxSuccessDiffPerTxAttempt = 0;
    public double totalTxRetriesDiffPerTxAttempt = 0;
    public double totalRadioTxTimeMsDiffPerTxAttempt = 0;
    public double totalRadioRxTimeMsDiffPerRxSuccess = 0;
    public double totalCcaBusyFreqTimeRatio = 0;
    public double totalTxAttempts = 0;
    public double estTxTput = 0;
    public double estRxTput = 0;

    private final WifiUsabilityStatsEntry mStats;

    WifiUsabilityStatsEntryWrapper(WifiUsabilityStatsEntry stats) {
        this.mStats = stats;
    }

    public WifiUsabilityStatsEntry getStats() {
        return mStats;
    }

    public void setDiffs(WifiUsabilityStatsEntry previous) {
        this.totalRadioOnTimeDiff =
                mStats.getTotalRadioOnTimeMillis() - previous.getTotalRadioOnTimeMillis();
        this.totalTxSuccessDiff = mStats.getTotalTxSuccess() - previous.getTotalTxSuccess();
        this.totalRxSuccessDiff = mStats.getTotalRxSuccess() - previous.getTotalRxSuccess();
        this.totalTxRetriesDiff = mStats.getTotalTxRetries() - previous.getTotalTxRetries();
        this.totalRadioRxTimeMsDiff =
                mStats.getTotalRadioRxTimeMillis() - previous.getTotalRadioRxTimeMillis();
        this.totalRadioTxTimeMsDiff =
                mStats.getTotalRadioTxTimeMillis() - previous.getTotalRadioTxTimeMillis();
        this.totalScanTimeMsDiff = mStats.getTotalScanTimeMillis()
                - previous.getTotalScanTimeMillis();
        this.totalRoamScanTimeMsDiff =
                mStats.getTotalRoamScanTimeMillis() - previous.getTotalRoamScanTimeMillis();
        this.totalRadioOnFreqTimeMsDiff =
                mStats.getTotalRadioOnFreqTimeMillis() - previous.getTotalRadioOnFreqTimeMillis();
        this.totalCcaBusyFreqTimeMsDiff =
                mStats.getTotalCcaBusyFreqTimeMillis() - previous.getTotalCcaBusyFreqTimeMillis();
        this.totalTxBad = mStats.getTotalTxBad() - previous.getTotalTxBad();
        this.totalBeaconRx = mStats.getTotalBeaconRx() - previous.getTotalBeaconRx();

        boolean isTrafficVeryHigh =
                this.totalTxSuccessDiff >= SUCCESS_PKT_COUNT_VERY_HIGH_THRESHOLD
                        || this.totalRxSuccessDiff >= SUCCESS_PKT_COUNT_VERY_HIGH_THRESHOLD;

        // Sanitize all features.
        if (this.totalRadioOnTimeDiff < 0) {
            this.totalRadioOnTimeDiff = Double.NaN;
        }
        if (this.totalTxSuccessDiff < 0) {
            this.totalTxSuccessDiff = Double.NaN;
        }
        if (this.totalRxSuccessDiff < 0) {
            this.totalRxSuccessDiff = Double.NaN;
        }
        if (this.totalTxRetriesDiff < 0) {
            this.totalTxRetriesDiff = Double.NaN;
        }
        if (this.totalRadioRxTimeMsDiff < 0) {
            this.totalRadioRxTimeMsDiff = Double.NaN;
        }
        if (this.totalRadioTxTimeMsDiff < 0) {
            this.totalRadioTxTimeMsDiff = Double.NaN;
        }
        if (this.totalScanTimeMsDiff < 0) {
            this.totalScanTimeMsDiff = Double.NaN;
        }
        if (this.totalRoamScanTimeMsDiff < 0) {
            this.totalRoamScanTimeMsDiff = Double.NaN;
        }
        if (this.totalRadioOnFreqTimeMsDiff < 0) {
            this.totalRadioOnFreqTimeMsDiff = Double.NaN;
        }
        if (this.totalCcaBusyFreqTimeMsDiff < 0) {
            this.totalCcaBusyFreqTimeMsDiff = Double.NaN;
        }
        if (this.totalTxBad < 0) {
            this.totalTxBad = Double.NaN;
        }
        if (this.totalBeaconRx < 0) {
            this.totalBeaconRx = Double.NaN;
        }

        // Derived ratios. Keep in sync with feature processing in
        // google3/learning/deepmind/applications/zebedee/connectivity/utils.py.
        // Normalize Tx
        if (Double.isNaN(totalTxSuccessDiff) || Double.isNaN(totalTxRetriesDiff)) {
            this.totalTxSuccessDiffPerTxAttempt = Double.NaN;
            this.totalTxRetriesDiffPerTxAttempt = Double.NaN;
            this.totalTxAttempts = Double.NaN;
        } else {
            this.totalTxAttempts = totalTxSuccessDiff + totalTxRetriesDiff;
            if (totalTxAttempts == 0
                    || (totalTxAttempts <= TX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD
                            && totalTxSuccessDiff == 0)
                    || isTrafficVeryHigh) {
                this.totalTxSuccessDiffPerTxAttempt = Double.NaN;
                this.totalTxRetriesDiffPerTxAttempt = Double.NaN;
            } else {
                this.totalTxSuccessDiffPerTxAttempt = totalTxSuccessDiff / totalTxAttempts;
                this.totalTxRetriesDiffPerTxAttempt = totalTxRetriesDiff / totalTxAttempts;
                if (!Double.isNaN(totalRadioTxTimeMsDiffPerTxAttempt)) {
                    this.totalRadioTxTimeMsDiffPerTxAttempt =
                            totalRadioTxTimeMsDiff / totalTxAttempts;
                }
            }
        }

        // Tx/Rx link speed and RSSI
        this.linkSpeedMbps =
            (mStats.getLinkSpeedMbps() < 0
                    || totalTxAttempts <= TX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD
                    || isTrafficVeryHigh)
                ? Double.NaN
                : mStats.getLinkSpeedMbps();
        this.rxLinkSpeedMbps =
            (mStats.getRxLinkSpeedMbps() < 0
                    || totalRxSuccessDiff <= RX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD
                    || isTrafficVeryHigh)
                ? Double.NaN
                : mStats.getRxLinkSpeedMbps();
        this.rssi = mStats.getRssi();

        // Normalize Rx
        if (Double.isNaN(totalRxSuccessDiff) || Double.isNaN(totalRadioRxTimeMsDiff)) {
            this.totalRadioRxTimeMsDiffPerRxSuccess = Double.NaN;
        } else {
            if (totalRxSuccessDiff == 0 || totalRadioRxTimeMsDiff == 0) {
                this.totalRadioRxTimeMsDiffPerRxSuccess = Double.NaN;
            } else {
                this.totalRadioRxTimeMsDiffPerRxSuccess =
                        totalRadioRxTimeMsDiff / totalRxSuccessDiff;
            }
        }

        // Normalize radio on time
        if (Double.isNaN(totalTxSuccessDiff)
                || Double.isNaN(totalRxSuccessDiff)
                || Double.isNaN(totalTxRetriesDiff)) {
            this.totalRadioOnTimeDiffPerAttempt = Double.NaN;
        } else {
            double attempts = totalTxSuccessDiff + totalRxSuccessDiff + totalTxRetriesDiff;
            double radioTime = totalRadioOnTimeDiff - totalScanTimeMsDiff;
            if (attempts == 0) {
                this.totalRadioOnTimeDiffPerAttempt = Double.NaN;
            } else {
                if (Double.isNaN(totalRadioOnTimeDiff) || Double.isNaN(totalScanTimeMsDiff)) {
                    this.totalRadioOnTimeDiffPerAttempt = Double.NaN;
                } else {
                    this.totalRadioOnTimeDiffPerAttempt = radioTime / attempts;
                }
            }
        }

        // Normalize CCA
        if (ENABLE_DEFAULT_MEAN_CCA_BUSY_RATIO) {
            this.totalCcaBusyFreqTimeRatio = FeatureUtils.getMeanCcaBusyRatio();
        } else {
            if (Double.isNaN(totalCcaBusyFreqTimeMsDiff)
                    || Double.isNaN(totalRadioOnFreqTimeMsDiff)
                    || Double.isNaN(totalRadioRxTimeMsDiff)
                    || (totalRadioOnFreqTimeMsDiff <= Constants.MIN_DURATION_UPDATING_CCA_MS)) {
                this.totalCcaBusyFreqTimeRatio = Double.NaN;
            } else {
                double ccaBusyRatio = ENABLE_SUBTRACT_RX_TIME_FROM_CCA_BUSY_TIME
                        ? (totalCcaBusyFreqTimeMsDiff - totalRadioRxTimeMsDiff)
                                / totalRadioOnFreqTimeMsDiff
                        : totalCcaBusyFreqTimeMsDiff / totalRadioOnFreqTimeMsDiff;
                this.totalCcaBusyFreqTimeRatio =
                        (ccaBusyRatio >= 0 && ccaBusyRatio <= 1) ? ccaBusyRatio : Double.NaN;
            }
        }

        // Derive estTxTput
        if (!Double.isNaN(totalCcaBusyFreqTimeRatio)
                && !Double.isNaN(totalTxRetriesDiffPerTxAttempt)
                && !Double.isNaN(linkSpeedMbps)) {
            this.estTxTput =
                    linkSpeedMbps * (1 - totalTxRetriesDiffPerTxAttempt)
                            * (1 - totalCcaBusyFreqTimeRatio);
        } else {
            this.estTxTput = Double.NaN;
        }

        // Derive estRxTput
        if (!Double.isNaN(totalCcaBusyFreqTimeRatio) && !Double.isNaN(rxLinkSpeedMbps)) {
            this.estRxTput = rxLinkSpeedMbps * (1 - totalCcaBusyFreqTimeRatio);
        } else {
            this.estRxTput = Double.NaN;
        }
    }

    public void setDefaultValues() {
        double[] normMeans = FeatureUtils.getNormMeans();

        // Keep order of features in sync with training!
        // Feature names should be in alphabetical order.
        // LINT.IfChange
        if (!ENABLE_MODEL_WITH_RX_LINK_SPEED_BEACON_RX) {
            this.estTxTput = normMeans[0];
            this.linkSpeedMbps = normMeans[1];
            this.rssi = normMeans[2];
            this.totalCcaBusyFreqTimeRatio = normMeans[3];
            this.totalRadioOnFreqTimeMsDiff = normMeans[4];
            this.totalRadioOnTimeDiffPerAttempt = normMeans[5];
            this.totalRadioRxTimeMsDiffPerRxSuccess = normMeans[6];
            this.totalRadioTxTimeMsDiffPerTxAttempt = normMeans[7];
            this.totalRoamScanTimeMsDiff = normMeans[8];
            this.totalRxSuccessDiff = normMeans[9];
            this.totalScanTimeMsDiff = normMeans[10];
            this.totalTxAttempts = normMeans[11];
            this.totalTxBad = normMeans[12];
            this.totalTxRetriesDiffPerTxAttempt = normMeans[13];
            this.totalTxSuccessDiffPerTxAttempt = normMeans[14];
        } else {
            this.estRxTput = normMeans[0];
            this.estTxTput = normMeans[1];
            this.linkSpeedMbps = normMeans[2];
            this.rssi = normMeans[3];
            this.rxLinkSpeedMbps = normMeans[4];
            this.totalBeaconRx = normMeans[5];
            this.totalCcaBusyFreqTimeRatio = normMeans[6];
            this.totalRadioOnFreqTimeMsDiff = normMeans[7];
            this.totalRadioOnTimeDiffPerAttempt = normMeans[8];
            this.totalRadioRxTimeMsDiffPerRxSuccess = normMeans[9];
            this.totalRadioTxTimeMsDiffPerTxAttempt = normMeans[10];
            this.totalRoamScanTimeMsDiff = normMeans[11];
            this.totalRxSuccessDiff = normMeans[12];
            this.totalScanTimeMsDiff = normMeans[13];
            this.totalTxAttempts = normMeans[14];
            this.totalTxBad = normMeans[15];
            this.totalTxRetriesDiffPerTxAttempt = normMeans[16];
            this.totalTxSuccessDiffPerTxAttempt = normMeans[17];
        }
        // LINT.ThenChange(FeatureUtils.java, ml_prebuilt_rf_model.pb.txt)
    }

    public double[] getFeaturesAsArray() {
        double[] features = new double[FeatureUtils.getNumFeaturesPerArray()];

        // Keep order of features in sync with training!
        // Feature names should be in alphabetical order.
        // LINT.IfChange
        if (!ENABLE_MODEL_WITH_RX_LINK_SPEED_BEACON_RX) {
            features[0] = estTxTput;
            features[1] = linkSpeedMbps;
            features[2] = rssi;
            features[3] = totalCcaBusyFreqTimeRatio;
            features[4] = totalRadioOnFreqTimeMsDiff;
            features[5] = totalRadioOnTimeDiffPerAttempt;
            features[6] = totalRadioRxTimeMsDiffPerRxSuccess;
            features[7] = totalRadioTxTimeMsDiffPerTxAttempt;
            features[8] = totalRoamScanTimeMsDiff;
            features[9] = totalRxSuccessDiff;
            features[10] = totalScanTimeMsDiff;
            features[11] = totalTxAttempts;
            features[12] = totalTxBad;
            features[13] = totalTxRetriesDiffPerTxAttempt;
            features[14] = totalTxSuccessDiffPerTxAttempt;
        } else {
            features[0] = estRxTput;
            features[1] = estTxTput;
            features[2] = linkSpeedMbps;
            features[3] = rssi;
            features[4] = rxLinkSpeedMbps;
            features[5] = totalBeaconRx;
            features[6] = totalCcaBusyFreqTimeRatio;
            features[7] = totalRadioOnFreqTimeMsDiff;
            features[8] = totalRadioOnTimeDiffPerAttempt;
            features[9] = totalRadioRxTimeMsDiffPerRxSuccess;
            features[10] = totalRadioTxTimeMsDiffPerTxAttempt;
            features[11] = totalRoamScanTimeMsDiff;
            features[12] = totalRxSuccessDiff;
            features[13] = totalScanTimeMsDiff;
            features[14] = totalTxAttempts;
            features[15] = totalTxBad;
            features[16] = totalTxRetriesDiffPerTxAttempt;
            features[17] = totalTxSuccessDiffPerTxAttempt;
        }
        // LINT.ThenChange(FeatureUtils.java, ml_prebuilt_rf_model.pb.txt)

        return features;
    }

}
