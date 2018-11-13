/*_############################################################################
  _## 
  _##  SNMP4J - DtlsAddress.java  
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

package org.snmp4j.smi;

import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;

import java.net.InetAddress;

/**
 * The <code>TlsAddress</code> represents a TLS transport addresses as defined
 * by RFC 5953 SnmpTSLAddress textual convention.
 *
 * @author Frank Fock
 * @version 3.0
 * @since 3.0
 */
public class DtlsAddress extends UdpAddress {

    static final long serialVersionUID = 0L;

    private static final LogAdapter logger = LogFactory.getLogger(DtlsAddress.class);

    public DtlsAddress() {
        super();
    }

    public DtlsAddress(UdpAddress udpAddress) {
        super(udpAddress.getInetAddress(), udpAddress.getPort());
    }

    public DtlsAddress(InetAddress inetAddress, int port) {
        super(inetAddress, port);
    }

    /**
     * Create a DtlsAddress for the local host ({@link InetAddress#getLocalHost()}
     * with the provided port. If the local host is not known, a {@link RuntimeException}
     * is thrown.
     *
     * @param port
     *         the UDP port.
     */
    public DtlsAddress(int port) {
        super(port);
    }

    public DtlsAddress(String address) {
        if (!parseAddress(address)) {
            throw new IllegalArgumentException(address);
        }
    }

    public static Address parse(String address) {
        try {
            DtlsAddress a = new DtlsAddress();
            if (a.parseAddress(address)) {
                return a;
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
        return null;
    }

    public boolean equals(Object o) {
        return (o instanceof DtlsAddress) && super.equals(o);
    }

}

