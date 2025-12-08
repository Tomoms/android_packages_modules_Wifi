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

import android.annotation.Nullable;
import android.net.wifi.WifiContext;
import android.util.Log;

import com.android.server.wifi.ml_connected_scorer.MlModelParams.RandomForestModel;
import com.android.wifi.resources.R;

import java.io.InputStream;

/** Module that provides the model parameters for the Random Forest. */
public class RandomForestModule {
    private static final String TAG = "RandomForestModule";

    @Nullable RandomForestModel getRandomForestParams(WifiContext wifiContext) {
        return loadModel(wifiContext, R.raw.ml_prebuilt_rf_model);
    }

    private static @Nullable RandomForestModel parsePrebuiltModel(WifiContext wifiContext,
            int resId) {
        try (InputStream prebuiltIs = wifiContext.getResources().openRawResource(resId)) {
            return RandomForestModel.parseFrom(prebuiltIs);
        } catch (Exception e) {
            Log.e(TAG, "Could not load prebuilt model resource");
        }
        return null;
    }

    private static @Nullable RandomForestModel loadModel(WifiContext wifiContext, int resId) {
        RandomForestModel prebuiltModel = parsePrebuiltModel(wifiContext, resId);
        if (prebuiltModel != null) {
            FeatureUtils.setNormMeans(prebuiltModel.getFeatureNormMeansList());
            FeatureUtils.setNormStds(prebuiltModel.getFeatureNormStdsList());
        }
        return prebuiltModel;
    }
}
