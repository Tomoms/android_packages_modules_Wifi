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

package com.android.server.wifi.mainline_supplicant;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.util.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Allows us to bring up, tear down, and make calls into the mainline supplicant process.
 * <p>
 * The mainline supplicant is a separate wpa_supplicant binary stored in the Wifi mainline module,
 * which provides specific functionalities such as USD.
 */
public class MainlineSupplicant {
    private static final String TAG = "MainlineSupplicant";
    private static final String MAINLINE_SUPPLICANT_SERVICE_NAME = "wifi_mainline_supplicant";
    private static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;

    private IMainlineSupplicant mIMainlineSupplicant;
    private final Object mLock = new Object();
    private SupplicantDeathRecipient mDeathRecipient;
    private CountDownLatch mWaitForDeathLatch;

    public MainlineSupplicant() {
        mDeathRecipient = new SupplicantDeathRecipient();
    }

    @VisibleForTesting
    protected IMainlineSupplicant getNewServiceBinderMockable() {
        return IMainlineSupplicant.Stub.asInterface(
                ServiceManagerWrapper.waitForService(MAINLINE_SUPPLICANT_SERVICE_NAME));
    }

    private @Nullable IBinder getCurrentServiceBinder() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                return null;
            }
            return mIMainlineSupplicant.asBinder();
        }
    }

    private class SupplicantDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
        }

        @Override
        public void binderDied(@NonNull IBinder who) {
            synchronized (mLock) {
                IBinder currentBinder = getCurrentServiceBinder();
                Log.i(TAG, "Death notification received. who=" + who
                        + ", currentBinder=" + currentBinder);
                if (currentBinder == null || currentBinder != who) {
                    Log.i(TAG, "Ignoring stale death notification");
                    return;
                }
                if (mWaitForDeathLatch != null) {
                    // Latch indicates that this event was triggered by stopService
                    mWaitForDeathLatch.countDown();
                }
                mIMainlineSupplicant = null;
                Log.i(TAG, "Service death was handled successfully");
            }
        }
    }

    /**
     * Start the mainline supplicant process.
     *
     * @return true if the process was started, false otherwise.
     */
    public boolean startService() {
        synchronized (mLock) {
            if (!Environment.isSdkAtLeastB()) {
                Log.e(TAG, "Service is not available before Android B");
                return false;
            }
            if (mIMainlineSupplicant != null) {
                Log.i(TAG, "Service has already been started");
                return true;
            }

            mIMainlineSupplicant = getNewServiceBinderMockable();
            if (mIMainlineSupplicant == null) {
                Log.e(TAG, "Unable to retrieve binder from the ServiceManager");
                return false;
            }

            try {
                mWaitForDeathLatch = null;
                mIMainlineSupplicant.asBinder().linkToDeath(mDeathRecipient, /* flags= */  0);
            } catch (RemoteException e) {
                handleRemoteException(e, "startService");
                return false;
            }

            Log.i(TAG, "Service was started successfully");
            return true;
        }
    }

    /**
     * Check whether this instance is active.
     */
    @VisibleForTesting
    protected boolean isActive() {
        synchronized (mLock) {
            return mIMainlineSupplicant != null;
        }
    }

    /**
     * Stop the mainline supplicant process.
     */
    public void stopService() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                Log.i(TAG, "Service has already been stopped");
                return;
            }
            try {
                Log.i(TAG, "Attempting to stop the service");
                mWaitForDeathLatch = new CountDownLatch(1);
                mIMainlineSupplicant.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, "stopService");
                return;
            }
        }

        // Wait for latch to confirm the service death
        try {
            if (mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.i(TAG, "Service death confirmation was received");
            } else {
                Log.e(TAG, "Timed out waiting for confirmation of service death");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for service death");
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodName) {
        Log.e(TAG, methodName + " encountered ServiceSpecificException " + e);
    }

    private void handleRemoteException(RemoteException e, String methodName) {
        synchronized (mLock) {
            Log.e(TAG, methodName + " encountered RemoteException " + e);
            mIMainlineSupplicant = null;
        }
    }
}
