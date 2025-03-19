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

package android.system.wifi.mainline_supplicant;

/**
 * Enum values indicating the status of any supplicant operation.
 */
enum SupplicantStatusCode {
    /**
     * No errors.
     */
    SUCCESS = 0,
    /**
     * Unknown failure occurred.
     */
    FAILURE_UNKNOWN = 1,
    /**
     * One of the provided arguments is invalid.
     */
    FAILURE_ARGS_INVALID = 2,
    /**
     * Interface with the provided name already exists.
     */
    FAILURE_IFACE_EXISTS = 3,
    /**
     * Interface with the provided name does not exist.
     */
    FAILURE_IFACE_UNKNOWN = 4,
}
