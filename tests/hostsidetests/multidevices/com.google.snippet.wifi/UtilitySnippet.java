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

package com.google.snippet.wifi;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcOptional;


/**
 * Snippet class for exposing utility RPCs.
 */
public class UtilitySnippet extends WifiShellPermissionSnippet implements Snippet {

    private final Context mContext;
    private static final String TAG = "UtilitySnippet";

    public UtilitySnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    /**
     * Drops the shell permission. This is no-op if shell permission identity
     * is not adopted.
     */
    @Rpc(description = "Drops the shell permission. This is no-op if shell"
            + " permission identity is not adopted.")
    public void utilityDropShellPermission() {
        dropShellPermission();
    }

    /**
     * Adopts shell permission, with each invocation overwriting preceding adoptions.
     *
     * @param permissions The permissions to grant (if null all permissions will be granted).
     */
    @Rpc(description = "Adopts shell permission, with each invocation overwriting preceding"
            + " adoptions.")
    public void utilityAdoptShellPermission(@RpcOptional String[] permissions)
            throws RemoteException {
        if (permissions == null) {
            adoptShellPermission();
        } else {
            adoptShellPermission(permissions);
        }
    }

    /**
     * Bring the snippet service to the foreground by starting an activity.
     */
    @Rpc(description = "Bring the snippet service to the foreground by starting an activity.")
    public void utilityBringToForeground() {
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * Bring the snippet service to the background by launching the home screen.
     */
    @Rpc(description = "Bring the snippet service to the background by launching the home screen.")
    public void utilityBringToBackground() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
