package ro.marcman.mixer.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.serial.model.ArduinoCommand;
import ro.marcman.mixer.serial.model.SerialResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serial communication manager for Arduino MASTER.
 * 
 * Features:
 * 1. Auto-detect Arduino Mega COM port
 * 2. Open connection at 115200 baud
 * 3. Send text commands terminated with \n
 * 4. Asynchronous read of responses
 * 5. Parse output and provide callbacks
 */
@Slf4j
public class SerialManager {
    
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = SerialPort.ONE_STOP_BIT;
    private static final int PARITY = SerialPort.NO_PARITY;
    
    private SerialPort serialPort;
    private final List<SerialListener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder buffer = new StringBuilder();
    private boolean connected = false;
    
    /**
     * 1. AUTO-DETECT Arduino Mega COM port
     * 
     * Searches for Arduino Mega by checking common port descriptors.
     * Returns the first matching port found.
     */
    public SerialPort detectArduinoPort() {
        log.info("Scanning for Arduino Mega...");
        
        SerialPort[] ports = SerialPort.getCommPorts();
        
        if (ports.length == 0) {
            log.warn("No COM ports found");
            return null;
        }
        
        for (SerialPort port : ports) {
            String description = port.getDescriptivePortName().toLowerCase();
            String portDesc = port.getPortDescription().toLowerCase();
            
            log.debug("Found port: {} - {}", port.getSystemPortName(), description);
            
            // Arduino Mega typically identifies as:
            // - "Arduino Mega"
            // - "CH340" (clone with CH340 USB chip)
            // - "FTDI" (with FTDI chip)
            // - "USB Serial Port"
            // - "USB-SERIAL CH340"
            if (description.contains("arduino") ||
                description.contains("mega") ||
                description.contains("ch340") ||
                description.contains("ch341") ||
                portDesc.contains("arduino") ||
                portDesc.contains("mega") ||
                portDesc.contains("ch340")) {
                
                log.info("Detected Arduino Mega on port: {}", port.getSystemPortName());
                return port;
            }
        }
        
        // If no specific Arduino found, return first available port
        if (ports.length > 0) {
            log.warn("No Arduino Mega specifically detected, using first available port: {}", 
                     ports[0].getSystemPortName());
            return ports[0];
        }
        
        return null;
    }
    
    /**
     * Get all available COM ports
     */
    public List<String> getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        List<String> portNames = new ArrayList<>();
        
        for (SerialPort port : ports) {
            portNames.add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
        }
        
        return portNames;
    }
    
    /**
     * 2. CONNECT to Arduino at 115200 baud
     * 
     * Opens the serial connection with proper parameters for Arduino communication.
     */
    public boolean connect(String portName) {
        if (connected) {
            log.warn("Already connected to {}", serialPort.getSystemPortName());
            // Disconnect first before connecting to a different port
            disconnect();
        }
        
        SerialPort port = SerialPort.getCommPort(portName);
        if (port == null) {
            log.error("Port not found: {}", portName);
            notifyError("Port not found: " + portName);
            return false;
        }
        return connect(port);
    }
    
    /**
     * Connect using auto-detected port
     */
    public boolean connectAuto() {
        SerialPort port = detectArduinoPort();
        if (port == null) {
            log.error("No Arduino port detected");
            notifyError("No Arduino port detected");
            return false;
        }
        return connect(port);
    }
    
    private boolean connect(SerialPort port) {
        if (port == null) {
            log.error("Serial port is null");
            return false;
        }
        
        // Check if this specific port is already open and close it if so
        if (port.isOpen()) {
            log.warn("Port {} is already open, closing first", port.getSystemPortName());
            port.closePort();
            try {
                Thread.sleep(300); // Wait for OS to release the port
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // If we have a previous port open, close it first
        if (serialPort != null && serialPort.isOpen()) {
            log.info("Closing previous connection to {}", serialPort.getSystemPortName());
            serialPort.closePort();
            try {
                Thread.sleep(200); // Wait for OS to release the port
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        serialPort = port;
        
        // Configure serial port parameters
        log.debug("Configuring port {}: baud={}, dataBits={}, stopBits={}, parity={}", 
                 port.getSystemPortName(), BAUD_RATE, DATA_BITS, STOP_BITS, PARITY);
        serialPort.setComPortParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        
        // Open the port
        log.debug("Attempting to open port: {}", port.getSystemPortName());
        if (!serialPort.openPort()) {
            log.error("Failed to open port: {} - port may be in use by another application", 
                     serialPort.getSystemPortName());
            notifyError("Failed to open port: " + serialPort.getSystemPortName() + 
                       " - port may be in use by another application");
            return false;
        }
        log.debug("Successfully opened port: {}", serialPort.getSystemPortName());
        
        // Add data listener for asynchronous reading
        serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }
            
            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    readData();
                }
            }
        });
        
        connected = true;
        log.info("Connected to Arduino on port: {} at {} baud", 
                 serialPort.getSystemPortName(), BAUD_RATE);
        
        notifyConnected(serialPort.getSystemPortName());
        
        return true;
    }
    
    /**
     * 3. SEND COMMAND to Arduino (terminated with \n)
     * 
     * Sends a text command to the Arduino MASTER.
     * Commands are automatically terminated with newline character.
     */
    public boolean sendCommand(ArduinoCommand command) {
        if (!connected || serialPort == null) {
            log.error("Not connected to Arduino");
            notifyError("Not connected to Arduino");
            return false;
        }
        
        String cmd = command.getRawCommand();
        if (!cmd.endsWith("\n")) {
            cmd += "\n";
        }
        
        try {
            byte[] bytes = cmd.getBytes(StandardCharsets.UTF_8);
            int written = serialPort.writeBytes(bytes, bytes.length);
            
            if (written == bytes.length) {
                log.debug("Sent command: {}", command.getRawCommand().trim());
                return true;
            } else {
                log.error("Failed to write complete command. Wrote {} of {} bytes", written, bytes.length);
                notifyError("Failed to write complete command");
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending command: {}", e.getMessage(), e);
            notifyError("Error sending command: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send raw string command
     */
    public boolean sendRawCommand(String command) {
        return sendCommand(ArduinoCommand.custom(command));
    }
    
    /**
     * 4. READ DATA asynchronously from Arduino
     * 
     * Called automatically when data is available.
     * Buffers incoming data and processes complete lines.
     */
    private void readData() {
        if (serialPort == null || !serialPort.isOpen()) {
            return;
        }
        
        byte[] readBuffer = new byte[1024];
        int numRead = serialPort.readBytes(readBuffer, readBuffer.length);
        
        if (numRead > 0) {
            String data = new String(readBuffer, 0, numRead, StandardCharsets.UTF_8);
            buffer.append(data);
            
            // Process complete lines
            processBuffer();
        }
    }
    
    /**
     * 5. PARSE OUTPUT and notify listeners
     * 
     * Processes buffered data line by line and creates SerialResponse objects.
     * Notifies all registered listeners with parsed responses.
     */
    private void processBuffer() {
        int newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) != -1) {
            String line = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);
            
            if (!line.isEmpty()) {
                SerialResponse response = SerialResponse.fromRaw(line);
                log.debug("Received: {}", line);
                notifyDataReceived(response);
            }
        }
    }
    
    /**
     * Disconnect from Arduino
     */
    public void disconnect() {
        if (serialPort != null) {
            try {
                // Remove all data listeners first
                serialPort.removeDataListener();
                
                // Close the port if it's open
                if (serialPort.isOpen()) {
                    serialPort.closePort();
                    log.info("Disconnected from {}", serialPort.getSystemPortName());
                    notifyDisconnected();
                }
                
                // Wait a bit to ensure OS releases the port
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Ignore
                }
            } catch (Exception e) {
                log.error("Error during disconnect: {}", e.getMessage(), e);
            }
        }
        connected = false;
        serialPort = null;
        buffer.setLength(0);
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected && serialPort != null && serialPort.isOpen();
    }
    
    /**
     * Get current port name
     */
    public String getPortName() {
        return serialPort != null ? serialPort.getSystemPortName() : null;
    }
    
    // ==================== LISTENER MANAGEMENT ====================
    
    public void addListener(SerialListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(SerialListener listener) {
        listeners.remove(listener);
    }
    
    public void clearListeners() {
        listeners.clear();
    }
    
    private void notifyDataReceived(SerialResponse response) {
        for (SerialListener listener : listeners) {
            try {
                listener.onDataReceived(response);
            } catch (Exception e) {
                log.error("Error in listener callback", e);
            }
        }
    }
    
    private void notifyError(String error) {
        for (SerialListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                log.error("Error in listener callback", e);
            }
        }
    }
    
    private void notifyConnected(String portName) {
        for (SerialListener listener : listeners) {
            try {
                listener.onConnected(portName);
            } catch (Exception e) {
                log.error("Error in listener callback", e);
            }
        }
    }
    
    private void notifyDisconnected() {
        for (SerialListener listener : listeners) {
            try {
                listener.onDisconnected();
            } catch (Exception e) {
                log.error("Error in listener callback", e);
            }
        }
    }
}


