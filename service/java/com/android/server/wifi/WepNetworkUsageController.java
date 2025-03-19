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

package com.android.server.wifi;

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_WEP_ALLOWED;

import android.annotation.NonNull;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.LocalLog;
import android.util.Log;

import com.android.wifi.flags.FeatureFlags;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Class used to control WEP network usage.
 * Start to access this class from android 16 and flag: wep_disabled_in_apm is true.
 */
public class WepNetworkUsageController {
    private static final String TAG = "WepNetworkUsageController";

    private final HandlerThread mHandlerThread;
    private final WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    private final WifiSettingsConfigStore mWifiSettingsConfigStore;
    private final WifiGlobals mWifiGlobals;
    private final ActiveModeWarden mActiveModeWarden;
    private final LocalLog mLocalLog;
    private final FeatureFlags mFeatureFlags;

    private boolean mVerboseLoggingEnabled;
    private boolean mIsWepAllowedSettingEnabled;
    private boolean mIsAdvancedProtectionModeEnabled;

    public WepNetworkUsageController(
            @NonNull HandlerThread handlerThread,
            @NonNull WifiDeviceStateChangeManager wifiDeviceStateChangeManager,
            @NonNull WifiSettingsConfigStore wifiSettingsConfigStore,
            @NonNull WifiGlobals wifiGlobals,
            @NonNull ActiveModeWarden activeModeWarden,
            @NonNull FeatureFlags featureFlags) {
        mHandlerThread = handlerThread;
        mWifiDeviceStateChangeManager = wifiDeviceStateChangeManager;
        mWifiSettingsConfigStore = wifiSettingsConfigStore;
        mWifiGlobals = wifiGlobals;
        mActiveModeWarden = activeModeWarden;
        mFeatureFlags = featureFlags;
        if (!mFeatureFlags.wepDisabledInApm()) {
            Log.wtf(TAG, "WepNetworkUsageController should work only"
                    + " after feature flag is enabled");
        }
        mLocalLog = new LocalLog(32);
    }

    /** Handle the boot completed event. Start to monitor WEP network usage */
    public void handleBootCompleted() {
        mIsWepAllowedSettingEnabled = mWifiSettingsConfigStore.get(WIFI_WEP_ALLOWED);
        mWifiSettingsConfigStore.registerChangeListener(WIFI_WEP_ALLOWED,
                (key, value) -> {
                    mIsWepAllowedSettingEnabled = value;
                    handleWepAllowedChanged();
                },
                new Handler(mHandlerThread.getLooper()));
        mWifiDeviceStateChangeManager.registerStateChangeCallback(
                new WifiDeviceStateChangeManager.StateChangeCallback() {
                    @Override
                    public void onAdvancedProtectionModeStateChanged(boolean apmOn) {
                        mIsAdvancedProtectionModeEnabled = apmOn;
                        handleWepAllowedChanged();
                    }
                });
    }

    /**
     * Enable verbose logging for WifiConnectivityManager.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Handles WEP allowed changed either settings: WIFI_WEP_ALLOWED changed or APM changed.
     */
    private void handleWepAllowedChanged() {
        final boolean isWepAllowed = mIsWepAllowedSettingEnabled
                && !mIsAdvancedProtectionModeEnabled;
        mLocalLog.log("handleWepAllowedChanged, mIsWepAllowedSettingEnabledByUser = "
                + mIsWepAllowedSettingEnabled
                + " and isAdvancedProtectionEnabled = " + mIsAdvancedProtectionModeEnabled);
        if (isWepAllowed == mWifiGlobals.isWepAllowed()) {
            return; // No changed.
        }
        mWifiGlobals.setWepAllowed(isWepAllowed);
        if (!isWepAllowed) {
            for (ClientModeManager clientModeManager
                    : mActiveModeWarden.getClientModeManagers()) {
                if (!(clientModeManager instanceof ConcreteClientModeManager)) {
                    continue;
                }
                ConcreteClientModeManager cmm = (ConcreteClientModeManager) clientModeManager;
                WifiInfo info = cmm.getConnectionInfo();
                if (info != null
                        && info.getCurrentSecurityType() == WifiInfo.SECURITY_TYPE_WEP) {
                    clientModeManager.disconnect();
                }
            }
        }
    }

    /**
     * Dump output for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WepNetworkUsageController:");
        if (mVerboseLoggingEnabled) {
            mLocalLog.dump(fd, pw, args);
        }
        pw.println("mIsAdvancedProtectionModeEnabled=" + mIsAdvancedProtectionModeEnabled);
        pw.println("mIsWepAllowedSettingEnabled=" + mIsWepAllowedSettingEnabled);

    }
}
