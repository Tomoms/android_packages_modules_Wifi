/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.wifi;

import static com.android.testutils.MiscAsserts.assertFieldCountEquals;
import static com.android.testutils.ParcelUtils.assertParcelSane;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.net.MacAddress;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.WifiClient}.
 */
@SmallTest
public class WifiClientTest {
    private static final String INTERFACE_NAME = "wlan0";
    private static final String INTERFACE_NAME_1 = "wlan1";
    private static final String MAC_ADDRESS_STRING = "00:0a:95:9d:68:16";
    private static final MacAddress MAC_ADDRESS = MacAddress.fromString(MAC_ADDRESS_STRING);
    private static final int DISCONNECT_REASON = DeauthenticationReasonCode.REASON_DEAUTH_LEAVING;

    /**
     *  Verify parcel write/read with WifiClient.
     */
    @Test
    public void testWifiClientParcelWriteRead() throws Exception {
        WifiClient writeWifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME, DISCONNECT_REASON);

        assertParcelSane(writeWifiClient, 3);
    }

    /**
     *  Verify equals with WifiClient.
     */
    @Test
    public void testWifiClientEquals() throws Exception {
        WifiClient writeWifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME, DISCONNECT_REASON);
        WifiClient writeWifiClientEquals = new WifiClient(MAC_ADDRESS, INTERFACE_NAME,
                DISCONNECT_REASON);

        assertEquals(writeWifiClient, writeWifiClientEquals);
        assertEquals(writeWifiClient.hashCode(), writeWifiClientEquals.hashCode());
        assertFieldCountEquals(3, WifiClient.class);
    }

    /**
     *  Verify not-equals for 2 WifiClients with different mac address.
     */
    @Test
    public void testWifiClientEqualsFailsWhenMacAddressIsDifferent() throws Exception {
        final MacAddress macAddressNotEquals = MacAddress.fromString("00:00:00:00:00:00");
        WifiClient writeWifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME);
        WifiClient writeWifiClientNotEquals = new WifiClient(macAddressNotEquals, INTERFACE_NAME);

        assertNotEquals(writeWifiClient, writeWifiClientNotEquals);
        assertNotEquals(writeWifiClient.hashCode(), writeWifiClientNotEquals.hashCode());
    }

    /**
     * Verify not-equals for 2 WifiClients with different interface name.
     */
    @Test
    public void testWifiClientEqualsFailsWhenInstanceIsDifferent() throws Exception {
        WifiClient writeWifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME);
        WifiClient writeWifiClientNotEquals = new WifiClient(MAC_ADDRESS, INTERFACE_NAME_1);

        assertNotEquals(writeWifiClient, writeWifiClientNotEquals);
        assertNotEquals(writeWifiClient.hashCode(), writeWifiClientNotEquals.hashCode());
    }

    /**
     * Verify not-equals for 2 WifiClients with different disconnect reason.
     */
    @Test
    public void testWifiClientEqualsFailsWhenDisconnectReasonIsDifferent() throws Exception {
        WifiClient writeWifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME, DISCONNECT_REASON);
        WifiClient writeWifiClientNotEquals = new WifiClient(MAC_ADDRESS, INTERFACE_NAME,
                DeauthenticationReasonCode.REASON_AKMP_NOT_VALID);

        assertNotEquals(writeWifiClient, writeWifiClientNotEquals);
        assertNotEquals(writeWifiClient.hashCode(), writeWifiClientNotEquals.hashCode());
    }

    /**
     * Verify that getDisconnectReason() returns REASON_UNKNOWN as the default value.
     */
    @Test
    public void testWifiClientGetDefaultDisconnectReason() throws Exception {
        WifiClient wifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME);
        assertEquals(wifiClient.getDisconnectReason(), DeauthenticationReasonCode.REASON_UNKNOWN);
    }

    /**
     * Verify that all getter methods in WifiClient (getMacAddress(),
     * getApInstanceIdentifier(), getDisconnectReason()) return the
     * expected values when a WifiClient object is created with specific data.
     */
    @Test
    public void testWifiClientGetMethods() throws Exception {
        WifiClient wifiClient = new WifiClient(MAC_ADDRESS, INTERFACE_NAME, DISCONNECT_REASON);
        assertEquals(wifiClient.getMacAddress(), MAC_ADDRESS);
        assertEquals(wifiClient.getApInstanceIdentifier(), INTERFACE_NAME);
        assertEquals(wifiClient.getDisconnectReason(), DISCONNECT_REASON);
    }
}
