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

from collections.abc import Sequence
import dataclasses
import datetime
import logging

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import errors
import wifi_test_utils

from direct import constants

_DEFAULT_TIMEOUT = datetime.timedelta(seconds=30)


@dataclasses.dataclass(frozen=True)
class DeviceState:
    """All objects related to operating p2p snippet RPCs.

    Attributes:
        ad: The Android device controller object.
        p2p_device: The object that represents a Wi-Fi p2p device.
    """

    ad: android_device.AndroidDevice
    p2p_device: constants.WifiP2pDevice
    broadcast_receiver: callback_handler_v2.CallbackHandlerV2


def _setup_wifi_p2p(ad: android_device.AndroidDevice) -> DeviceState:
    """Sets up Wi-Fi p2p for automation tests on an Android device."""
    broadcast_receiver = _init_wifi_p2p(ad)
    _delete_all_persistent_groups(ad)
    p2p_device = _get_p2p_device(ad)
    asserts.assert_not_equal(
        p2p_device.device_address,
        constants.ANONYMIZED_MAC_ADDRESS,
        'Failed to get p2p device MAC address, please check permissions '
        'required by API WifiP2pManager#requestConnectionInfo',
    )
    return DeviceState(
        ad=ad, p2p_device=p2p_device, broadcast_receiver=broadcast_receiver
    )


def _init_wifi_p2p(
    ad: android_device.AndroidDevice,
) -> callback_handler_v2.CallbackHandlerV2:
    """Registers the snippet app with the Wi-Fi p2p framework.

    This must be the first to be called before any p2p operations are performed.

    Args:
        ad: The Android device controller object.

    Returns:
        The broadcast receiver from which you can get snippet events
        corresponding to Wi-Fi p2p intents received on device.
    """
    broadcast_receiver = ad.wifi.wifiP2pInitialize()
    init_event = broadcast_receiver.waitAndGet(
        event_name=constants.WIFI_P2P_STATE_CHANGED_ACTION,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    state = constants.ExtraWifiState(
        init_event.data[constants.EXTRA_WIFI_STATE]
    )
    asserts.assert_equal(
        state,
        constants.ExtraWifiState.WIFI_P2P_STATE_ENABLED,
        f'Failed to initialize Wi-Fi P2P, state: {state}',
    )
    return broadcast_receiver


def _capture_p2p_intents(
    ad: android_device.AndroidDevice,
) -> callback_handler_v2.CallbackHandlerV2:
    """Starts capturing Wi-Fi p2p intents and returns the intent receiver."""
    broadcast_receiver = ad.wifi.wifiP2pCaptureP2pIntents()
    return broadcast_receiver


def _delete_all_persistent_groups(
    ad: android_device.AndroidDevice,
) -> None:
    """Deletes all persistent Wi-Fi p2p groups."""
    groups = _request_persistent_group_info(ad)
    ad.log.debug('Wi-Fi p2p persistent groups before delete: %s', groups)
    for group in groups:
        result_data = ad.wifi.wifiP2pDeletePersistentGroup(group.network_id)
        result = result_data[constants.EVENT_KEY_CALLBACK_NAME]
        if result != constants.ACTION_LISTENER_ON_SUCCESS:
            reason = constants.ActionListenerOnFailure(
                result_data[constants.EVENT_KEY_REASON]
            )
            raise RuntimeError(
                'Failed to delete persistent group with network id '
                f'{group.network_id}. Reason: {reason.name}'
            )
    groups = _request_persistent_group_info(ad)
    ad.log.debug('Wi-Fi p2p persistent groups after delete: %s', groups)


def _request_persistent_group_info(
    ad: android_device.AndroidDevice,
) -> Sequence[constants.WifiP2pGroup]:
    """Requests persistent group information."""
    callback_handler = ad.wifi.wifiP2pRequestPersistentGroupInfo()
    event = callback_handler.waitAndGet(
        event_name=constants.ON_PERSISTENT_GROUP_INFO_AVAILABLE,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    groups = constants.WifiP2pGroup.from_dict_list(event.data['groupList'])
    return groups


def _get_p2p_device(
    ad: android_device.AndroidDevice,
) -> constants.WifiP2pDevice:
    """Gets the Wi-Fi p2p device information."""
    callback_handler = ad.wifi.wifiP2pRequestDeviceInfo()
    event = callback_handler.waitAndGet(
        event_name=constants.ON_DEVICE_INFO_AVAILABLE,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    return constants.WifiP2pDevice.from_dict(
        event.data[constants.EVENT_KEY_P2P_DEVICE]
    )


def _find_p2p_device(
    requester: DeviceState,
    responder: DeviceState,
) -> constants.WifiP2pDevice:
    """Initiates Wi-Fi p2p discovery for the requester to find the responder.

    This initiates Wi-Fi p2p discovery on both devices and checks that the
    requester can discover responder and return peer p2p device.
    """
    requester.ad.log.debug('Discovering Wi-Fi P2P peers.')
    responder.ad.wifi.wifiP2pDiscoverPeers()

    _clear_events(requester, constants.WIFI_P2P_PEERS_CHANGED_ACTION)
    requester.ad.wifi.wifiP2pDiscoverPeers()

    event = requester.broadcast_receiver.waitAndGet(
        event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    requester_peers = constants.WifiP2pDevice.from_dict_list(
        event.data[constants.EVENT_KEY_PEER_LIST]
    )

    responder_mac = responder.p2p_device.device_address
    filtered_peers = [
        peer for peer in requester_peers if peer.device_address == responder_mac
    ]
    if len(filtered_peers) == 0:
        asserts.fail(
            f'{requester.ad} did not find the responder device. Responder MAC '
            f'address: {responder_mac}, found peers: {requester_peers}.'
        )
    if len(filtered_peers) > 1:
        asserts.fail(
            f'{requester.ad} found more than one responder device. Responder '
            f'MAC address: {responder_mac}, found peers: {requester_peers}.'
        )
    return filtered_peers[0]


def _p2p_connect_with_push_button(
    requester: DeviceState,
    responder: DeviceState,
) -> constants.WifiP2pDevice:
    """Establishes Wi-Fi p2p connection with WPS push button configuration.

    This initiates p2p connection on requester, accepts invitation on responder,
    and checks connection status on both devices.

    Args:
        requester: The requester device.
        responder: The respodner device.

    Returns:
        The peer p2p device found on the requester.
    """
    logging.info('Establishing a p2p connection through WPS PBC.')

    # Clear events in broadcast receiver.
    _clear_events(requester, constants.WIFI_P2P_PEERS_CHANGED_ACTION)
    _clear_events(requester, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    _clear_events(responder, constants.WIFI_P2P_PEERS_CHANGED_ACTION)
    _clear_events(responder, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)

    # Send P2P connect invitation from requester.
    config = constants.WifiP2pConfig(
        device_address=responder.p2p_device.device_address,
        wps_setup=constants.WpsInfo.PBC,
    )
    requester.ad.wifi.wifiP2pConnect(config.to_dict())
    requester.ad.log.info(
        'Successfully sent P2P connect invitation to responder.'
    )

    # Click accept button on responder.
    responder.ad.wifi.wifiP2pAcceptInvitation(requester.p2p_device.device_name)
    responder.ad.log.info('Accepted connect invitation.')

    # Check p2p status on requester.
    _wait_connection_notice(requester.broadcast_receiver)
    _wait_peer_connected(
        requester.broadcast_receiver,
        responder.p2p_device.device_address,
    )
    requester.ad.log.debug(
        'Connected with device %s through wifi p2p.',
        responder.p2p_device.device_address,
    )

    # Check p2p status on responder.
    _wait_connection_notice(responder.broadcast_receiver)
    _wait_peer_connected(
        responder.broadcast_receiver,
        requester.p2p_device.device_address,
    )
    responder.ad.log.debug(
        'Connected with device %s through wifi p2p.',
        requester.p2p_device.device_address,
    )

    logging.info('Established wifi p2p connection.')


def _wait_peer_connected(
    broadcast_receiver: callback_handler_v2.CallbackHandlerV2, peer_address: str
):
    """Waits for event that indicates expected Wi-Fi p2p peer is connected."""

    def _is_peer_connected(event):
        devices = constants.WifiP2pDevice.from_dict_list(event.data['peerList'])
        for device in devices:
            if (
                device.device_address == peer_address
                and device.status == constants.WifiP2pDeviceStatus.CONNECTED
            ):
                return True
        return False

    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
        predicate=_is_peer_connected,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _wait_connection_notice(
    broadcast_receiver: callback_handler_v2.CallbackHandlerV2,
):
    """Waits for event that indicates a p2p connection is established."""

    def _is_group_formed(event):
        try:
            p2p_info = constants.WifiP2pInfo.from_dict(
                event.data[constants.EVENT_KEY_P2P_INFO]
            )
            return p2p_info.group_formed
        except KeyError:
            return False

    event = broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_CONNECTION_CHANGED_ACTION,
        predicate=_is_group_formed,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _remove_group_and_verify_disconnected(
    requester: DeviceState,
    responder: DeviceState,
):
    """Stops p2p connection and verifies disconnection status on devices."""
    logging.info('Stopping wifi p2p connection.')

    # Clear events in broadcast receiver.
    _clear_events(requester, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    _clear_events(requester, constants.ON_DEVICE_INFO_AVAILABLE)
    _clear_events(responder, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    _clear_events(responder, constants.ON_DEVICE_INFO_AVAILABLE)

    # Requester initiates p2p group removal.
    requester.ad.wifi.wifiP2pRemoveGroup()

    # Check p2p status on requester.
    _wait_disconnection_notice(requester.broadcast_receiver)
    _wait_peer_disconnected(
        requester.broadcast_receiver, responder.p2p_device.device_address
    )
    requester.ad.log.debug(
        'Disconnected with device %s through wifi p2p.',
        responder.p2p_device.device_address,
    )

    # Check p2p status on responder.
    _wait_disconnection_notice(responder.broadcast_receiver)
    _wait_peer_disconnected(
        responder.broadcast_receiver, requester.p2p_device.device_address
    )
    responder.ad.log.debug(
        'Disconnected with device %s through wifi p2p.',
        requester.p2p_device.device_address,
    )

    logging.info('Stopped wifi p2p connection.')


def _wait_disconnection_notice(broadcast_receiver):
    """Waits for event that indicates the p2p connection is disconnected."""

    def _is_disconnect_event(event):
        info = constants.WifiP2pInfo.from_dict(
            event.data[constants.EVENT_KEY_P2P_INFO]
        )
        return not info.group_formed

    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_CONNECTION_CHANGED_ACTION,
        predicate=_is_disconnect_event,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _wait_peer_disconnected(broadcast_receiver, target_address):
    """Waits for event that indicates current Wi-Fi p2p peer is disconnected."""

    def _is_peer_disconnect_event(event):
        devices = constants.WifiP2pDevice.from_dict_list(
            event.data[constants.EVENT_KEY_PEER_LIST]
        )
        for device in devices:
            if device.device_address == target_address:
                return device.status != constants.WifiP2pDeviceStatus.CONNECTED
        # Target device not found also means it is disconnected.
        return True

    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
        predicate=_is_peer_disconnect_event,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _clear_events(device: DeviceState, event_name):
    """Clears the events with the given name in the broadcast receiver."""
    all_events = device.broadcast_receiver.getAll(event_name)
    device.ad.log.debug(
        'Cleared %d events of event name %s', len(all_events), event_name
    )


def _teardown_wifi_p2p(ad: android_device.AndroidDevice):
    """Destroys all resources initialized in `_setup_wifi_p2p`."""
    ad.wifi.wifiP2pStopPeerDiscovery()
    ad.wifi.wifiP2pCancelConnect()
    ad.wifi.wifiP2pRemoveGroup()
    ad.wifi.p2pClose()


class GroupOwnerNegotiationTest(base_test.BaseTestClass):
    """Group owner negotiation tests."""

    ads: Sequence[android_device.AndroidDevice]
    requester_ad: android_device.AndroidDevice
    responder_ad: android_device.AndroidDevice

    def setup_class(self) -> None:
        super().setup_class()
        self.ads = self.register_controller(android_device, min_number=2)
        self.responder_ad, self.requester_ad, *_ = self.ads
        self.responder_ad.debug_tag = f'{self.responder_ad.serial}(Responder)'
        self.requester_ad.debug_tag = f'{self.requester_ad.serial}(Requester)'
        utils.concurrent_exec(
            self._setup_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def _setup_device(self, ad: android_device.AndroidDevice) -> DeviceState:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    def test_group_owner_negotiation_with_push_button(self) -> None:
        """Test against group owner negotiation and WPS PBC (push button).

        Steps:
            1. Initialize Wi-Fi p2p on both responder and requester device.
            2. Initiate p2p discovery. Requester should be able to find
               the responder.
            3. Establish a p2p connection with WPS PBC (push button
               configuration). Requester initiates a connection request.
               Responder clicks accept button to accept the connection.
            4. Stop the connection.
        """
        responder = _setup_wifi_p2p(self.responder_ad)
        requester = _setup_wifi_p2p(self.requester_ad)

        requester_peer_p2p_device = _find_p2p_device(requester, responder)

        # Make sure that peer is not a group owner (GO) as this is testing
        # against GO negotiation.
        asserts.assert_false(
            requester_peer_p2p_device.is_group_owner,
            f'{requester} found target responder device with invalid role.'
            ' It should not be group owner.',
        )

        _p2p_connect_with_push_button(requester, responder)

        _remove_group_and_verify_disconnected(requester, responder)

    def _teardown_device(self, ad: android_device.AndroidDevice):
        _teardown_wifi_p2p(ad)
        ad.services.create_output_excerpts_all(self.current_test_info)

    def teardown_test(self) -> None:
        utils.concurrent_exec(
            self._teardown_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )


if __name__ == '__main__':
    test_runner.main()
