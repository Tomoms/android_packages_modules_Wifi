/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net.wifi.wificond;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.test.TestAlarmManager;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiScanner;
import android.net.wifi.util.HexEncoding;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link android.net.wifi.WifiCondManager}.
 */
@SmallTest
public class WifiCondManagerTest {
    @Mock
    private IWificond mWificond;
    @Mock
    private IBinder mWifiCondBinder;
    @Mock
    private IClientInterface mClientInterface;
    @Mock
    private IWifiScannerImpl mWifiScannerImpl;
    @Mock
    private IApInterface mApInterface;
    @Mock
    private WifiCondManager.SoftApCallback mSoftApListener;
    @Mock
    private WifiCondManager.SendMgmtFrameCallback mSendMgmtFrameCallback;
    @Mock
    private WifiCondManager.ScanEventCallback mNormalScanCallback;
    @Mock
    private WifiCondManager.ScanEventCallback mPnoScanCallback;
    @Mock
    private WifiCondManager.PnoScanRequestCallback mPnoScanRequestCallback;
    @Mock
    private Context mContext;
    private TestLooper mLooper;
    private TestAlarmManager mTestAlarmManager;
    private AlarmManager mAlarmManager;
    private WifiCondManager mWificondControl;
    private static final String TEST_INTERFACE_NAME = "test_wlan_if";
    private static final String TEST_INTERFACE_NAME1 = "test_wlan_if1";
    private static final String TEST_INVALID_INTERFACE_NAME = "asdf";
    private static final byte[] TEST_SSID =
            new byte[]{'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    private static final byte[] TEST_PSK =
            new byte[]{'T', 'e', 's', 't'};

    private static final Set<Integer> SCAN_FREQ_SET =
            new HashSet<Integer>() {{
                add(2410);
                add(2450);
                add(5050);
                add(5200);
            }};
    private static final String TEST_QUOTED_SSID_1 = "\"testSsid1\"";
    private static final String TEST_QUOTED_SSID_2 = "\"testSsid2\"";
    private static final int[] TEST_FREQUENCIES_1 = {};
    private static final int[] TEST_FREQUENCIES_2 = {2500, 5124};
    private static final byte[] TEST_RAW_MAC_BYTES = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05};

    private static final List<byte[]> SCAN_HIDDEN_NETWORK_SSID_LIST =
            new ArrayList<byte[]>() {{
                add(LocalNativeUtil.byteArrayFromArrayList(
                        LocalNativeUtil.decodeSsid(TEST_QUOTED_SSID_1)));
                add(LocalNativeUtil.byteArrayFromArrayList(
                        LocalNativeUtil.decodeSsid(TEST_QUOTED_SSID_2)));
            }};

    private static final PnoSettings TEST_PNO_SETTINGS = new PnoSettings();
    static {
        TEST_PNO_SETTINGS.setIntervalMillis(6000);
        List<PnoNetwork> initPnoNetworks = new ArrayList<>();
        PnoNetwork network = new PnoNetwork();
        network.setSsid(LocalNativeUtil.byteArrayFromArrayList(
                LocalNativeUtil.decodeSsid(TEST_QUOTED_SSID_1)));
        network.setHidden(true);
        network.setFrequenciesMhz(TEST_FREQUENCIES_1);
        initPnoNetworks.add(network);
        network.setSsid(LocalNativeUtil.byteArrayFromArrayList(
                LocalNativeUtil.decodeSsid(TEST_QUOTED_SSID_2)));
        network.setHidden(false);
        network.setFrequenciesMhz(TEST_FREQUENCIES_2);
        initPnoNetworks.add(network);
        TEST_PNO_SETTINGS.setPnoNetworks(initPnoNetworks);
    }

    private static final int TEST_MCS_RATE = 5;
    private static final int TEST_SEND_MGMT_FRAME_ELAPSED_TIME_MS = 100;
    private static final byte[] TEST_PROBE_FRAME = {
            0x40, 0x00, 0x3c, 0x00, (byte) 0xa8, (byte) 0xbd, 0x27, 0x5b,
            0x33, 0x72, (byte) 0xf4, (byte) 0xf5, (byte) 0xe8, 0x51, (byte) 0x9e, 0x09,
            (byte) 0xa8, (byte) 0xbd, 0x27, 0x5b, 0x33, 0x72, (byte) 0xb0, 0x66,
            0x00, 0x00
    };

    @Before
    public void setUp() throws Exception {
        // Setup mocks for successful WificondControl operation. Failure case mocks should be
        // created in specific tests
        MockitoAnnotations.initMocks(this);

        mTestAlarmManager = new TestAlarmManager();
        mAlarmManager = mTestAlarmManager.getAlarmManager();
        when(mContext.getSystemServiceName(AlarmManager.class)).thenReturn(Context.ALARM_SERVICE);
        when(mContext.getSystemService(AlarmManager.class)).thenReturn(mAlarmManager);

        mLooper = new TestLooper();
        when(mContext.getMainLooper()).thenReturn(mLooper.getLooper());

        when(mWificond.asBinder()).thenReturn(mWifiCondBinder);
        when(mClientInterface.getWifiScannerImpl()).thenReturn(mWifiScannerImpl);
        when(mWificond.createClientInterface(any())).thenReturn(mClientInterface);
        when(mWificond.createApInterface(any())).thenReturn(mApInterface);
        when(mWificond.tearDownClientInterface(any())).thenReturn(true);
        when(mWificond.tearDownApInterface(any())).thenReturn(true);
        when(mClientInterface.getWifiScannerImpl()).thenReturn(mWifiScannerImpl);
        when(mClientInterface.getInterfaceName()).thenReturn(TEST_INTERFACE_NAME);
        mWificondControl = new WifiCondManager(mContext, mWificond);
        assertEquals(true,
                mWificondControl.setupInterfaceForClientMode(TEST_INTERFACE_NAME, Runnable::run,
                        mNormalScanCallback, mPnoScanCallback));
    }

    /**
     * Verifies that setupInterfaceForClientMode(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testSetupInterfaceForClientMode() throws Exception {
        when(mWificond.createClientInterface(TEST_INTERFACE_NAME)).thenReturn(mClientInterface);
        verify(mWificond).createClientInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that setupInterfaceForClientMode(TEST_INTERFACE_NAME) calls subscribeScanEvents().
     */
    @Test
    public void testSetupInterfaceForClientModeCallsScanEventSubscripiton() throws Exception {
        verify(mWifiScannerImpl).subscribeScanEvents(any(IScanEvent.class));
    }

    /**
     * Verifies that tearDownClientInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownClientInterface() throws Exception {
        when(mWificond.tearDownClientInterface(TEST_INTERFACE_NAME)).thenReturn(true);

        assertTrue(mWificondControl.tearDownClientInterface(TEST_INTERFACE_NAME));
        verify(mWifiScannerImpl).unsubscribeScanEvents();
        verify(mWifiScannerImpl).unsubscribePnoScanEvents();
        verify(mWificond).tearDownClientInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that tearDownClientInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownClientInterfaceOnInvalidIface() throws Exception {
        when(mWificond.tearDownClientInterface(TEST_INTERFACE_NAME1)).thenReturn(true);

        assertFalse(mWificondControl.tearDownClientInterface(TEST_INTERFACE_NAME1));
        verify(mWifiScannerImpl, never()).unsubscribeScanEvents();
        verify(mWifiScannerImpl, never()).unsubscribePnoScanEvents();
        verify(mWificond, never()).tearDownClientInterface(any());
    }

    /**
     * Verifies that tearDownClientInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownClientInterfaceFailDueToExceptionScannerUnsubscribe() throws Exception {
        when(mWificond.tearDownClientInterface(TEST_INTERFACE_NAME)).thenReturn(true);
        doThrow(new RemoteException()).when(mWifiScannerImpl).unsubscribeScanEvents();

        assertFalse(mWificondControl.tearDownClientInterface(TEST_INTERFACE_NAME));
        verify(mWifiScannerImpl).unsubscribeScanEvents();
        verify(mWifiScannerImpl, never()).unsubscribePnoScanEvents();
        verify(mWificond, never()).tearDownClientInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that tearDownClientInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownClientInterfaceErrorWhenWificondFailed() throws Exception {
        when(mWificond.tearDownClientInterface(TEST_INTERFACE_NAME)).thenReturn(false);

        assertFalse(mWificondControl.tearDownClientInterface(TEST_INTERFACE_NAME));
        verify(mWifiScannerImpl).unsubscribeScanEvents();
        verify(mWifiScannerImpl).unsubscribePnoScanEvents();
        verify(mWificond).tearDownClientInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that the client handles are cleared after teardown.
     */
    @Test
    public void testTeardownClientInterfaceClearsHandles() throws Exception {
        testTeardownClientInterface();

        assertNull(mWificondControl.signalPoll(TEST_INTERFACE_NAME));
        verify(mClientInterface, never()).signalPoll();

        assertFalse(mWificondControl.startScan(
                TEST_INTERFACE_NAME, WifiScanner.SCAN_TYPE_LOW_LATENCY,
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_LIST));
        verify(mWifiScannerImpl, never()).scan(any());
    }

    /**
     * Verifies that setupInterfaceForSoftApMode(TEST_INTERFACE_NAME) calls wificond.
     */
    @Test
    public void testSetupInterfaceForSoftApMode() throws Exception {
        when(mWificond.createApInterface(TEST_INTERFACE_NAME)).thenReturn(mApInterface);

        assertEquals(true, mWificondControl.setupInterfaceForSoftApMode(TEST_INTERFACE_NAME));
        verify(mWificond).createApInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that setupInterfaceForSoftAp() returns null when wificond is not started.
     */
    @Test
    public void testSetupInterfaceForSoftApModeErrorWhenWificondIsNotStarted() throws Exception {
        // Invoke wificond death handler to clear the handle.
        mWificondControl.binderDied();
        mLooper.dispatchAll();

        assertEquals(false, mWificondControl.setupInterfaceForSoftApMode(TEST_INTERFACE_NAME));
    }

    /**
     * Verifies that setupInterfaceForSoftApMode(TEST_INTERFACE_NAME) returns null when wificond
     * failed to setup AP interface.
     */
    @Test
    public void testSetupInterfaceForSoftApModeErrorWhenWificondFailedToSetupInterface()
            throws Exception {
        when(mWificond.createApInterface(TEST_INTERFACE_NAME)).thenReturn(null);

        assertEquals(false, mWificondControl.setupInterfaceForSoftApMode(TEST_INTERFACE_NAME));
    }

    /**
     * Verifies that tearDownClientInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownSoftApInterface() throws Exception {
        testSetupInterfaceForSoftApMode();
        when(mWificond.tearDownApInterface(TEST_INTERFACE_NAME)).thenReturn(true);

        assertTrue(mWificondControl.tearDownSoftApInterface(TEST_INTERFACE_NAME));
        verify(mWificond).tearDownApInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that tearDownSoftapInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownSoftApInterfaceOnInvalidIface() throws Exception {
        testSetupInterfaceForSoftApMode();
        when(mWificond.tearDownApInterface(TEST_INTERFACE_NAME1)).thenReturn(true);

        assertFalse(mWificondControl.tearDownSoftApInterface(TEST_INTERFACE_NAME1));
        verify(mWificond, never()).tearDownApInterface(any());
    }

    /**
     * Verifies that tearDownClientInterface(TEST_INTERFACE_NAME) calls Wificond.
     */
    @Test
    public void testTeardownSoftApInterfaceErrorWhenWificondFailed() throws Exception {
        testSetupInterfaceForSoftApMode();
        when(mWificond.tearDownApInterface(TEST_INTERFACE_NAME)).thenReturn(false);

        assertFalse(mWificondControl.tearDownSoftApInterface(TEST_INTERFACE_NAME));
        verify(mWificond).tearDownApInterface(TEST_INTERFACE_NAME);
    }

    /**
     * Verifies that the SoftAp handles are cleared after teardown.
     */
    @Test
    public void testTeardownSoftApInterfaceClearsHandles() throws Exception {
        testTeardownSoftApInterface();

        assertFalse(mWificondControl.registerApCallback(
                TEST_INTERFACE_NAME, Runnable::run, mSoftApListener));
        verify(mApInterface, never()).registerCallback(any());
    }

    /**
     * Verifies that we can setup concurrent interfaces.
     */
    @Test
    public void testSetupMultipleInterfaces() throws Exception {
        when(mWificond.createApInterface(TEST_INTERFACE_NAME1)).thenReturn(mApInterface);

        assertEquals(true, mWificondControl.setupInterfaceForSoftApMode(TEST_INTERFACE_NAME1));

        verify(mWificond).createClientInterface(TEST_INTERFACE_NAME);
        verify(mWificond).createApInterface(TEST_INTERFACE_NAME1);
    }

    /**
     * Verifies that we can setup concurrent interfaces.
     */
    @Test
    public void testTeardownMultipleInterfaces() throws Exception {
        testSetupMultipleInterfaces();
        assertTrue(mWificondControl.tearDownClientInterface(TEST_INTERFACE_NAME));
        assertTrue(mWificondControl.tearDownSoftApInterface(TEST_INTERFACE_NAME1));

        verify(mWificond).tearDownClientInterface(TEST_INTERFACE_NAME);
        verify(mWificond).tearDownApInterface(TEST_INTERFACE_NAME1);
    }

    /**
     * Verifies that tearDownInterfaces() calls wificond.
     */
    @Test
    public void testTearDownInterfaces() throws Exception {
        assertTrue(mWificondControl.tearDownInterfaces());
        verify(mWificond).tearDownInterfaces();
    }

    /**
     * Verifies that tearDownInterfaces() calls unsubscribeScanEvents() when there was
     * a configured client interface.
     */
    @Test
    public void testTearDownInterfacesRemovesScanEventSubscription() throws Exception {
        assertTrue(mWificondControl.tearDownInterfaces());
        verify(mWifiScannerImpl).unsubscribeScanEvents();
    }

    /**
     * Verifies that tearDownInterfaces() returns false when wificond is not started.
     */
    @Test
    public void testTearDownInterfacesErrorWhenWificondIsNotStarterd() throws Exception {
        // Invoke wificond death handler to clear the handle.
        mWificondControl.binderDied();
        mLooper.dispatchAll();
        assertFalse(mWificondControl.tearDownInterfaces());
    }

    /**
     * Verifies that signalPoll() calls wificond.
     */
    @Test
    public void testSignalPoll() throws Exception {
        when(mWificond.createClientInterface(TEST_INTERFACE_NAME)).thenReturn(mClientInterface);

        mWificondControl.setupInterfaceForClientMode(TEST_INTERFACE_NAME, Runnable::run,
                mNormalScanCallback, mPnoScanCallback);
        mWificondControl.signalPoll(TEST_INTERFACE_NAME);
        verify(mClientInterface).signalPoll();
    }

    /**
     * Verifies that signalPoll() returns null when there is no configured client interface.
     */
    @Test
    public void testSignalPollErrorWhenNoClientInterfaceConfigured() throws Exception {
        when(mWificond.createClientInterface(TEST_INTERFACE_NAME)).thenReturn(mClientInterface);

        // Configure client interface.
        assertEquals(true, mWificondControl.setupInterfaceForClientMode(TEST_INTERFACE_NAME,
                Runnable::run, mNormalScanCallback, mPnoScanCallback));

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Signal poll should fail.
        assertEquals(null, mWificondControl.signalPoll(TEST_INTERFACE_NAME));
    }

    /**
     * Verifies that getTxPacketCounters() calls wificond.
     */
    @Test
    public void testGetTxPacketCounters() throws Exception {
        when(mWificond.createClientInterface(TEST_INTERFACE_NAME)).thenReturn(mClientInterface);

        mWificondControl.setupInterfaceForClientMode(TEST_INTERFACE_NAME, Runnable::run,
                mNormalScanCallback, mPnoScanCallback);
        mWificondControl.getTxPacketCounters(TEST_INTERFACE_NAME);
        verify(mClientInterface).getPacketCounters();
    }

    /**
     * Verifies that getTxPacketCounters() returns null when there is no configured client
     * interface.
     */
    @Test
    public void testGetTxPacketCountersErrorWhenNoClientInterfaceConfigured() throws Exception {
        when(mWificond.createClientInterface(TEST_INTERFACE_NAME)).thenReturn(mClientInterface);

        // Configure client interface.
        assertEquals(true, mWificondControl.setupInterfaceForClientMode(TEST_INTERFACE_NAME,
                Runnable::run, mNormalScanCallback, mPnoScanCallback));

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Signal poll should fail.
        assertEquals(null, mWificondControl.getTxPacketCounters(TEST_INTERFACE_NAME));
    }

    /**
     * Verifies that getScanResults() returns null when there is no configured client
     * interface.
     */
    @Test
    public void testGetScanResultsErrorWhenNoClientInterfaceConfigured() throws Exception {
        when(mWificond.createClientInterface(TEST_INTERFACE_NAME)).thenReturn(mClientInterface);

        // Configure client interface.
        assertEquals(true, mWificondControl.setupInterfaceForClientMode(TEST_INTERFACE_NAME,
                Runnable::run, mNormalScanCallback, mPnoScanCallback));

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // getScanResults should fail.
        assertEquals(0,
                mWificondControl.getScanResults(TEST_INTERFACE_NAME,
                        WifiCondManager.SCAN_TYPE_SINGLE_SCAN).size());
    }

    /**
     * Verifies that Scan() can convert input parameters to SingleScanSettings correctly.
     */
    @Test
    public void testScan() throws Exception {
        when(mWifiScannerImpl.scan(any(SingleScanSettings.class))).thenReturn(true);
        assertTrue(mWificondControl.startScan(
                TEST_INTERFACE_NAME, WifiScanner.SCAN_TYPE_LOW_POWER,
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_LIST));
        verify(mWifiScannerImpl).scan(argThat(new ScanMatcher(
                IWifiScannerImpl.SCAN_TYPE_LOW_POWER,
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_LIST)));
    }

    /**
     * Verifies that Scan() removes duplicates hiddenSsids passed in from input.
     */
    @Test
    public void testScanWithDuplicateHiddenSsids() throws Exception {
        when(mWifiScannerImpl.scan(any(SingleScanSettings.class))).thenReturn(true);
        // Create a list of hiddenSsid that has a duplicate element
        List<byte[]> hiddenSsidWithDup = new ArrayList<>(SCAN_HIDDEN_NETWORK_SSID_LIST);
        hiddenSsidWithDup.add(SCAN_HIDDEN_NETWORK_SSID_LIST.get(0));
        assertEquals(hiddenSsidWithDup.get(0),
                hiddenSsidWithDup.get(hiddenSsidWithDup.size() - 1));
        // Pass the List with duplicate elements into scan()
        assertTrue(mWificondControl.startScan(
                TEST_INTERFACE_NAME, WifiScanner.SCAN_TYPE_LOW_POWER,
                SCAN_FREQ_SET, hiddenSsidWithDup));
        // But the argument passed down should have the duplicate removed.
        verify(mWifiScannerImpl).scan(argThat(new ScanMatcher(
                IWifiScannerImpl.SCAN_TYPE_LOW_POWER,
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_LIST)));
    }

    /**
     * Verifies that Scan() can handle null input parameters correctly.
     */
    @Test
    public void testScanNullParameters() throws Exception {
        when(mWifiScannerImpl.scan(any(SingleScanSettings.class))).thenReturn(true);
        assertTrue(mWificondControl.startScan(
                TEST_INTERFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null, null));
        verify(mWifiScannerImpl).scan(argThat(new ScanMatcher(
                IWifiScannerImpl.SCAN_TYPE_HIGH_ACCURACY, null, null)));
    }

    /**
     * Verifies that Scan() can handle wificond scan failure.
     */
    @Test
    public void testScanFailure() throws Exception {
        when(mWifiScannerImpl.scan(any(SingleScanSettings.class))).thenReturn(false);
        assertFalse(mWificondControl.startScan(
                TEST_INTERFACE_NAME, WifiScanner.SCAN_TYPE_LOW_LATENCY,
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_LIST));
        verify(mWifiScannerImpl).scan(any(SingleScanSettings.class));
    }

    /**
     * Verifies that Scan() can handle invalid type.
     */
    @Test
    public void testScanFailureDueToInvalidType() throws Exception {
        assertFalse(mWificondControl.startScan(
                TEST_INTERFACE_NAME, 100,
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_LIST));
        verify(mWifiScannerImpl, never()).scan(any(SingleScanSettings.class));
    }

    /**
     * Verifies that startPnoScan() can convert input parameters to PnoSettings correctly.
     */
    @Test
    public void testStartPnoScan() throws Exception {
        when(mWifiScannerImpl.startPnoScan(any(PnoSettings.class))).thenReturn(true);
        assertTrue(
                mWificondControl.startPnoScan(TEST_INTERFACE_NAME, TEST_PNO_SETTINGS, Runnable::run,
                        mPnoScanRequestCallback));
        verify(mWifiScannerImpl).startPnoScan(eq(TEST_PNO_SETTINGS));
        verify(mPnoScanRequestCallback).onPnoRequestSucceeded();
    }

    /**
     * Verifies that stopPnoScan() calls underlying wificond.
     */
    @Test
    public void testStopPnoScan() throws Exception {
        when(mWifiScannerImpl.stopPnoScan()).thenReturn(true);
        assertTrue(mWificondControl.stopPnoScan(TEST_INTERFACE_NAME));
        verify(mWifiScannerImpl).stopPnoScan();
    }

    /**
     * Verifies that stopPnoScan() can handle wificond failure.
     */
    @Test
    public void testStopPnoScanFailure() throws Exception {

        when(mWifiScannerImpl.stopPnoScan()).thenReturn(false);
        assertFalse(mWificondControl.stopPnoScan(TEST_INTERFACE_NAME));
        verify(mWifiScannerImpl).stopPnoScan();
    }

    /**
     * Verifies that WificondControl can invoke WifiMonitor broadcast methods upon scan
     * result event.
     */
    @Test
    public void testScanResultEvent() throws Exception {
        ArgumentCaptor<IScanEvent> messageCaptor = ArgumentCaptor.forClass(IScanEvent.class);
        verify(mWifiScannerImpl).subscribeScanEvents(messageCaptor.capture());
        IScanEvent scanEvent = messageCaptor.getValue();
        assertNotNull(scanEvent);
        scanEvent.OnScanResultReady();

        verify(mNormalScanCallback).onScanResultReady();
    }

    /**
     * Verifies that WificondControl can invoke WifiMonitor broadcast methods upon scan
     * failed event.
     */
    @Test
    public void testScanFailedEvent() throws Exception {
        ArgumentCaptor<IScanEvent> messageCaptor = ArgumentCaptor.forClass(IScanEvent.class);
        verify(mWifiScannerImpl).subscribeScanEvents(messageCaptor.capture());
        IScanEvent scanEvent = messageCaptor.getValue();
        assertNotNull(scanEvent);
        scanEvent.OnScanFailed();

        verify(mNormalScanCallback).onScanFailed();
    }

    /**
     * Verifies that WificondControl can invoke WifiMonitor broadcast methods upon pno scan
     * result event.
     */
    @Test
    public void testPnoScanResultEvent() throws Exception {
        ArgumentCaptor<IPnoScanEvent> messageCaptor = ArgumentCaptor.forClass(IPnoScanEvent.class);
        verify(mWifiScannerImpl).subscribePnoScanEvents(messageCaptor.capture());
        IPnoScanEvent pnoScanEvent = messageCaptor.getValue();
        assertNotNull(pnoScanEvent);
        pnoScanEvent.OnPnoNetworkFound();
        verify(mPnoScanCallback).onScanResultReady();
    }

    /**
     * Verifies that WificondControl can invoke WifiMetrics pno scan count methods upon pno event.
     */
    @Test
    public void testPnoScanEventsForMetrics() throws Exception {
        ArgumentCaptor<IPnoScanEvent> messageCaptor = ArgumentCaptor.forClass(IPnoScanEvent.class);
        verify(mWifiScannerImpl).subscribePnoScanEvents(messageCaptor.capture());
        IPnoScanEvent pnoScanEvent = messageCaptor.getValue();
        assertNotNull(pnoScanEvent);

        pnoScanEvent.OnPnoNetworkFound();
        verify(mPnoScanCallback).onScanResultReady();

        pnoScanEvent.OnPnoScanFailed();
        verify(mPnoScanCallback).onScanFailed();
    }

    /**
     * Verifies that startPnoScan() can invoke WifiMetrics pno scan count methods correctly.
     */
    @Test
    public void testStartPnoScanForMetrics() throws Exception {
        when(mWifiScannerImpl.startPnoScan(any(PnoSettings.class))).thenReturn(false);

        assertFalse(
                mWificondControl.startPnoScan(TEST_INTERFACE_NAME, TEST_PNO_SETTINGS, Runnable::run,
                        mPnoScanRequestCallback));
        verify(mPnoScanRequestCallback).onPnoRequestFailed();
    }

    /**
     * Verifies that abortScan() calls underlying wificond.
     */
    @Test
    public void testAbortScan() throws Exception {
        mWificondControl.abortScan(TEST_INTERFACE_NAME);
        verify(mWifiScannerImpl).abortScan();
    }

    /**
     * Ensures that the Ap interface callbacks are forwarded to the
     * SoftApListener used for starting soft AP.
     */
    @Test
    public void testSoftApListenerInvocation() throws Exception {
        testSetupInterfaceForSoftApMode();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = new String(TEST_SSID, StandardCharsets.UTF_8);

        when(mApInterface.registerCallback(any())).thenReturn(true);

        final ArgumentCaptor<IApInterfaceEventCallback> apInterfaceCallbackCaptor =
                ArgumentCaptor.forClass(IApInterfaceEventCallback.class);

        assertTrue(mWificondControl.registerApCallback(
                TEST_INTERFACE_NAME, Runnable::run, mSoftApListener));
        verify(mApInterface).registerCallback(apInterfaceCallbackCaptor.capture());

        final NativeWifiClient testClient = new NativeWifiClient(TEST_RAW_MAC_BYTES);
        apInterfaceCallbackCaptor.getValue().onConnectedClientsChanged(testClient, true);
        verify(mSoftApListener).onConnectedClientsChanged(eq(testClient), eq(true));

        int channelFrequency = 2437;
        int channelBandwidth = IApInterfaceEventCallback.BANDWIDTH_20;
        apInterfaceCallbackCaptor.getValue().onSoftApChannelSwitched(channelFrequency,
                channelBandwidth);
        verify(mSoftApListener).onSoftApChannelSwitched(eq(channelFrequency),
                eq(SoftApInfo.CHANNEL_WIDTH_20MHZ));
    }

    /**
     * Verifies registration and invocation of wificond death handler.
     */
    @Test
    public void testRegisterDeathHandler() throws Exception {
        Runnable deathHandler = mock(Runnable.class);
        assertTrue(mWificondControl.initialize(deathHandler));
        verify(mWificond).tearDownInterfaces();
        mWificondControl.binderDied();
        mLooper.dispatchAll();
        verify(deathHandler).run();
    }

    /**
     * Verifies handling of wificond death and ensures that all internal state is cleared and
     * handlers are invoked.
     */
    @Test
    public void testDeathHandling() throws Exception {
        Runnable deathHandler = mock(Runnable.class);
        assertTrue(mWificondControl.initialize(deathHandler));

        testSetupInterfaceForClientMode();

        mWificondControl.binderDied();
        mLooper.dispatchAll();
        verify(deathHandler).run();

        // The handles should be cleared after death.
        assertEquals(0, mWificondControl.getChannelsMhzForBand(WifiScanner.WIFI_BAND_5_GHZ).length);
        verify(mWificond, never()).getAvailable5gNonDFSChannels();
    }

    /**
     * sendMgmtFrame() should fail if a null callback is passed in.
     */
    @Test
    public void testSendMgmtFrameNullCallback() throws Exception {
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, null);

        verify(mClientInterface, never()).SendMgmtFrame(any(), any(), anyInt());
    }

    /**
     * sendMgmtFrame() should fail if a null frame is passed in.
     */
    @Test
    public void testSendMgmtFrameNullFrame() throws Exception {
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, null, TEST_MCS_RATE, Runnable::run,
                mSendMgmtFrameCallback);

        verify(mClientInterface, never()).SendMgmtFrame(any(), any(), anyInt());
        verify(mSendMgmtFrameCallback).onFailure(anyInt());
    }

    /**
     * sendMgmtFrame() should fail if an interface name that does not exist is passed in.
     */
    @Test
    public void testSendMgmtFrameInvalidInterfaceName() throws Exception {
        mWificondControl.sendMgmtFrame(TEST_INVALID_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, mSendMgmtFrameCallback);

        verify(mClientInterface, never()).SendMgmtFrame(any(), any(), anyInt());
        verify(mSendMgmtFrameCallback).onFailure(anyInt());
    }

    /**
     * sendMgmtFrame() should fail if it is called a second time before the first call completed.
     */
    @Test
    public void testSendMgmtFrameCalledTwiceBeforeFinished() throws Exception {
        WifiCondManager.SendMgmtFrameCallback cb1 = mock(
                WifiCondManager.SendMgmtFrameCallback.class);
        WifiCondManager.SendMgmtFrameCallback cb2 = mock(
                WifiCondManager.SendMgmtFrameCallback.class);

        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, cb1);
        verify(cb1, never()).onFailure(anyInt());
        verify(mClientInterface, times(1))
                .SendMgmtFrame(AdditionalMatchers.aryEq(TEST_PROBE_FRAME),
                        any(), eq(TEST_MCS_RATE));

        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, cb2);
        verify(cb2).onFailure(WifiCondManager.SEND_MGMT_FRAME_ERROR_ALREADY_STARTED);
        // verify SendMgmtFrame() still was only called once i.e. not called again
        verify(mClientInterface, times(1))
                .SendMgmtFrame(any(), any(), anyInt());
    }

    /**
     * Tests that when a RemoteException is triggered on AIDL call, onFailure() is called only once.
     */
    @Test
    public void testSendMgmtFrameThrowsException() throws Exception {
        WifiCondManager.SendMgmtFrameCallback cb = mock(
                WifiCondManager.SendMgmtFrameCallback.class);

        final ArgumentCaptor<ISendMgmtFrameEvent> sendMgmtFrameEventCaptor =
                ArgumentCaptor.forClass(ISendMgmtFrameEvent.class);

        doThrow(new RemoteException()).when(mClientInterface)
                .SendMgmtFrame(any(), sendMgmtFrameEventCaptor.capture(), anyInt());

        final ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        doNothing().when(mAlarmManager).set(anyInt(), anyLong(), any(),
                alarmListenerCaptor.capture(), handlerCaptor.capture());

        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, cb);
        mLooper.dispatchAll();

        verify(cb).onFailure(anyInt());
        verify(mAlarmManager).cancel(eq(alarmListenerCaptor.getValue()));

        sendMgmtFrameEventCaptor.getValue().OnFailure(
                WifiCondManager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
        mLooper.dispatchAll();

        handlerCaptor.getValue().post(() -> alarmListenerCaptor.getValue().onAlarm());
        mLooper.dispatchAll();

        verifyNoMoreInteractions(cb);
    }

    /**
     * Tests that the onAck() callback is triggered correctly.
     */
    @Test
    public void testSendMgmtFrameSuccess() throws Exception {
        WifiCondManager.SendMgmtFrameCallback cb = mock(
                WifiCondManager.SendMgmtFrameCallback.class);

        final ArgumentCaptor<ISendMgmtFrameEvent> sendMgmtFrameEventCaptor =
                ArgumentCaptor.forClass(ISendMgmtFrameEvent.class);
        doNothing().when(mClientInterface)
                .SendMgmtFrame(any(), sendMgmtFrameEventCaptor.capture(), anyInt());
        final ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        doNothing().when(mAlarmManager).set(anyInt(), anyLong(), any(),
                alarmListenerCaptor.capture(), handlerCaptor.capture());
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, cb);

        sendMgmtFrameEventCaptor.getValue().OnAck(TEST_SEND_MGMT_FRAME_ELAPSED_TIME_MS);
        mLooper.dispatchAll();
        verify(cb).onAck(eq(TEST_SEND_MGMT_FRAME_ELAPSED_TIME_MS));
        verify(cb, never()).onFailure(anyInt());
        verify(mAlarmManager).cancel(eq(alarmListenerCaptor.getValue()));

        // verify that even if timeout is triggered afterwards, SendMgmtFrameCallback is not
        // triggered again
        handlerCaptor.getValue().post(() -> alarmListenerCaptor.getValue().onAlarm());
        mLooper.dispatchAll();
        verify(cb, times(1)).onAck(anyInt());
        verify(cb, never()).onFailure(anyInt());
    }

    /**
     * Tests that the onFailure() callback is triggered correctly.
     */
    @Test
    public void testSendMgmtFrameFailure() throws Exception {
        WifiCondManager.SendMgmtFrameCallback cb = mock(
                WifiCondManager.SendMgmtFrameCallback.class);

        final ArgumentCaptor<ISendMgmtFrameEvent> sendMgmtFrameEventCaptor =
                ArgumentCaptor.forClass(ISendMgmtFrameEvent.class);
        doNothing().when(mClientInterface)
                .SendMgmtFrame(any(), sendMgmtFrameEventCaptor.capture(), anyInt());
        final ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        doNothing().when(mAlarmManager).set(anyInt(), anyLong(), any(),
                alarmListenerCaptor.capture(), handlerCaptor.capture());
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, cb);

        sendMgmtFrameEventCaptor.getValue().OnFailure(
                WifiCondManager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
        mLooper.dispatchAll();
        verify(cb, never()).onAck(anyInt());
        verify(cb).onFailure(WifiCondManager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
        verify(mAlarmManager).cancel(eq(alarmListenerCaptor.getValue()));

        // verify that even if timeout is triggered afterwards, SendMgmtFrameCallback is not
        // triggered again
        handlerCaptor.getValue().post(() -> alarmListenerCaptor.getValue().onAlarm());
        mLooper.dispatchAll();
        verify(cb, never()).onAck(anyInt());
        verify(cb, times(1)).onFailure(anyInt());
    }

    /**
     * Tests that the onTimeout() callback is triggered correctly.
     */
    @Test
    public void testSendMgmtFrameTimeout() throws Exception {
        WifiCondManager.SendMgmtFrameCallback cb = mock(
                WifiCondManager.SendMgmtFrameCallback.class);

        final ArgumentCaptor<ISendMgmtFrameEvent> sendMgmtFrameEventCaptor =
                ArgumentCaptor.forClass(ISendMgmtFrameEvent.class);
        doNothing().when(mClientInterface)
                .SendMgmtFrame(any(), sendMgmtFrameEventCaptor.capture(), anyInt());
        final ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        doNothing().when(mAlarmManager).set(anyInt(), anyLong(), any(),
                alarmListenerCaptor.capture(), handlerCaptor.capture());
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, cb);

        handlerCaptor.getValue().post(() -> alarmListenerCaptor.getValue().onAlarm());
        mLooper.dispatchAll();
        verify(cb, never()).onAck(anyInt());
        verify(cb).onFailure(WifiCondManager.SEND_MGMT_FRAME_ERROR_TIMEOUT);

        // verify that even if onAck() callback is triggered after timeout,
        // SendMgmtFrameCallback is not triggered again
        sendMgmtFrameEventCaptor.getValue().OnAck(TEST_SEND_MGMT_FRAME_ELAPSED_TIME_MS);
        mLooper.dispatchAll();
        verify(cb, never()).onAck(anyInt());
        verify(cb, times(1)).onFailure(anyInt());
    }

    /**
     * Tests every possible test outcome followed by every other test outcome to ensure that the
     * internal state is reset correctly between calls.
     * i.e. (success, success), (success, failure), (success, timeout),
     * (failure, failure), (failure, success), (failure, timeout),
     * (timeout, timeout), (timeout, success), (timeout, failure)
     *
     * Also tests that internal state is reset correctly after a transient AIDL RemoteException.
     */
    @Test
    public void testSendMgmtFrameMixed() throws Exception {
        testSendMgmtFrameThrowsException();
        testSendMgmtFrameSuccess();
        testSendMgmtFrameSuccess();
        testSendMgmtFrameFailure();
        testSendMgmtFrameFailure();
        testSendMgmtFrameTimeout();
        testSendMgmtFrameTimeout();
        testSendMgmtFrameSuccess();
        testSendMgmtFrameTimeout();
        testSendMgmtFrameFailure();
        testSendMgmtFrameSuccess();
    }

    /**
     * Tests that OnAck() does not perform any non-thread-safe operations on the binder thread.
     *
     * The sequence of instructions are:
     * 1. post onAlarm() onto main thread
     * 2. OnAck()
     * 3. mLooper.dispatchAll()
     *
     * The actual order of execution is:
     * 1. binder thread portion of OnAck()
     * 2. onAlarm() (which purely executes on the main thread)
     * 3. main thread portion of OnAck()
     *
     * If the binder thread portion of OnAck() is not thread-safe, it can possibly mess up
     * onAlarm(). Tests that this does not occur.
     */
    @Test
    public void testSendMgmtFrameTimeoutAckThreadSafe() throws Exception {
        final ArgumentCaptor<ISendMgmtFrameEvent> sendMgmtFrameEventCaptor =
                ArgumentCaptor.forClass(ISendMgmtFrameEvent.class);
        doNothing().when(mClientInterface)
                .SendMgmtFrame(any(), sendMgmtFrameEventCaptor.capture(), anyInt());
        final ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        doNothing().when(mAlarmManager).set(anyInt(), anyLong(), any(),
                alarmListenerCaptor.capture(), handlerCaptor.capture());
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, mSendMgmtFrameCallback);

        // AlarmManager should post the onAlarm() callback onto the handler, but since we are
        // triggering onAlarm() ourselves during the test, manually post onto handler
        handlerCaptor.getValue().post(() -> alarmListenerCaptor.getValue().onAlarm());
        // OnAck posts to the handler
        sendMgmtFrameEventCaptor.getValue().OnAck(TEST_SEND_MGMT_FRAME_ELAPSED_TIME_MS);
        mLooper.dispatchAll();
        verify(mSendMgmtFrameCallback, never()).onAck(anyInt());
        verify(mSendMgmtFrameCallback).onFailure(WifiCondManager.SEND_MGMT_FRAME_ERROR_TIMEOUT);
    }

    /**
     * See {@link #testSendMgmtFrameTimeoutAckThreadSafe()}. This test replaces OnAck() with
     * OnFailure().
     */
    @Test
    public void testSendMgmtFrameTimeoutFailureThreadSafe() throws Exception {
        final ArgumentCaptor<ISendMgmtFrameEvent> sendMgmtFrameEventCaptor =
                ArgumentCaptor.forClass(ISendMgmtFrameEvent.class);
        doNothing().when(mClientInterface)
                .SendMgmtFrame(any(), sendMgmtFrameEventCaptor.capture(), anyInt());
        final ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        doNothing().when(mAlarmManager).set(anyInt(), anyLong(), any(),
                alarmListenerCaptor.capture(), handlerCaptor.capture());
        mWificondControl.sendMgmtFrame(TEST_INTERFACE_NAME, TEST_PROBE_FRAME, TEST_MCS_RATE,
                Runnable::run, mSendMgmtFrameCallback);

        // AlarmManager should post the onAlarm() callback onto the handler, but since we are
        // triggering onAlarm() ourselves during the test, manually post onto handler
        handlerCaptor.getValue().post(() -> alarmListenerCaptor.getValue().onAlarm());
        // OnFailure posts to the handler
        sendMgmtFrameEventCaptor.getValue().OnFailure(
                WifiCondManager.SEND_MGMT_FRAME_ERROR_UNKNOWN);
        mLooper.dispatchAll();
        verify(mSendMgmtFrameCallback).onFailure(WifiCondManager.SEND_MGMT_FRAME_ERROR_TIMEOUT);
    }

    /**
     * Tests getDeviceWiphyCapabililties
     */
    @Test
    public void testGetDeviceWiphyCapabilities() throws Exception {
        DeviceWiphyCapabilities capaExpected = new DeviceWiphyCapabilities();

        capaExpected.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, true);
        capaExpected.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC, true);
        capaExpected.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AX, false);
        capaExpected.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ, true);
        capaExpected.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ, false);
        capaExpected.setMaxNumberTxSpatialStreams(2);
        capaExpected.setMaxNumberRxSpatialStreams(1);

        when(mWificond.getDeviceWiphyCapabilities(TEST_INTERFACE_NAME))
                .thenReturn(capaExpected);

        DeviceWiphyCapabilities capaActual =
                mWificondControl.getDeviceWiphyCapabilities(TEST_INTERFACE_NAME);
        assertEquals(capaExpected, capaActual);
    }

    // Create a ArgumentMatcher which captures a SingleScanSettings parameter and checks if it
    // matches the provided frequency set and ssid set.
    private class ScanMatcher implements ArgumentMatcher<SingleScanSettings> {
        int mExpectedScanType;
        private final Set<Integer> mExpectedFreqs;
        private final List<byte[]> mExpectedSsids;

        ScanMatcher(int expectedScanType, Set<Integer> expectedFreqs, List<byte[]> expectedSsids) {
            this.mExpectedScanType = expectedScanType;
            this.mExpectedFreqs = expectedFreqs;
            this.mExpectedSsids = expectedSsids;
        }

        @Override
        public boolean matches(SingleScanSettings settings) {
            if (settings.scanType != mExpectedScanType) {
                return false;
            }
            ArrayList<ChannelSettings> channelSettings = settings.channelSettings;
            ArrayList<HiddenNetwork> hiddenNetworks = settings.hiddenNetworks;
            if (mExpectedFreqs != null) {
                Set<Integer> freqSet = new HashSet<Integer>();
                for (ChannelSettings channel : channelSettings) {
                    freqSet.add(channel.frequency);
                }
                if (!mExpectedFreqs.equals(freqSet)) {
                    return false;
                }
            } else {
                if (channelSettings != null && channelSettings.size() > 0) {
                    return false;
                }
            }

            if (mExpectedSsids != null) {
                List<byte[]> ssidSet = new ArrayList<>();
                for (HiddenNetwork network : hiddenNetworks) {
                    ssidSet.add(network.ssid);
                }
                if (!mExpectedSsids.equals(ssidSet)) {
                    return false;
                }

            } else {
                if (hiddenNetworks != null && hiddenNetworks.size() > 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "ScanMatcher{mExpectedFreqs=" + mExpectedFreqs
                    + ", mExpectedSsids=" + mExpectedSsids + '}';
        }
    }

    private static class LocalNativeUtil {
        private static final int SSID_BYTES_MAX_LEN = 32;

        /**
         * Converts an ArrayList<Byte> of UTF_8 byte values to string.
         * The string will either be:
         * a) UTF-8 String encapsulated in quotes (if all the bytes are UTF-8 encodeable and non
         * null),
         * or
         * b) Hex string with no delimiters.
         *
         * @param bytes List of bytes for ssid.
         * @throws IllegalArgumentException for null bytes.
         */
        public static String bytesToHexOrQuotedString(ArrayList<Byte> bytes) {
            if (bytes == null) {
                throw new IllegalArgumentException("null ssid bytes");
            }
            byte[] byteArray = byteArrayFromArrayList(bytes);
            // Check for 0's in the byte stream in which case we cannot convert this into a string.
            if (!bytes.contains(Byte.valueOf((byte) 0))) {
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                try {
                    CharBuffer decoded = decoder.decode(ByteBuffer.wrap(byteArray));
                    return "\"" + decoded.toString() + "\"";
                } catch (CharacterCodingException cce) {
                }
            }
            return hexStringFromByteArray(byteArray);
        }

        /**
         * Converts an ssid string to an arraylist of UTF_8 byte values.
         * These forms are acceptable:
         * a) UTF-8 String encapsulated in quotes, or
         * b) Hex string with no delimiters.
         *
         * @param ssidStr String to be converted.
         * @throws IllegalArgumentException for null string.
         */
        public static ArrayList<Byte> decodeSsid(String ssidStr) {
            ArrayList<Byte> ssidBytes = hexOrQuotedStringToBytes(ssidStr);
            if (ssidBytes.size() > SSID_BYTES_MAX_LEN) {
                throw new IllegalArgumentException(
                        "ssid bytes size out of range: " + ssidBytes.size());
            }
            return ssidBytes;
        }

        /**
         * Convert from an array list of Byte to an array of primitive bytes.
         */
        public static byte[] byteArrayFromArrayList(ArrayList<Byte> bytes) {
            byte[] byteArray = new byte[bytes.size()];
            int i = 0;
            for (Byte b : bytes) {
                byteArray[i++] = b;
            }
            return byteArray;
        }

        /**
         * Converts a byte array to hex string.
         *
         * @param bytes List of bytes for ssid.
         * @throws IllegalArgumentException for null bytes.
         */
        public static String hexStringFromByteArray(byte[] bytes) {
            if (bytes == null) {
                throw new IllegalArgumentException("null hex bytes");
            }
            return new String(HexEncoding.encode(bytes)).toLowerCase();
        }

        /**
         * Converts an string to an arraylist of UTF_8 byte values.
         * These forms are acceptable:
         * a) UTF-8 String encapsulated in quotes, or
         * b) Hex string with no delimiters.
         *
         * @param str String to be converted.
         * @throws IllegalArgumentException for null string.
         */
        public static ArrayList<Byte> hexOrQuotedStringToBytes(String str) {
            if (str == null) {
                throw new IllegalArgumentException("null string");
            }
            int length = str.length();
            if ((length > 1) && (str.charAt(0) == '"') && (str.charAt(length - 1) == '"')) {
                str = str.substring(1, str.length() - 1);
                return stringToByteArrayList(str);
            } else {
                return byteArrayToArrayList(hexStringToByteArray(str));
            }
        }

        /**
         * Convert the string to byte array list.
         *
         * @return the UTF_8 char byte values of str, as an ArrayList.
         * @throws IllegalArgumentException if a null or unencodable string is sent.
         */
        public static ArrayList<Byte> stringToByteArrayList(String str) {
            if (str == null) {
                throw new IllegalArgumentException("null string");
            }
            // Ensure that the provided string is UTF_8 encoded.
            CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
            try {
                ByteBuffer encoded = encoder.encode(CharBuffer.wrap(str));
                byte[] byteArray = new byte[encoded.remaining()];
                encoded.get(byteArray);
                return byteArrayToArrayList(byteArray);
            } catch (CharacterCodingException cce) {
                throw new IllegalArgumentException("cannot be utf-8 encoded", cce);
            }
        }

        /**
         * Convert from an array of primitive bytes to an array list of Byte.
         */
        public static ArrayList<Byte> byteArrayToArrayList(byte[] bytes) {
            ArrayList<Byte> byteList = new ArrayList<>();
            for (Byte b : bytes) {
                byteList.add(b);
            }
            return byteList;
        }

        /**
         * Converts a hex string to byte array.
         *
         * @param hexStr String to be converted.
         * @throws IllegalArgumentException for null string.
         */
        public static byte[] hexStringToByteArray(String hexStr) {
            if (hexStr == null) {
                throw new IllegalArgumentException("null hex string");
            }
            return HexEncoding.decode(hexStr.toCharArray(), false);
        }
    }
}