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

import android.system.wifi.mainline_supplicant.ISupplicantNanIfaceEventCallback;

/**
 * Interface used to represent a single NAN (Neighbour Aware Network) iface.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness Networking
 * (NAN) Technical Specification".
 */
interface ISupplicantNanIface {
    /**
     * Requests notifications of significant events on this iface. Multiple calls to this must
     * register multiple callbacks, each of which must receive all events.
     *
     * @param callback An instance of the |ISupplicantNanIfaceEventCallback| AIDL interface
     * object.
     * @throws ServiceSpecificException with one of the following values:
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|
     */
    void registerEventCallback(in ISupplicantNanIfaceEventCallback callback);

    /**
     * Get NAN capabilities. Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyCapabilitiesResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @throws ServiceSpecificException with one of the following values:
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    void getCapabilitiesRequest(in char cmdId);
}
