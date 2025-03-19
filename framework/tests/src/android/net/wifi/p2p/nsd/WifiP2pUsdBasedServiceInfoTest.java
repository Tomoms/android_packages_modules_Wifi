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

package android.net.wifi.p2p.nsd;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.util.Environment;

import androidx.test.filters.SmallTest;

import org.junit.Test;
/**
 * Unit tests for creating {@link WifiP2pServiceInfo} with
 * {@link WifiP2pUsdBasedServiceConfig}.
 */
@SmallTest
public final class WifiP2pUsdBasedServiceInfoTest {
    private static final String TEST_USD_SERVICE_NAME = "test_service_name";
    private static final int TEST_USD_PROTOCOL_TYPE = 4;
    private static final byte[] TEST_USD_SERVICE_SPECIFIC_INFO = {10, 20, 30, 40, 50, 60};
    @Test
    public void testWifiP2pUsdBasedServiceInfo() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceConfig expectedUsdConfig = new WifiP2pUsdBasedServiceConfig.Builder(
                TEST_USD_SERVICE_NAME)
                .setServiceProtocolType(TEST_USD_PROTOCOL_TYPE)
                .setServiceSpecificInfo(TEST_USD_SERVICE_SPECIFIC_INFO).build();
        WifiP2pServiceInfo serviceInfo = new WifiP2pServiceInfo(expectedUsdConfig);
        assertNotNull(serviceInfo);
        WifiP2pUsdBasedServiceConfig usdConfig =
                serviceInfo.getWifiP2pUsdBasedServiceConfig();
        assertNotNull(usdConfig);
        assertEquals(TEST_USD_SERVICE_NAME, usdConfig.getServiceName());
        assertEquals(TEST_USD_PROTOCOL_TYPE, usdConfig.getServiceProtocolType());
        assertArrayEquals(TEST_USD_SERVICE_SPECIFIC_INFO, usdConfig.getServiceSpecificInfo());
    }
}
