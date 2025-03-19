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
package android.net.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class BlockingOptionTest {

    @Test
    public void testBuilderWithValidInput() {
        BlockingOption option = new BlockingOption.Builder(100)
                .setBlockingBssidOnly(true)
                .build();
        assertEquals(100, option.getBlockingTimeSeconds());
        assertTrue(option.isBlockingBssidOnly());
    }

    @Test
    public void testBuilderWithInValidInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BlockingOption.Builder(0)
                    .setBlockingBssidOnly(true)
                    .build();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new BlockingOption.Builder(1000000)
                    .setBlockingBssidOnly(true)
                    .build();
        });
    }

    @Test
    public void testParcel() {
        BlockingOption option = new BlockingOption.Builder(100)
                .setBlockingBssidOnly(true)
                .build();
        Parcel parcelW = Parcel.obtain();
        option.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        BlockingOption parcelOption = BlockingOption.CREATOR.createFromParcel(parcelR);
        assertEquals(option, parcelOption);
    }
}
