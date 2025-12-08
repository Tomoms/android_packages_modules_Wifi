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


class WifiSoftApCountryTest(base_test.BaseTestClass):
  """SoftAp test class.

  Attributes:
    host: Android device providing Wi-Fi hotspot
    client: Android device connecting to host provided hotspot
  """

  def setup_class(self) -> None:
    self.ads = self.register_controller(android_device, min_number=2)
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
      ad.wifi.wifiClearConfiguredNetworks()
      ad.wifi.wifiDisableAllSavedNetworks()
      if not ad.wifi.wifiIsEnabled():
        ad.wifi.wifiToggleEnable()
      if ad.is_adb_root:
        autils.set_airplane_mode(self.host, False)
        sutils.set_wifi_country_code(ad, "US")

  def teardown_class(self):
    for ad in self.ads:
      ad.wifi.wifiDisableAllSavedNetworks()
      ad.wifi.wifiClearConfiguredNetworks()
      ad.wifi.wifiToggleEnable()
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

  def full_tether_startup_one_client_ping_softap_country_code(
      self, country, band=None):

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
    """
    sutils.set_wifi_country_code(self.host, country)
    initial_wifi_state = self.host.wifi.wifiCheckState()
    self.host.log.info("current state: %s", initial_wifi_state)
    config = sutils.create_softap_config()
    sutils.start_wifi_tethering(self.host,
                                config[constants.WiFiTethering.SSID_KEY],
                                config[constants.WiFiTethering.PWD_KEY],
                                band)
    self.confirm_softap_in_scan_results(config[constants.WiFiTethering.SSID_KEY])
    self.validate_ping_between_softap_and_client(config)
    sutils._stop_tethering(self.host)
    asserts.assert_false(self.host.wifi.wifiIsApEnabled(),
                        "SoftAp is still reported as running")
    if initial_wifi_state:
      sutils.wait_for_wifi_state(self.host, True)
    elif self.host.wifi.wifiCheckState():
      asserts.fail("Wifi was disabled before softap and now it is enabled")

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

  def test_check_wifi_tethering_2G_au(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting AU country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "AU",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_de(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting DE country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "DE",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_dz(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting DZ country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "DZ",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_uk(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting ID country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "GB",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_id(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting ID country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "ID",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_jp(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting JP country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "JP",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_tw(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting TW country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "TW",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_2G_us(self):
    """ Device can connect to 2G hotspot

        Steps:
        1. Setting US country code for each device
        2. Turn on DUT's 2G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "US",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G
    )

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

  def test_check_wifi_tethering_5G_au(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting AU country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "AU",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G

    )

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

  def test_check_wifi_tethering_5G_de(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting DE country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "DE",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_5G_dz(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting DZ country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "DZ",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_5G_uk(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting ID country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "GB",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_5G_id(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting ID country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "ID",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_5G_jp(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting JP country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "JP",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_5G_tw(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting TW country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "TW",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_5G_us(self):
    """ Device can connect to 5G hotspot

        Steps:
        1. Setting US country code for each device
        2. Turn on DUT's 5G softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "US",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G
    )

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

  def test_check_wifi_tethering_auto_au(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting AU country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "AU",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_de(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting DE country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "DE",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_dz(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting DZ country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "DZ",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_uk(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting ID country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "GB",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_id(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting ID country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "ID",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_jp(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting IP country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "JP",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_tw(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting TW country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "TW",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

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

  def test_check_wifi_tethering_auto_us(self):
    """ Device can connect to Auto hotspot

        Steps:
        1. Setting US country code for each device
        2. Turn on DUT's Auto softap
        3. Client connects to the softap
        4. Client and DUT ping each other
    """
    self.full_tether_startup_one_client_ping_softap_country_code(
      "US",
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G
    )

if __name__ == '__main__':

  test_runner.main()
