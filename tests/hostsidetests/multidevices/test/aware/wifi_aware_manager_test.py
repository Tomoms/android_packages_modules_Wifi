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
"""CTS-V Wi-Fi Aware test reimplemented in Mobly."""
import datetime
import enum
import logging
import random
import sys
import time
from typing import Tuple, Any

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event
import wifi_test_utils

from aware import constants
from aware import aware_lib_utils

PACKAGE_NAME = constants.WIFI_SNIPPET_PACKAGE_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000
_MSG_ID_SUB_TO_PUB = random.randint(1000, 5000)
_MSG_ID_PUB_TO_SUB = random.randint(5001, 9999)
_MSG_SUB_TO_PUB = "Let's talk [Random Identifier: %s]" % utils.rand_ascii_str(5)
_MSG_PUB_TO_SUB = 'Ready [Random Identifier: %s]' % utils.rand_ascii_str(5)
_PUB_SSI = constants.WifiAwareTestConstants.PUB_SSI
_MATCH_FILTER = [constants.WifiAwareTestConstants.MATCH_FILTER_BYTES]
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)
_LARGE_ENOUGH_DISTANCE_MM = 100000  # 100 meters
_MIN_RSSI = -100
_WAIT_SEC_FOR_RTT_INITIATOR_RESPONDER_SWITCH = 5


@enum.unique
class AttachCallBackMethodType(enum.StrEnum):
    """Represents Attach Callback Method Type in Wi-Fi Aware.

    https://developer.android.com/reference/android/net/wifi/aware/AttachCallback
    """
    ATTACHED = 'onAttached'
    ATTACH_FAILED = 'onAttachFailed'
    AWARE_SESSION_TERMINATED = 'onAwareSessionTerminated'


class WifiAwareManagerTest(base_test.BaseTestClass):
    """Wi-Fi Aware test class."""

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    # Wi-Fi Aware attach session ID
    publisher_attach_session: str | None = None
    subscriber_attach_session: str | None = None
    # Wi-Fi Aware discovery session ID
    publish_session: str | None = None
    subscribe_session: str | None = None
    # Wi-Fi Aware peer ID
    publisher_peer: int | None = None
    subscriber_peer: int | None = None
    # Mac addresses.
    publisher_mac: str | None = None
    subscriber_mac: str | None = None

    def setup_class(self):
        # Register and set up Android devices in parallel.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device: android_device.AndroidDevice):
            device.load_snippet(
                'wifi_aware_snippet', PACKAGE_NAME
            )
            aware_lib_utils.control_wifi(device, wifi_state=True)
            asserts.abort_all_if(
                not device.wifi_aware_snippet.wifiAwareIsSupported(),
                f'{device} does not support Wi-Fi Aware.',
            )
            wifi_test_utils.set_screen_on_and_unlock(device)
            asserts.abort_all_if(
                not device.wifi_aware_snippet.wifiAwareIsAvailable(),
                f'{device} Wi-Fi Aware is not available.',
            )

        utils.concurrent_exec(
            setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

    def test_data_path_open_unsolicited_pub_and_passive_sub(self) -> None:
        """Test OPEN Wi-Fi Aware network with unsolicited publish and passive subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
        )

    def test_data_path_passphrase_unsolicited_pub_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware network with passphrase, unsolicited publish, and passive subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
            )
        )

    def test_data_path_pmk_unsolicited_pub_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware network using PMK with unsolicited publish and passive subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
                pmk=constants.WifiAwareTestConstants.PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=constants.WifiAwareTestConstants.PMK
                )
            )
        )

    def test_data_path_open_solicited_pub_and_active_sub(self) -> None:
        """Test OPEN Wi-Fi Aware network with solicited publish and active subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),

        )

    def test_data_path_passphrase_solicited_pub_and_active_sub(self) -> None:
        """Test password-protected Wi-Fi Aware network with solicited publish and active subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
            )
        )

    def test_data_path_pmk_solicited_pub_and_active_sub(self) -> None:
        """Test Wi-Fi Aware network using PMK with solicited publish and active subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
                pmk=constants.WifiAwareTestConstants.PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=constants.WifiAwareTestConstants.PMK
                )
            )
        )

    def test_data_path_open_unsolicited_pub_accept_any_and_passive_sub(self) -> None:
        """Test OPEN Wi-Fi Aware with unsolicited publish (accept any peer) and passive subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """
        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            is_pub_accept_any_peer=True,
        )

    def test_data_path_passphrase_unsolicited_pub_accept_any_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware with passphrase unsolicited publish (accept any), and passive subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """
        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
            ),
            is_pub_accept_any_peer=True,
        )

    def test_data_path_pmk_unsolicited_pub_accept_any_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware with PMK, unsolicited publish (accept any), and passive subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """
        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
                pmk=constants.WifiAwareTestConstants.PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=constants.WifiAwareTestConstants.PMK
                )
            ),
            is_pub_accept_any_peer=True,
        )

    def test_data_path_open_solicited_pub_accept_any_active_sub(self) -> None:
        """Test Wi-Fi Aware with open network, solicited publish (accept any), and active subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """
        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            is_pub_accept_any_peer=True,
        )

    def test_data_passphrase_solicited_pub_accept_any_and_active_sub(self) -> None:
        """Test Wi-Fi Aware with passphrase, solicited publish (accept any), and active subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """
        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=constants.WifiAwareTestConstants.PASSWORD,
            ),
            is_pub_accept_any_peer=True,
        )

    def test_data_path_pmk_solicited_pub_accept_any_and_active_sub(self) -> None:
        """Test Wi-Fi Aware with PMK, solicited publish (accept any), and active subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
                pmk=constants.WifiAwareTestConstants.PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=constants.WifiAwareTestConstants.PMK
                )
            ),
            is_pub_accept_any_peer=True,
        )

    def test_discovery_ranging_to_peer_handle(self) -> None:
        """Test ranging to a Wi-Fi Aware peer handle.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Test ranging to Wi-Fi Aware peer handle.
        """
        # Check test condition.
        self._skip_if_wifi_rtt_is_not_supported()

        # Step 1 - 3. Publish and subscribe Wi-Fi Aware service.
        self._publish_and_subscribe(
            pub_config=constants.PublishConfig(
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=True,
            ),
            sub_config=constants.SubscribeConfig(
                subscribe_type=constants.SubscribeType.PASSIVE,
                max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            ),
        )

        # 4. Perform ranging on the publisher and subscriber, respectively.
        self.publisher.log.info(
            'Performing ranging to peer ID %d.',  self.publisher_peer
        )
        self._perform_ranging(
            self.publisher,
            constants.RangingRequest(peer_ids=[self.publisher_peer]),
        )

        # RTT initiator/responder role switch takes time. We don't have an
        # API to enforce it. So wait a few seconds for a semi-arbitrary
        # teardown.
        time.sleep(_WAIT_SEC_FOR_RTT_INITIATOR_RESPONDER_SWITCH)
        self.subscriber.log.info(
            'Performing ranging to peer ID %d.', self.subscriber_peer
        )
        self._perform_ranging(
            self.subscriber,
            constants.RangingRequest(peer_ids=[self.subscriber_peer]),
        )

    def test_discovery_ranging_to_peer_mac_address(self) -> None:
        """Test ranging to a Wi-Fi Aware peer mac address.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Test ranging to Wi-Fi Aware peer mac address.
        """
        # Check test condition.
        self._skip_if_wifi_rtt_is_not_supported()

        # Step 1 - 3. Publish and subscribe Wi-Fi Aware service.
        self._publish_and_subscribe(
            pub_config=constants.PublishConfig(
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=True,
            ),
            sub_config=constants.SubscribeConfig(
                subscribe_type=constants.SubscribeType.PASSIVE,
                max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            ),
        )

        # 4. Perform ranging on the publisher and subscriber, respectively.
        self.publisher.log.info(
            'Performing ranging to peer MAC address %s.', self.subscriber_mac
        )
        self._perform_ranging(
            self.publisher,
            constants.RangingRequest(peer_mac_addresses=[self.subscriber_mac]),
        )

        # RTT initiator/responder role switch takes time. We don't have an
        # API to enforce it. So wait a few seconds for a semi-arbitrary
        # teardown.
        time.sleep(_WAIT_SEC_FOR_RTT_INITIATOR_RESPONDER_SWITCH)
        self.subscriber.log.info(
            'Performing ranging to peer MAC address %s.', self.publisher_mac
        )
        self._perform_ranging(
            self.subscriber,
            constants.RangingRequest(peer_mac_addresses=[self.publisher_mac]),
        )

    def test_data_path_force_channel_setup(self):
        """ Test Wi-Fi Aware with PMK, force channel publish, and subscribe.

        Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publish and subscribe to a discovery session.
        3. Send messages through discovery session’s API.
        4. Request a Wi-Fi Aware network.
        5. Establish a socket connection and send messages through it.
        """
        # The support of this function depends on the chip used.
        asserts.skip_if(
            not self.publisher.wifi_aware_snippet.wifiAwareIsSetChannelOnDataPathSupported(),
            'Publish device not support this test feature.'
        )
        asserts.skip_if(
            not self.subscriber.wifi_aware_snippet.wifiAwareIsSetChannelOnDataPathSupported(),
            'Subscriber device not support this test feature.'
        )

        self._test_wifi_aware(
            pub_config=constants.PublishConfig(
                service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
                match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP,
                pmk=constants.WifiAwareTestConstants.PMK,
                channel_frequency_m_hz=constants.WifiAwareTestConstants.CHANNEL_IN_MHZ,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=constants.WifiAwareTestConstants.PMK
                ),
                channel_frequency_m_hz=constants.WifiAwareTestConstants.CHANNEL_IN_MHZ,
            )
        )

    def _test_wifi_aware(
        self,
        pub_config: constants.PublishConfig,
        sub_config: constants.SubscribeConfig,
        network_specifier_on_pub: constants.WifiAwareNetworkSpecifier | None = None,
        network_specifier_on_sub: constants.WifiAwareNetworkSpecifier | None = None,
        is_pub_accept_any_peer: bool = False,
    ):
        """Tests Wi-Fi Aware using given publish and subscribe configurations."""
        # Step 1 - 3: Publish and subscribe Wi-Fi Aware service and send
        # messages through a Wi-Fi Aware session.
        self._publish_and_subscribe(pub_config, sub_config)

        # 4. Request a Wi-Fi Aware network.
        pub_accept_handler = self.publisher.wifi_aware_snippet.connectivityServerSocketAccept()
        network_id = pub_accept_handler.callback_id
        pub_local_port = pub_accept_handler.ret_value
        if network_specifier_on_pub and (
            network_specifier_on_pub.psk_passphrase or network_specifier_on_pub.pmk):
            network_specifier_on_pub.port = pub_local_port
        pub_network_cb_handler = self._request_network(
            ad=self.publisher,
            discovery_session=self.publish_session,
            peer=self.publisher_peer,
            net_work_request_id=network_id,
            network_specifier_params=network_specifier_on_pub,
            is_accept_any_peer=is_pub_accept_any_peer,
        )
        sub_network_cb_handler = self._request_network(
            ad=self.subscriber,
            discovery_session=self.subscribe_session,
            peer=self.subscriber_peer,
            net_work_request_id=network_id,
            network_specifier_params=network_specifier_on_sub,
        )
        expected_channel = None
        if (network_specifier_on_pub and
                network_specifier_on_pub.channel_frequency_m_hz and
                network_specifier_on_sub and
                network_specifier_on_sub.channel_frequency_m_hz):
            expected_channel = network_specifier_on_sub.channel_frequency_m_hz
        self._wait_for_network(
            ad=self.publisher,
            request_network_cb_handler=pub_network_cb_handler,
            expected_channel=None,
        )
        self._wait_for_network(
            ad=self.subscriber,
            request_network_cb_handler=sub_network_cb_handler,
            expected_channel=expected_channel,
        )
        # 5. Establish a socket connection and send messages through it.
        self._establish_socket_and_send_msg(
            pub_accept_handler=pub_accept_handler,
            network_id=network_id,
            pub_local_port=pub_local_port

        )
        self.publisher.wifi_aware_snippet.connectivityUnregisterNetwork(
            network_id
        )
        self.subscriber.wifi_aware_snippet.connectivityUnregisterNetwork(
            network_id
        )
        self.publisher.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            self.publish_session
        )
        self.publish_session = None
        self.subscriber.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            self.subscribe_session
        )
        self.subscribe_session = None
        self.publisher.wifi_aware_snippet.wifiAwareDetach(
            self.publisher_attach_session
        )
        self.publisher_attach_session = None
        self.subscriber.wifi_aware_snippet.wifiAwareDetach(
            self.subscriber_attach_session
        )
        self.subscriber_attach_session = None
        self.publisher.wifi_aware_snippet.connectivityCloseAllSocket(network_id)
        self.subscriber.wifi_aware_snippet.connectivityCloseAllSocket(network_id)

    def _publish_and_subscribe(self, pub_config, sub_config):
        """Publishes and subscribes a Wi-Fi Aware session."""
        # 1. Attach Wi-Fi Aware sessions.
        self.publisher_attach_session, self.publisher_mac = (
            self._start_attach(
                self.publisher, is_ranging_enabled=pub_config.ranging_enabled
            )
        )
        self.subscriber_attach_session, self.subscriber_mac = (
            self._start_attach(
                self.subscriber, is_ranging_enabled=pub_config.ranging_enabled
            )
        )

        # 2.1. Initialize discovery sessions (publish and subscribe).
        pub_aware_session_cb_handler = self._start_publish(
            attach_session_id=self.publisher_attach_session,
            pub_config=pub_config,
        )
        self.publish_session = pub_aware_session_cb_handler.callback_id
        self.publisher.log.info('Created the publish session.')
        sub_aware_session_cb_handler = self._start_subscribe(
            attach_session_id=self.subscriber_attach_session,
            sub_config=sub_config,
        )
        self.subscribe_session = sub_aware_session_cb_handler.callback_id
        self.subscriber.log.info('Subscribe session created.')
        # 2.2. Wait for discovery.
        self.subscriber_peer = self._wait_for_discovery(
            sub_aware_session_cb_handler,
            pub_service_specific_info=pub_config.service_specific_info,
            is_ranging_enabled=pub_config.ranging_enabled,
        )
        self.subscriber.log.info('Subscriber discovered the published service.')
        # 3. Send messages through discovery session’s API.
        self.publisher_peer = self._send_msg_through_discovery_session(
            sender=self.subscriber,
            sender_aware_session_cb_handler=sub_aware_session_cb_handler,
            receiver=self.publisher,
            receiver_aware_session_cb_handler=pub_aware_session_cb_handler,
            discovery_session=self.subscribe_session,
            peer=self.subscriber_peer,
            send_message=_MSG_SUB_TO_PUB,
            send_message_id=_MSG_ID_SUB_TO_PUB,
        )
        logging.info(
            'The subscriber sent a message and the publisher received it.'
        )
        self._send_msg_through_discovery_session(
            sender=self.publisher,
            sender_aware_session_cb_handler=pub_aware_session_cb_handler,
            receiver=self.subscriber,
            receiver_aware_session_cb_handler=sub_aware_session_cb_handler,
            discovery_session=self.publish_session,
            peer=self.publisher_peer,
            send_message=_MSG_PUB_TO_SUB,
            send_message_id=_MSG_ID_PUB_TO_SUB,
        )
        logging.info(
            'The publisher sent a message and the subscriber received it.'
        )

    def _establish_socket_and_send_msg(
        self,
        pub_accept_handler: callback_handler_v2.CallbackHandlerV2,
        network_id: str,
        pub_local_port: int
    ):
        """Handles socket-based communication between publisher and subscriber."""
        # Init socket
        # Create a ServerSocket and makes it listen for client connections.
        self.subscriber.wifi_aware_snippet.connectivityCreateSocketOverWiFiAware(
            network_id, pub_local_port
        )
        self._wait_accept_success(pub_accept_handler)
        # Subscriber Send socket data
        self.subscriber.log.info('Subscriber create a socket.')
        self._send_socket_msg(
            sender_ad=self.subscriber,
            receiver_ad=self.publisher,
            msg=constants.WifiAwareTestConstants.MSG_CLIENT_TO_SERVER,
            send_callback_id=network_id,
            receiver_callback_id=network_id
        )
        self._send_socket_msg(
            sender_ad=self.publisher,
            receiver_ad=self.subscriber,
            msg=constants.WifiAwareTestConstants.MSG_SERVER_TO_CLIENT,
            send_callback_id=network_id,
            receiver_callback_id=network_id
        )
        self.publisher.wifi_aware_snippet.connectivityCloseWrite(network_id)
        self.subscriber.wifi_aware_snippet.connectivityCloseWrite(network_id)
        self.publisher.wifi_aware_snippet.connectivityCloseRead(network_id)
        self.subscriber.wifi_aware_snippet.connectivityCloseRead(network_id)
        logging.info('Communicated through socket connection of Wi-Fi Aware network successfully.')

    def _wait_accept_success(
        self,
        pub_accept_handler: callback_handler_v2.CallbackHandlerV2
    ) -> None:
        pub_accept_event = pub_accept_handler.waitAndGet(
            event_name=constants.SnippetEventNames.SERVER_SOCKET_ACCEPT,
            timeout=_DEFAULT_TIMEOUT
        )
        is_accept = pub_accept_event.data.get(constants.SnippetEventParams.IS_ACCEPT, False)
        if not is_accept:
            error = pub_accept_event.data[constants.SnippetEventParams.ERROR]
            asserts.fail(
                f'{self.publisher} Failed to accept the connection. Error: {error}'
            )

    def _start_attach(
        self,
        ad: android_device.AndroidDevice,
        is_ranging_enabled: bool,
    ) -> str:
        """Starts the attach process on the provided device."""
        attach_handler = ad.wifi_aware_snippet.wifiAwareAttached(
            is_ranging_enabled
        )
        attach_event = attach_handler.waitAndGet(
            event_name=AttachCallBackMethodType.ATTACHED,
            timeout=_DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(attach_event.callback_id),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )
        mac_address = None
        if is_ranging_enabled:
            identity_changed_event = attach_handler.waitAndGet(
                event_name='WifiAwareAttachOnIdentityChanged',
                timeout=_DEFAULT_TIMEOUT,
            )
            mac_address = identity_changed_event.data.get('mac', None)
            asserts.assert_true(bool(mac_address), 'Mac address should not be empty')
        ad.log.info('Attach Wi-Fi Aware session succeeded.')
        return attach_event.callback_id, mac_address

    def _start_publish(
        self,
        attach_session_id: str,
        pub_config: constants.PublishConfig,
    ) -> callback_event.CallbackEvent:
        """Starts a publish session on the publisher device."""

        # Start the publishing session and return the handler.
        publish_handler = self.publisher.wifi_aware_snippet.wifiAwarePublish(
            attach_session_id,
            pub_config.to_dict(),
        )

        # Wait for publish session to start.
        discovery_event = publish_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
            timeout=_DEFAULT_TIMEOUT
        )
        callback_name = discovery_event.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{self.publisher} publish failed, got callback: {callback_name}.',
        )

        is_session_init = discovery_event.data[_IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.publisher} publish succeeded, but null discovery session returned.'
        )
        return publish_handler

    def _start_subscribe(
        self,
        attach_session_id: str,
        sub_config: constants.SubscribeConfig,
    ) -> callback_event.CallbackEvent:
        """Starts a subscribe session on the subscriber device."""

        # Start the subscription session and return the handler.
        subscribe_handler = self.subscriber.wifi_aware_snippet.wifiAwareSubscribe(
            attach_session_id,
            sub_config.to_dict(),
        )

        # Wait for subscribe session to start.
        discovery_event = subscribe_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
            timeout=_DEFAULT_TIMEOUT
        )
        callback_name = discovery_event.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{self.subscriber} subscribe failed, got callback: {callback_name}.'
        )
        is_session_init = discovery_event.data[_IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.subscriber} subscribe succeeded, but null session returned.'
        )
        return subscribe_handler

    def _wait_for_discovery(
        self,
        sub_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        pub_service_specific_info: bytes,
        is_ranging_enabled: bool,
    ) -> int:
        """Waits for discovery of the publisher's service by the subscriber."""
        event_name = constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
        if is_ranging_enabled:
            event_name = (
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED_WITHIN_RANGE
            )
        discover_data = sub_aware_session_cb_handler.waitAndGet(
            event_name=event_name, timeout=_DEFAULT_TIMEOUT
        )

        service_info = bytes(
            discover_data.data[constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]
        )
        asserts.assert_equal(
            service_info,
            pub_service_specific_info,
            f'{self.subscriber} got unexpected service info in discovery'
            f' callback event "{event_name}".'
        )
        match_filters = discover_data.data[
            constants.WifiAwareSnippetParams.MATCH_FILTER]
        match_filters = [
            bytes(filter[constants.WifiAwareSnippetParams.MATCH_FILTER_VALUE])
            for filter in match_filters
        ]
        asserts.assert_equal(
            match_filters,
            [constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
            f'{self.subscriber} got unexpected match filter data in discovery'
            f' callback event "{event_name}".'
        )
        return discover_data.data[constants.WifiAwareSnippetParams.PEER_ID]

    def _send_msg_through_discovery_session(
        self,
        *,
        sender: android_device.AndroidDevice,
        sender_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        receiver: android_device.AndroidDevice,
        receiver_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        discovery_session: str,
        peer: int,
        send_message: str,
        send_message_id: int,
    ) -> int:
        sender.wifi_aware_snippet.wifiAwareSendMessage(
            discovery_session, peer, send_message_id, send_message
        )
        message_send_result = sender_aware_session_cb_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT,
            timeout=_DEFAULT_TIMEOUT,
        )
        callback_name = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
        ]
        asserts.assert_equal(
            callback_name,
            constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_SUCCEEDED,
            f'{sender} failed to send message with an unexpected callback.',
        )
        actual_send_message_id = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
        ]
        asserts.assert_equal(
            actual_send_message_id,
            send_message_id,
            f'{sender} send message succeeded but message ID mismatched.'
        )
        receive_message_event = receiver_aware_session_cb_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
            timeout=_DEFAULT_TIMEOUT,
        )
        received_message_raw = receive_message_event.data[
            constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
        ]
        received_message = bytes(received_message_raw).decode('utf-8')
        asserts.assert_equal(
            received_message,
            send_message,
            f'{receiver} received the message but message content mismatched.'
        )
        return receive_message_event.data[constants.WifiAwareSnippetParams.PEER_ID]

    def _request_network(
        self,
        ad: android_device.AndroidDevice,
        discovery_session: str,
        peer: int,
        net_work_request_id: str,
        network_specifier_params: constants.WifiAwareNetworkSpecifier | None = None,
        is_accept_any_peer: bool = False,
    ) -> callback_handler_v2.CallbackHandlerV2:
        """Requests and configures a Wi-Fi Aware network connection."""
        network_specifier_parcel = (
            ad.wifi_aware_snippet.wifiAwareCreateNetworkSpecifier(
                discovery_session,
                peer,
                is_accept_any_peer,
                network_specifier_params.to_dict() if network_specifier_params else None,
            )
        )
        network_request_dict = constants.NetworkRequest(
            transport_type=_TRANSPORT_TYPE_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel,
        ).to_dict()
        ad.log.debug('Requesting Wi-Fi Aware network: %r', network_request_dict)
        return ad.wifi_aware_snippet.connectivityRequestNetwork(
            net_work_request_id, network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )

    def _wait_for_network(
        self,
        ad: android_device.AndroidDevice,
        request_network_cb_handler: callback_handler_v2.CallbackHandlerV2,
        expected_channel: str | None = None,
    ):
        """Waits for and verifies the establishment of a Wi-Fi Aware network."""
        network_callback_event = request_network_cb_handler.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
        )
        callback_name = network_callback_event.data[_CALLBACK_NAME]
        if callback_name == constants.NetworkCbName.ON_UNAVAILABLE:
            asserts.fail(
                f'{ad} failed to request the network, got callback'
                f' {callback_name}.'
            )
        elif callback_name == constants.NetworkCbName.ON_CAPABILITIES_CHANGED:
            # `network` is the network whose capabilities have changed.
            network = network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{ad} received a null Network or NetworkCapabilities!?.'
            )
            transport_info_class_name = network_callback_event.data[
                constants.NetworkCbEventKey.TRANSPORT_INFO_CLASS_NAME]
            asserts.assert_equal(
                transport_info_class_name,
                constants.AWARE_NETWORK_INFO_CLASS_NAME,
                f'{ad} network capabilities changes but it is not a WiFi Aware'
                ' network.',
            )
            if expected_channel:
                mhz_list = network_callback_event.data[constants.NetworkCbEventKey.CHANNEL_IN_MHZ]
                asserts.assert_equal(
                    mhz_list,
                    [expected_channel],
                    f'{ad} Channel freq is not match the request.'
                )
        else:
            asserts.fail(
                f'{ad} got unknown request network callback {callback_name}.'
            )

    def teardown_test(self):
        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(self.current_test_info),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )
        utils.concurrent_exec(
            self._teardown_on_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )
        self.publisher_mac = None
        self.subscriber_mac = None
        self.publisher_peer = None
        self.subscriber_peer = None
        self.publish_session = None
        self.subscribe_session = None
        self.publisher_attach_session = None
        self.subscriber_attach_session = None

    def _send_socket_msg(
        self,
        sender_ad: android_device.AndroidDevice,
        receiver_ad: android_device.AndroidDevice,
        msg: str,
        send_callback_id: str,
        receiver_callback_id: str,
    ):
        """Sends a message from one device to another and verifies receipt."""
        is_write_socket = sender_ad.wifi_aware_snippet.connectivityWriteSocket(
            send_callback_id, msg
        )
        asserts.assert_true(
            is_write_socket,
            f'{sender_ad} Failed to write data to the socket.'
        )
        sender_ad.log.info('Wrote data to the socket.')
        self.publisher.log.info('Server socket accepted the connection.')
        # Verify received message
        received_message = receiver_ad.wifi_aware_snippet.connectivityReadSocket(
            receiver_callback_id, len(msg)
        )
        asserts.assert_equal(
            received_message,
            msg,
            f'{receiver_ad} received message mismatched.Failure:Expected {msg} but got '
            f'{received_message}.'
        )
        receiver_ad.log.info('Read data from the socket.')

    def _skip_if_wifi_rtt_is_not_supported(self):
      """Skips this test case if Wi-Fi RTT is not supported on any device."""
      asserts.skip_if(
          not self.publisher.wifi_aware_snippet.wifiAwareIsRttSupported(),
          f'Publisher {self.publisher} does not support Wi-Fi RTT.'
      )
      asserts.skip_if(
          not self.subscriber.wifi_aware_snippet.wifiAwareIsRttSupported(),
          f'Subscriber {self.subscriber} does not support Wi-Fi RTT.'
      )

    def _perform_ranging(
        self,
        ad: android_device.AndroidDevice,
        request: constants.RangingRequest,
    ):
        """Performs ranging and checks the ranging result."""
        ad.log.debug('Starting ranging with request: %s', request)
        ranging_cb_handler = ad.wifi_aware_snippet.wifiAwareStartRanging(
            request.to_dict()
        )
        event = ranging_cb_handler.waitAndGet(
            event_name=constants.RangingResultCb.EVENT_NAME_ON_RANGING_RESULT,
            timeout=_DEFAULT_TIMEOUT,
        )

        callback_name = event.data.get(
            constants.RangingResultCb.DATA_KEY_CALLBACK_NAME, None
        )
        asserts.assert_equal(
            callback_name,
            constants.RangingResultCb.CB_METHOD_ON_RANGING_RESULT,
            'Ranging failed: got unexpected callback.',
        )

        results = event.data.get(
            constants.RangingResultCb.DATA_KEY_RESULTS, None
        )
        asserts.assert_true(
            results is not None and len(results) == 1,
            'Ranging got invalid results: null, empty, or wrong length.'
        )

        status_code = results[0].get(
            constants.RangingResultCb.DATA_KEY_RESULT_STATUS, None
        )
        asserts.assert_equal(
            status_code,
            constants.RangingResultStatusCode.SUCCESS,
            'Ranging peer failed: invalid result status code.',
        )

        distance_mm = results[0].get(
            constants.RangingResultCb.DATA_KEY_RESULT_DISTANCE_MM, None
        )
        asserts.assert_true(
            (
                distance_mm is not None
                and distance_mm <= _LARGE_ENOUGH_DISTANCE_MM
            ),
            'Ranging peer failed: invalid distance in ranging result.',
        )
        rssi = results[0].get(
            constants.RangingResultCb.DATA_KEY_RESULT_RSSI, None
        )
        asserts.assert_true(
            rssi is not None and rssi >= _MIN_RSSI,
            'Ranging peer failed: invalid rssi in ranging result.',
        )

        peer_id = results[0].get(
            constants.RangingResultCb.DATA_KEY_PEER_ID, None
        )
        if peer_id is not None:
            msg = 'Ranging peer failed: invalid peer ID in ranging result.'
            asserts.assert_in(peer_id, request.peer_ids, msg)

        peer_mac = results[0].get(constants.RangingResultCb.DATA_KEY_MAC, None)
        if peer_mac is not None:
            msg = (
                'Ranging peer failed: invalid peer MAC address in ranging '
                'result.'
            )
            asserts.assert_in(peer_mac, request.peer_mac_addresses, msg)

    def _teardown_on_device(self, ad: android_device.AndroidDevice) -> None:
        """Releases resources and sessions after each test."""
        ad.wifi_aware_snippet.connectivityReleaseAllSockets()
        ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(self.ads, destination=self.current_test_info.output_path)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
