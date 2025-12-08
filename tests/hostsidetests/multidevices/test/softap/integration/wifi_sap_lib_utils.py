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

import time
import logging
import enum

from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly import asserts
from mobly.snippet import errors
from mobly import utils
from mobly import signals
from softap import constants
from queue import Empty
from mobly.controllers.android_device_lib import adb


_CALLBACK_NAME = "callbackName"
_CALLBACK_TIMEOUT = constants.CALLBACK_TIMEOUT.total_seconds()
_WIFI_SCAN_INTERVAL_SEC = constants.WIFI_SCAN_INTERVAL_SEC.total_seconds()
SOFTAP_INFO_FREQUENCY_CALLBACK_KEY = (
    constants.SoftApOnConnectedClientsChangedDataKey.SOFTAP_INFO_FREQUENCY_CALLBACK_KEY
)
SOFTAP_INFO_BANDWIDTH_CALLBACK_KEY = (
    constants.SoftApOnConnectedClientsChangedDataKey.SOFTAP_INFO_BANDWIDTH_CALLBACK_KEY
)
AP_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLE_KEY = (
    constants.WiFiTethering.AP_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLE_KEY
)



def match_networks(target_params, networks):
    """Finds the WiFi networks that match a given set of parameters in a list
    of WiFi networks.

    To be considered a match, the network should contain every key-value pair
    of target_params

    Args:
        target_params:
        A dict with 1 or more key-value pairs representing a Wi-Fi network.
        E.g. { 'SSID': 'wh_ap1_5g', 'BSSID': '30:b5:c2:33:e4:47' }
        networks: A list of dict objects representing WiFi networks.

    Returns:
        The networks that match the target parameters.
    """
    results = []
    asserts.assert_true(target_params,
                        "Expected networks object 'target_params' is empty")
    for n in networks:
        add_network = 1
        for k, v in target_params.items():
            if k not in n:
                add_network = 0
                break
            if n[k] != v:
                add_network = 0
                break
        if add_network:
            results.append(n)
    return results

def start_wifi_connection_scan_and_check_for_network(
    ad: android_device.AndroidDevice,
    network_ssid: str,
    max_tries: int=3):
    """
    Start connectivity scans & checks if the |network_ssid| is seen in
    scan results. The method performs a max of |max_tries| connectivity scans
    to find the network.

    Args:
        ad: An AndroidDevice object.
        network_ssid: SSID of the network we are looking for.
        max_tries: Number of scans to try.
    Returns:
        True: if network_ssid status is expected in scan results.
        False: if network_ssid status is expected in scan results.
    """
    for num_tries in range(max_tries):
        scanned_results = ad.wifi.wifiScanAndGetResults()
        ad.wifi.wifiSetScanThrottleDisable()
        scanned_ssids = sorted(
            [scan_result['SSID'] for scan_result in scanned_results]
            )
        if network_ssid in scanned_ssids:
            return True
        else:
            if (num_tries + 1) == max_tries:
                break
            time.sleep(_WIFI_SCAN_INTERVAL_SEC)
            continue
    return False

def start_wifi_connection_scan_and_ensure_network_found(
    ad, network_ssid, max_tries=3):
    """
    Start connectivity scans & ensure the |network_ssid| is seen in
    scan results. The method performs a max of |max_tries| connectivity scans
    to find the network.
    This method asserts on failure!

    Args:
        ad: An AndroidDevice object.
        network_ssid: SSID of the network we are looking for.
        max_tries: Number of scans to try.
    """
    logging.info("Starting scans to ensure %s is present", network_ssid)
    assert_msg = "Failed to find " + network_ssid + " in scan results" \
        " after " + str(max_tries) + " tries"
    asserts.assert_true(
        start_wifi_connection_scan_and_check_for_network(
      ad, network_ssid, max_tries), assert_msg)

def create_softap_config():
    """Create a softap config with random ssid and password."""
    ap_ssid = "softap_" + utils.rand_ascii_str(8)
    ap_password = utils.rand_ascii_str(8)
    logging.info("softap setup: %s %s", ap_ssid, ap_password)
    config = {
        constants.WiFiTethering.SSID_KEY: ap_ssid,
        constants.WiFiTethering.PWD_KEY: ap_password,
    }
    return config

def start_wifi_tethering(ad: android_device.AndroidDevice,
                         ssid: str,
                         password: str,
                         band: str=None,
                         hidden:  bool = False,
                         security: str =None,
                         cIsolatEnabled:  bool = False):
    """Starts wifi tethering on an android_device.

    Args:
        ad: android_device to start wifi tethering on.
        ssid: The SSID the soft AP should broadcast.
        password: The password the soft AP should use.
        band: The band the soft AP should be set on. It should be either
            WifiEnums.WIFI_CONFIG_APBAND_2G or WifiEnums.WIFI_CONFIG_APBAND_5G.
        hidden: boolean to indicate if the AP needs to be hidden or not.
        security: security type of softap.

    Returns:
        No return value.
        Error checks in this function will raise test failure signals
    """
    config = {constants.WiFiTethering.SSID_KEY: ssid}
    if password:
        config[constants.WiFiTethering.PWD_KEY] = password
    if band:
        config[constants.WiFiTethering.AP_BAND_KEY] = band
    if hidden:
        config[constants.WiFiTethering.HIDDEN_KEY] = hidden
    if security:
        config[constants.WiFiTethering.SECURITY] = security
    if cIsolatEnabled:
        config[constants.WiFiTethering.AP_CLIENT_ISOLATION_ENABLE] =(
            cIsolatEnabled)

    asserts.assert_true(ad.wifi.wifiSetWifiApConfiguration(config),
                        "Failed to update WifiAp Configuration")
    handler_state = ad.wifi.tetheringStartTrackingTetherStateChange()
    handler_tethering = ad.wifi.tetheringStartTetheringWithProvisioning(0, False)
    time.sleep(_WIFI_SCAN_INTERVAL_SEC)
    try:
        result_receiver = handler_state.waitAndGet('TetherStateChangedReceiver')
        callback_name = result_receiver.data["callbackName"]
        ad.log.info('StateChanged callback_name: %s', callback_name)
        ad.log.debug("Tethering started successfully.")
    except Exception:
        msg = "Failed to receive confirmation of wifi tethering starting"
        asserts.fail(msg)
    finally:
      ad.wifi.tetheringStopTrackingTetherStateChange()


def start_wifi_connection_scan_and_ensure_network_not_found(
    ad, network_ssid, max_tries=2):
    """
    Start connectivity scans & ensure the |network_ssid| is not seen in
    scan results. The method performs a max of |max_tries| connectivity scans
    to find the network.
    This method asserts on failure!

    Args:
        ad: An AndroidDevice object.
        network_ssid: SSID of the network we are looking for.
        max_tries: Number of scans to try.
    """
    ad.log.info("Starting scans to ensure %s is not present", network_ssid)
    assert_msg = "Found " + network_ssid + " in scan results" \
        " after " + str(max_tries) + " tries"

    asserts.assert_false(
        start_wifi_connection_scan_and_check_for_network(
            ad, network_ssid, max_tries), assert_msg)

def wait_for_wifi_state(ad, state):
    """Toggles the state of wifi.

    TestFailure signals are raised when something goes wrong.

    Args:
        ad: An AndroidDevice object.
        state: Wifi state to wait for.
    """
    if state == ad.wifi.wifiCheckState():
        # Check if the state is already achieved, so we don't wait for the
        # state change event by mistake.
        return
    state_handler = ad.wifi.wifiStartTrackForStateChange()
    fail_msg = "Device did not transition to Wi-Fi state to %s on %s." % (
        state, ad.serial)
    try:
        state_handler.waitAndGet(event_name="WifiNetworkConnected",
                                 timeout=10)
    except Empty:
        asserts.assert_equal(state, ad.wifi.wifiCheckState(), fail_msg)
    finally:
        ad.wifi.wifiStopTrackForStateChange()


def adb_shell_ping(ad, count=4, dest_ip="www.google.com"):
    """
    Executes a ping command via adb shell and determines its success.

    Args:
        ad: The AndroidDevice object (Mobly's ad instance).
        count: The number of ping packets to send. Default is 4.
        dest_ip: The IP address or hostname to ping.

    Returns:
        True if the ping was successful (0% packet loss), False otherwise.
    Raises:
        AssertionError: If ad.adb.shell returns an empty result, which indicates a critical issue.
    """
    ping_cmd = f"ping -c {count} {dest_ip}"
    results = "" # Initialize results outside the try block

    for attempt in range(2): # Allow for one retry
        try:
            ad.log.info(
                f"Attempt {attempt + 1}: Pinging {dest_ip}"+
                f"from {ad.serial} with command: '{ping_cmd}'")
            results = ad.adb.shell(ping_cmd)
            ad.log.info(f"Ping results (Attempt {attempt + 1}): {results}")

            if not results:
                # If adb.shell returns empty, it's usually a serious issue.
                # It might not even raise AdbError in some edge cases.
                ad.log.error("adb.shell returned empty ping results.")
                # This should probably be an immediate failure rather than trying to parse
                asserts.fail(
                    "ping empty results - seems like a critical failure")
                return False # Or raise a more specific exception if needed

            # Check for success indicators in the ping output
            # Common success indicators: "0% packet loss", "received, 0% packet loss"
            Zeoloss="0% packet loss".encode('utf-8')
            received_Zeoloss = "received, 0% packet loss".encode('utf-8')
            if Zeoloss in results or received_Zeoloss in results:
            # if "0% packet loss".encode('utf-8') in results or "received, 0% packet loss".encode('utf-8') in results:
                ad.log.info(f"Ping to {dest_ip} succeeded.")
                return True
            else:
                # Ping command ran, but indicated packet loss or other issues.
                ad.log.warning(f"Ping to {dest_ip}"+
                               "failed with packet loss or other errors. Output: {results}")
                # If this is the last attempt, return False
                if attempt == 1: # This means it failed on the second attempt too
                    return False
                # If not the last attempt, fall through to retry
                time.sleep(1) # Wait before retrying

        except adb.AdbError as e:
            ad.log.error(f"Attempt {attempt + 1}: "+
                        "AdbError during ping to {dest_ip}: {e}")
            # If this is the last attempt, return False
            if attempt == 1:
                return False
            # If not the last attempt, sleep and then retry
            time.sleep(1)

    # If the loop finishes without returning True (meaning both attempts failed)
    ad.log.error(f"Ping to {dest_ip} failed after {2} attempts.")
    return False

def verify_11ax_softap(dut, dut_client, wifi6_supported_models):
    """Verify 11ax SoftAp if devices support it.

    Check if both DUT and DUT client supports 11ax, then SoftAp turns on
    with 11ax mode and DUT client can connect to it.

    Args:
      dut: Softap device.
      dut_client: Client connecting to softap.
      wifi6_supported_models: List of device models supporting 11ax.
    """
    if dut.model in wifi6_supported_models and dut_client.model in wifi6_supported_models:
        logging.info(
            "Verifying 11ax softap. DUT model: %s, DUT Client model: %s",
            dut.model, dut_client.model)
        asserts.assert_true(
            dut_client.wifi.wifiGetConnectionStandard() ==
            constants.WIFI_STANDARD_11AX,
            "DUT failed to start SoftAp in 11ax.")


def reset_wifi(ad: android_device.AndroidDevice):
    """Clears all saved Wi-Fi networks on a device.

    This will turn Wi-Fi on.

    Args:
        ad: An AndroidDevice object.
    """
    networks = ad.wifi.wifiGetConfiguredNetworks()
    ad.log.info('Configured Networks = %s', networks)
    if not networks:
        return
    removed = []
    for net in networks:
        if net['networkId'] not in removed:
           ad.wifi.wifiForgetNetwork(net['networkId'])
           removed.append(net['networkId'])
        else:
           continue
    # Check again to see if there's any network left.
    asserts.assert_true(
        not ad.wifi.wifiGetConfiguredNetworks(),
        "Failed to remove these Wi-Fi network Lists: %s" % networks)

def _wifi_connect(ad: android_device.AndroidDevice,
                  network: dict,
                  num_of_tries=1,
                  check_connectivity=True):

    """Connect an Android device to a wifi network.

    Initiate connection to a wifi network, wait for the "connected" event, then
    confirm the connected ssid is the one requested.

    This will directly fail a test if anything goes wrong.

    Args:
        ad: android_device object to initiate connection on.
        network: A dictionary representing the network to connect to. The
                 dictionary must have the key "SSID".
        num_of_tries: An integer that is the number of times to try before
                      delaring failure. Default is 1.
    """
    asserts.assert_true(
      constants.WiFiTethering.SSID_KEY in network,
      "Key '%s' must be present in network definition." % constants.WiFiTethering.SSID_KEY)
    state_handler = ad.wifi.wifiStartTrackForStateChange()
    expected_ssid = network[constants.WiFiTethering.SSID_KEY]
    ad.log.info("Starting connection process to %s", expected_ssid)
    ad.wifi.wifiConnecting(network)
    try:
      results = state_handler.waitAndGet(event_name="WifiNetworkConnected",
                                         timeout=10)
      ad.log.info("Connected to Wi-Fi network %s.",
                  results.data[constants.WiFiTethering.SSID_KEY])
      asserts.assert_equal(
        expected_ssid,
        results.data[constants.WiFiTethering.SSID_KEY],
        f'{ad} Need to connect to expected ssid {expected_ssid}.',
        )
      results = state_handler.waitAndGet(event_name="WifiStateChanged",
                                         timeout=10)
      ad.log.info("Wifi state = %s", results.data['enabled'])
      asserts.assert_true(results.data['enabled'] , "Wifi State is not correct.")
    except Exception as error:
      ad.log.error("Failed to connect to %s with error %s", expected_ssid,
                     error)
    finally:
      ad.wifi.wifiStopTrackForStateChange()

def _stop_tethering(ad: android_device.AndroidDevice) -> bool:
    """Stops any ongoing tethering sessions on the android device.

    Args:
      ad: The Android device object.

    Returns:
      True if tethering is disabled successfully, False otherwise.
    """
    if not ad.wifi.wifiIsApEnabled():
      return True
    ad.wifi.tetheringStopTethering()
    return ad.wifi.wifiWaitForTetheringDisabled()

def start_wifi_tethering_saved_config(ad: android_device.AndroidDevice):
    """ Turn on wifi hotspot with a config that is already saved """
    handler_state = ad.wifi.tetheringStartTrackingTetherStateChange()
    handler_tethering = ad.wifi.tetheringStartTetheringWithProvisioning(0, False)
    time.sleep(_WIFI_SCAN_INTERVAL_SEC)
    try:
        result_receiver = handler_state.waitAndGet('TetherStateChangedReceiver')
        callback_name = result_receiver.data["callbackName"]
        ad.log.info('StateChanged callback_name: %s', callback_name)
        ad.log.debug("Tethering started successfully.")
    except Exception:
        msg = "Failed to receive confirmation of wifi tethering starting"
        asserts.fail(msg)
    finally:
      ad.wifi.tetheringStopTrackingTetherStateChange()

def connect_to_wifi_network(ad, network, assert_on_fail=True,
                            check_connectivity=False, hidden=False,
                            num_of_scan_tries=3,
                            num_of_connect_tries=3):
    """Connection logic for open and psk wifi networks.
        Args:
            ad: AndroidDevice to use for connection
            network: network info of the network to connect to
            assert_on_fail: If true, errors from wifi_connect will raise
                            test failure signals.
            hidden: Is the Wifi network hidden.
            num_of_scan_tries: The number of times to try scan
                           interface before declaring failure.
            num_of_connect_tries: The number of times to try
                              connect wifi before declaring failure.
    """
    if hidden:
        start_wifi_connection_scan_and_ensure_network_not_found(
            ad,
            network[constants.WiFiTethering.SSID_KEY],
            max_tries=num_of_scan_tries)
    else:
        start_wifi_connection_scan_and_ensure_network_found(
            ad,
            network[constants.WiFiTethering.SSID_KEY],
            max_tries=num_of_scan_tries)
    config = {
      "SSID": network[constants.WiFiTethering.SSID_KEY],
      "password": network[constants.WiFiTethering.PWD_KEY],
      }
    if hidden:
        config[constants.WiFiTethering.HIDDEN_KEY] = True
        ret = ad.wifi.wifiAddNetwork(config)
        asserts.assert_true(ret != -1, "Add network %r failed" % config)
        ad.wifi.wifiEnableNetwork(ret, 0)
    _wifi_connect(ad, config, check_connectivity=check_connectivity)

def get_current_softap_info(ad, callbackId):
    frequency = 0
    bandwidth = 0
    event= callbackId.waitAndGet(
        event_name=(
            constants.SoftApCallbackEventName.SOFTAP_INFO_CHANGED
            ),
        timeout=_CALLBACK_TIMEOUT,
        )
    frequency = event.data[SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
    bandwidth = event.data[SOFTAP_INFO_BANDWIDTH_CALLBACK_KEY]
    ad.log.info("softap info updated, frequency is %s, bandwidth is %s",
                frequency, bandwidth)
    return frequency, bandwidth

def start_softap_and_verify(dut, client, band):
    dut.wifi.tetheringStartTrackingTetherStateChange()
    callbackId = dut.wifi.wifiRegisterSoftApCallback()
    frequency, bandwdith = get_current_softap_info(dut, callbackId)
    asserts.assert_true(frequency == 0, "Softap frequency is not reset")
    asserts.assert_true(bandwdith == 0, "Softap bandwdith is not reset")
    config = create_softap_config()
    start_wifi_tethering(dut,
                        config[constants.WiFiTethering.SSID_KEY],
                        config[constants.WiFiTethering.PWD_KEY],
                        band=band)
    asserts.assert_true(dut.wifi.wifiIsApEnabled(),
                        "SoftAp is not reported as running")
    start_wifi_connection_scan_and_ensure_network_found(
        client, config[constants.WiFiTethering.SSID_KEY])
    config = {
        "SSID": config[constants.WiFiTethering.SSID_KEY],
        "password": config[constants.WiFiTethering.PWD_KEY],
        }
    _wifi_connect(client, config, check_connectivity=False)
    frequency, bandwdith = get_current_softap_info(dut, callbackId)
    asserts.assert_true(frequency > 0, "Softap frequency is not valid")
    asserts.assert_true(bandwdith > 0, "Softap bandwdith is not valid")
    dut.wifi.tetheringStopTethering()
    dut.wifi.wifiUnregisterSoftApCallback()
    return config

def wait_for_expected_number_of_softap_clients(
        ad, callbackId, connect, expected_num_of_softap_clients):
    if connect:
        on_connected_clients_changed_event = callbackId.waitAndGet(
            event_name=(
                constants.SoftApCallbackEventName.ON_CONNECTED_CLIENTS_CHANGED
            ),
            timeout=_CALLBACK_TIMEOUT,
        )
        expected_num_of_softap_clients  = on_connected_clients_changed_event.data[
        constants.SoftApOnConnectedClientsChangedDataKey.CONNECTED_CLIENTS_COUNT]
    else:
        dis_connected_clients_changed_event = callbackId.waitAndGet(
            event_name=(
                constants.SoftApCallbackEventName.ON_CLIENTS_DISCONNECTED
            ),
            timeout=_CALLBACK_TIMEOUT,
        )
        expected_num_of_softap_clients  = dis_connected_clients_changed_event.data[
        constants.SoftApOnClientsDisconnectedDataKey.DISCONNECTED_CLIENTS_COUNT]

def convert_decimal_to_mac_address(decimal_mac):
    """
    Converts a decimal integer representation of a MAC address
    to the colon-separated hexadecimal format (e.g., 'aa:bb:cc:dd:ee:ff').
    """
    # 1. Convert the decimal integer to a hexadecimal string.
    #    'x' for hexadecimal, '012' to ensure 12 digits (6 bytes * 2 hex digits/byte)
    hex_mac = f'{decimal_mac:012x}'

    # 2. Split the hexadecimal string into pairs of characters and join with colons.
    #    Example: 'aabbccddeeff' -> ['aa', 'bb', 'cc', 'dd', 'ee', 'ff']
    #  -> 'aa:bb:cc:dd:ee:ff'
    formatted_mac = ':'.join([hex_mac[i:i+2] for i in range(0, 12, 2)])

    return formatted_mac

def convert_mac_string_to_decimal(mac_string):
    hex_mac = mac_string.replace(':', '')
    decimal_mac = int(hex_mac, 16)
    return decimal_mac


def save_wifi_soft_ap_config(ad,
                             config,
                             band=None,
                             hidden=None,
                             security=None,
                             password=None,
                             channel=None,
                             max_clients=None,
                             shutdown_timeout_enable=None,
                             shutdown_timeout_millis=None,
                             client_control_enable=None,
                             allowedList=None,
                             blockedList=None,
                             bands=None,
                             channel_frequencys=None,
                             mac_randomization_setting=None,
                             bridged_opportunistic_shutdown_enabled=None,
                             ieee80211ax_enabled=None):
    """ Save a soft ap configuration and verified
    Args:
        ad: android_device to set soft ap configuration.
        wifi_config: a soft ap configuration object, at least include SSID.
        band: specifies the band for the soft ap.
        hidden: specifies the soft ap need to broadcast its SSID or not.
        security: specifies the security type for the soft ap.
        password: specifies the password for the soft ap.
        channel: specifies the channel for the soft ap.
        max_clients: specifies the maximum connected client number.
        shutdown_timeout_enable: specifies the auto shut down enable or not.
        shutdown_timeout_millis: specifies the shut down timeout value.
        client_control_enable: specifies the client control enable or not.
        allowedList: specifies allowed clients list.
        blockedList: specifies blocked clients list.
        bands: specifies the band list for the soft ap.
        channel_frequencys: specifies the channel frequency list for soft ap.
        mac_randomization_setting: specifies the mac randomization setting.
        bridged_opportunistic_shutdown_enabled: specifies the opportunistic
                shutdown enable or not.
        ieee80211ax_enabled: specifies the ieee80211ax enable or not.
    """
    if security and password:
        config[constants.WiFiTethering.PWD_KEY] = password
        config[constants.WiFiTethering.SECURITY] = security
    if hidden is not None:
        config[constants.WiFiTethering.HIDDEN_KEY] = hidden
    if max_clients is not None:
        config[constants.WiFiTethering.AP_MAXCLIENTS_KEY] = max_clients
    if shutdown_timeout_enable is not None:
        config[constants.WiFiTethering.AP_SHUTDOWNTIMEOUTENABLE_KEY] = (
            shutdown_timeout_enable)
    if shutdown_timeout_millis is not None:
        config[constants.WiFiTethering.AP_SHUTDOWNTIMEOUT_KEY] = (
            shutdown_timeout_millis)
    if client_control_enable is not None:
        config[constants.WiFiTethering.AP_CLIENTCONTROL_KEY] = (
            client_control_enable)
    if allowedList is not None:
        config[constants.WiFiTethering.AP_ALLOWEDLIST_KEY] = allowedList
    if blockedList is not None:
        config[constants.WiFiTethering.AP_BLOCKEDLIST_KEY] = blockedList
    if mac_randomization_setting is not None:
        config[constants.WiFiTethering.AP_MAC_RANDOMIZATION_SETTING_KEY
                ] = mac_randomization_setting
    if bridged_opportunistic_shutdown_enabled is not None:
        config[
            constants.WiFiTethering.AP_BRIDGED_OPPORTUNISTIC_SHUTDOWN_ENABLE_KEY
                ] = bridged_opportunistic_shutdown_enabled
    if ieee80211ax_enabled is not None:
        config[constants.WiFiTethering.AP_IEEE80211AX_ENABLED_KEY]= (
            ieee80211ax_enabled)
    if channel_frequencys is not None:
        config[constants.WiFiTethering.AP_CHANNEL_FREQUENCYS_KEY] = (
            channel_frequencys)
    elif bands is not None:
        config[constants.WiFiTethering.AP_BANDS_KEY] = bands
    elif band is not None:
        if channel is not None:
            config[constants.WiFiTethering.AP_BAND_KEY] = band
            config[constants.WiFiTethering.AP_CHANNEL_KEY] = channel
        else:
            config[constants.WiFiTethering.AP_BAND_KEY] = band
    if constants.WiFiTethering.AP_CHANNEL_KEY in config and config[
            constants.WiFiTethering.AP_CHANNEL_KEY] == 0:
        del config[constants.WiFiTethering.AP_CHANNEL_KEY]
    if constants.WiFiTethering.SECURITY in config and config[
            constants.WiFiTethering.SECURITY] == (
                constants.SoftApSecurityType.OPEN):
        del config[constants.WiFiTethering.SECURITY]
        del config[constants.WiFiTethering.PWD_KEY]
    asserts.assert_true(ad.wifi.wifiSetWifiApConfiguration(config),
                        "Failed to set WifiAp Configuration")
    wifi_ap = ad.wifi.wifiGetSapConfiguration()
    asserts.assert_true(
        wifi_ap[constants.WiFiTethering.SSID_KEY] == (
            config[constants.WiFiTethering.SSID_KEY]),
        "Hotspot SSID doesn't match")
    if constants.WiFiTethering.SECURITY in config:
        securityType = wifi_ap["mSecurityType"]
        asserts.assert_true(
            securityType == config[constants.WiFiTethering.SECURITY],
            "Hotspot Security doesn't match")
    if constants.WiFiTethering.PWD_KEY in config:
        asserts.assert_true(
            wifi_ap["Passphrase"] == config[constants.WiFiTethering.PWD_KEY],
            "Hotspot Password doesn't match")

def set_wifi_country_code(
    ad: android_device.AndroidDevice,
    country_code: str):
    """Sets the wifi country code on the device.

        Args:
            ad: An AndroidDevice object.
            country_code: 2 letter ISO country code

        Raises:
            An RpcException if unable to set the country code.
    """
    try:
        ad.adb.shell('cmd wifi force-country-code enabled %s' % country_code)
    except android_device.adb.AdbError as e:
        ad.log.error(f"Failed to set country code: {e}")

def is_SIM_network_active(ad) -> bool:

    """Determine whether the device has access to the network.
    Args:
        ad: Android device object.

    Returns:
        bool: if have network return True, else return False。
    """
    try:
        network_type = ad.wifi.getDataNetworkType()
        return network_type != 0
    except Exception as e:
        logging.info(f"An error occurred while checking network status:{e}")
        return False

def _toggle_wifi_and_wait_for_reconnection(ad, network, num_of_tries=3):
    expected_ssid = network[constants.WiFiTethering.SSID_KEY]

    verify_con = {constants.WiFiTethering.SSID_KEY: expected_ssid}
    verify_wifi_connection_info(ad, verify_con)
    ad.wifi.wifiToggleDisable()
    time.sleep(5)
    ad.wifi.wifiToggleEnable()
    time.sleep(5)
    state_handler = ad.wifi.wifiStartTrackForStateChange()
    try:
        connect_result = None
        for i in range(num_of_tries):
            try:
                connect_result = state_handler.waitAndGet(
                    event_name="WifiNetworkConnected",
                    timeout=10)
                break
            except Empty:
                pass
        asserts.assert_true(
            connect_result, "Failed to connect to Wi-Fi network %s on %s" %
                            (network, ad.serial))
        logging.debug("Connection result on %s: %s.", ad.serial,
                      connect_result)
        actual_ssid = connect_result.data[
            constants.WiFiTethering.SSID_KEY]
        asserts.assert_equal(
            actual_ssid, expected_ssid, "Connected to the wrong network on %s."
                                        "Expected %s, but got %s." %
                                        (ad.serial, expected_ssid, actual_ssid))
        logging.info("Connected to Wi-Fi network %s on %s", actual_ssid,
                     ad.serial)
    finally:
        ad.wifi.wifiStopTrackForStateChange()

def verify_wifi_connection_info(ad, expected_con):
    current_con = ad.wifi.wifiGetConnectionInfo()
    case_insensitive = ["BSSID", "supplicant_state"]
    for k, expected_v in expected_con.items():
        # Do not verify authentication related fields.
        if k == "password":
            continue
        msg = "Field %s does not exist in wifi connection info %s." % (
            k, current_con)
        if k not in current_con:
            raise signals.TestFailure(msg)
        actual_v = current_con[k]
        if k in case_insensitive:
            actual_v = actual_v.lower()
            expected_v = expected_v.lower()
        msg = "Expected %s to be %s, actual %s is %s." % (k, expected_v, k,
                                                          actual_v)
        if actual_v != expected_v:
            raise signals.TestFailure(msg)

def _assert_on_fail_handler(func, assert_on_fail, *args, **kwargs):
    """Wrapper function that handles the bahevior of assert_on_fail.

    When assert_on_fail is True, let all test signals through, which can
    terminate test cases directly. When assert_on_fail is False, the wrapper
    raises no test signals and reports operation status by returning True or
    False.

    Args:
        func: The function to wrap. This function reports operation status by
              raising test signals.
        assert_on_fail: A boolean that specifies if the output of the wrapper
                        is test signal based or return value based.
        args: Positional args for func.
        kwargs: Name args for func.

    Returns:
        If assert_on_fail is True, returns True/False to signal operation
        status, otherwise return nothing.
    """
    try:
        func(*args, **kwargs)
        if not assert_on_fail:
            return True
    except signals.TestSignal:
        if assert_on_fail:
            raise
        return False

def toggle_wifi_and_wait_for_reconnection(ad,
                                          network,
                                          num_of_tries=1,
                                          assert_on_fail=True):
    """Toggle wifi state and then wait for Android device to reconnect to
    the provided wifi network.

    This expects the device to be already connected to the provided network.

    Logic steps are
     1. Ensure that we're connected to the network.
     2. Turn wifi off.
     3. Wait for 10 seconds.
     4. Turn wifi on.
     5. Wait for the "connected" event, then confirm the connected ssid is the
        one requested.

    Args:
        ad: android_device object to initiate connection on.
        network: A dictionary representing the network to await connection. The
                 dictionary must have the key "SSID".
        num_of_tries: An integer that is the number of times to try before
                      delaring failure. Default is 1.
        assert_on_fail: If True, error checks in this function will raise test
                        failure signals.

    Returns:
        If assert_on_fail is False, function returns True if the toggle was
        successful, False otherwise. If assert_on_fail is True, no return value.
    """
    return _assert_on_fail_handler(_toggle_wifi_and_wait_for_reconnection,
                                   assert_on_fail,
                                   ad,
                                   network,
                                   num_of_tries=num_of_tries)

def wait_for_disconnect(ad, timeout=10):
    """Wait for a disconnect event within the specified timeout.

    Args:
        ad: Android device object.
        timeout: Timeout in seconds.

    """
    try:
        state_handler = ad.wifi.wifiStartTrackForStateChange()
        connect_result = state_handler.waitAndGet(
            event_name="WifiNetworkDisconnected", timeout=timeout)
        logging.info("connect_result %s", connect_result)
    except Empty:
        raise signals.TestFailure("Device did not disconnect from the network")
    finally:
        ad.wifi.wifiStopTrackForStateChange()