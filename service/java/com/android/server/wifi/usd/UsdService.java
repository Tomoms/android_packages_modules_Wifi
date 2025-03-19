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

package com.android.server.wifi.usd;

import android.content.Context;
import android.net.wifi.WifiContext;
import android.net.wifi.util.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.SystemService;
import com.android.server.wifi.WifiInjector;

/**
 * Service implementing USD functionality. Delegates actual interface implementation to
 * {@link UsdServiceImpl}.
 */
public class UsdService extends SystemService {
    private static final String TAG = UsdService.class.getName();
    final UsdServiceImpl mUsdServiceImpl;

    public UsdService(@NonNull Context context) {
        super(new WifiContext(context));
        mUsdServiceImpl = new UsdServiceImpl(getContext());
    }

    @Override
    public void onStart() {
        if (!Environment.isSdkAtLeastB()) {
            return;
        }
        Log.i(TAG, "Registering " + Context.WIFI_USD_SERVICE);
        publishBinderService(Context.WIFI_USD_SERVICE, mUsdServiceImpl);
    }

    @Override
    public void onBootPhase(int phase) {
        if (!Environment.isSdkAtLeastB()) {
            return;
        }
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            WifiInjector wifiInjector = WifiInjector.getInstance();
            if (wifiInjector == null) {
                Log.e(TAG, "onBootPhase(PHASE_SYSTEM_SERVICES_READY): NULL injector!");
                return;
            }
            mUsdServiceImpl.start(wifiInjector);
        } else if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            mUsdServiceImpl.startLate();
        }
    }
}
