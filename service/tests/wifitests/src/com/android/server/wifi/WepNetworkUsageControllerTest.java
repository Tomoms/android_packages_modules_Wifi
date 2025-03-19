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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.wifi.flags.FeatureFlags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiVoipDetectorTest}.
 */
@SmallTest
public class WepNetworkUsageControllerTest extends WifiBaseTest {

    @Mock HandlerThread mHandlerThread;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;
    @Mock WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock WifiGlobals mWifiGlobals;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock FeatureFlags mFeatureFlags;

    private WepNetworkUsageController mWepNetworkUsageController;
    private TestLooper mLooper;
    final ArgumentCaptor<WifiSettingsConfigStore.OnSettingsChangedListener>
            mWepAllowedSettingChangedListenerCaptor =
            ArgumentCaptor.forClass(WifiSettingsConfigStore.OnSettingsChangedListener.class);

    final ArgumentCaptor<WifiDeviceStateChangeManager.StateChangeCallback>
            mWifiDeviceStateChangeCallbackCaptor =
            ArgumentCaptor.forClass(WifiDeviceStateChangeManager.StateChangeCallback.class);
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        when(mFeatureFlags.wepDisabledInApm()).thenReturn(true);
        when(mWifiSettingsConfigStore.get(eq(WIFI_WEP_ALLOWED))).thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(WifiDeviceStateChangeManager.StateChangeCallback callback) {
                    callback.onAdvancedProtectionModeStateChanged(false);
            }
        }).when(mWifiDeviceStateChangeManager).registerStateChangeCallback(any());

        doAnswer(new AnswerWithArguments() {
            public void answer(boolean isWepAllowed) {
                when(mWifiGlobals.isWepAllowed()).thenReturn(isWepAllowed);
            }
        }).when(mWifiGlobals).setWepAllowed(anyBoolean());
        mWepNetworkUsageController = new WepNetworkUsageController(
                mHandlerThread, mWifiDeviceStateChangeManager,
                mWifiSettingsConfigStore, mWifiGlobals, mActiveModeWarden, mFeatureFlags);
    }

    @Test
    public void testHandleBootCompleted() {
        mWepNetworkUsageController.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).get(eq(WIFI_WEP_ALLOWED));
        verify(mWifiSettingsConfigStore).registerChangeListener(
                eq(WIFI_WEP_ALLOWED),
                mWepAllowedSettingChangedListenerCaptor.capture(),
                any(Handler.class));
        verify(mWifiDeviceStateChangeManager).registerStateChangeCallback(
                mWifiDeviceStateChangeCallbackCaptor.capture());
        mLooper.dispatchAll();
        // WEP should be allowed since WIFI_WEP_ALLOWED is true
        // and isAdvancedProtectionEnabled is false. (no mock);
        verify(mWifiGlobals).setWepAllowed(true);
    }

    @Test
    public void testHandleWepAllowedSettingChange() {
        mWepNetworkUsageController.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mWifiSettingsConfigStore).registerChangeListener(
                eq(WIFI_WEP_ALLOWED),
                mWepAllowedSettingChangedListenerCaptor.capture(),
                any(Handler.class));
        // WIFI_WEP_ALLOWED is true in setUp
        verify(mWifiGlobals).setWepAllowed(true);

        // WIFI_WEP_ALLOWED Settings changed, B&R use case
        mWepAllowedSettingChangedListenerCaptor.getValue()
                .onSettingsChanged(WIFI_WEP_ALLOWED, false);
        verify(mWifiGlobals).setWepAllowed(false);

        mWepAllowedSettingChangedListenerCaptor.getValue()
                .onSettingsChanged(WIFI_WEP_ALLOWED, true);
        verify(mWifiGlobals, times(2)).setWepAllowed(true);
    }

    @Test
    public void testAdvancedProtectionModeChanged() {
        mWepNetworkUsageController.handleBootCompleted();
        mLooper.dispatchAll();
        verify(mWifiDeviceStateChangeManager).registerStateChangeCallback(
                mWifiDeviceStateChangeCallbackCaptor.capture());
        // WIFI_WEP_ALLOWED is true in setUp
        verify(mWifiGlobals).setWepAllowed(true);
        mWifiDeviceStateChangeCallbackCaptor.getValue()
                .onAdvancedProtectionModeStateChanged(true);
        verify(mWifiGlobals).setWepAllowed(false);

        mWifiDeviceStateChangeCallbackCaptor.getValue()
                .onAdvancedProtectionModeStateChanged(false);
        verify(mWifiGlobals, times(2)).setWepAllowed(true);
    }

    @Test
    public void testHandleWepAllowedChangedWhenWepIsConnected() {
        mWepNetworkUsageController.handleBootCompleted();
        mLooper.dispatchAll();
        // WIFI_WEP_ALLOWED is true in setUp
        verify(mWifiDeviceStateChangeManager).registerStateChangeCallback(
                mWifiDeviceStateChangeCallbackCaptor.capture());
        verify(mWifiGlobals).setWepAllowed(true);

        // Mock wep connection to make sure it will disconnect
        ConcreteClientModeManager cmmWep = mock(ConcreteClientModeManager.class);
        ConcreteClientModeManager cmmWpa = mock(ConcreteClientModeManager.class);
        WifiInfo mockWifiInfoWep = mock(WifiInfo.class);
        WifiInfo mockWifiInfoWpa = mock(WifiInfo.class);
        List<ClientModeManager> cmms = Arrays.asList(cmmWep, cmmWpa);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(cmms);
        when(mockWifiInfoWep.getCurrentSecurityType()).thenReturn(WifiInfo.SECURITY_TYPE_WEP);
        when(mockWifiInfoWpa.getCurrentSecurityType()).thenReturn(WifiInfo.SECURITY_TYPE_PSK);
        when(cmmWep.getConnectionInfo()).thenReturn(mockWifiInfoWep);
        when(cmmWpa.getConnectionInfo()).thenReturn(mockWifiInfoWpa);
        // Force setWepAllowed to false by enable APM mode.
        mWifiDeviceStateChangeCallbackCaptor.getValue()
                .onAdvancedProtectionModeStateChanged(true);
        mLooper.dispatchAll();
        verify(mWifiGlobals).setWepAllowed(false);
        // Only WEP disconnect
        verify(cmmWep).disconnect();
        verify(cmmWpa, never()).disconnect();
    }
}
