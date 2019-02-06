/*_############################################################################
  _## 
  _##  SNMP4J - TLSTMUtil.java  
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
package org.snmp4j.transport.tls;

import org.snmp4j.TransportStateReference;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import org.snmp4j.smi.OctetString;
import org.snmp4j.transport.DTLSTM;
import org.snmp4j.transport.TLSTM;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * The {@code TLSTMUtil} class implements common functions for {@link org.snmp4j.transport.TLSTM} and
 * {@link org.snmp4j.transport.DTLSTM}.
 *
 * @author Frank Fock
 * @since 3.0
 */
public class TLSTMUtil {

    private static final LogAdapter logger =
            LogFactory.getLogger(TLSTMUtil.class);

    private static final int MD_SHA_PREFIX_LENGTH = "SHA".length();

    public static OctetString getFingerprint(X509Certificate cert) {
        OctetString certFingerprint = null;
        try {
            String algo = cert.getSigAlgName();
            if (algo.contains("with")) {
                algo = algo.substring(0, algo.indexOf("with"));
            }
            if (algo.length() > MD_SHA_PREFIX_LENGTH) {
                char c = algo.charAt(MD_SHA_PREFIX_LENGTH);
                switch (c) {
                    case '1':
                    case '2':
                        String algoPrefix = algo.substring(0, MD_SHA_PREFIX_LENGTH);
                        String algoSuffix = algo.substring(MD_SHA_PREFIX_LENGTH);
                        algo = algoPrefix + "-" +algoSuffix;
                        break;
                }
            }
            MessageDigest md = MessageDigest.getInstance(algo);
            md.update(cert.getEncoded());
            certFingerprint = new OctetString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            logger.error("No such digest algorithm exception while getting fingerprint from " +
                    cert + ": " + e.getMessage(), e);
        } catch (CertificateEncodingException e) {
            logger.error("Certificate encoding exception while getting fingerprint from " +
                    cert + ": " + e.getMessage(), e);
        }
        return certFingerprint;
    }

    public static Object getSubjAltName(Collection<List<?>> subjAltNames, int type) {
        if (subjAltNames != null) {
            for (List<?> entry : subjAltNames) {
                int t = (Integer) entry.get(0);
                if (t == type) {
                    return entry.get(1);
                }
            }
        }
        return null;
    }

    public static SSLContext createSSLContext(String protocol, String keyStore, String keyStorePassword,
                                              String trustStore, String trustStorePassword,
                                              TransportStateReference transportStateReference,
                                              TLSTMTrustManagerFactory trustManagerFactory, boolean useClientMode,
                                              TlsTmSecurityCallback<X509Certificate> securityCallback,
                                              String localCertificateAlias)
            throws NoSuchAlgorithmException
    {
        SSLContext sslContext = SSLContext.getInstance(protocol);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunPKIX");
        try (FileInputStream fisKeyStore = new FileInputStream(keyStore);
             FileInputStream fisTrustStore = new FileInputStream(trustStore)) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(fisKeyStore, (keyStorePassword != null) ? keyStorePassword.toCharArray() : null);
            if (logger.isInfoEnabled()) {
                logger.info("KeyStore '" + keyStore + "' contains: " + Collections.list(ks.aliases()));
            }
            filterCertificates(ks, transportStateReference, securityCallback, localCertificateAlias);
            KeyStore ts = KeyStore.getInstance("JKS");
            ts.load(fisTrustStore, (trustStorePassword != null) ? trustStorePassword.toCharArray() : null);
            if (logger.isInfoEnabled()) {
                logger.info("TrustStore '" + trustStore + "' contains: " + Collections.list(ts.aliases()));
            }
            // Set up key manager factory to use our key store
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, (keyStorePassword != null) ? keyStorePassword.toCharArray() : null);
            tmf.init(ts);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            if (logger.isDebugEnabled()) {
                logger.debug("SSL context initializing with TrustManagers: " + Arrays.asList(trustManagers) +
                        " and factory " + trustManagerFactory.getClass().getName());
            }
            sslContext.init(kmf.getKeyManagers(),
                    new TrustManager[]{trustManagerFactory.create((X509TrustManager) trustManagers[0],
                            useClientMode, transportStateReference)},
                    null);
            return sslContext;
        } catch (NullPointerException npe) {
            logger.error("Failed to initialize SSLContext because of missing key store (javax.net.ssl.keyStore)");
        } catch (KeyStoreException e) {
            logger.error("Failed to initialize SSLContext because of a KeyStoreException: " + e.getMessage(), e);
        } catch (KeyManagementException e) {
            logger.error("Failed to initialize SSLContext because of a KeyManagementException: " + e.getMessage(), e);
        } catch (UnrecoverableKeyException e) {
            logger.error("Failed to initialize SSLContext because of an UnrecoverableKeyException: " + e.getMessage(), e);
        } catch (CertificateException e) {
            logger.error("Failed to initialize SSLContext because of a CertificateException: " + e.getMessage(), e);
        } catch (FileNotFoundException e) {
            logger.error("Failed to initialize SSLContext because of a FileNotFoundException: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("Failed to initialize SSLContext because of an IOException: " + e.getMessage(), e);
        }
        return null;

    }

    public static void filterCertificates(KeyStore ks, TransportStateReference transportStateReference,
                                          TlsTmSecurityCallback<X509Certificate> securityCallback,
                                          String localCertificateAlias) {
        String localCertAlias = localCertificateAlias;
        if ((securityCallback != null) && (transportStateReference != null)) {
            localCertAlias = securityCallback.getLocalCertificateAlias(transportStateReference.getAddress());
            if (localCertAlias == null) {
                localCertAlias = localCertificateAlias;
            }
        }
        if (localCertAlias != null) {
            try {
                java.security.cert.Certificate[] chain = ks.getCertificateChain(localCertAlias);
                if (chain == null) {
                    logger.warn("Local certificate with alias '" + localCertAlias + "' not found. Known aliases are: " +
                            Collections.list(ks.aliases()));
                } else {
                    List<String> chainAliases = new ArrayList<String>(chain.length);
                    for (java.security.cert.Certificate certificate : chain) {
                        String alias = ks.getCertificateAlias(certificate);
                        if (alias != null) {
                            chainAliases.add(alias);
                        }
                    }
                    // now delete all others from key store
                    for (String alias : Collections.list(ks.aliases())) {
                        if (!chainAliases.contains(alias)) {
                            ks.deleteEntry(alias);
                        }
                    }
                }
            } catch (KeyStoreException e) {
                logger.error("Failed to get certificate chain for alias " +
                        localCertAlias + ": " + e.getMessage(), e);
            }
        }
    }

}

