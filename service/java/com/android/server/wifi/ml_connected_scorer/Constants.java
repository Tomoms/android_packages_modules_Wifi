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

/** Defines shared constants used in the scorer. */
public final class Constants {
    public static final String TAG = "MlConnectedScorer";
    // Model IDs
    public static final int LOGISTIC_REGRESSION_MODEL_ID = 1;
    public static final int RANDOM_FOREST_MODEL_ID = 7;

    // Scorer constants/parameters
    /** The WiFi link layer stats polling interval. */
    public static final long POLLING_INTERVAL_MS = 3000;

    /** The delay between the polling is triggered and the polling stats is received. */
    public static final long POLLING_DELAY_MS = 3000;

    /** Invalid interval because polling interval should be positive. */
    public static final long INVALID_INTERVAL_MS = -1;

    /** Maximum number of entries that constitutes one example. */
    public static final int MAX_BUFFER_SIZE = 5;

    /** Minimum number of entries that constitutes one example. */
    public static final int MIN_BUFFER_SIZE = 1;

    /** Score to signal that the model failed to produce a prediction. */
    public static final double UNCLASSIFIED_SCORE = -1.0;

    /** Prediction horizon. */
    public static final int PREDICTION_HORIZON = 3;

    /** Maximum score output. */
    public static final double MAX_SCORE = 100.0;

    /** Minimum duration for updating CCA level in each RSSI sample. */
    public static final int MIN_DURATION_UPDATING_CCA_MS = 50;

    /** Invalid system time */
    public static final long INVALID_SYSTEM_TIME_MILLIS = -1;

    private Constants() {}
}
