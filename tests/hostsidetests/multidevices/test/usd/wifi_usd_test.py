# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# Lint as: python3
"""CTS-V Wifi test reimplemented in Mobly.
This version tests discovery and message sending from the subscriber, but
does not verify message reception on the publisher.
"""
import logging
import sys
import time

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

WIFI_USD_SNIPPET_PATH = 'wifi_usd_snippet'
WIFI_USD_SNIPPET_PACKAGE = 'com.google.snippet.wifi.usd'
USD_SERVICE_NAME = '_test'
USD_SSI = "6677"
TEST_MESSAGE = 'test message!'

class WifiUsdTest(base_test.BaseTestClass):
    def setup_class(self):
        """Sets up the devices and snippets for the test."""
        self.publisher, self.subscriber = self.register_controller(
            android_device, min_number=2)

        def setup_device(device):
            """Loads snippets and grants permissions on a single device."""
            device.load_snippet(WIFI_USD_SNIPPET_PATH, WIFI_USD_SNIPPET_PACKAGE)
            device.adb.shell(['pm', 'grant', WIFI_USD_SNIPPET_PACKAGE,
                             'android.permission.ACCESS_FINE_LOCATION'])
            device.adb.shell(['pm', 'grant', WIFI_USD_SNIPPET_PACKAGE,
                               'android.permission.NEARBY_WIFI_DEVICES'])

        utils.concurrent_exec(setup_device,
                              ((self.publisher,), (self.subscriber,)),
                              max_workers=2, raise_on_exception=True)

    def on_fail(self, record):
        """Takes bug reports on test failure."""
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            ads=[self.publisher, self.subscriber],
            test_name=record.test_name,
            begin_time=record.begin_time,
            destination=self.current_test_info.output_path
        )

    def teardown_test(self):
        """Stops any active sessions after the test."""
        logging.info("Tearing down test and stopping sessions.")
        try:
            self.publisher.wifi_usd_snippet.stopUsdPublishSession()
            self.subscriber.wifi_usd_snippet.stopUsdSubscribeSession()
        except Exception as e:
            logging.error("Error during teardown: %s", e)

    def test_discovery_and_message_exchange(self):
        # TODO: Add more test cases to cover all CTS-V scenarios.

        """Tests Wi-Fi USD service discovery and then sends a message."""
        logging.info("Starting test_discovery_and_message_exchange...")

        try:
            # 1. Start publisher session
            logging.info("Initiating publish operation on the publisher device...")
            self.publisher.wifi_usd_snippet.startUsdPublishSession(
                USD_SERVICE_NAME, USD_SSI)
            logging.info("Successfully started publish session.")

            time.sleep(2)

            # 2. On subscriber, perform subscribe, discovery, and send in one atomic call.
            logging.info("Subscriber: Initiating atomic subscribe, discover, and send...")
            self.subscriber.wifi_usd_snippet.subscribeDiscoverAndSendMessage(
                USD_SERVICE_NAME, USD_SSI, TEST_MESSAGE)
            logging.info("Subscriber successfully discovered peer and sent message.")
            #TODO: <b/421452165> Message reception verification on publisher is disabled for now
            # 3. Message reception verification on publisher is disabled for now.
            #    This part of the test will be uncommented once manual testing confirms
            #    that message reception is working reliably.
            #
            #logging.info("Publisher attempting to receive message...")
            #received_message = self.publisher.wifi_usd_snippet.receiveMessage()
            #asserts.assert_is_not_none(received_message, "Publisher did not receive a message.")
            #asserts.assert_equal(received_message, TEST_MESSAGE,
            #"Message received by publisher does not match message sent.")
            #logging.info(f"Publisher successfully received message: '{received_message}'")

        except Exception as e:
            asserts.fail(f"Test failed due to an exception: {e}")

        logging.info("Wi-Fi USD discovery and message sending test completed successfully.")


if __name__ == '__main__':
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
