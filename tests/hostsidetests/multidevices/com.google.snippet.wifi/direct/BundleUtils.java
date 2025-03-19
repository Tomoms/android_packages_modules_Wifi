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
package com.google.snippet.wifi.direct;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * The class for converting Wi-Fi p2p classes to bundles.
 *
 * This is for passing them to Mobly snippet events.
 */
public class BundleUtils {

    /**
     * Convert a WifiP2pDevice obeject to a bundle.
     * @param device The object to be converted.
     * @return The bundle.
     */
    public static Bundle fromWifiP2pDevice(WifiP2pDevice device) {
        Bundle bundle = new Bundle();
        bundle.putString("deviceAddress", device.deviceAddress);
        bundle.putString("deviceName", device.deviceName);
        bundle.putString("primaryDeviceType", device.primaryDeviceType);
        bundle.putString("secondaryDeviceType", device.secondaryDeviceType);
        bundle.putInt("status", device.status);
        bundle.putBoolean("isGroupOwner", device.isGroupOwner());
        return bundle;
    }

    /**
     * Convert a WifiP2pDeviceList obeject to a bundle array.
     * @param deviceList The object to be converted.
     * @return The bundle array.
     */
    public static ArrayList<Bundle> fromWifiP2pDeviceList(WifiP2pDeviceList deviceList) {
        Collection<WifiP2pDevice> devices = deviceList.getDeviceList();
        ArrayList<Bundle> bundles = new ArrayList<Bundle>();
        Iterator<WifiP2pDevice> i = devices.iterator();
        while (i.hasNext()) {
            bundles.add(BundleUtils.fromWifiP2pDevice(i.next()));
        }
        return bundles;
    }

    /**
     * Convert a WifiP2pInfo object to a bundle.
     * @param info The object to be converted.
     * @return The bundle.
     */
    public static Bundle fromWifiP2pInfo(WifiP2pInfo info) {
        if (info == null) {
            return null;
        }
        Bundle bundle = new Bundle();
        String ownerAddress = null;
        if (info.groupOwnerAddress != null) {
            ownerAddress = info.groupOwnerAddress.getHostAddress();
        }
        bundle.putBoolean("groupFormed", info.groupFormed);
        bundle.putString("groupOwnerAddress", ownerAddress);
        bundle.putBoolean("isGroupOwner", info.isGroupOwner);
        return bundle;
    }

    /**
     * Convert a WifiP2pGroup object to a bundle.
     * @param group The object to be converted.
     * @return The bundle.
     */
    public static Bundle fromWifiP2pGroup(WifiP2pGroup group) {
        if (group == null) {
            return null;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("frequency", group.getFrequency());
        bundle.putString("interface", group.getInterface());
        bundle.putInt("networkId", group.getNetworkId());
        bundle.putString("networkName", group.getNetworkName());
        bundle.putBundle("owner", fromWifiP2pDevice(group.getOwner()));
        bundle.putString("passphrase", group.getPassphrase());
        bundle.putBoolean("isGroupOwner", group.isGroupOwner());
        return bundle;
    }

    /**
     * Convert a WifiP2pGroupList object to a bundle array.
     * @param groupList The object to be converted
     * @return The bundle array.
     */
    public static ArrayList<Bundle> fromWifiP2pGroupList(WifiP2pGroupList groupList) {
        Collection<WifiP2pGroup> groups = groupList.getGroupList();
        ArrayList<Bundle> bundles = new ArrayList<Bundle>();
        Iterator<WifiP2pGroup> i = groups.iterator();
        while (i.hasNext()) {
            bundles.add(BundleUtils.fromWifiP2pGroup(i.next()));
        }
        return bundles;
    }
}
