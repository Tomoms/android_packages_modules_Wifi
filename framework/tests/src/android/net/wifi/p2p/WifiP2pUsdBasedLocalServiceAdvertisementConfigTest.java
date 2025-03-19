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

package android.net.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.util.Environment;

import androidx.test.filters.SmallTest;

import org.junit.Test;
/**
 * Unit tests for {@link WifiP2pUsdBasedLocalServiceAdvertisementConfigTest}
 */
@SmallTest
public final class WifiP2pUsdBasedLocalServiceAdvertisementConfigTest {
    private static final int TEST_USD_DISCOVERY_CHANNEL_FREQUENCY_MHZ = 2412;
    @Test
    public void testWifiP2pUsdBasedLocalServiceAdvertisementConfig() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedLocalServiceAdvertisementConfig localServiceAdvertisementConfig =
                new WifiP2pUsdBasedLocalServiceAdvertisementConfig.Builder()
                        .setFrequencyMhz(TEST_USD_DISCOVERY_CHANNEL_FREQUENCY_MHZ).build();
        assertNotNull(localServiceAdvertisementConfig);
        assertEquals(TEST_USD_DISCOVERY_CHANNEL_FREQUENCY_MHZ,
                localServiceAdvertisementConfig.getFrequencyMhz());
    }
}
