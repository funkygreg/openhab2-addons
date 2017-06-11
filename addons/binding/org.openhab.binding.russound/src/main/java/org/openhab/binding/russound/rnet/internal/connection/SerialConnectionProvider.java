/**
     * Copyright (c) 2010-2016 by the respective copyright holders.
     *
     * All rights reserved. This program and the accompanying materials
     * are made available under the terms of the Eclipse Public License v1.0
     * which accompanies this distribution, and is available at
     * http://www.eclipse.org/legal/epl-v10.html
     */
package org.openhab.binding.russound.rnet.internal.connection;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

/**
 * Implements IOStream for serial devices.
 *
 * @author Bernd Pfrommer
 * @author Daniel Pfrommer
 * @since 1.7.0
 */
public class SerialConnectionProvider implements ConnectionProvider {
    private final Logger logger = LoggerFactory.getLogger(SerialConnectionProvider.class);
    private SerialPort m_port = null;
    private final String m_appName = "Russound";
    private final int m_speed = 19200; // baud rate
    private String m_devName = null;

    private ObjectOutputStream m_out = null;
    private DataInputStream m_in = null;

    public SerialConnectionProvider(String devName) {
        m_devName = devName;
    }

    @Override
    public boolean connect() throws NoConnectionException {
        try {
            updateSerialProperties(m_devName);
            CommPortIdentifier ci = CommPortIdentifier.getPortIdentifier(m_devName);
            CommPort cp = ci.open(m_appName, 1000);
            if (cp instanceof SerialPort) {
                m_port = (SerialPort) cp;
            } else {
                throw new IllegalStateException("unknown port type");
            }
            m_port.setSerialPortParams(m_speed, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            m_port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            logger.debug("setting port speed to {}", m_speed);
            m_port.disableReceiveFraming();
            m_port.enableReceiveThreshold(1);
            // m_port.disableReceiveTimeout();
            m_port.enableReceiveTimeout(1000);
            m_in = new DataInputStream(m_port.getInputStream());
            m_out = new ObjectOutputStream(m_port.getOutputStream());
            logger.info("successfully opened port {}", m_devName);
            return true;
        } catch (IOException e) {
            throw new NoConnectionException("cannot open port got IOException: " + m_devName);
        } catch (PortInUseException e) {
            throw new NoConnectionException("cannot open port: {}, it is in use! : " + m_devName);
        } catch (UnsupportedCommOperationException e) {
            throw new NoConnectionException("got unsupported operation: " + e.getMessage() + " on port:" + m_devName);
        } catch (NoSuchPortException e) {
            throw new NoConnectionException("got no such port for: " + m_devName);
        } catch (IllegalStateException e) {
            throw new NoConnectionException("got unknown port type for:" + m_devName);
        }

    }

    private void updateSerialProperties(String devName) {

        /*
         * By default, RXTX searches only devices /dev/ttyS* and
         * /dev/ttyUSB*, and will therefore not find devices that
         * have been symlinked. Adding them however is tricky, see below.
         */

        //
        // first go through the port identifiers to find any that are not in
        // "gnu.io.rxtx.SerialPorts"
        //
        ArrayList<String> allPorts = new ArrayList<String>();
        @SuppressWarnings("rawtypes")
        Enumeration portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier id = (CommPortIdentifier) portList.nextElement();
            if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                allPorts.add(id.getName());
            }
        }
        logger.trace("ports found from identifiers: {}", StringUtils.join(allPorts, ":"));
        //
        // now add our port so it's in the list
        //
        if (!allPorts.contains(devName)) {
            allPorts.add(devName);
        }
        //
        // add any that are already in "gnu.io.rxtx.SerialPorts"
        // so we don't accidentally overwrite some of those ports

        String ports = System.getProperty("gnu.io.rxtx.SerialPorts");
        if (ports != null) {
            ArrayList<String> propPorts = new ArrayList<String>(Arrays.asList(ports.split(":")));
            for (String p : propPorts) {
                if (!allPorts.contains(p)) {
                    allPorts.add(p);
                }
            }
        }
        String finalPorts = StringUtils.join(allPorts, ":");
        logger.trace("final port list: {}", finalPorts);

        //
        // Finally overwrite the "gnu.io.rxtx.SerialPorts" System property.
        //
        // Note: calling setProperty() is not threadsafe. All bindings run in
        // the same address space, System.setProperty() is globally visible
        // to all bindings.
        // This means if multiple bindings use the serial port there is a
        // race condition where two bindings could be changing the properties
        // at the same time
        //
        System.setProperty("gnu.io.rxtx.SerialPorts", finalPorts);
    }

    @Override
    public void disconnect() {
        if (m_port != null) {
            m_port.close();
        }
        m_port = null;
    }

    @Override
    public ObjectOutputStream getOutputStream() {
        return m_out;
    }

    @Override
    public DataInputStream getInputStream() {
        return m_in;
    }

    @Override
    public boolean isConnected() {
        // TODO Auto-generated method stub
        return false;
    }

}
