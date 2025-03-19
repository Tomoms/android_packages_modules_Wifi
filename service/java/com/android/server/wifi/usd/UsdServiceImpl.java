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

import android.annotation.NonNull;
import android.content.Context;
import android.net.wifi.IBooleanListener;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.IAvailabilityCallback;
import android.net.wifi.usd.IPublishSessionCallback;
import android.net.wifi.usd.ISubscribeSessionCallback;
import android.net.wifi.usd.IUsdManager;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.PublishSession;
import android.net.wifi.usd.PublishSessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.usd.SubscribeSession;
import android.net.wifi.usd.SubscribeSessionCallback;
import android.net.wifi.usd.UsdManager;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import com.android.server.wifi.RunnerHandler;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.util.WifiPermissionsUtil;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Implementation of the IUsdManager.
 */
public class UsdServiceImpl extends IUsdManager.Stub {
    private static final String TAG = UsdServiceImpl.class.getName();
    private final Context mContext;
    private RunnerHandler mHandler;
    private WifiInjector mWifiInjector;
    WifiPermissionsUtil mWifiPermissionsUtil;


    /**
     * Constructor
     */
    public UsdServiceImpl(Context context) {
        mContext = context;
    }

    /**
     * Start the service
     */
    public void start(@NonNull WifiInjector wifiInjector) {
        mWifiInjector = wifiInjector;
        mWifiPermissionsUtil = mWifiInjector.getWifiPermissionsUtil();
        Log.i(TAG, "start");
    }

    /**
     * Start/initialize portions of the service which require the boot stage to be complete.
     */
    public void startLate() {
        Log.i(TAG, "startLate");
    }

    /**
     * Proxy for the final native call of the parent class. Enables mocking of
     * the function.
     */
    public int getMockableCallingUid() {
        return Binder.getCallingUid();
    }

    /**
     * See {@link UsdManager#isSubscriberAvailable()}
     */
    @Override
    public boolean isSubscriberAvailable() {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "Subscriber is not available");
        return false;
    }

    /**
     * See {@link UsdManager#isPublisherAvailable()}
     */
    @Override
    public boolean isPublisherAvailable() {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "Publisher is not available");
        return false;
    }

    /**
     * See
     * {@link UsdManager#registerAvailabilityCallback(Executor, UsdManager.AvailabilityCallback)}
     */
    @Override
    public void registerAvailabilityCallback(IAvailabilityCallback callback) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
    }

    /**
     * See {@link UsdManager#unregisterAvailabilityCallback(UsdManager.AvailabilityCallback)}
     */
    @Override
    public void unregisterAvailabilityCallback(IAvailabilityCallback callback) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
    }

    /**
     * See {@link UsdManager#getCharacteristics()}
     */
    @Override
    public Characteristics getCharacteristics() {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }

        Bundle bundle = new Bundle();
        bundle.putInt(Characteristics.KEY_MAX_NUM_SUBSCRIBE_SESSIONS, 0);
        bundle.putInt(Characteristics.KEY_MAX_NUM_SUBSCRIBE_SESSIONS, 0);
        bundle.putInt(Characteristics.KEY_MAX_SERVICE_SPECIFIC_INFO_LENGTH, 0);
        bundle.putInt(Characteristics.KEY_MAX_MATCH_FILTER_LENGTH, 0);
        bundle.putInt(Characteristics.KEY_MAX_SERVICE_NAME_LENGTH, 0);
        return new Characteristics(bundle);
    }

    /**
     * See {@link SubscribeSession#sendMessage(int, byte[], Executor, Consumer)}
     */
    public void sendMessage(int peerId, @NonNull byte[] message,
            @NonNull IBooleanListener listener) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "sendMessage ( peerId = " + peerId + " , message length = " + message.length
                + " )");
    }

    /**
     * See {@link SubscribeSession#cancel()}
     */
    public void cancelSubscribe(int sessionId) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "cancelSubscribe: ( sessionId = " + sessionId + " )");
    }

    /**
     * See {@link PublishSession#cancel()}
     */
    public void cancelPublish(int sessionId) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "cancelPublish: ( sessionId = " + sessionId + " )");
    }

    /**
     * See {@link PublishSession#updatePublish(byte[])}
     */
    public void updatePublish(int sessionId, @NonNull byte[] ssi) {
        Objects.requireNonNull(ssi, "Service specific info must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "updatePublish: ( sessionId = " + sessionId + " )");
    }

    /**
     * See {@link UsdManager#publish(PublishConfig, Executor, PublishSessionCallback)}
     */
    @Override
    public void publish(PublishConfig publishConfig, IPublishSessionCallback callback) {
        Objects.requireNonNull(publishConfig, "publishConfig must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "publish " + publishConfig);
    }

    /**
     * See {@link UsdManager#subscribe(SubscribeConfig, Executor, SubscribeSessionCallback)}
     */
    @Override
    public void subscribe(SubscribeConfig subscribeConfig, ISubscribeSessionCallback callback) {
        Objects.requireNonNull(subscribeConfig, "subscribeConfig must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "subscribe " + subscribeConfig);
    }
}
