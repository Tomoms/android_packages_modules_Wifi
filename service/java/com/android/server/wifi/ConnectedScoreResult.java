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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ConnectedScoreResult {
    /** The calculated score by the scorer */
    public abstract int score();
    /** The adjusted score by the scorer */
    public abstract int adjustedScore();
    /** The prediction of Wi-Fi usability by the scorer */
    public abstract boolean isWifiUsable();
    /** The request to check NUD from the scorer */
    public abstract boolean shouldCheckNud();
    /** The request to block BSSID from the scorer */
    public abstract boolean shouldBlockBssid();
    /** The request to trigger Wi-Fi scan from the scorer */
    public abstract boolean shouldTriggerScan();

    /** Builder of {@link ConnectedScoreResult}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /**
         * Builds a {@link ConnectedScoreResult} object.
         */
        public abstract ConnectedScoreResult build();

        /**
         * Sets the score.
         */
        public abstract Builder setScore(int score);

        /**
         * Sets the adjustedScore.
         */
        public abstract Builder setAdjustedScore(int adjustedScore);

        /**
         * Sets whether the Wi-Fi is usable or not.
         */
        public abstract Builder setIsWifiUsable(boolean isWifiUsable);

        /**
         * Sets whether to check NUD.
         */
        public abstract Builder setShouldCheckNud(boolean shouldCheckNud);

        /**
         * Sets whether to block BSSID
         */
        public abstract Builder setShouldBlockBssid(boolean shouldBlockBssid);

        /**
         * Sets whether trigger Wi-Fi scan.
         */
        public abstract Builder setShouldTriggerScan(boolean shouldTriggerScan);
    }

    /**
     * Generates a {@link ConnectedScoreResult#Builder}.
     */
    public static Builder builder() {
        return new AutoValue_ConnectedScoreResult.Builder()
            .setShouldCheckNud(false)
            .setShouldBlockBssid(false)
            .setShouldTriggerScan(false);
    }
}
