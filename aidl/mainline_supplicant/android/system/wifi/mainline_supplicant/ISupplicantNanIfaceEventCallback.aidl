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

package android.system.wifi.mainline_supplicant;

import android.system.wifi.mainline_supplicant.NanCapabilities;
import android.system.wifi.mainline_supplicant.NanStatus;

/**
 * NAN Response and Asynchronous Event Callbacks.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness Networking
 * (NAN) Technical Specification".
 */
oneway interface ISupplicantNanIfaceEventCallback {
    /**
     * Callback invoked in response to a capability request
     * |ISupplicantNanIface.getCapabilitiesRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *     |NanStatusCode.SUCCESS|
     * @param capabilities Capability data.
     */
    void notifyCapabilitiesResponse(
        in char id, in NanStatus status, in NanCapabilities capabilities);
}
