

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
"""Util for aware test."""
import base64
import datetime
import json
import logging
import time
from typing import Any, Callable, Dict, List, Optional

from aware import constants

from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import errors


_WAIT_DOZE_MODE_IN_SEC = 5
_TIMEOUT_INTERVAL_IN_SEC = 1
_WAIT_WIFI_STATE_TIME_OUT = datetime.timedelta(seconds=10)
_WAIT_TIME_SEC = 3
_CONTROL_WIFI_TIMEOUT_SEC = 10


def callback_no_response(
    callback: callback_handler_v2.CallbackHandlerV2,
    event_name: str,
    timeout: int = _WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
    use_callbackid: bool = False,
    ):
  """Makes a callback call and expects no response within a given timeout.

  Args:
    callback: Snippet callback object.
    event_name: event name to wait.
    timeout: Timeout in second.
    use_callbackid: Using callbackid in eventname, default False.

  Raises:
    CallBackError: if receive response.
  """
  if use_callbackid:
    event_name += callback.callback_id
  try:
    data = callback.waitAndGet(event_name=event_name, timeout=timeout)
    raise CallBackError(f' Unexpected response {data}')
  except errors.CallbackHandlerTimeoutError:
    return


class CallBackError(Exception):
  """Error raised when there is a problem to get callback response."""

def control_wifi(
        ad: android_device.AndroidDevice,
        wifi_state: bool,
):
    """Control Android Wi-Fi status.

    Args:
      ad: Android test device.
      wifi_state: True if or Wi-Fi on False if Wi-Fi off.
      timeout_seconds: Maximum wait time (seconds), default is 10 seconds.

    Raises:
      TimeoutError: If the Wi-Fi state cannot be set within the timeout (in seconds).
    """
    if _check_wifi_status(ad) == wifi_state:
        return
    if wifi_state:
        ad.adb.shell("svc wifi enable")
    else:
        ad.adb.shell("svc wifi disable")
    start_time = time.time()
    while True:
        if _check_wifi_status(ad) == wifi_state:
            return
        # Check for timeout
        if time.time() - start_time > _CONTROL_WIFI_TIMEOUT_SEC:
            raise TimeoutError(
                f"Failed to set Wi-Fi state to {wifi_state} within {_CONTROL_WIFI_TIMEOUT_SEC} seconds."
            )

        time.sleep(1)  # Wait for a second before checking again

def _check_wifi_status(ad: android_device.AndroidDevice):
  """Check Android Wi-Fi status.

  Args:
      ad: android device object.

  Returns:
    True if wifi on, False if wifi off.
  """
  cmd = ad.adb.shell("cmd wifi status").decode("utf-8").strip()
  first_line = cmd.split("\n")[0]
  logging.info("device wifi status: %s", first_line)
  if "enabled" in first_line:
    return True
  else:
    return False


def set_doze_mode(ad: android_device.AndroidDevice, state: bool) -> bool:
  """Enables/Disables Android doze mode.

  Args:
      ad: android device object.
      state: bool, True if intent to enable Android doze mode, False otherwise.

  Returns:
    True if doze mode is enabled, False otherwise.

  Raises:
    TimeoutError: If timeout is hit.
  """
  if state:
    ad.log.info("Enables Android doze mode")
    _dumpsys(ad, "battery unplug")
    _dumpsys(ad, "deviceidle enable")
    _dumpsys(ad, "deviceidle force-idle")
    time.sleep(_WAIT_DOZE_MODE_IN_SEC)
  else:
    ad.log.info("Disables Android doze mode")
    _dumpsys(ad, "deviceidle disable")
    _dumpsys(ad, "battery reset")
  for _ in range(10 + 1):
    adb_shell_result = _dumpsys(ad, "deviceidle get deep")
    logging.info("dumpsys deviceidle get deep: %s", adb_shell_result)
    if adb_shell_result.startswith(constants.DeviceidleState.IDLE.value):
      return True
    if adb_shell_result.startswith(constants.DeviceidleState.ACTIVE.value):
      return False
    time.sleep(_TIMEOUT_INTERVAL_IN_SEC)
  # At this point, timeout must have occurred.
  raise errors.CallbackHandlerTimeoutError(
      ad, "Timed out after waiting for doze_mode set to {state}"
  )


def _dumpsys(ad: android_device.AndroidDevice, command: str) -> str:
  """Dumpsys device info.

  Args:
      ad: android device object.
      command: adb command.

  Returns:
    Android dumsys info
  """
  return ad.adb.shell(f"dumpsys {command}").decode().strip()


def check_android_os_version(
    ad: android_device.AndroidDevice,
    operator_func: Callable[[Any, Any], bool],
    android_version: constants.AndroidVersion,
    ) -> bool:
  """Compares device's Android OS version with the given one.

  Args:
    ad: Android devices.
    operator_func: Operator used in the comparison.
    android_version: The given Android OS version.

  Returns:
    bool: The comparison result.
  """
  device_os_version = int(ad.adb.shell("getprop ro.build.version.release"))
  result = False
  if isinstance(operator_func, constants.Operator):
    return operator_func.value(device_os_version, android_version)
  return result


def _get_airplane_mode(ad: android_device.AndroidDevice) -> bool:
  """Gets the airplane mode.

  Args:
    ad: android device object.

  Returns:
    True if airplane mode On, False for Off.
  """
  state = ad.adb.shell("settings get global airplane_mode_on")
  return bool(int(state))


def set_airplane_mode(ad: android_device.AndroidDevice, state: bool):
  """Sets the airplane mode to the given state.

  Args:
    ad: android device object.
    state: bool, True for Airplane mode on, False for off.
  """
  ad.adb.shell(
      ["settings", "put", "global", "airplane_mode_on", str(int(state))]
  )
  ad.adb.shell([
      "am",
      "broadcast",
      "-a",
      "android.intent.action.AIRPLANE_MODE",
      "--ez",
      "state",
      str(state),
  ])
  start_time = time.time()
  while _get_airplane_mode(ad) != state:
    time.sleep(_TIMEOUT_INTERVAL_IN_SEC)
    asserts.assert_greater(
        time.time() - start_time > _WAIT_TIME_SEC,
        f"Failed to set airplane mode to: {state}",
    )


def decode_list(list_of_b64_strings: List[str]) -> List[bytes]:
  """Converts the list of b64 encoded strings to a list of bytearray.

  Args:
    list_of_b64_strings: A list of strings, each of which is b64 encoded array.

  Returns:
    A list of bytearrays.
  """
  decoded_list = []
  for string_item in list_of_b64_strings:
    decoded_list.append(base64.b64decode(string_item))
  return decoded_list


def encode_list(
    list_of_objects: List[Any]) -> List[str]:
  """Converts a list of strings/bytearrays to a list of b64 encoded bytearrays.

  A None object is treated as a zero-length bytearray.

  Args:
    list_of_objects: A list of strings or bytearray objects.
  Returns:
    A list of the same objects, converted to bytes and b64 encoded.
  """
  encoded_list = []
  for obj in list_of_objects:
    if obj is None:
      obj = bytes()
    if isinstance(obj, str):
      encoded_list.append(base64.b64encode(bytes(obj, "utf-8")).decode("utf-8"))
    else:
      encoded_list.append(base64.b64encode(bytes(obj)).decode("utf-8"))
  return encoded_list


def construct_max_match_filter(max_size: int)-> List[bytes]:
  """Constructs a maximum size match filter that fits into the 'max_size' bytes.

  Match filters are a set of LVs (Length, Value pairs) where L is 1 byte. The
  maximum size match filter will contain max_size/2 LVs with all Vs (except
  possibly the last one) of 1 byte, the last V may be 2 bytes for odd max_size.

  Args:
    max_size: Maximum size of the match filter.
  Returns:
    A list of bytearrays.
  """
  mf_list = []
  num_lvs = max_size // 2
  for i in range(num_lvs - 1):
    mf_list.append(bytes([i]))
  if max_size % 2 == 0:
    mf_list.append(bytes([255]))
  else:
    mf_list.append(bytes([254, 255]))
  return mf_list


def validate_forbidden_callbacks(ad: android_device.AndroidDevice,
                                 limited_cb: Optional[Dict[str, int]] = None
                                ) -> None:
  """Validate the specified callbacks have not been called more than permitted.

  In addition to the input configuration also validates that forbidden callbacks
  have never been called.

  Args:
    ad: Device on which to run.
    limited_cb: Dictionary of CB_EV_* ids and maximum permitted calls (0
                meaning never).
  Raises:
    CallBackError: If forbidden callbacks are triggered.
  """
  cb_data = json.loads(ad.adb.shell("cmd wifiaware native_cb get_cb_count"))
  if limited_cb is None:
    limited_cb = {}
  # Add callbacks which should never be called.
  limited_cb["5"] = 0
  fail = False
  for cb_event in limited_cb.keys():
    if cb_event in cb_data:
      if cb_data[cb_event] > limited_cb[cb_event]:
        fail = True
        ad.log.info(
            "Callback %s observed %d times: more than permitted %d times",
            cb_event, cb_data[cb_event], limited_cb[cb_event])
        break
  if fail:
    raise CallBackError("Forbidden callbacks observed.")


def reset_device_parameters(ad: android_device.AndroidDevice):
  """Reset device configurations which may have been set by tests.
  Should be done before tests start (in case previous one was killed
  without tearing down) and after they end (to leave device in usable
  state).

  Args:
    ad: device to be reset
  """
  ad.adb.shell("cmd wifiaware reset")


def reset_device_statistics(ad: android_device.AndroidDevice,):
  """Reset device statistics.

  Args:
    ad: device to be reset
  """
  ad.adb.shell("cmd wifiaware native_cb get_cb_count --reset")

def get_aware_capabilities(ad: android_device.AndroidDevice):
    """Get the Wi-Fi Aware capabilities from the specified device. The
  capabilities are a dictionary keyed by aware_const.CAP_* keys.

  Args:
    ad: the Android device
  Returns: the capability dictionary.
  """
    return json.loads(ad.adb.shell('cmd wifiaware state_mgr get_capabilities'))

def create_discovery_config(service_name,
                            p_type=None,
                            s_type=None,
                            ssi=None,
                            match_filter=None,
                            match_filter_list=None,
                            ttl=0,
                            term_cb_enable=True,
                            instant_mode=None):
    """Create a publish discovery configuration based on input parameters.

    Args:
        service_name: Service name - required
        d_type: Discovery type (publish or subscribe constants)
        ssi: Supplemental information - defaults to None
        match_filter, match_filter_list: The match_filter, only one mechanism can
                                     be used to specify. Defaults to None.
        ttl: Time-to-live - defaults to 0 (i.e. non-self terminating)
        term_cb_enable: True (default) to enable callback on termination, False
                      means that no callback is called when session terminates.
        instant_mode: set the band to use instant communication mode, 2G or 5G
    Returns:
        publish discovery configuration object.
    """
    config = {}
    config[constants.SERVICE_NAME] = service_name
    if p_type is not None:
      config[constants.PUBLISH_TYPE] = p_type
    if s_type is not None:
      config[constants.SUBSCRIBE_TYPE] = s_type
    if ssi is not None:
        config[constants.SERVICE_SPECIFIC_INFO] = ssi
    if match_filter is not None:
        config[constants.MATCH_FILTER] = match_filter
    if match_filter_list is not None:
        config[constants.MATCH_FILTER_LIST] = match_filter_list
    if instant_mode is not None:
        config[constants.INSTANTMODE_ENABLE] = instant_mode
    config[constants.TTL_SEC] = ttl
    config[constants.TERMINATE_NOTIFICATION_ENABLED] = term_cb_enable
    return config
