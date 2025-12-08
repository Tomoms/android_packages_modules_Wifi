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

import com.android.server.wifi.ml_connected_scorer.MlModelParams.DecisionTreeModel;
import com.android.server.wifi.ml_connected_scorer.MlModelParams.RandomForestModel;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Random Forest Classifier implementation. */
final class RandomForestClassifier implements WifiUsabilityClassifier {

    private List<DecisionTreeClassifier> mTrees;
    private final int mModelId;

    RandomForestClassifier(RandomForestModel modelParams, int modelId) {
        this.mModelId = modelId;
        this.mTrees = new ArrayList<>();
        for (DecisionTreeModel treeParams : modelParams.getDecisionTreesList()) {
            this.mTrees.add(new DecisionTreeClassifier(treeParams));
        }
    }

    /**
     * Uses a random forest to obtain the probability of a wifi usability event occurring soon, and
     * reports the inverse probability of that as a score.
     */
    @Override
    public double calculateScore(Deque<WifiUsabilityStatsEntryWrapper> data) {
        double[] features = FeatureUtils.extractFeatures(data);
        return 100 * (1.0f - predictProbability(features));
    }

    private double predictProbability(double[] features) {
        double sum = 0;
        for (DecisionTreeClassifier tree : mTrees) {
            sum += tree.predictProbability(features);
        }
        return sum / mTrees.size();
    }

    @Override
    public int getModelId() {
        return mModelId;
    }
}
