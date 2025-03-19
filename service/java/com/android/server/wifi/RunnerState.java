/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.server.wifi.proto.WifiStatsLog.WIFI_THREAD_TASK_EXECUTED;

import android.annotation.NonNull;
import android.os.Message;
import android.os.SystemClock;
import android.os.Trace;
import android.util.LocalLog;

import com.android.internal.util.State;
import com.android.server.wifi.proto.WifiStatsLog;

/**
 * RunnerState class is a wrapper based on State class to monitor and track the State enter/exit
 * and message handler execution for taking longer time than the expected threshold.
 * User must extend the RunnerState class instead of State, and provide the implementation of:
 * { @link RunnerState#enterImpl() } { @link RunnerState#exitImpl() }
 * { @link RunnerState#processMessageImpl() }
 * { @link RunnerState#getMessageLogRec() }
 *
 */
public abstract class RunnerState extends State {
    private static final String TAG = "RunnerState";
    private static final int METRICS_THRESHOLD_MILLIS = 100;

    /** Message.what value when entering */
    public static final int STATE_ENTER_CMD = -3;

    /** Message.what value when exiting */
    public static final int STATE_EXIT_CMD = -4;

    private final int mRunningTimeThresholdInMilliseconds;
    // TODO: b/246623192 Add Wifi metric for Runner state overruns.
    private final LocalLog mLocalLog;
    private final WifiInjector mWifiInjector;

    /**
     * The Runner state Constructor
     * @param threshold the running time threshold in milliseconds
     */
    public RunnerState(int threshold, @NonNull LocalLog localLog) {
        mRunningTimeThresholdInMilliseconds = threshold;
        mLocalLog = localLog;
        mWifiInjector = WifiInjector.getInstance();
    }

    private boolean isVerboseLoggingEnabled() {
        return mWifiInjector.isVerboseLoggingEnabled();
    }

    @Override
    public boolean processMessage(Message message) {
        String signatureToLog = getMessageLogRec(message);
        if (signatureToLog == null) {
            signatureToLog = getMessageLogRec(message.what);
        }
        boolean traceEvent = isVerboseLoggingEnabled();
        if (traceEvent) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, signatureToLog);
        }

        long startTime = SystemClock.uptimeMillis();
        // TODO(b/295398783): Support deferMessage and sendMessageAtFrontOfQueue where when is 0;
        long scheduleLatency = message.getWhen() != 0 ? startTime - message.getWhen() : 0;
        boolean ret = processMessageImpl(message);
        long runTime = SystemClock.uptimeMillis() - startTime;
        if (traceEvent) {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
        if (runTime > mRunningTimeThresholdInMilliseconds) {
            mLocalLog.log(signatureToLog + " was running for " + runTime + " ms");
        }
        if (runTime > METRICS_THRESHOLD_MILLIS || scheduleLatency > METRICS_THRESHOLD_MILLIS) {
            WifiStatsLog.write(WIFI_THREAD_TASK_EXECUTED, (int) runTime, (int) scheduleLatency,
                    signatureToLog);
        }
        return ret;
    }

    @Override
    public void enter() {
        String signatureToLog = getMessageLogRec(STATE_ENTER_CMD);
        boolean traceEvent = isVerboseLoggingEnabled();
        if (traceEvent) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, signatureToLog);
        }
        long startTime = SystemClock.uptimeMillis();
        enterImpl();
        long runTime = SystemClock.uptimeMillis() - startTime;
        if (traceEvent) {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
        if (runTime > mRunningTimeThresholdInMilliseconds) {
            mLocalLog.log(signatureToLog + " was running for " + runTime + " ms");
        }
        if (runTime > METRICS_THRESHOLD_MILLIS) {
            WifiStatsLog.write(WIFI_THREAD_TASK_EXECUTED, (int) runTime, 0, signatureToLog);
        }
    }

    @Override
    public void exit() {
        String signatureToLog = getMessageLogRec(STATE_EXIT_CMD);
        boolean traceEvent = isVerboseLoggingEnabled();
        if (traceEvent) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, signatureToLog);
        }
        long startTime = SystemClock.uptimeMillis();
        exitImpl();
        long runTime = SystemClock.uptimeMillis() - startTime;
        if (traceEvent) {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }

        if (runTime > mRunningTimeThresholdInMilliseconds) {
            mLocalLog.log(signatureToLog + " was running for " + runTime + " ms");
        }
        if (runTime > METRICS_THRESHOLD_MILLIS) {
            WifiStatsLog.write(WIFI_THREAD_TASK_EXECUTED, (int) runTime, 0, signatureToLog);
        }
    }

    /**
     * Implement this method for State enter process, instead of enter()
     */
    public abstract void enterImpl();

    /**
     * Implement this method for State exit process, instead of exit()
     */
    public abstract void exitImpl();

    /**
     * Implement this method for process message, instead of processMessage()
     */
    public abstract boolean processMessageImpl(Message msg);

    /**
     * Implement this to translate a message `what` into a readable String
     * @param what message 'what' field
     * @return Readable string
     */
    public String getMessageLogRec(int what) {
        return null;
    };

    /**
     * Implement this to translate a message into a readable String
     * @return Readable string
     */
    public String getMessageLogRec(Message message) {
        return null;
    };
}
