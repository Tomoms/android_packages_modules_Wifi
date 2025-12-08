#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Lint as: python3
import logging
import time


from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from aware import aware_lib_utils as autils
from softap import constants
import wifi_test_utils
from mobly.controllers.android_device_lib import adb
from softap.integration import wifi_sap_lib_utils as sutils


DBS_SUPPORTED_MODELS =  ["komodo"]
STA_CONCURRENCY_SUPPORTED_MODELS  =["komodo", "caiman"]
WIFI6_MODELS =  ["komodo"]

class WifiSoftApThreeDevicesTest(base_test.BaseTestClass):
  """SoftAp with multi-devices test class.

  Attributes:
    host: Android device providing Wi-Fi hotspot
    client: Android device connecting to host provided hotspot
  """

  def setup_class(self) -> None:
    self.ads = self.register_controller(android_device, min_number=3)
    self.host = self.ads[0]
    self.client = self.ads[1]


    utils.concurrent_exec(
        self._setup_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

    asserts.abort_class_if(
        not self.host.wifi.wifiIsPortableHotspotSupported(),
        'Hotspot is not supported on host, abort remaining tests.',
    )

  def _setup_device(self, ad: android_device.AndroidDevice) -> None:
    ad.load_snippet('wifi', 'com.google.snippet.wifi')
    sutils._stop_tethering(ad)
    wifi_test_utils.enable_wifi_verbose_logging(ad)
    wifi_test_utils.set_screen_on_and_unlock(ad)

    self.AP_IFACE = 'wlan0'
    if ad.model in DBS_SUPPORTED_MODELS:
      self.AP_IFACE = 'wlan1'
    if ad.model in STA_CONCURRENCY_SUPPORTED_MODELS:
      self.AP_IFACE = 'wlan2'

  def setup_test(self):
    for ad in self.ads:
      if not ad.wifi.wifiIsEnabled():
        ad.wifi.wifiEnable()
      sutils._stop_tethering(self.host)

  def on_fail(self, record):
    logging.info('Collecting bugreports...')
    android_device.take_bug_reports(
      ads=[self.host, self.client],
      test_name=record.test_name,
      begin_time=record.begin_time,
      destination=self.current_test_info.output_path
        )

  def teardown_test(self):
    for ad in self.ads:
      sutils._stop_tethering(self.host)
      self.host.wifi.wifiUnregisterSoftApCallback()
      ad.wifi.wifiClearConfiguredNetworks()
      ad.wifi.wifiDisableAllSavedNetworks()
      ad.wifi.wifiEnable()
      if ad.is_adb_root:
        autils.set_airplane_mode(self.host, False)

  def teardown_class(self):
    for ad in self.ads:
      ad.wifi.wifiDisableAllSavedNetworks()
      ad.wifi.wifiClearConfiguredNetworks()
      ad.wifi.wifiEnable()
      ad.wifi.wifiFactoryReset()


  def confirm_softap_in_scan_results(self, ap_ssid):
    """Confirm the ap started by wifi tethering is seen in scan results.

        Args:
            ap_ssid: SSID of the ap we are looking for.
    """
    sutils.start_wifi_connection_scan_and_check_for_network(
      self.client, ap_ssid)

  def validate_ping_between_softap_and_client(self, config):
    """Test ping between softap and its client.

        Connect one android device to the wifi hotspot.
        Verify they can ping each other.

        Args:
            config: wifi network config with SSID, password
    """
    config = {
      "SSID": config[constants.WiFiTethering.SSID_KEY],
      "password": config[constants.WiFiTethering.PWD_KEY],
      }
    sutils._wifi_connect(self.client, config, check_connectivity=False)
    host_ip = self.host.wifi.connectivityGetIPv4Addresses(self.AP_IFACE)[0]
    client_ip = self.client.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    sutils.verify_11ax_softap(self.host, self.client, WIFI6_MODELS)
    self.client.log.info("Try to ping %s" % host_ip)
    asserts.assert_true(
      sutils.adb_shell_ping(self.client, count=10, dest_ip=host_ip),
      "%s ping %s failed" % (self.client.serial, host_ip))
    self.host.log.info("Try to ping %s" % client_ip)
    asserts.assert_true(
      sutils.adb_shell_ping(self.host, count=10, dest_ip=client_ip),
      "%s ping %s failed" % (self.host.serial, client_ip))

  def validate_full_tether_startup(self, band=None, hidden=False,
                                     test_ping=False, test_clients=None,
                                     security=None,
                                     cIsolatEnabled=False):

    """Test full startup of wifi tethering.

        This test case performs the following steps:
        1. Reports the current WiFi state of the host device.
        2. Attempts to switch the host device to WiFi Access Point (AP) mode,
          configuring the SoftAP with provided or default settings.
        3. Verifies that the SoftAP is successfully activated on the host device.
        4. Shuts down the WiFi tethering (disables SoftAP) on the host device.
        5. Verifies that the WiFi state on the host device has returned to its
          previous state before tethering was enabled.

        Args:
            band (str, optional):
              The Wi-Fi band to use for tethering (e.g., "2.4GHz",
              "5GHz"). Defaults to None,which might use a default
              or let the system decide.
            hidden (bool, optional):
              A boolean indicating whether the SoftAP network
              should be hidden (not broadcast its SSID). Defaults to False.
            test_ping (bool, optional):
              A boolean indicating whether to perform a ping test between
                the SoftAP host and a connected client. Requires a client
                device to be associated with `self.client`. Defaults to False.
            test_clients (bool, optional):
              A boolean indicating whether to perform a ping test between
                two connected client devices. Requires at least
                two client devices to be associated with `self.ads
                (excluding the host). Defaults to None.
            security (str, optional):
              The security protocol to use for the SoftAP (e.g., "WPA2_PSK").
                Defaults to None, which might use a default or open network.
            cIsolatEnabled(bool, optional):
              A boolean indicating whether the SoftAP network of
                clientIsolationEnabled setting. Defaults to False.
    """

    initial_wifi_state = self.host.wifi.wifiCheckState()
    self.host.log.info("current state: %s", initial_wifi_state)
    config = sutils.create_softap_config()
    sutils.start_wifi_tethering(self.host,
                                config[constants.WiFiTethering.SSID_KEY],
                                config[constants.WiFiTethering.PWD_KEY],
                                band,
                                hidden,
                                security,
                                cIsolatEnabled)
    if hidden:
      # First ensure it's not seen in scan results.
      # If the network is hidden, it should be saved on the client to be
      # seen in scan results.
      sutils.start_wifi_connection_scan_and_ensure_network_not_found(
        self.client, config[constants.WiFiTethering.SSID_KEY])
      config[constants.WiFiTethering.HIDDEN_KEY] = True
      ret = self.client.wifi.wifiAddNetwork(config)
      asserts.assert_true(ret != -1, "Add network %r failed" % config)
      self.client.wifi.wifiEnableNetwork(ret, 0)
    self.confirm_softap_in_scan_results(config[constants.WiFiTethering.SSID_KEY])
    config = {
      "SSID": config[constants.WiFiTethering.SSID_KEY],
      "password": config[constants.WiFiTethering.PWD_KEY],
    }
    if test_ping:
      self.validate_ping_between_softap_and_client(config)
    if test_clients:
      if len(self.ads) > 2:
        self.validate_ping_between_two_clients(config)
    if cIsolatEnabled:
      if len(self.ads) > 2:
        self.validate_isolation_between_two_clients(config)
    sutils._stop_tethering(self.host)
    asserts.assert_false(self.host.wifi.wifiIsApEnabled(),
                         "SoftAp is still reported as running")
    if initial_wifi_state:
      sutils.wait_for_wifi_state(self.host, True)
    elif self.host.wifi.wifiCheckState():
      asserts.fail("Wifi was disabled before softap and now it is enabled")

  def validate_ping_between_two_clients(self, config):
    """Test ping between softap's clients.

        Connect two android device to the wifi hotspot.
        Verify the clients can ping each other.

        Args:
            config: wifi network config with SSID, password
    """
    ad1 = self.client
    ad2 = self.ads[2]
    sutils._wifi_connect(ad1, config, check_connectivity=False)
    sutils._wifi_connect(ad2, config, check_connectivity=False)
    ad1_ip = ad1.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    ad2_ip = ad2.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    ad1.log.info("Try to ping test from %s to %s" % (ad1.serial,ad2_ip))
    asserts.assert_true(
      sutils.adb_shell_ping(ad1, count=10, dest_ip=ad2_ip),
      "%s ping %s failed" % (ad1.serial, ad2_ip))
    ad2.log.info("Try to ping test from %s to %s" % (ad2.serial,ad1_ip))
    asserts.assert_true(
      sutils.adb_shell_ping(ad2, count=10, dest_ip=ad1_ip),
      "%s ping %s failed" % (ad2.serial, ad1_ip))

  def validate_isolation_between_two_clients(self, config):
    """Test ping between softap's clients, expecting them NOT to ping each other.
    Connect two Android devices to the Wi-Fi hotspot.
    Verify the clients CANNOT ping each other (due to isolation).

    Args:
      config: Wi-Fi network config with SSID, password
    """
    ad1 = self.client
    ad2 = self.ads[2]
    sutils._wifi_connect(ad1, config, check_connectivity=False)
    sutils._wifi_connect(ad2, config, check_connectivity=False)
    ad1_ip = ad1.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    ad2_ip = ad2.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    ad1.log.info("Try to ping test from %s to %s" % (ad1.serial,ad2_ip))
    asserts.assert_false(
      sutils.adb_shell_ping(ad1, count=10, dest_ip=ad2_ip),
      "%s ping %s successfully, isolation setting failed" % (ad1.serial, ad2_ip))
    ad2.log.info("Try to ping test from %s to %s" % (ad2.serial,ad1_ip))
    asserts.assert_false(
      sutils.adb_shell_ping(ad2, count=10, dest_ip=ad1_ip),
      "%s ping %s successfully, isolation setting failed" % (ad2.serial, ad1_ip))

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_2G_two_clients_ping_each_other(self):

    """Test for 2G hotspot with 2 clients

        1. Turn on 2G hotspot
        2. Two clients connect to the hotspot
        3. Two clients ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G, test_clients=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_5G_two_clients_ping_each_other(self):
    """Test for 5G hotspot with 2 clients

        1. Turn on 5G hotspot
        2. Two clients connect to the hotspot
        3. Two clients can't ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G, test_clients=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration('+
      'SoftApConfiguration.Builder()#setClientIsolationEnabled(true)',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_2G_two_clients_isolation_each_other(self):

    """Test for 2G hotspot with 2 clients

        1. Turn on 2G hotspot
        2. Two clients connect to the hotspot
        3. Two clients can't ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G, cIsolatEnabled=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration('+
      'SoftApConfiguration.Builder()#setClientIsolationEnabled(true)',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_5G_two_clients_isolation_each_other(self):

    """Test for 2G hotspot with 2 clients

        1. Turn on 2G hotspot
        2. Two clients connect to the hotspot
        3. Two clients can't ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G, cIsolatEnabled=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration('+
      'SoftApConfiguration.Builder()#setClientIsolationEnabled(true)',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_auto_two_clients_isolation_each_other(self):

    """Test for auto-band hotspot with 2 clients

        1. Turn on auto-band hotspot
        2. Two clients connect to the hotspot
        3. Two clients ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G, cIsolatEnabled=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_max_client_setting(self):
    """Test Client Control feature
        1. Check device number and capability to make sure feature is supported
        2. Backup config
        3. Setup configuration which used to start softap
        4. Register callback after softap enabled
        5. Trigger client connect to softap
        6. Verify blocking event
        7. Extend max client setting
        8. Verify client connected
        9. Restore Config
    """
    self.host.wifi.tetheringStartTrackingTetherStateChange()
    callbackId = self.host.wifi.wifiRegisterSoftApCallback()
    capability = callbackId.waitAndGet(
      constants.SoftApCallbackEventName.SOFTAP_CAPABILITY_CHANGED,
      10
    )
    asserts.skip_if(
      not capability.data[
        constants.SoftApCallbackEventName.SOFTAP_CAPABILITY_FEATURE_CLIENT_CONTROL],
      "Client control isn't supported, ignore test")
    # Unregister callback before start test to avoid
    # unnecessary callback impact the test
    self.host.wifi.tetheringStopTethering()
    self.host.wifi.wifiUnregisterSoftApCallback()
    time.sleep(1)
    self.host.wifi.tetheringStartTrackingTetherStateChange()
    callbackId = self.host.wifi.wifiRegisterSoftApCallback()
    # Backup config
    original_softap_config = self.host.wifi.wifiGetSapConfiguration()
    sutils.save_wifi_soft_ap_config(
      self.host,
      {"SSID":"ACTS_TEST"},
      band=constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
      hidden=False,
      security=constants.SoftApSecurityType.WPA2,
      password="12345678",
      max_clients=1)
    sutils.start_wifi_tethering_saved_config(self.host)
    current_softap_config = self.host.wifi.wifiGetSapConfiguration()
    # impact the test
    asserts.assert_equal(self.host.wifi.wifiGetSoftApConnectedClientsCount(),
                        0)
    self.host.wifi.tetheringStartTetheringWithProvisioning(0, False)
    asserts.assert_true(self.host.wifi.wifiIsApEnabled(),
                        "SoftAp is not reported as running")
    # Trigger client connection
    self.client_2 = self.ads[2]
    config = {
      "SSID": current_softap_config[constants.WiFiTethering.SSID_KEY],
      "password": current_softap_config[constants.WiFiTethering.PWD_KEY],
    }
    sutils._wifi_connect(self.client, config, check_connectivity=False)
    time.sleep(1)
    sutils._wifi_connect(self.client_2, config, check_connectivity=False)
    time.sleep(3)
    blockerClient = callbackId.waitAndGet(
      constants.SoftApCallbackEventName.SOFTAP_BLOCKING_CLIENT_CONNECTING,
      10
    )
    asserts.assert_equal(self.host.wifi.wifiGetSoftApConnectedClientsCount(),
                        1)
    sutils.save_wifi_soft_ap_config(
      self.host,
      {"SSID":"ACTS_TEST"},
      band=constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
      hidden=False,
      security=constants.SoftApSecurityType.WPA2,
      password="12345678",
      max_clients=2)
    sutils.start_wifi_tethering_saved_config(self.host)
    softap_config = self.host.wifi.wifiGetSapConfiguration()
    config = {
      "SSID": softap_config[constants.WiFiTethering.SSID_KEY],
      "password": softap_config[constants.WiFiTethering.PWD_KEY],
    }
    sutils._wifi_connect(self.client_2, config, check_connectivity=False)
    asserts.assert_equal(self.host.wifi.wifiGetSoftApConnectedClientsCount(),
                        2)
    config = {
      "SSID": original_softap_config[constants.WiFiTethering.SSID_KEY],
      "password": original_softap_config[constants.WiFiTethering.PWD_KEY],
    }
    sutils.save_wifi_soft_ap_config(
      self.host,
      config)
    self.host.wifi.tetheringStopTethering()
    self.host.wifi.wifiUnregisterSoftApCallback()

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softp_2g_channel_when_connected_to_chan_13(self):
    """Verify softAp 2G channel when connected to network on channel 13.

        Steps:
            1. Configure AP in channel 13 on 2G band and connect DUT to it.
            2. Start softAp on DUT on 2G band.
            3. Verify softAp is started on channel 13.
    """
    self.client_2 = self.ads[2]
    sutils.set_wifi_country_code(self.host, "JP")
    sutils.set_wifi_country_code(self.client, "JP")
    self.host.wifi.tetheringStartTrackingTetherStateChange()
    callbackId = self.host.wifi.wifiRegisterSoftApCallback()
    sutils.save_wifi_soft_ap_config(
      self.host, {"SSID":"ACTS_TEST"},
      band=constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      security=constants.SoftApSecurityType.WPA2,
      password="12345678",
      channel=13)
    current_softap_config = self.host.wifi.wifiGetSapConfiguration()
    self.host.wifi.tetheringStartTrackingTetherStateChange()
    self.host.wifi.tetheringStartTetheringWithProvisioning(0, False)
    asserts.assert_true(self.host.wifi.wifiIsApEnabled(),
                        "SoftAp is not reported as running")
    config = {
      "SSID": current_softap_config[constants.WiFiTethering.SSID_KEY],
      "password": current_softap_config[constants.WiFiTethering.PWD_KEY],
    }
    time.sleep(5)
    sutils._wifi_connect(self.client, config, check_connectivity=False)
    config2 = sutils.create_softap_config()
    config2[constants.WiFiTethering.AP_BAND_KEY] = (
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G)
    asserts.assert_true(
      self.client.wifi.wifiSetWifiApConfiguration(config2),
      "Failed to set WifiAp Configuration")
    sutils.start_wifi_tethering_saved_config(self.client)
    softap_conf = self.client.wifi.wifiGetSapConfiguration()
    time.sleep(2)
    self.client.wifi.tetheringStartTrackingTetherStateChange()
    self.client.wifi.tetheringStartTetheringWithProvisioning(0, False)
    asserts.assert_true(self.client.wifi.wifiIsApEnabled(),
                        "SoftAp is not reported as running")
    config2 = {
      "SSID": softap_conf[constants.WiFiTethering.SSID_KEY],
      "password": softap_conf[constants.WiFiTethering.PWD_KEY],
    }
    sutils._wifi_connect(self.client_2, config2, check_connectivity=False)
    softap_channel = self.client_2.wifi.wifiGetConnectionInfo()
    channel = constants.WifiEnums.freq_to_channel[softap_channel["mFrequency"]]
    asserts.assert_true(channel == 13,
                        "Dut client did not connect to softAp on channel 13"
                      )

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_number_of_softap_clients(self):
    """Test for number of softap clients to be updated correctly

        1. Turn of hotspot
        2. Register softap callback
        3. Let client connect to the hotspot
        4. Register second softap callback
        5. Force client connect/disconnect to hotspot
        6. Unregister second softap callback
        7. Force second client connect to hotspot (if supported)
        8. Turn off hotspot
        9. Verify second softap callback doesn't respond after unregister
    """
    config = sutils.start_softap_and_verify(
      self.host, self.client,
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G)
      # Register callback after softap enabled to avoid unnecessary callback
      # impact the test
    self.host.wifi.tetheringStartTrackingTetherStateChange()
    callbackId = self.host.wifi.wifiRegisterSoftApCallback()
    asserts.assert_equal(self.host.wifi.wifiGetSoftApConnectedClientsCount(),
                         0)
    self.host.wifi.tetheringStartTetheringWithProvisioning(0, False)
    asserts.assert_true(self.host.wifi.wifiIsApEnabled(),
                        "SoftAp is not reported as running")
    asserts.skip_if(sutils.is_SIM_network_active(self.host) is False,
                    "DUT does not support Data ")
    # Force DUTs connect to Network
    config = {
      "SSID": config[constants.WiFiTethering.SSID_KEY],
      "password": config[constants.WiFiTethering.PWD],
    }
    sutils._wifi_connect(self.client, config, check_connectivity=False)
    asserts.assert_equal(self.host.wifi.wifiGetSoftApConnectedClientsCount(),
                         1)
    sutils.toggle_wifi_and_wait_for_reconnection(self.client, config)
    self.client_2 = self.ads[2]
    sutils._wifi_connect(self.client_2, config, check_connectivity=False)
    asserts.assert_equal(self.host.wifi.wifiGetSoftApConnectedClientsCount(),
                         2)
    self.host.wifi.tetheringStopTethering()
    self.host.wifi.wifiUnregisterSoftApCallback()
    sutils.wait_for_disconnect(self.client, 10)
    sutils.wait_for_disconnect(self.client_2, 10)


if __name__ == '__main__':
  test_runner.main()

