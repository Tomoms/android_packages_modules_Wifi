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

package com.google.snippet.wifi.usd;

import android.content.Context;
import android.net.wifi.usd.DiscoveryResult;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.PublishSession;
import android.net.wifi.usd.PublishSessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.usd.SubscribeSession;
import android.net.wifi.usd.SubscribeSessionCallback;
import android.net.wifi.usd.UsdManager;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/** Snippet class for Wi-Fi USD functionality. */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class WifiUsdManagerSnippet implements Snippet {
    private static final String TAG = "WifiUsdManagerSnippet";
    private static final int WAIT_FOR_USD_CHANGE_SECS = 30;
    private static final int MESSAGE_TIMEOUT_SECS = 30;

    /** Custom exception for snippet failures. */
    public static class WifiUsdManagerSnippetException extends Exception {
        public WifiUsdManagerSnippetException(String message) {
            super(message);
        }
    }

    private final Context mContext;
    private final UsdManager mUsdManager;

    private PublishSession mActivePublishSession;
    private SubscribeSession mActiveSubscribeSession;

    private ScheduledExecutorService mPublishExecutor;
    private ScheduledExecutorService mSubscribeExecutor;

    private final BlockingQueue<String> mReceivedMessages = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<Integer, DiscoveryResult> sDiscoveredPeers =
            new ConcurrentHashMap<>();

    /** Snippet constructor. */
    public WifiUsdManagerSnippet() {
        Log.d(TAG, "WifiUsdManagerSnippet constructor: Initializing resources.");
        this.mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        this.mUsdManager = mContext.getSystemService(UsdManager.class);
    }

    @Override
    public void shutdown() {
        Log.d(TAG, "Shutting down WifiUsdSnippet...");
        stopUsdPublishSession();
        stopUsdSubscribeSession();
    }

    // --- Publisher Code ---
    private static class PublishSessionCallbackTest extends PublishSessionCallback {
        private static final String TAG = "WifiUsdCallback";

        private final CountDownLatch mStartedLatch = new CountDownLatch(1);
        private final BlockingQueue<String> mParentMessageQueue;
        private PublishSession mPublishSession;
        private String mFailureReason = null;

        PublishSessionCallbackTest(BlockingQueue<String> parentMessageQueue) {
            this.mParentMessageQueue = parentMessageQueue;
        }

        @Override
        public void onPublishFailed(int reason) {
            mFailureReason = "Publish failed with reason: " + reason;
            Log.e(TAG, "PUBLISHER CALLBACK FIRED: onPublishFailed with reason: " + reason);
            mStartedLatch.countDown();
        }

        @Override
        public void onPublishStarted(@NonNull PublishSession session) {
            Log.d(TAG, "PUBLISHER CALLBACK FIRED: onPublishStarted.");
            mPublishSession = session;
            mStartedLatch.countDown();
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            Log.d(TAG, "PUBLISHER CALLBACK FIRED: onMessageReceived from peerId " + peerId);
            if (message != null) {
                mParentMessageQueue.offer(new String(message, StandardCharsets.UTF_8));
            }
        }

        @Override
        public void onSessionTerminated(int reason) {
            Log.d(TAG, "PUBLISHER CALLBACK FIRED: onSessionTerminated with reason: " + reason);
        }

        void waitForStarted()
                throws WifiUsdManagerSnippetException, InterruptedException {
            if (!mStartedLatch.await(WAIT_FOR_USD_CHANGE_SECS, TimeUnit.SECONDS)) {
                throw new WifiUsdManagerSnippetException(
                        "Timeout waiting for publish session to start.");
            }
            if (mFailureReason != null) {
                throw new WifiUsdManagerSnippetException(mFailureReason);
            }
        }

        PublishSession getPublishSession() {
            return mPublishSession;
        }
    }

    /** Starts a USD publish session. */
    @Rpc(description = "Starts a USD publish session.")
    public void startUsdPublishSession(String serviceName, @Nullable String ssi) throws Exception {
        if (mUsdManager == null) {
            throw new WifiUsdManagerSnippetException("UsdManager is null.");
        }
        if (mActivePublishSession != null) {
            stopUsdPublishSession();
        }

        PublishConfig.Builder configBuilder = new PublishConfig.Builder(serviceName);
        if (ssi != null && !ssi.isEmpty()) {
            configBuilder.setServiceSpecificInfo(ssi.getBytes(StandardCharsets.UTF_8));
        }
        PublishConfig publishConfig = configBuilder.build();
        Log.i(TAG, "Starting publish with config: " + publishConfig);

        mPublishExecutor = Executors.newSingleThreadScheduledExecutor();
        PublishSessionCallbackTest callback = new PublishSessionCallbackTest(mReceivedMessages);

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mUsdManager.publish(publishConfig, mPublishExecutor, callback));

        callback.waitForStarted();
        mActivePublishSession = callback.getPublishSession();
        if (mActivePublishSession == null) {
            throw new WifiUsdManagerSnippetException("Publish session started but is null.");
        }
    }


    // --- Subscriber Code ---
    private static class SubscribeSessionCallbackTest extends SubscribeSessionCallback {
        private static final String TAG = "WifiUsdCallback";

        private final CountDownLatch mStartedLatch = new CountDownLatch(1);
        private final CountDownLatch mDiscoveredLatch = new CountDownLatch(1);
        private SubscribeSession mSubscribeSession;
        private String mFailureReason = null;

        @Override
        public void onSubscribeFailed(int reason) {
            mFailureReason = "Subscribe failed with reason: " + reason;
            Log.e(TAG, "SUBSCRIBER CALLBACK FIRED: onSubscribeFailed with reason: " + reason);
            mStartedLatch.countDown();
        }

        @Override
        public void onSubscribeStarted(@NonNull SubscribeSession session) {
            Log.d(TAG, "SUBSCRIBER CALLBACK FIRED: onSubscribeStarted.");
            mSubscribeSession = session;
            mStartedLatch.countDown();
        }

        @Override
        public void onServiceDiscovered(@NonNull DiscoveryResult discoveryResult) {
            Log.d(TAG, "SUBSCRIBER CALLBACK FIRED: onServiceDiscovered with result: "
                    + discoveryResult.toString());
            sDiscoveredPeers.put(discoveryResult.getPeerId(), discoveryResult);
            mDiscoveredLatch.countDown();
        }

        @Override
        public void onSessionTerminated(int reason) {
            Log.d(TAG, "SUBSCRIBER CALLBACK FIRED: onSessionTerminated with reason: " + reason);
        }

        void waitForStarted() throws WifiUsdManagerSnippetException, InterruptedException {
            if (!mStartedLatch.await(WAIT_FOR_USD_CHANGE_SECS, TimeUnit.SECONDS)) {
                throw new WifiUsdManagerSnippetException(
                        "Timeout waiting for subscribe session to start.");
            }
            if (mFailureReason != null) {
                throw new WifiUsdManagerSnippetException(mFailureReason);
            }
        }

        void waitForDiscovery() throws WifiUsdManagerSnippetException, InterruptedException {
            if (!mDiscoveredLatch.await(WAIT_FOR_USD_CHANGE_SECS, TimeUnit.SECONDS)) {
                throw new WifiUsdManagerSnippetException("Timeout waiting for service discovery.");
            }
        }

        SubscribeSession getSubscribeSession() {
            return mSubscribeSession;
        }
    }

    /** Performs the entire subscriber workflow atomically. */
    @Rpc(description = "Subscribes, waits for discovery, and sends a message.")
    public void subscribeDiscoverAndSendMessage(String serviceName, @Nullable String ssi,
            String message) throws Exception {
        Log.i(TAG, "subscribeDiscoverAndSendMessage: Starting...");
        if (mUsdManager == null) {
            throw new WifiUsdManagerSnippetException("UsdManager is null.");
        }
        if (mActiveSubscribeSession != null) {
            stopUsdSubscribeSession();
        }
        sDiscoveredPeers.clear();

        SubscribeConfig.Builder configBuilder = new SubscribeConfig.Builder(serviceName);
        if (ssi != null && !ssi.isEmpty()) {
            configBuilder.setServiceSpecificInfo(ssi.getBytes(StandardCharsets.UTF_8));
        }
        SubscribeConfig subscribeConfig = configBuilder.build();
        Log.i(TAG, "Starting subscribe with config: " + subscribeConfig);

        mSubscribeExecutor = Executors.newSingleThreadScheduledExecutor();
        SubscribeSessionCallbackTest callback = new SubscribeSessionCallbackTest();

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mUsdManager.subscribe(subscribeConfig, mSubscribeExecutor, callback));

        callback.waitForStarted();
        mActiveSubscribeSession = callback.getSubscribeSession();
        if (mActiveSubscribeSession == null) {
            throw new WifiUsdManagerSnippetException("Subscribe session started but is null.");
        }
        Log.i(TAG, "subscribeDiscoverAndSendMessage: Subscribe session started. "
                + "Waiting for discovery...");

        callback.waitForDiscovery();
        Log.i(TAG, "subscribeDiscoverAndSendMessage: Discovery Succeeded.");

        int peerId = getLastDiscoveredPeerId();
        if (peerId == -1) {
            throw new WifiUsdManagerSnippetException(
                    "Discovery succeeded but failed to get a peer ID.");
        }
        Log.i(TAG, "subscribeDiscoverAndSendMessage: Sending message to peer " + peerId + "...");

        sendMessage(peerId, message);

        Log.i(TAG, "subscribeDiscoverAndSendMessage: All steps completed successfully.");
    }

    // --- Common RPC Methods ---
    /** Cancels the active USD publish session. */
    @Rpc(description = "Cancels the active USD publish session.")
    public void stopUsdPublishSession() {
        if (mActivePublishSession == null) {
            return;
        }
        try {
            ShellIdentityUtils.invokeWithShellPermissions(mActivePublishSession::cancel);
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling publish session", e);
        } finally {
            mActivePublishSession = null;
            if (mPublishExecutor != null) {
                mPublishExecutor.shutdown();
                mPublishExecutor = null;
            }
        }
    }

    /** Cancels the active USD subscribe session. */
    @Rpc(description = "Cancels the active USD subscribe session.")
    public void stopUsdSubscribeSession() {
        if (mActiveSubscribeSession == null) {
            return;
        }
        try {
            ShellIdentityUtils.invokeWithShellPermissions(mActiveSubscribeSession::cancel);
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling subscribe session", e);
        } finally {
            mActiveSubscribeSession = null;
            if (mSubscribeExecutor != null) {
                mSubscribeExecutor.shutdown();
                mSubscribeExecutor = null;
            }
            sDiscoveredPeers.clear();
        }
    }

    /** Sends a message to a specified peer. */
    @Rpc(description = "Sends a message to a specified peer.")
    public void sendMessage(int peerId, String message) throws Exception {
        if (mActiveSubscribeSession == null) {
            throw new WifiUsdManagerSnippetException(
                    "No active subscribe session to send message from.");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] failureReason = {null};
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            mActiveSubscribeSession.sendMessage(
                    peerId, messageBytes, mSubscribeExecutor, success -> {
                        if (!success) {
                            failureReason[0] = "sendMessage callback returned false.";
                        }
                        latch.countDown();
                    });
        });

        if (!latch.await(MESSAGE_TIMEOUT_SECS, TimeUnit.SECONDS)) {
            throw new WifiUsdManagerSnippetException("Timeout waiting for sendMessage result.");
        }
        if (failureReason[0] != null) {
            throw new WifiUsdManagerSnippetException(failureReason[0]);
        }
    }

    /** Retrieves the last discovered peer ID for messaging. */
    @Rpc(description = "Retrieves the last discovered peer ID for messaging.")
    public int getLastDiscoveredPeerId() {
        if (sDiscoveredPeers.isEmpty()) {
            return -1;
        }
        return sDiscoveredPeers.keys().nextElement();
    }

    /** Waits for and returns a message received via USD. */
    @Rpc(description = "Waits for and returns a message received via USD.")
    @Nullable
    public String receiveMessage() throws InterruptedException {
        return mReceivedMessages.poll(MESSAGE_TIMEOUT_SECS, TimeUnit.SECONDS);
    }
}
