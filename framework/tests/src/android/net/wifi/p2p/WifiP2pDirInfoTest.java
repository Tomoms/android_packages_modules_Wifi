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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.net.MacAddress;
import android.net.wifi.util.Environment;

import androidx.test.filters.SmallTest;

import org.junit.Test;
/**
 * Unit tests for {@link WifiP2pDirInfo}
 */
@SmallTest
public final class WifiP2pDirInfoTest {
    private static final String TEST_MAC_ADDRESS_STRING = "00:11:22:33:44:55";
    private static final byte[] TEST_NONCE = {10, 20, 30, 40, 50, 60, 70, 80};
    private static final byte[] TEST_DIR_TAG = {11, 22, 33, 44, 55, 66, 77, 88};
    @Test
    public void testWifiP2pDirInfo() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pDirInfo dirInfo = new WifiP2pDirInfo(
                MacAddress.fromString(TEST_MAC_ADDRESS_STRING), TEST_NONCE, TEST_DIR_TAG);
        assertNotNull(dirInfo);
        assertEquals(MacAddress.fromString(TEST_MAC_ADDRESS_STRING), dirInfo.getMacAddress());
        assertArrayEquals(TEST_NONCE, dirInfo.getNonce());
        assertArrayEquals(TEST_DIR_TAG, dirInfo.getDirTag());
    }
}
