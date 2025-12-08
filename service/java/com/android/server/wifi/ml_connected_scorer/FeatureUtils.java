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

import java.util.Deque;
import java.util.List;

/** Utils class with helper methods to extract features from the signal. */
final class FeatureUtils {

    public static final int NUM_FEATURES_PER_ENTRY = 15;
    private static final double[] FEATURE_NORM_MEANS = new double[3 * NUM_FEATURES_PER_ENTRY];
    private static final double[] FEATURE_NORM_STDS = new double[3 * NUM_FEATURES_PER_ENTRY];

    /** Transforms a sequence of data points into the feature array. */
    public static double[] extractFeatures(Deque<WifiUsabilityStatsEntryWrapper> data) {
        int numEntries = data.size();
        int numFeaturesPerArray = getNumFeaturesPerArray();

        double[][] dataArray = new double[numEntries][numFeaturesPerArray];

        int entryIdx = 0;
        for (WifiUsabilityStatsEntryWrapper entry : data) {
            dataArray[entryIdx] = entry.getFeaturesAsArray();
            entryIdx++;
        }

        // Calculate means and standard deviations per feature. Fill NaNs with the computed mean.
        double[] means = computeMeans(dataArray);
        replaceNaNs(dataArray, means);
        double[] stds = computeStds(dataArray, means);

        // Create single array containing all features.
        double[] features = new double[3 * numFeaturesPerArray];
        // First group of features are the stats from the last entry, followed by all means and all
        // standard deviations.
        for (int i = 0; i < numFeaturesPerArray; i++) {
            features[i] = dataArray[numEntries - 1][i];
            features[i + numFeaturesPerArray] = means[i];
            features[i + 2 * numFeaturesPerArray] = stds[i];
        }
        // Normalize all features.
        return normalize(features);
    }

    /** Normalizes each feature by scaling according to normalization mean and std. */
    private static double[] normalize(double[] data) {
        double[] normMeans = getNormMeans();
        double[] normStds = getNormStds();

        double[] normalizedData = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            normalizedData[i] = (data[i] - normMeans[i]) / normStds[i];
        }
        return normalizedData;
    }

    /**
     * Computes the mean for each feature. Ignores NaNs (mean set to the global mean if all entries
     * are NaN).
     */
    private static double[] computeMeans(double[][] data) {
        double[] normMeans = getNormMeans();
        int numFeaturesPerArray = getNumFeaturesPerArray();

        double[] means = new double[numFeaturesPerArray];
        for (int featureIdx = 0; featureIdx < numFeaturesPerArray; featureIdx++) {
            int count = 0;
            for (double[] entries : data) {
                if (!Double.isNaN(entries[featureIdx])) {
                    means[featureIdx] += entries[featureIdx];
                    count++;
                }
            }
            if (count > 0) {
                means[featureIdx] /= count;
            } else {
                means[featureIdx] = normMeans[featureIdx];
            }
        }
        return means;
    }

    /** Computes the standard deviation for each feature. */
    private static double[] computeStds(double[][] data, double[] means) {
        int numFeaturesPerArray = getNumFeaturesPerArray();
        double[] stds = new double[numFeaturesPerArray];
        for (int featureIdx = 0; featureIdx < numFeaturesPerArray; featureIdx++) {
            for (double[] entries : data) {
                double entryDiff = entries[featureIdx] - means[featureIdx];
                stds[featureIdx] += entryDiff * entryDiff;
            }
            stds[featureIdx] /= data.length;
            stds[featureIdx] = Math.sqrt(stds[featureIdx]);
        }
        return stds;
    }

    /** Replaces all NaN entries by the mean of that feature. */
    private static void replaceNaNs(double[][] data, double[] means) {
        int numFeaturesPerArray = getNumFeaturesPerArray();
        for (int featureIdx = 0; featureIdx < numFeaturesPerArray; featureIdx++) {
            for (double[] element : data) {
                if (Double.isNaN(element[featureIdx])) {
                    element[featureIdx] = means[featureIdx];
                }
            }
        }
    }

    /** Get the number of features per entry. */
    public static int getNumFeaturesPerArray() {
        return NUM_FEATURES_PER_ENTRY;
    }

    /** Get the mean of feature vector. */
    public static double[] getNormMeans() {
        return FEATURE_NORM_MEANS;
    }

    /** Get the std of feature vector. */
    public static double[] getNormStds() {
        return FEATURE_NORM_STDS;
    }

    /** Get default CCA level. */
    public static double getMeanCcaBusyRatio() {
        return FEATURE_NORM_MEANS[3];
    }

    /** Set the mean of feature vector. */
    public static void setNormMeans(List<Double> normMeans) {
        for (int idx = 0; idx < normMeans.size(); idx++) {
            FEATURE_NORM_MEANS[idx] = normMeans.get(idx);
        }
    }

    /** Set the std of feature vector. */
    public static void setNormStds(List<Double> normStds) {
        for (int idx = 0; idx < normStds.size(); idx++) {
            FEATURE_NORM_STDS[idx] = normStds.get(idx);
        }
    }

    private FeatureUtils() {}
}
