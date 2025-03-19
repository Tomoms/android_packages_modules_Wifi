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
package com.google.snippet.wifi.aware;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.aware.WifiAwareNetworkInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectivityManagerSnippet implements Snippet {
    private static final String EVENT_KEY_CB_NAME = "callbackName";
    private static final String EVENT_KEY_NETWORK = "network";
    private static final String EVENT_KEY_NETWORK_CAP = "networkCapabilities";
    private static final String EVENT_KEY_TRANSPORT_INFO_CLASS = "transportInfoClassName";
    private static final String EVENT_KEY_TRANSPORT_INFO_CHANNEL_IN_MHZ = "channelInMhz";
    private static final int CLOSE_SOCKET_TIMEOUT = 15 * 1000;
    private static final int ACCEPT_TIMEOUT = 30 * 1000;
    private static final int SOCKET_SO_TIMEOUT = 30 * 1000;
    private static final int TRANSPORT_PROTOCOL_TCP = 6;

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;

    private final ConcurrentHashMap<String, ServerSocket> mServerSockets =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NetworkCallback> mNetworkCallBacks =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Socket> mSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OutputStream> mOutputStreams =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InputStream> mInputStreams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> mSocketThreads = new ConcurrentHashMap<>();

    /**
     * Custom exception class for handling specific errors related to the ConnectivityManagerSnippet
     * operations.
     */
    class ConnectivityManagerSnippetException extends Exception {
        ConnectivityManagerSnippetException(String msg) {
            super(msg);
        }
    }

    public ConnectivityManagerSnippet() throws ConnectivityManagerSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        if (mConnectivityManager == null) {
            throw new ConnectivityManagerSnippetException(
                    "ConnectivityManager not " + "available.");
        }
    }

    public class NetworkCallback extends ConnectivityManager.NetworkCallback {


        String mCallBackId;
        Network mNetWork;
        NetworkCapabilities mNetworkCapabilities;


        NetworkCallback(String callBackId) {
            mCallBackId = callBackId;
        }

        @Override
        public void onUnavailable() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString(EVENT_KEY_CB_NAME, "onUnavailable");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                @NonNull NetworkCapabilities networkCapabilities) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString(EVENT_KEY_CB_NAME, "onCapabilitiesChanged");
            event.getData().putParcelable(EVENT_KEY_NETWORK, network);
            event.getData().putParcelable(EVENT_KEY_NETWORK_CAP, networkCapabilities);
            mNetWork = network;
            mNetworkCapabilities = networkCapabilities;
            TransportInfo transportInfo = networkCapabilities.getTransportInfo();
            String transportInfoClassName = "";
            if (transportInfo != null) {
                transportInfoClassName = transportInfo.getClass().getName();
                event.getData().putString(EVENT_KEY_TRANSPORT_INFO_CLASS, transportInfoClassName);
            }
            if (networkCapabilities.getTransportInfo() instanceof WifiAwareNetworkInfo) {
                WifiAwareNetworkInfo
                        newWorkInfo =
                        (WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
                List<WifiAwareChannelInfo> channelInfoList = newWorkInfo.getChannelInfoList();
                ArrayList<Integer> channelFrequencies = new ArrayList<>();
                if (!channelInfoList.isEmpty()) {
                    for (WifiAwareChannelInfo info : channelInfoList) {
                        channelFrequencies.add(info.getChannelFrequencyMhz());
                    }
                }
                event.getData().putIntegerArrayList(
                    EVENT_KEY_TRANSPORT_INFO_CHANNEL_IN_MHZ, channelFrequencies
                );

            }
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Requests a network with the specified network request and sets a callback for network
     * events.
     *
     * @param callBackId              A unique identifier assigned automatically by Mobly. This is
     *                                used as the request ID for further operations and event
     *                                handling.
     * @param request                 The NetworkRequest object that specifies the desired network
     *                                characteristics.
     * @param requestNetWorkId        A unique ID to support managing multiple network sessions.
     * @param requestNetworkTimeoutMs The timeout period (in milliseconds) after which the network
     *                                request will expire if no suitable network is found.
     */
    @AsyncRpc(description = "Request a network.")
    public void connectivityRequestNetwork(String callBackId, String requestNetWorkId,
            NetworkRequest request, int requestNetworkTimeoutMs) {
        Log.v("Requesting network with request: " + request.toString());
        NetworkCallback callback = new NetworkCallback(callBackId);
        mNetworkCallBacks.put(requestNetWorkId, callback);
        mConnectivityManager.requestNetwork(request, callback, requestNetworkTimeoutMs);
    }

    /**
     * Unregisters the registered network callback and possibly releases requested networks.
     *
     * @param requestId Id of the network request.
     */
    @Rpc(description = "Unregister a network request")
    public void connectivityUnregisterNetwork(String requestId) {
        NetworkCallback callback = mNetworkCallBacks.get(requestId);
        if (callback == null) {
            return;
        }
        if (mConnectivityManager == null) {
            return;
        }
        mConnectivityManager.unregisterNetworkCallback(callback);
    }

    /**
     * Starts a server socket on a random available port and waits for incoming connections. A
     * separate thread is started to handle the socket accept operation asynchronously. The accepted
     * socket is stored and used for further communication (read/write).
     *
     * @param callbackId A unique identifier assigned automatically by Mobly to track the event and
     *                   response.
     * @return The port number assigned by the local system.
     */
    @AsyncRpc(description = "Start a server socket to accept incoming connections.")
    public int connectivityServerSocketAccept(String callbackId)
            throws ConnectivityManagerSnippetException, IOException {
        if (mServerSockets.containsKey(callbackId) && mServerSockets.get(callbackId) != null) {
            throw new ConnectivityManagerSnippetException("Server socket is already created.");
        }
        ServerSocket serverSocket = new ServerSocket(0);
        int localPort = serverSocket.getLocalPort();
        mServerSockets.put(callbackId, serverSocket);
        // https://developer.callbackId.com/reference/java/net/ServerSocket#setSoTimeout(int)
        // A call to accept() for this ServerSocket will block for only this amount of time.
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT);
        if (mSocketThreads.get(callbackId) != null) {
            throw new ConnectivityManagerSnippetException(
                    "Server socket thread is already running.");
        }
        Thread socketThread = new Thread(() -> {
            try {
                Socket tempSocket = mServerSockets.get(callbackId).accept();
                mSockets.put(callbackId, tempSocket);
                mInputStreams.put(callbackId, tempSocket.getInputStream());
                mOutputStreams.put(callbackId, tempSocket.getOutputStream());
                SnippetEvent event = new SnippetEvent(callbackId, "ServerSocketAccept");
                event.getData().putBoolean("isAccept", true);
                EventCache.getInstance().postEvent(event);
            } catch (IOException e) {
                Log.e("Socket accept error", e);
                SnippetEvent event = new SnippetEvent(callbackId, "ServerSocketAccept");
                event.getData().putBoolean("isAccept", false);
                event.getData().putString("error", e.getMessage());
                EventCache.getInstance().postEvent(event);
            }
        });
        mSocketThreads.put(callbackId, socketThread);
        socketThread.start();
        return localPort;
    }

    /**
     * Check if the server socket thread is alive.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     * @return True if the server socket thread is alive.
     */
    public boolean connectivityIsSocketThreadAlive(String sessionId) {
        Thread thread = mSocketThreads.get(sessionId);
        if (thread != null) {
            return thread.isAlive();
        } else {
            return false;
        }
    }

    /**
     * Stops the server socket thread if it's running.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     */
    @Rpc(description = "Stop the server socket thread if it's running.")
    public void connectivityStopAcceptThread(String sessionId) throws IOException {
        if (connectivityIsSocketThreadAlive(sessionId)) {
            Thread thread = mSocketThreads.get(sessionId);

            try {
                connectivityCloseServerSocket(sessionId);
                thread.join(CLOSE_SOCKET_TIMEOUT);  // Wait for the thread to terminate
                if (thread.isAlive()) {
                    throw new RuntimeException("Server socket thread did not terminate in time");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Error stopping server socket thread", e);
            } finally {
                connectivityCloseSocket(sessionId);
                mSocketThreads.remove(sessionId);
            }
        } else {
            connectivityCloseSocket(sessionId);
            mSocketThreads.remove(sessionId);
        }
    }

    /**
     * Reads from a socket.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     * @param len       The number of bytes to read.
     */
    @Rpc(description = "Reads from a socket.")
    public String connectivityReadSocket(String sessionId, int len)
            throws ConnectivityManagerSnippetException, JSONException, IOException {
        checkInputStream(sessionId);
        // Read the specified number of bytes from the input stream
        byte[] buffer = new byte[len];
        InputStream inputStream = mInputStreams.get(sessionId);
        int bytesReadLength = inputStream.read(buffer, 0, len); // Read up to len bytes
        if (bytesReadLength == -1) { // End of stream reached unexpectedly
            throw new ConnectivityManagerSnippetException(
                    "End of stream reached before reading expected bytes.");
        }
        // Convert the bytes read to a String
        String receiveStrMsg = new String(buffer, 0, bytesReadLength, StandardCharsets.UTF_8);
        return receiveStrMsg;
    }

    /**
     * Writes to a socket.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     * @param message   The message to send.
     * @throws ConnectivityManagerSnippetException
     */
    @Rpc(description = "Writes to a socket.")
    public Boolean connectivityWriteSocket(String sessionId, String message)
            throws ConnectivityManagerSnippetException, IOException {
        checkOutputStream(sessionId);
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        // Write the message to the output stream
        OutputStream outputStream = mOutputStreams.get(sessionId);
        outputStream.write(bytes, 0, bytes.length);
        outputStream.flush();
        return true;


    }

    /**
     * Closes the socket.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     * @throws ConnectivityManagerSnippetException
     */
    public void connectivityCloseSocket(String sessionId) throws IOException {
        Socket socket = mSockets.get(sessionId);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        mSockets.remove(sessionId);

    }

    /**
     * Closes the server socket.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     * @throws IOException
     */
    public void connectivityCloseServerSocket(String sessionId) throws IOException {
        ServerSocket serverSocket = mServerSockets.get(sessionId);
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        mServerSockets.remove(sessionId);
    }

    /**
     * Closes the outputStream.
     *
     * @throws ConnectivityManagerSnippetException
     */
    @Rpc(description = "Close the outputStream.")
    public void connectivityCloseWrite(String sessionId)
            throws IOException, ConnectivityManagerSnippetException {
        OutputStream outputStream = mOutputStreams.get(sessionId);
        if (outputStream != null) {
            outputStream.close();
        }
        mOutputStreams.remove(sessionId);


    }

    /**
     * Closes the inputStream.
     *
     * @throws ConnectivityManagerSnippetException
     */
    @Rpc(description = "Close the inputStream.")
    public void connectivityCloseRead(String sessionId)
            throws IOException, ConnectivityManagerSnippetException {
        InputStream inputStream = mInputStreams.get(sessionId);
        if (inputStream != null) {
            inputStream.close();
        }
        mInputStreams.remove(sessionId);
    }

    private void checkOutputStream(String sessionId) throws ConnectivityManagerSnippetException {
        OutputStream outputStream = mOutputStreams.get(sessionId);
        if (outputStream == null) {
            throw new ConnectivityManagerSnippetException("Output stream is not created.Please "
                    + "call connectivityCreateSocketOverWiFiAware() or "
                    + "connectivityServerSocketAccept() first.");
        }
    }

    private void checkInputStream(String sessionId) throws ConnectivityManagerSnippetException {
        InputStream inputStream = mInputStreams.get(sessionId);
        if (inputStream == null) {
            throw new ConnectivityManagerSnippetException("Input stream is not created.Please "
                    + "call connectivityCreateSocketOverWiFiAware() or "
                    + "connectivityServerSocketAccept() first.");
        }
    }

    /**
     * Creates a socket using Wi-Fi Aware's peer-to-peer connection capabilities. Only TCP transport
     * protocol is supported. The method uses the session ID to track and manage the socket.
     *
     * @param sessionId     A unique ID to manage multiple network requests simultaneously.
     * @param peerLocalPort The port number of the peer device.
     */
    @Rpc(description = "Create to a socket.")
    public void connectivityCreateSocketOverWiFiAware(String sessionId, int peerLocalPort)
            throws ConnectivityManagerSnippetException, IOException {
        NetworkCallback netWorkCallBackBySessionId = getNetWorkCallbackBySessionId(sessionId);
        NetworkCapabilities networkCapabilities = netWorkCallBackBySessionId.mNetworkCapabilities;
        Network netWork = netWorkCallBackBySessionId.mNetWork;
        checkNetworkCapabilities(networkCapabilities);
        checkNetwork(netWork);
        Socket socket = mSockets.get(sessionId);
        if (socket != null) {
            throw new ConnectivityManagerSnippetException("Socket is already created"
                    + ".Please call connectivityCloseSocket(String sessionId) or "
                    + "connectivityStopAcceptThread" + "(String sessionId) " + "to release first.");
        }

        checkNetworkCapabilities(networkCapabilities);
        WifiAwareNetworkInfo peerAwareInfo =
                (WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
        if (peerAwareInfo == null) {
            throw new ConnectivityManagerSnippetException("PeerAwareInfo is null.");
        }
        int peerPort = peerAwareInfo.getPort();
        Inet6Address peerIpv6Addr = peerAwareInfo.getPeerIpv6Addr();
        if (peerPort == 0) {
            peerPort = peerLocalPort;
            if (peerPort == 0) {
                throw new ConnectivityManagerSnippetException("Invalid port number.");
            }
        } else {

            int transportProtocol = peerAwareInfo.getTransportProtocol();
            if (transportProtocol != TRANSPORT_PROTOCOL_TCP) {
                throw new ConnectivityManagerSnippetException(
                        "Only support TCP transport protocol.");
            }
        }


        Socket createSocket = netWork.getSocketFactory().createSocket(peerIpv6Addr, peerPort);
        createSocket.setSoTimeout(SOCKET_SO_TIMEOUT);
        mSockets.put(sessionId, createSocket);
        mInputStreams.put(sessionId, createSocket.getInputStream());
        mOutputStreams.put(sessionId, createSocket.getOutputStream());
    }


    private NetworkCallback getNetWorkCallbackBySessionId(String sessionId)
            throws ConnectivityManagerSnippetException {
        NetworkCallback callback = mNetworkCallBacks.get(sessionId);
        if (callback == null) {
            throw new ConnectivityManagerSnippetException("Network callback is not created.Please "
                    + "call connectivityRequestNetwork() first.");

        }
        return callback;
    }

    /**
     * Check if the network capabilities is created.
     *
     * @throws ConnectivityManagerSnippetException
     */
    private void checkNetworkCapabilities(NetworkCapabilities networkCapabilities)
            throws ConnectivityManagerSnippetException {
        if (networkCapabilities == null) {
            throw new ConnectivityManagerSnippetException("Network capabilities is not created.");
        }
    }

    /**
     * Check if the network is created.
     *
     * @throws ConnectivityManagerSnippetException
     */
    private void checkNetwork(Network network) throws ConnectivityManagerSnippetException {
        if (network == null) {
            throw new ConnectivityManagerSnippetException("Network is not created.");
        }
    }

    /**
     * Check if the server socket is created.
     *
     * @throws ConnectivityManagerSnippetException
     */
    private void checkServerSocket(String sessionId) throws ConnectivityManagerSnippetException {
        if (mServerSockets.get(sessionId) == null) {
            throw new ConnectivityManagerSnippetException("Server socket is not created"
                    + ".Please call connectivityInitServerSocket() first.");
        }
    }

    /**
     * Close all sockets.
     *
     * @param sessionId To support multiple network requests happening simultaneously
     * @throws IOException
     */
    @Rpc(description = "Close all sockets.")
    public void connectivityCloseAllSocket(String sessionId)
            throws IOException, ConnectivityManagerSnippetException {
        connectivityStopAcceptThread(sessionId);
        connectivityCloseServerSocket(sessionId);
        connectivityCloseRead(sessionId);
        connectivityCloseWrite(sessionId);
    }

    @Override
    public void shutdown() throws Exception {
        try {
            for (NetworkCallback callback : mNetworkCallBacks.values()) {
                mConnectivityManager.unregisterNetworkCallback(callback);
            }
            mNetworkCallBacks.clear();

        } catch (Exception e) {
            Log.e("Error unregistering network callback", e);
        }
        try {
            connectivityReleaseAllSockets();
        } catch (Exception e) {
            Log.e("Error closing sockets", e);
        }
        Snippet.super.shutdown();
    }

    /**
     * Close all sockets.
     *
     * @throws IOException
     */
    @Rpc(description = "Close all sockets.")
    public void connectivityReleaseAllSockets() {
        for (Socket socket : mSockets.values()) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e("Error closing socket", e);
            }
        }
        mSockets.clear();
        for (ServerSocket serverSocket : mServerSockets.values()) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e("Error closing server socket", e);
            }
        }
        mServerSockets.clear();
        for (OutputStream outputStream : mOutputStreams.values()) {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e("Error closing output stream", e);
            }
        }
        mOutputStreams.clear();
        for (InputStream inputStream : mInputStreams.values()) {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e("Error closing input stream", e);
            }
        }
        mInputStreams.clear();
    }
}
