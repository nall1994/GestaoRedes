/*_############################################################################
  _## 
  _##  SNMP4J - DTLSTM.java  
  _## 
  _##  Copyright (C) 2003-2018  Frank Fock and Jochen Katz (SNMP4J.org)
  _##  
  _##  Licensed under the Apache License, Version 2.0 (the "License");
  _##  you may not use this file except in compliance with the License.
  _##  You may obtain a copy of the License at
  _##  
  _##      http://www.apache.org/licenses/LICENSE-2.0
  _##  
  _##  Unless required by applicable law or agreed to in writing, software
  _##  distributed under the License is distributed on an "AS IS" BASIS,
  _##  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  _##  See the License for the specific language governing permissions and
  _##  limitations under the License.
  _##  
  _##########################################################################*/

package org.snmp4j.transport;

import org.snmp4j.SNMP4JSettings;
import org.snmp4j.TransportMapping;
import org.snmp4j.TransportStateReference;
import org.snmp4j.event.CounterEvent;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import org.snmp4j.mp.CounterSupport;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.smi.*;
import org.snmp4j.transport.tls.*;
import org.snmp4j.util.*;

import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;

import static javax.net.ssl.SSLEngineResult.*;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN;

/**
 * The <code>DTLSTM</code> implements the Datagram Transport Layer Security
 * Transport Mapping (TLS-TM) as defined by RFC 5953
 * with the new IO API and {@link SSLEngine}.
 * <p>
 * It uses a single thread for processing incoming and outgoing messages.
 * The thread is started when the <code>listen</code> method is called, or
 * when an outgoing request is sent using the <code>sendMessage</code> method.
 * </p>
 *
 * @author Frank Fock
 * @version 3.0
 * @since 3.0
 */
public class DTLSTM extends DefaultUdpTransportMapping implements X509TlsTransportMappingConfig,
        ConnectionOrientedTransportMapping<UdpAddress> {

    private static final LogAdapter logger =
            LogFactory.getLogger(DTLSTM.class);

    public static final int MAX_HANDSHAKE_LOOPS = 100;
    public static final int DEFAULT_SOCKET_TIMEOUT = 5000;
    public static final int DEFAULT_HANDSHAKE_TIMEOUT = 5000;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 300000;
    private static final int DEFAULT_DTLS_HANDSHAKE_THREADPOOL_SIZE = 2;

    private long nextSessionID = 1;

    private Map<UdpAddress, SocketEntry> sockets = new Hashtable<UdpAddress, SocketEntry>();
    private CommonTimer socketCleaner;
    private SSLEngineConfigurator sslEngineConfigurator =
            new DefaultSSLEngineConfiguration();

    private TlsTmSecurityCallback<X509Certificate> securityCallback;
    private CounterSupport counterSupport;
    // 1 minute default timeout
    private long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private int handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;

    public static final String DEFAULT_DTLSTM_PROTOCOLS = "DTLSv1.2";
    public static final int MAX_TLS_PAYLOAD_SIZE = 64 * 1024;

    private String localCertificateAlias;
    private String keyStore;
    private String keyStorePassword;
    private String trustStore;
    private String trustStorePassword;
    private String[] dtlsProtocols;
    private TLSTMTrustManagerFactory trustManagerFactory = new DefaultDTLSTMTrustManagerFactory();

    private ThreadPool dtlsHandshakeThreadPool;
    private int dtlsHandshakeThreadPoolSize = DEFAULT_DTLS_HANDSHAKE_THREADPOOL_SIZE;

    private boolean serverEnabled = false;

    private transient List<TransportStateListener> transportStateListeners;

    /**
     * Creates a default UDP transport mapping with the server for incoming
     * messages disabled.
     *
     * @throws UnknownHostException if the local host cannot be determined.
     */
    public DTLSTM() throws IOException {
        super(new UdpAddress(InetAddress.getLocalHost(), 0));
        this.counterSupport = CounterSupport.getInstance();
        super.maxInboundMessageSize = MAX_TLS_PAYLOAD_SIZE;
        setSocketTimeout(DEFAULT_SOCKET_TIMEOUT);
    }

    /**
     * Creates a TLS transport mapping with the server for incoming
     * messages bind to the given address. The <code>securityCallback</code>
     * needs to be specified before {@link #listen()} is called.
     *
     * @param address
     *   server address to bind.
     *
     * @throws IOException on failure of binding a local port.
     */
    public DTLSTM(UdpAddress address) throws IOException {
        super(address);
        super.maxInboundMessageSize = MAX_TLS_PAYLOAD_SIZE;
        this.counterSupport = CounterSupport.getInstance();
        setSocketTimeout(DEFAULT_SOCKET_TIMEOUT);
        try {
            if (Class.forName("javax.net.ssl.X509ExtendedTrustManager") != null) {
                Class trustManagerFactoryClass =
                        Class.forName("org.snmp4j.transport.tls.DTLSTMExtendedTrustManagerFactory");
                Constructor c = trustManagerFactoryClass.getConstructors()[0];
                TLSTMTrustManagerFactory trustManagerFactory = (TLSTMTrustManagerFactory) c.newInstance(this);
                setTrustManagerFactory(trustManagerFactory);
            }
        } catch (ClassNotFoundException ex) {
            //throw new IOException("Failed to load TLSTMTrustManagerFactory: "+ex.getMessage(), ex);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new IOException("Failed to init DTLSTMTrustManagerFactory: " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Failed to setup DTLSTMTrustManagerFactory: " + ex.getMessage(), ex);
        } catch (InstantiationException ex) {
            throw new IOException("Failed to instantiate DTLSTMTrustManagerFactory: " + ex.getMessage(), ex);
        }
    }

    /**
     * Creates a DTLS transport mapping that binds to the given address
     * (interface) on the local host.
     *
     * @param securityCallback a security name callback to resolve X509 certificates to tmSecurityNames.
     * @param serverAddress    the UdpAddress instance that describes the server address to listen
     *                         on incoming connection requests.
     * @throws IOException if the given address cannot be bound.
     */
    public DTLSTM(TlsTmSecurityCallback<X509Certificate> securityCallback,
                  UdpAddress serverAddress) throws IOException {
        this(securityCallback, serverAddress, CounterSupport.getInstance());
    }

    /**
     * Creates a TLS transport mapping that binds to the given address
     * (interface) on the local host.
     *
     * @param securityCallback a security name callback to resolve X509 certificates to tmSecurityNames.
     * @param serverAddress    the UdpAddress instance that describes the server address to listen
     *                         on incoming connection requests.
     * @param counterSupport   The CounterSupport instance to be used to count events created by this
     *                         TLSTM instance. To get a default instance, use
     *                         {@link CounterSupport#getInstance()}.
     * @throws IOException if the given address cannot be bound.
     */
    public DTLSTM(TlsTmSecurityCallback<X509Certificate> securityCallback,
                  UdpAddress serverAddress, CounterSupport counterSupport) throws IOException {
        super(serverAddress);
        super.maxInboundMessageSize = MAX_TLS_PAYLOAD_SIZE;
        setSocketTimeout(DEFAULT_SOCKET_TIMEOUT);
        this.securityCallback = securityCallback;
        this.counterSupport = counterSupport;
    }

    /**
     * Starts the listener thread that accepts incoming messages. The thread is
     * started in daemon mode and thus it will not block application terminated.
     * Nevertheless, the {@link #close()} method should be called to stop the
     * listen thread gracefully and free associated ressources.
     *
     * @throws IOException
     *         if the listen port could not be bound to the server thread.
     */
    @Override
    public synchronized void listen() throws IOException {
        dtlsHandshakeThreadPool =
                ThreadPool.create("DTLSTM_"+getListenAddress(), getDtlsHandshakeThreadPoolSize());
        if (connectionTimeout > 0) {
            // run as daemon
            socketCleaner = SNMP4JSettings.getTimerFactory().createTimer();
        }
        super.listen();
    }

    /**
     * Closes the socket and stops the listener thread and socket cleaner timer
     * (if {@link #getSocketTimeout()} is greater than zero).
     *
     * @throws IOException
     *         if the socket cannot be closed.
     */
    @Override
    public void close() throws IOException {
        for (SocketEntry socketEntry : sockets.values()) {
            socketEntry.closeSession();
        }
        super.close();
        if (dtlsHandshakeThreadPool != null) {
            dtlsHandshakeThreadPool.stop();
        }
        sockets.clear();
        if (socketCleaner != null) {
            socketCleaner.cancel();
        }
        socketCleaner = null;
        dtlsHandshakeThreadPool = null;
    }

    public int getDtlsHandshakeThreadPoolSize() {
        return dtlsHandshakeThreadPoolSize;
    }

    /**
     * Sets the maximum number of threads reserved for DTLS inbound connection handshake processing.
     * @param dtlsHandshakeThreadPoolSize
     *    the thread pool size that gets effective when {@link #listen()} is called. Default is
     *    {@link #DEFAULT_DTLS_HANDSHAKE_THREADPOOL_SIZE}.
     */
    public void setDtlsHandshakeThreadPoolSize(int dtlsHandshakeThreadPoolSize) {
        this.dtlsHandshakeThreadPoolSize = dtlsHandshakeThreadPoolSize;
    }

    public String getLocalCertificateAlias() {
        if (localCertificateAlias == null) {
            return System.getProperty(SnmpConfigurator.P_TLS_LOCAL_ID, null);
        }
        return localCertificateAlias;
    }

    public String[] getProtocolVersions() {
        if (dtlsProtocols == null) {
            String s = System.getProperty(getProtocolVersionPropertyName(), DEFAULT_DTLSTM_PROTOCOLS);
            return s.split(",");
        }
        return dtlsProtocols;
    }

    /**
     * Returns the property name that is used by this transport mapping to determine the protocol versions
     * from system properties.
     *
     * @return a property name like {@link SnmpConfigurator#P_TLS_VERSION} or
     * {@link SnmpConfigurator#P_DTLS_VERSION}.
     */
    @Override
    public String getProtocolVersionPropertyName() {
        return SnmpConfigurator.P_DTLS_VERSION;
    }

    /**
     * Sets the DTLS protocols/versions that DTLSTM should use during handshake.
     * The default is defined by {@link #DEFAULT_DTLSTM_PROTOCOLS}.
     *
     * @param dtlsProtocols an array of TLS protocol (version) names supported by the SunJSSE provider.
     *                     The order in the array defines which protocol is tried during handshake
     *                     first.
     * @since 3.0
     */
    public void setProtocolVersions(String[] dtlsProtocols) {
        this.dtlsProtocols = dtlsProtocols;
    }

    public String getKeyStore() {
        if (keyStore == null) {
            return System.getProperty("javax.net.ssl.keyStore");
        }
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        if (keyStorePassword == null) {
            return System.getProperty("javax.net.ssl.keyStorePassword");
        }
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getTrustStore() {
        if (trustStore == null) {
            return System.getProperty("javax.net.ssl.trustStore");
        }
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        if (trustStorePassword == null) {
            return System.getProperty("javax.net.ssl.trustStorePassword");
        }
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * Sets the certificate alias used for client and server authentication
     * by this TLSTM. Setting this property to a value other than {@code null}
     * filters out any certificates which are not in the chain of the given
     * alias.
     *
     * @param localCertificateAlias a certificate alias which filters a single certification chain from
     *                              the {@code javax.net.ssl.keyStore} key store to be used to
     *                              authenticate this TLS transport mapping. If {@code null} no
     *                              filtering appears, which could lead to more than a single chain
     *                              available for authentication by the peer, which would violate the
     *                              TLSTM standard requirements.
     */
    public void setLocalCertificateAlias(String localCertificateAlias) {
        this.localCertificateAlias = localCertificateAlias;
    }

    public CounterSupport getCounterSupport() {
        return counterSupport;
    }

    @Override
    public Class<? extends Address> getSupportedAddressClass() {
        return DtlsAddress.class;
    }

    public TlsTmSecurityCallback<X509Certificate> getSecurityCallback() {
        return securityCallback;
    }

    public void setSecurityCallback(TlsTmSecurityCallback<X509Certificate> securityCallback) {
        this.securityCallback = securityCallback;
    }

    public TLSTMTrustManagerFactory getTrustManagerFactory() {
        return trustManagerFactory;
    }

    /**
     * Set the TLSTM trust manager factory. Using a trust manager factory other than the
     * default allows to add support for Java 1.7 X509ExtendedTrustManager.
     *
     * @param trustManagerFactory a X.509 trust manager factory implementing the interface
     * {@link TLSTMTrustManagerFactory}.
     * @since 3.0.0
     */
    public void setTrustManagerFactory(TLSTMTrustManagerFactory trustManagerFactory) {
        if (trustManagerFactory == null) {
            throw new NullPointerException();
        }
        this.trustManagerFactory = trustManagerFactory;
    }

    @Override
    public UdpAddress getListenAddress() {
        UdpAddress actualListenAddress = null;
        DatagramSocket socketCopy = socket;
        if (socketCopy != null) {
            actualListenAddress = new DtlsAddress(socketCopy.getLocalAddress(), socketCopy.getLocalPort());
        }
        return actualListenAddress;
    }

    /**
     * Closes a connection to the supplied remote address, if it is open. This
     * method is particularly useful when not using a timeout for remote
     * connections.
     *
     * @param remoteAddress the address of the peer socket.
     * @return {@code true} if the connection has been closed and
     * {@code false} if there was nothing to close.
     * @throws java.io.IOException if the remote address cannot be closed due to an IO exception.
     */
    public synchronized boolean close(UdpAddress remoteAddress) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Closing socket for peer address " + remoteAddress);
        }
        SocketEntry socketEntry = sockets.remove(remoteAddress);
        if (socketEntry != null) {
            socketEntry.closeSession();
            return true;
        }
        return false;
    }

    /**
     * Gets the connection timeout. This timeout specifies the time a connection
     * may be idle before it is closed.
     *
     * @return long
     * the idle timeout in milliseconds.
     */
    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Returns the <code>MessageLengthDecoder</code> used by this transport
     * mapping.
     *
     * @return a MessageLengthDecoder instance.
     */
//    @Override
    public MessageLengthDecoder getMessageLengthDecoder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the <code>MessageLengthDecoder</code> that decodes the total
     * message length from the header of a message.
     *
     * @param messageLengthDecoder
     *         a MessageLengthDecoder instance.
     */
//    @Override
    public void setMessageLengthDecoder(MessageLengthDecoder messageLengthDecoder) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the connection timeout. This timeout specifies the time a connection
     * may be idle before it is closed.
     *
     * @param connectionTimeout the idle timeout in milliseconds. A zero or negative value will disable
     *                          any timeout and connections opened by this transport mapping will stay
     *                          opened until they are explicitly closed.
     */
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Adds a transport state listener that is to be informed about connection
     * state changes.
     *
     * @param l
     *         a TransportStateListener.
     */
    @Override
    public synchronized void addTransportStateListener(TransportStateListener l) {
        if (transportStateListeners == null) {
            transportStateListeners = new ArrayList<>(2);
        }
        transportStateListeners.add(l);
    }

    /**
     * Removes the supplied transport state listener.
     *
     * @param l
     *         a TransportStateListener.
     */
    @Override
    public synchronized void removeTransportStateListener(TransportStateListener l) {
        if (transportStateListeners != null) {
            transportStateListeners.remove(l);
        }
    }

    /**
     * Gets the {@link CommonTimer} that controls socket cleanup operations.
     *
     * @return a socket cleaner timer.
     * @since 3.0
     */
    @Override
    public CommonTimer getSocketCleaner() {
        return socketCleaner;
    }

    /**
     * Checks whether a server for incoming requests is enabled.
     *
     * @return boolean
     */
    public boolean isServerEnabled() {
        return serverEnabled;
    }

    /**
     * Sets whether a server for incoming requests should be created when
     * the transport is set into listen state. Setting this value has no effect
     * until the {@link #listen()} method is called (if the transport is already
     * listening, {@link #close()} has to be called before).
     *
     * @param serverEnabled if {@code true} if the transport will listens for incoming
     *                      requests after {@link #listen()} has been called.
     */
    public void setServerEnabled(boolean serverEnabled) {
        this.serverEnabled = serverEnabled;
    }

    /**
     * Gets the inbound buffer size for incoming requests. When SNMP packets are
     * received that are longer than this maximum size, the messages will be
     * silently dropped and the connection will be closed.
     *
     * @return the maximum inbound buffer size in bytes.
     */
    public int getMaxInboundMessageSize() {
        return super.getMaxInboundMessageSize();
    }

    /**
     * Sets the maximum buffer size for incoming requests. When SNMP packets are
     * received that are longer than this maximum size, the messages will be
     * silently dropped and the connection will be closed.
     *
     * @param maxInboundMessageSize the length of the inbound buffer in bytes.
     */
    public void setMaxInboundMessageSize(int maxInboundMessageSize) {
        this.maxInboundMessageSize = maxInboundMessageSize;
    }

    /**
     * Gets the maximum number of milliseconds to wait for the DTLS handshake operation to succeed.
     * @return
     *    the handshake timeout millis.
     */
    public int getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * Sets the maximum number of milliseconds to wait for the DTLS handshake operation to succeed.
     * @param handshakeTimeout
     *    the new handshake timeout millis.
     */
    public void setHandshakeTimeout(int handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
    }

    private synchronized void timeoutSocket(SocketEntry entry) {
        if ((connectionTimeout > 0) && (socketCleaner != null)) {
            socketCleaner.schedule(new SocketTimeout<>(this, entry), connectionTimeout);
        }
    }

    protected void fireConnectionStateChanged(TransportStateEvent change) {
        if (logger.isDebugEnabled()) {
            logger.debug("Firing transport state event: " + change);
        }
        final List<TransportStateListener> listenersFinalRef = transportStateListeners;
        if (listenersFinalRef != null) {
            try {
                List<TransportStateListener> listeners;
                synchronized (listenersFinalRef) {
                    listeners = new ArrayList<>(listenersFinalRef);
                }
                for (TransportStateListener listener : listeners) {
                    listener.connectionStateChanged(change);
                }
            } catch (RuntimeException ex) {
                logger.error("Exception in fireConnectionStateChanged: " + ex.getMessage(), ex);
                if (SNMP4JSettings.isForwardRuntimeExceptions()) {
                    throw ex;
                }
            }
        }
    }

    @Override
    protected List<DatagramPacket> prepareOutPackets(UdpAddress targetAddress, byte[] message,
                                                     TransportStateReference tmStateReference,
                                                     DatagramSocket socket, long timeoutMillis, int maxRetries) throws IOException {
        InetSocketAddress targetSocketAddress =
                new InetSocketAddress(targetAddress.getInetAddress(),
                        targetAddress.getPort());
        ByteBuffer outNet = ByteBuffer.allocate(MAX_TLS_PAYLOAD_SIZE);
        SocketEntry socketEntry = sockets.get(targetAddress);
        List<DatagramPacket> packets = new ArrayList<>(1);
        if (socketEntry == null) {
            try {
                socketEntry = new SocketEntry(targetAddress, true, tmStateReference);
                sockets.put(targetAddress, socketEntry);
                synchronized (socketEntry.outboundLock) {
                    HandshakeTask handshakeTask =
                            new HandshakeTask(socketEntry, socket, targetSocketAddress, null,
                                    timeoutMillis, maxRetries);
                    handshakeTask.run();
                }
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
        ByteBuffer outApp = ByteBuffer.wrap(message);
        synchronized (socketEntry.outboundLock) {
            SSLEngineResult r = socketEntry.sslEngine.wrap(outApp, outNet);
            outNet.flip();
            Status rs = r.getStatus();
            if (rs == Status.BUFFER_OVERFLOW) {
                // the client maximum fragment size config does not work?
                throw new IOException("DTLSTM: Buffer overflow: incorrect server maximum fragment size");
            } else if (rs == Status.BUFFER_UNDERFLOW) {
                // unlikely
                throw new IOException("DTLSTM: Buffer underflow during wrapping");
            } else if (rs == Status.CLOSED) {
                throw new IOException("DTLSTM: SSLEngine has closed");
            }   // otherwise, SSLEngineResult.Status.OK
            // SSLEngineResult.Status.OK:
            if (outNet.hasRemaining()) {
                byte[] ba = new byte[outNet.remaining()];
                outNet.get(ba);
                DatagramPacket packet = new DatagramPacket(ba, ba.length, targetSocketAddress);
                packets.add(packet);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Prepared "+packets+" for "+targetAddress);
        }
        return packets;
    }

    protected List<DatagramPacket> onReceiveTimeout(SSLEngine engine, SocketAddress socketAddr) throws IOException {

        HandshakeStatus hs = engine.getHandshakeStatus();
        if (hs == NOT_HANDSHAKING) {
            return new ArrayList<DatagramPacket>();
        } else {
            // retransmission of handshake messages
            return produceHandshakePackets(engine, socketAddr);
        }
    }

    class HandshakeTask implements WorkerTask {
        private boolean endLoops = false;

        private SocketEntry socketEntry;
        private DatagramSocket socket;
        private SocketAddress peerAddr;
        private DatagramPacket receivedPacket;
        private long handshakeTimeout;
        private int maxRetries;

        private int retries = 0;

        public HandshakeTask(SocketEntry socketEntry, DatagramSocket socket, SocketAddress peerAddr,
                             DatagramPacket receivedPacket, long handshakeTimeout, int maxRetries) {
            this.socketEntry = socketEntry;
            this.socket = socket;
            this.peerAddr = peerAddr;
            this.receivedPacket = receivedPacket;
            this.handshakeTimeout = handshakeTimeout;
            this.maxRetries = maxRetries;
        }

        public void run() {
            socketEntry.setHandshakeFinished(false);
            DatagramPacket received = receivedPacket;
            SSLEngine engine = socketEntry.sslEngine;
            engine.setEnableSessionCreation(true);
            boolean endLoops = false;
            int loops = MAX_HANDSHAKE_LOOPS;
            ByteBuffer iNet = null;
            ByteBuffer iApp = null;
            try {
                engine.beginHandshake();
                long startTime = System.nanoTime();
                long timeoutMillis = handshakeTimeout <= 0 ? getHandshakeTimeout() : handshakeTimeout;
                while (!endLoops && !engine.isInboundDone() && (sockets.containsKey(socketEntry.getPeerAddress())) &&
                        (((System.nanoTime() - startTime) / SnmpConstants.MILLISECOND_TO_NANOSECOND) < timeoutMillis)) {
                    if (--loops < 0) {
                        throw new IOException("DTLSTM: Too much loops to produce handshake packets");
                    }
                    HandshakeStatus hs = engine.getHandshakeStatus();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Processing handshake status " + hs + " in loop #" + (MAX_HANDSHAKE_LOOPS - loops));
                    }
                    Status rs = null;
                    while (!endLoops && (hs == NEED_UNWRAP || hs == NEED_UNWRAP_AGAIN)) {
                        if (hs != NEED_UNWRAP_AGAIN) {
                            if (received == null && !((iNet != null) && (iNet.hasRemaining()))) {
                                if (isListening()) {
                                    long timeout = timeoutMillis - ((System.nanoTime() - startTime) /
                                            SnmpConstants.MILLISECOND_TO_NANOSECOND);
                                    if (timeout > 0) {
                                        synchronized (socketEntry) {
                                            try {
                                                if (socketEntry.inboundPacketQueue.isEmpty()) {
                                                    logger.debug("Waiting for next handshake packet timeout=" + timeout);
                                                    socketEntry.wait(timeoutMillis);
                                                }
                                            } catch (InterruptedException iex) {
                                                // ignore
                                            }
                                            synchronized (socketEntry.inboundLock) {
                                                received = socketEntry.inboundPacketQueue.pollFirst();
                                                if (logger.isDebugEnabled() && (received != null)) {
                                                    logger.debug("Polled DTLS packet with length " + received.getLength());
                                                }
                                            }
                                        }
                                    }
                                    else {
                                        endLoops = true;

                                    }
                                    if (received == null) {
                                        continue;
                                    }
                                } else {
                                    byte[] buf = new byte[getMaxInboundMessageSize()];
                                    // receive ClientHello request and other SSL/TLS records
                                    received = new DatagramPacket(buf, buf.length);
                                    try {
                                        socket.receive(received);
                                    } catch (SocketTimeoutException ste) {
                                        if (logger.isInfoEnabled()) {
                                            logger.info("Socket timeout while receiving DTLS handshake packet");
                                        }
                                        if (maxRetries > retries++) {
                                            synchronized (socketEntry.outboundLock) {
                                                // ignore and handle later below
                                                List<DatagramPacket> packets = onReceiveTimeout(engine, peerAddr);
                                                for (DatagramPacket p : packets) {
                                                    socket.send(p);
                                                    if (logger.isDebugEnabled()) {
                                                        logger.debug("Sent " + new OctetString(p.getData()).toHexString() +
                                                                " to " + p.getAddress() + ":" + p.getPort());
                                                    }
                                                }
                                            }
                                        }
                                        else {
                                            endLoops = true;
                                        }
                                        break;
                                    }
                                }
                            }
                            if (received != null) {
                                if (((iNet == null) || (!iNet.hasRemaining()))) {
                                    iNet = ByteBuffer.wrap(received.getData(), 0, received.getLength());
                                } else {
                                    iNet.compact();
                                    iNet.put(received.getData(), 0, received.getLength());
                                    iNet.flip();
                                }
                            }
                            iApp = ByteBuffer.allocate(getMaxInboundMessageSize());
                        } else {
                            iApp = ByteBuffer.allocate(getMaxInboundMessageSize());
                        }
                        received = null;
                        synchronized (socketEntry.inboundLock) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("unrwap start: iNet=" + iNet + ",iApp=" + iApp);
                            }
                            SSLEngineResult r = engine.unwrap(iNet, iApp);
                            rs = r.getStatus();
                            hs = r.getHandshakeStatus();
                            if (logger.isDebugEnabled()) {
                                logger.debug("unrwap done: iNet=" + iNet + ",iApp=" + iApp + ",rs=" + rs + ",hs=" + hs);
                            }
                        }
                        if (rs == Status.BUFFER_OVERFLOW) {
                            // the client maximum fragment size config does not work?
                            throw new IOException("DTLSTM: Buffer overflow: incorrect client maximum fragment size");
                        } else if (rs == Status.BUFFER_UNDERFLOW) {
                            // bad packet, or the client maximum fragment size
                            logger.warn("DTLS buffer underflow iNet="+iNet+",iApp="+iApp);
                            // config does not work?
                            if (hs == NOT_HANDSHAKING) {
                                endLoops = true;
                                break;
                            } // otherwise, ignore this packet
                            continue;
                        } else if (rs == Status.CLOSED) {
                            endLoops = true;
                        }   // otherwise, SSLEngineResult.Status.OK:
                        if (rs != Status.OK) {
                            break;
                        }
                    }
                    if (hs == NEED_WRAP) {
                        synchronized (socketEntry.outboundLock) {
                            List<DatagramPacket> packets = produceHandshakePackets(engine, peerAddr);
                            for (DatagramPacket p : packets) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Sending handshake packet with length "+p.getLength()+
                                            " [" + new OctetString(p.getData()).toHexString() +
                                            "] to " + p.getAddress() + ":" + p.getPort());
                                }
                                socket.send(p);
                            }
                        }
                    } else if (hs == NEED_TASK) {
                        runDelegatedTasks(engine);
                    } else if (hs == NOT_HANDSHAKING) {
                        // OK, time to do application data exchange.
                        endLoops = true;
                    } else if (hs == FINISHED) {
                        endLoops = true;
                    }
                }
            } catch (IOException iox) {
                logger.error("DTLS handshake failed for "+peerAddr+
                        " failed with IO exception:"+iox.getMessage(), iox);
            }
            HandshakeStatus hs = engine.getHandshakeStatus();
            if (hs != NOT_HANDSHAKING) {
                sockets.remove(socketEntry);
                logger.error("DTLS handshake failed for "+peerAddr+": Not ready for application data yet, giving up");
                socketEntry.closeSession();
            }
            else {
                socketEntry.setHandshakeFinished(true);
                if (logger.isInfoEnabled()) {
                    logger.info("SSL handshake completed for "+peerAddr);
                }
                timeoutSocket(socketEntry);
                TransportStateEvent e = new TransportStateEvent(DTLSTM.this, socketEntry.getPeerAddress(),
                        TransportStateEvent.STATE_CONNECTED, null);
                fireConnectionStateChanged(e);
            }
        }

        /**
         * The {@code WorkerPool} might call this method to hint the active
         * {@code WorkTask} instance to complete execution as soon as possible.
         */
        @Override
        public void terminate() {
            endLoops = true;
        }

        /**
         * Waits until this task has been finished.
         *
         * @throws InterruptedException
         *         if the join has been interrupted by another thread.
         */
        @Override
        public void join() throws InterruptedException {
            while (!endLoops) {
                Thread.sleep(10);
            }
        }

        /**
         * Interrupts this task.
         *
         * @see Thread#interrupt()
         */
        @Override
        public void interrupt() {
            synchronized (socketEntry) {
                socketEntry.notify();
            }
        }
    }

    @Override
    public boolean isAsyncMsgProcessingSupported() {
        // Is needed to correctly run DTLS handshake and other inbound packets
        return true;
    }

    @Override
    public void setAsyncMsgProcessingSupported(boolean asyncMsgProcessingSupported) {
        if (!asyncMsgProcessingSupported) {
            throw new IllegalArgumentException("Async message processing cannot be disabled for DTLS");
        }
    }

    @Override
    protected ByteBuffer prepareInPacket(DatagramPacket packet, byte[] buf, TransportStateReference tmStateReference)
            throws IOException
    {
        InetAddress peerAddress = packet.getAddress();
        DtlsAddress peerUdpAddress = new DtlsAddress(peerAddress, packet.getPort());
        if (logger.isDebugEnabled()) {
            logger.debug("Preparing inbound DTLS packet from " + peerUdpAddress);
        }
        InetSocketAddress peerSocketAddress =
                new InetSocketAddress(peerAddress, packet.getPort());
        SocketEntry entry = sockets.get(peerUdpAddress);
        if (entry == null) {
            if (logger.isInfoEnabled()) {
                logger.info("New DTLS connection from " + peerUdpAddress);
            }
            try {
                entry = new SocketEntry(peerUdpAddress, !isServerEnabled(), tmStateReference);
                synchronized (entry.inboundLock) {
                    SocketEntry otherEntry = sockets.get(peerUdpAddress);
                    final SocketEntry handshakeEntry = entry;
                    if (otherEntry == null) {
                        sockets.put(peerUdpAddress, entry);
                        HandshakeTask handshakeTask = new HandshakeTask(handshakeEntry, socket,
                                peerSocketAddress, packet, 0, 0);
                        dtlsHandshakeThreadPool.execute(handshakeTask);
                        return null;
                    }
                    else {
                        entry = otherEntry;
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                logger.error("DTLS handshake failed because of missing algorithm: "+e.getMessage(), e);
                return null;
            }
        }
        // note that socket has been used
        entry.used();
        if (!entry.isHandshakeFinished()) {
            logger.debug("Adding DTLS packet to handshake queue: "+packet);
            synchronized (entry) {
                entry.inboundPacketQueue.add(packet);
                entry.notify();
            }
        }
        else {
            ByteBuffer inAppBuffer = ByteBuffer.allocate(getMaxInboundMessageSize());
            ByteBuffer inNetBuffer;
            synchronized (entry.outboundLock) {
               inNetBuffer = ByteBuffer.wrap(buf, 0, packet.getLength());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Read " + packet.getLength() + " bytes from " + peerUdpAddress);
                logger.debug("DTLS inNetBuffer: " + inNetBuffer);
            }
            if (inNetBuffer.hasRemaining()) {
                SSLEngineResult result;
                synchronized (entry.inboundLock) {
                    result = entry.sslEngine.unwrap(inNetBuffer, inAppBuffer);
                    switch (result.getStatus()) {
                        case BUFFER_OVERFLOW:
                            // TODO handle overflow
                            logger.error("DTLS BUFFER_OVERFLOW");
                            throw new RuntimeException("DTLS BUFFER_OVERFLOW");
                    }
                    if (runDelegatedTasks(entry.sslEngine)) {
                        if (logger.isInfoEnabled()) {
                            logger.info("SSL session established for peer " + peerUdpAddress);
                        }
                        if (result.bytesProduced() > 0) {
                            inAppBuffer.flip();
                            logger.debug("SSL established, dispatching inappBuffer=" + inAppBuffer);
                            // SSL session is established
                            entry.checkTransportStateReference(tmStateReference);
                            return inAppBuffer;
                        }
                    }
                }
            }
        }
        return null;
    }


    /**
     * If the result indicates that we have outstanding tasks to do,
     * go ahead and run them in this thread.
     *
     * @param engine the SSLEngine wrap/unwrap result.
     * @return {@code true} if processing of delegated tasks has been
     * finished, {@code false} otherwise.
     */
    boolean runDelegatedTasks(SSLEngine engine) {
        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null) {
            runnable.run();
        }
        HandshakeStatus hs = engine.getHandshakeStatus();
        if (hs == NEED_TASK) {
            return false;
        }
        return true;
    }


    protected List<DatagramPacket> produceHandshakePackets(SSLEngine sslEngine,
                                                           SocketAddress socketAddress) throws IOException {
        List<DatagramPacket> packets = new ArrayList<>();
        boolean endLoops = false;
        int loops = MAX_HANDSHAKE_LOOPS;
        while (!endLoops) {

            if (--loops < 0) {
                throw new RuntimeException(
                        "Too much loops to produce handshake packets");
            }

            ByteBuffer oNet = ByteBuffer.allocate(getMaxInboundMessageSize());
            ByteBuffer oApp = ByteBuffer.allocate(0);
            SSLEngineResult r = sslEngine.wrap(oApp, oNet);
            oNet.flip();

            Status rs = r.getStatus();
            HandshakeStatus hs = r.getHandshakeStatus();
            if (rs == Status.BUFFER_OVERFLOW) {
                // the client maximum fragment size config does not work?
                throw new IOException("Buffer overflow: " +
                        "incorrect server maximum fragment size");
            } else if (rs == Status.BUFFER_UNDERFLOW) {
                // bad packet, or the client maximum fragment size
                // config does not work?
                if (hs != NOT_HANDSHAKING) {
                    throw new IOException("Buffer underflow: " +
                            "incorrect server maximum fragment size");
                } // otherwise, ignore this packet
            } else if (rs == Status.CLOSED) {
                throw new IOException("SSLEngine has closed");
            }   // otherwise, SSLEngineResult.Status.OK

            // SSLEngineResult.Status.OK:
            if (oNet.hasRemaining()) {
                byte[] ba = new byte[oNet.remaining()];
                oNet.get(ba);
                DatagramPacket packet = createHandshakePacket(ba, socketAddress);
                packets.add(packet);
            }
            boolean endInnerLoop = false;
            HandshakeStatus nhs = hs;
            while (!endInnerLoop) {
                if (nhs == NEED_TASK) {
                    runDelegatedTasks(sslEngine);
                    nhs = sslEngine.getHandshakeStatus();
                } else if ((nhs == FINISHED) ||
                        (nhs == NEED_UNWRAP) ||
                        (nhs == NEED_UNWRAP_AGAIN) ||
                        (nhs == NOT_HANDSHAKING)) {
                    endInnerLoop = true;
                    endLoops = true;
                } else if (nhs == NEED_WRAP) {
                    endInnerLoop = true;
                }
            }
        }
        return packets;
    }

    protected DatagramPacket createHandshakePacket(byte[] buf, SocketAddress socketAddr) {
        return new DatagramPacket(buf, buf.length, socketAddr);
    }


    class SocketEntry extends AbstractServerSocket<UdpAddress> {
        private SSLEngine sslEngine;
        private long sessionID;
        private TransportStateReference tmStateReference;
        private boolean handshakeFinished;

        private final Object outboundLock = new Object();
        private final Object inboundLock = new Object();

        private LinkedList<DatagramPacket> inboundPacketQueue = new LinkedList<>();

        public SocketEntry(UdpAddress address, boolean useClientMode,
                           TransportStateReference tmStateReference) throws NoSuchAlgorithmException {
            super(address);
            this.tmStateReference = tmStateReference;
            if (tmStateReference == null) {
                counterSupport.fireIncrementCounter(new CounterEvent(this, SnmpConstants.snmpTlstmSessionAccepts));
            }
            SSLContext sslContext = sslEngineConfigurator.getSSLContext(useClientMode, tmStateReference);
            if (sslContext == null) {
                throw new RuntimeException("Failed to initialize SSLContext");
            }
            this.sslEngine = sslContext.createSSLEngine(address.getInetAddress().getHostName(), address.getPort());
            sslEngine.setUseClientMode(useClientMode);
            sslEngine.setNeedClientAuth(false);
            SSLParameters parameters = this.sslEngine.getSSLParameters();
            parameters.setMaximumPacketSize(getMaxInboundMessageSize());
            this.sslEngine.setSSLParameters(parameters);
            sslEngineConfigurator.configure(sslEngine);
            synchronized (DTLSTM.this) {
                sessionID = nextSessionID++;
            }
        }


        public String toString() {
            return "SocketEntry[peerAddress=" + getPeerAddress() +
                    ",socket=" + socket + ",lastUse=" +
                    new Date(getLastUse() / SnmpConstants.MILLISECOND_TO_NANOSECOND) +
                    "]";
        }

        public void checkTransportStateReference(TransportStateReference tmStateReference) {
            tmStateReference.setTransport(DTLSTM.this);
            tmStateReference.setRequestedSecurityLevel(SecurityLevel.authPriv);
            if (tmStateReference.getTransportSecurityLevel().equals(SecurityLevel.undefined)) {
                tmStateReference.setTransportSecurityLevel(SecurityLevel.authPriv);
            }
            OctetString securityName = tmStateReference.getSecurityName();
            if (securityCallback != null) {
                try {
                    securityName = securityCallback.getSecurityName(
                            (X509Certificate[]) sslEngine.getSession().getPeerCertificates());
                } catch (SSLPeerUnverifiedException e) {
                    logger.error("SSL peer '" + getPeerAddress() + "' is not verified: " + e.getMessage(), e);
                    sslEngine.setEnableSessionCreation(false);
                }
            }
            else if (securityName == null) {
                logger.warn("No security callback configured to match DTLS peer certificate to local security name");
            }
            tmStateReference.setSecurityName(securityName);
        }

        public boolean isHandshakeFinished() {
            return handshakeFinished;
        }

        public void setHandshakeFinished(boolean handshakeFinished) {
            this.handshakeFinished = handshakeFinished;
        }

        public long getSessionID() {
            return sessionID;
        }

        public void closeSession() {
            sslEngine.closeOutbound();
            counterSupport.fireIncrementCounter(new CounterEvent(this, SnmpConstants.snmpTlstmSessionServerCloses));
            ByteBuffer outNetBuffer = ByteBuffer.allocate(getMaxInboundMessageSize());
            try {
                SSLEngineResult sslEngineResult;
                do {
                    sslEngineResult = sslEngine.wrap(ByteBuffer.allocate(0), outNetBuffer);
                    outNetBuffer.flip();
                    socket.send(new DatagramPacket(outNetBuffer.array(), outNetBuffer.limit(),
                            getPeerAddress().getInetAddress(), getPeerAddress().getPort()));
                }
                while ((sslEngineResult.getStatus() != Status.CLOSED) &&
                        (sslEngineResult.getHandshakeStatus() == NEED_WRAP));
            } catch (Exception e) {
                logger.error("DTLSM: Exception while closing TLS session " + this + ": " + e.getMessage(), e);
            }
            TransportStateEvent e =
                    new TransportStateEvent(DTLSTM.this, getPeerAddress(), TransportStateEvent.STATE_CLOSED,
                            null);
            fireConnectionStateChanged(e);
        }
    }

    interface SSLEngineConfigurator {
        /**
         * Configure the supplied SSLEngine for TLS.
         * Configuration includes enabled protocol(s),
         * cipher codes, etc.
         *
         * @param sslEngine a {@link SSLEngine} to configure.
         */
        void configure(SSLEngine sslEngine);

        /**
         * Gets the SSLContext for this SSL connection.
         *
         * @param useClientMode           {@code true} if the connection is established in client mode.
         * @param transportStateReference the transportStateReference with additional
         *                                security information for the SSL connection
         *                                to establish.
         * @return the SSLContext.
         */
        SSLContext getSSLContext(boolean useClientMode, TransportStateReference transportStateReference);
    }

    protected class DefaultSSLEngineConfiguration implements SSLEngineConfigurator {

        @Override
        public void configure(SSLEngine sslEngine) {
            logger.debug("Configuring SSL engine, supported protocols are " +
                    Arrays.asList(sslEngine.getSupportedProtocols()) + ", supported ciphers are " +
                    Arrays.asList(sslEngine.getSupportedCipherSuites()) + ", https defaults are " +
                    System.getProperty("https.cipherSuites"));
            String[] supportedCipherSuites = sslEngine.getEnabledCipherSuites();
            List<String> enabledCipherSuites = new ArrayList<>(supportedCipherSuites.length);
            for (String cs : supportedCipherSuites) {
                if (!cs.contains("_anon_") && (!cs.contains("_NULL_"))) {
                    enabledCipherSuites.add(cs);
                }
            }
            //enabledCipherSuites.add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256");
            sslEngine.setEnabledCipherSuites(enabledCipherSuites.toArray(new String[0]));
            sslEngine.setEnabledProtocols(getProtocolVersions());
            if (!sslEngine.getUseClientMode()) {
                sslEngine.setNeedClientAuth(true);
                sslEngine.setWantClientAuth(true);
                logger.info("Need client authentication set to true");
            }
            logger.info("Configured SSL engine, enabled protocols are " +
                    Arrays.asList(sslEngine.getEnabledProtocols()) + ", enabled ciphers are " +
                    Arrays.asList(sslEngine.getEnabledCipherSuites()));
        }

        @Override
        public SSLContext getSSLContext(boolean useClientMode, TransportStateReference transportStateReference) {
            try {
                String protocol = DEFAULT_DTLSTM_PROTOCOLS;
                    if ((getProtocolVersions() != null) && (getProtocolVersions().length > 0)) {
                        protocol = getProtocolVersions()[0];
                    }
                    return TLSTMUtil.createSSLContext(protocol, getKeyStore(), getKeyStorePassword(),
                            getTrustStore(), getTrustStorePassword(),
                            transportStateReference, trustManagerFactory,
                            useClientMode, securityCallback, localCertificateAlias);

            } catch (NoSuchAlgorithmException e) {
                logger.error("Failed to initialize SSLContext because of an NoSuchAlgorithmException: " +
                        e.getMessage(), e);
            }
            return null;
        }
    }

    protected class TlsTrustManager implements X509TrustManager {

        X509TrustManager trustManager;
        private boolean useClientMode;
        private TransportStateReference tmStateReference;

        protected TlsTrustManager(X509TrustManager trustManager, boolean useClientMode,
                                  TransportStateReference tmStateReference) {
            this.trustManager = trustManager;
            this.useClientMode = useClientMode;
            this.tmStateReference = tmStateReference;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                throws CertificateException {
            if ((tmStateReference != null) && (tmStateReference.getCertifiedIdentity() != null)) {
                OctetString fingerprint = tmStateReference.getCertifiedIdentity().getClientFingerprint();
                if (isMatchingFingerprint(x509Certificates, fingerprint)) {
                    return;
                }
            }
            TlsTmSecurityCallback<X509Certificate> callback = securityCallback;
            if (!useClientMode && (callback != null)) {
                if (callback.isClientCertificateAccepted(x509Certificates[0])) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Client is trusted with certificate '" + x509Certificates[0] + "'");
                    }
                    return;
                }
            }
            try {
                trustManager.checkClientTrusted(x509Certificates, s);
            } catch (CertificateException cex) {
                counterSupport.fireIncrementCounter(new CounterEvent(this, SnmpConstants.snmpTlstmSessionOpenErrors));
                counterSupport.fireIncrementCounter(new CounterEvent(this, SnmpConstants.snmpTlstmSessionInvalidClientCertificates));
                logger.warn("Client certificate validation failed for '" + x509Certificates[0] + "'");
                throw cex;
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            if (tmStateReference.getCertifiedIdentity() != null) {
                OctetString fingerprint = tmStateReference.getCertifiedIdentity().getServerFingerprint();
                if (isMatchingFingerprint(x509Certificates, fingerprint)) return;
            }
            Object entry = null;
            try {
                entry = TLSTMUtil.getSubjAltName(x509Certificates[0].getSubjectAlternativeNames(), 2);
            } catch (CertificateParsingException e) {
                logger.error("CertificateParsingException while verifying server certificate " +
                        Arrays.asList(x509Certificates));
            }
            if (entry == null) {
                X500Principal x500Principal = x509Certificates[0].getSubjectX500Principal();
                if (x500Principal != null) {
                    entry = x500Principal.getName();
                }
            }
            if (entry != null) {
                String dNSName = ((String) entry).toLowerCase();
                String hostName = ((IpAddress) tmStateReference.getAddress())
                        .getInetAddress().getCanonicalHostName();
                if (dNSName.length() > 0) {
                    if (dNSName.charAt(0) == '*') {
                        int pos = hostName.indexOf('.');
                        hostName = hostName.substring(pos);
                        dNSName = dNSName.substring(1);
                    }
                    if (hostName.equalsIgnoreCase(dNSName)) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Peer hostname " + hostName + " matches dNSName " + dNSName);
                        }
                        return;
                    }
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Peer hostname " + hostName + " did not match dNSName " + dNSName);
                }
            }
            try {
                trustManager.checkServerTrusted(x509Certificates, s);
            } catch (CertificateException cex) {
                counterSupport.fireIncrementCounter(new CounterEvent(this, SnmpConstants.snmpTlstmSessionOpenErrors));
                counterSupport.fireIncrementCounter(new CounterEvent(this, SnmpConstants.snmpTlstmSessionUnknownServerCertificate));
                logger.warn("Server certificate validation failed for '" + x509Certificates[0] + "'");
                throw cex;
            }
            TlsTmSecurityCallback<X509Certificate> callback = securityCallback;
            if (useClientMode && (callback != null)) {
                if (!callback.isServerCertificateAccepted(x509Certificates)) {
                    logger.info("Server is NOT trusted with certificate '" + Arrays.asList(x509Certificates) + "'");
                    throw new CertificateException("Server's certificate is not trusted by this application (although it was trusted by the JRE): " +
                            Arrays.asList(x509Certificates));
                }
            }
        }

        private boolean isMatchingFingerprint(X509Certificate[] x509Certificates, OctetString fingerprint) {
            if ((fingerprint != null) && (fingerprint.length() > 0)) {
                for (X509Certificate cert : x509Certificates) {
                    OctetString certFingerprint = null;
                    certFingerprint = TLSTMUtil.getFingerprint(cert);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Comparing certificate fingerprint " + certFingerprint +
                                " with " + fingerprint);
                    }
                    if (certFingerprint == null) {
                        logger.error("Failed to determine fingerprint for certificate " + cert +
                                " and algorithm " + cert.getSigAlgName());
                    } else if (certFingerprint.equals(fingerprint)) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Peer is trusted by fingerprint '" + fingerprint + "' of certificate: '" + cert + "'");
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            TlsTmSecurityCallback<X509Certificate> callback = securityCallback;
            X509Certificate[] accepted = trustManager.getAcceptedIssuers();
            if ((accepted != null) && (callback != null)) {
                ArrayList<X509Certificate> acceptedIssuers = new ArrayList<X509Certificate>(accepted.length);
                for (X509Certificate cert : accepted) {
                    if (callback.isAcceptedIssuer(cert)) {
                        acceptedIssuers.add(cert);
                    }
                }
                return acceptedIssuers.toArray(new X509Certificate[0]);
            }
            return accepted;
        }
    }

    private class DefaultDTLSTMTrustManagerFactory implements TLSTMTrustManagerFactory {
        public X509TrustManager create(X509TrustManager trustManager, boolean useClientMode,
                                       TransportStateReference tmStateReference) {
            return new TlsTrustManager(trustManager, useClientMode, tmStateReference);
        }
    }

}
