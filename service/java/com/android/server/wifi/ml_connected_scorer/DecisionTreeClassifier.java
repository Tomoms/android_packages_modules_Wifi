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

/** Decision Tree Classifier implementation. */
class DecisionTreeClassifier {

    private static final int ROOT_NODE = 0;
    private static final int NONEXISTENT_NODE = -1;

    private final DecisionTreeModel mParams;

    DecisionTreeClassifier(DecisionTreeModel modelParams) {
        mParams = modelParams;
    }

    public double predictProbability(double[] features) {
        DecisionTreeModel.Node curNode = mParams.getNodes(ROOT_NODE);
        while (true) {
            if (curNode.getLeftChild() == NONEXISTENT_NODE) {
                return curNode.getPositiveProbability();
            }
            if (features[curNode.getSplitFeature()] <= curNode.getSplitThreshold()) {
                curNode = mParams.getNodes(curNode.getLeftChild());
            } else {
                curNode = mParams.getNodes(curNode.getRightChild());
            }
        }
    }
}
