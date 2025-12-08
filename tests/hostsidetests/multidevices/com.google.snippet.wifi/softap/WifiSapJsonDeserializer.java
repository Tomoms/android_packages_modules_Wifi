/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.snippet.wifi.softap;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApConfiguration;
import android.util.SparseIntArray;

import com.android.modules.utils.build.SdkLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


/**
 * Deserializes JSONObject into data objects defined in Wi-Fi Aware API.
 */
public class WifiSapJsonDeserializer {

    private WifiSapJsonDeserializer() {}

    private static int getApBandFromChannelFrequency(int freq) {
        int[] testFrequency_2G = {2412, 2437, 2462, 2484};
        int[] testFrequency_5G = {5180, 5220, 5540, 5745};
        int[] testFrequency_6G = {5955, 6435, 6535, 7115};
        int[] testFrequency_60G = {58320, 64800};
        if (freq >= testFrequency_2G[0]  && freq <=  testFrequency_2G[3]) {
            return SoftApConfiguration.BAND_2GHZ;
        } else if (freq >= testFrequency_5G[0]  && freq <=  testFrequency_5G[3]) {
            return SoftApConfiguration.BAND_5GHZ;
        } else if (freq >= testFrequency_6G[0]  && freq <=  testFrequency_6G[3]) {
            return SoftApConfiguration.BAND_6GHZ;
        } else if (freq >= testFrequency_60G[0]  && freq <=  testFrequency_60G[3]) {
            return SoftApConfiguration.BAND_60GHZ;
        }
        return -1;
    }

    private static int[] convertJSONArrayToIntArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        int[] iArray = new int[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            iArray[i] = jArray.getInt(i);
        }
        return iArray;
    }
    /**
     * Converts JSON object to {@link SoftApConfiguration}.
     *
     * @param configJson corresponding to SoftApConfiguration in
     * @param configBuilder    builder to build the SoftApConfiguration
     * @return SoftApConfiguration object
     */
    public static SoftApConfiguration jsonToSoftApConfiguration(
            JSONObject configJson, SoftApConfiguration.Builder configBuilder) throws JSONException {
        if (configJson == null) {
            return configBuilder.build();
        }
        if (configJson.has("SSID")) {
            configBuilder.setSsid(configJson.getString("SSID"));
        }
        if (configJson.has("Passphrase")) {
            String pwd = configJson.getString("Passphrase");
            // Check if new security type SAE (WPA3) is present. Default to PSK
            if (configJson.has("mSecurityType")) {
                String securityTypeStr = configJson.getString("mSecurityType");
                int securityTypeInt = SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
                try {
                    securityTypeInt = Integer.parseInt(securityTypeStr);
                } catch (NumberFormatException nfe) {
                    throw new JSONException("Invalid security type: " + securityTypeStr);
                }
                if (securityTypeInt == SoftApConfiguration.SECURITY_TYPE_WPA2_PSK) {
                    configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
                } else if (securityTypeInt
                        == SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION) {
                    configBuilder.setPassphrase(pwd,
                            SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
                } else if (securityTypeInt == SoftApConfiguration.SECURITY_TYPE_WPA3_SAE) {
                    configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
                } else {
                    configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
                }
            } else {
                configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            }
        }
        if (configJson.has("BSSID")) {
            configBuilder.setBssid(MacAddress.fromString(configJson.getString("BSSID")));
        }
        if (configJson.has("hiddenSSID")) {
            configBuilder.setHiddenSsid(configJson.getBoolean("hiddenSSID"));
        }
        if (configJson.has("apBand")) {
            configBuilder.setBand(configJson.getInt("apBand"));
        }
        if (configJson.has("apChannel") && configJson.has("apBand")) {
            configBuilder.setChannel(configJson.getInt("apChannel"), configJson.getInt("apBand"));
        }

        if (configJson.has("mMaxNumberOfClients")) {
            configBuilder.setMaxNumberOfClients(configJson.getInt("mMaxNumberOfClients"));
        }

        if (configJson.has("mShutdownTimeoutMillis")) {
            configBuilder.setShutdownTimeoutMillis(configJson.getLong("mShutdownTimeoutMillis"));
        }

        if (configJson.has("mAutoShutdownEnabled")) {
            configBuilder.setAutoShutdownEnabled(configJson.getBoolean("mAutoShutdownEnabled"));
        }

        if (configJson.has("mClientControlByUser")) {
            configBuilder.setClientControlByUserEnabled(
                    configJson.getBoolean("mClientControlByUser"));
        }

        List allowedClientList = new ArrayList<>();
        if (configJson.has("mAllowedClientList")) {
            JSONArray allowedList = configJson.getJSONArray("mAllowedClientList");
            for (int i = 0; i < allowedList.length(); i++) {
                allowedClientList.add(MacAddress.fromString(allowedList.getString(i)));
            }
        }

        List blockedClientList = new ArrayList<>();
        if (configJson.has("mBlockedClientList")) {
            JSONArray blockedList = configJson.getJSONArray("mBlockedClientList");
            for (int j = 0; j < blockedList.length(); j++) {
                blockedClientList.add(MacAddress.fromString(blockedList.getString(j)));
            }
        }

        configBuilder.setAllowedClientList(allowedClientList);
        configBuilder.setBlockedClientList(blockedClientList);

        if (SdkLevel.isAtLeastS()) {
            if (configJson.has("apBands")) {
                JSONArray jBands = configJson.getJSONArray("apBands");
                int[] bands = convertJSONArrayToIntArray(jBands);
                configBuilder.setBands(bands);
            }
            if (configJson.has("mMacRandomizationSetting")) {
                configBuilder.setMacRandomizationSetting(
                        configJson.getInt("mMacRandomizationSetting"));
            }

            if (configJson.has("mBridgedModeOpportunisticShutdownEnabled")) {
                configBuilder.setBridgedModeOpportunisticShutdownEnabled(
                        configJson.getBoolean("mBridgedModeOpportunisticShutdownEnabled"));
            }

            if (configJson.has("mIeee80211axEnabled")) {
                configBuilder.setIeee80211axEnabled(configJson.getBoolean("mIeee80211axEnabled"));
            }

            if (configJson.has("apChannelFrequencies")) {
                JSONArray jChannelFrequencys = configJson.getJSONArray("apChannelFrequencies");
                int[] channelFrequencies = convertJSONArrayToIntArray(jChannelFrequencys);
                SparseIntArray channels = new SparseIntArray();
                for (int channelFrequency : channelFrequencies) {
                    if (channelFrequency != 0) {
                        channels.put(getApBandFromChannelFrequency(channelFrequency),
                                ScanResult.convertFrequencyMhzToChannelIfSupported(
                                        channelFrequency));
                    }
                }
                if (channels.size() != 0) {
                    configBuilder.setChannels(channels);
                }
            }

            if (configJson.has("mClientIsolationEnabled")) {
                configBuilder.setClientIsolationEnabled(
                        configJson.getBoolean("mClientIsolationEnabled"));
            }
        }
        return configBuilder.build();
    }
}
