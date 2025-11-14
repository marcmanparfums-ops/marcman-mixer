package ro.marcman.mixer.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.serial.model.ArduinoCommand;
import ro.marcman.mixer.serial.model.SerialResponse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    // NOTE: Do NOT use SerialPort constants as static fields - they cause SerialPort class to load
    // before architecture is fixed. Use them directly in methods instead.
    // SerialPort.ONE_STOP_BIT = 1
    // SerialPort.NO_PARITY = 0
    
    private SerialPort serialPort;
    private final List<SerialListener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder buffer = new StringBuilder();
    private boolean connected = false;
    
    // Static initializer to ALWAYS clean jSerialComm DLLs on Windows to force correct extraction
    // This is necessary because Java 25 sometimes reports wrong architecture after Windows updates
    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "");
        
        // Fix architecture detection for Windows on AMD64/x64 systems
        // Java 25 sometimes reports wrong architecture (aarch64 instead of amd64)
        if (osName.contains("windows")) {
            // Check if we're on a 64-bit Windows system but Java reports wrong architecture
            String archEnv = System.getenv("PROCESSOR_ARCHITECTURE");
            String archEnv64 = System.getenv("PROCESSOR_ARCHITEW6432");
            boolean isAmd64System = (archEnv != null && archEnv.equals("AMD64")) || 
                                   (archEnv64 != null && archEnv64.equals("AMD64"));
            
            if (isAmd64System && (osArch.equals("aarch64") || osArch.contains("arm"))) {
                log.warn("Architecture mismatch detected: os.arch={}, but system is AMD64", osArch);
                log.warn("This will cause jSerialComm to extract wrong DLL. Attempting to fix...");
                
                // Try to override os.arch property (may not work but worth trying)
                try {
                    System.setProperty("os.arch", "amd64");
                    log.info("Set os.arch to amd64");
                } catch (Exception e) {
                    log.warn("Could not override os.arch: {}", e.getMessage());
                }
            }
            
            // Quick check: only clean if DLL doesn't exist or is wrong size
            // DLL copying is done in App.main() before Application.launch()
            cleanWrongArchitectureDlls();
        }
    }
    
    /**
     * Quick check: only clean if DLL doesn't exist or is wrong size.
     * DLL copying is done in App.main() before Application.launch(), so this should rarely be needed.
     */
    private static void cleanWrongArchitectureDlls() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String userHome = System.getProperty("user.home");
            
            // Quick check: if correct DLL exists, do nothing
            Path tempDll = Paths.get(tempDir, "jSerialComm", "2.10.4", "jSerialComm.dll");
            Path homeDll = Paths.get(userHome, ".jSerialComm", "2.10.4", "jSerialComm.dll");
            
            // Check if correct DLL exists (x86_64 is ~208KB)
            boolean correctDllExists = (Files.exists(tempDll) && Files.size(tempDll) > 200000) ||
                                      (Files.exists(homeDll) && Files.size(homeDll) > 200000);
            
            if (correctDllExists) {
                // DLL is correct, no cleanup needed
                return;
            }
            
            // Only clean if DLL is wrong or missing - delete wrong size DLLs
            if (Files.exists(tempDll) && Files.size(tempDll) < 200000) {
                try {
                    Files.delete(tempDll);
                    log.debug("Deleted wrong architecture DLL from temp");
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            if (Files.exists(homeDll) && Files.size(homeDll) < 200000) {
                try {
                    Files.delete(homeDll);
                    log.debug("Deleted wrong architecture DLL from home");
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore - not critical
        }
    }
    
    /**
     * Quick check: only clean if DLL doesn't exist or is wrong size.
     * DLL copying is done in App.main() before Application.launch(), so this should rarely be needed.
     */
    private static void cleanJSerialCommCacheImmediately() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String userHome = System.getProperty("user.home");
            
            // Quick check: if correct DLL exists, do nothing
            Path tempDll = Paths.get(tempDir, "jSerialComm", "2.10.4", "jSerialComm.dll");
            Path homeDll = Paths.get(userHome, ".jSerialComm", "2.10.4", "jSerialComm.dll");
            
            // Check if correct DLL exists (x86_64 is ~208KB)
            boolean correctDllExists = (Files.exists(tempDll) && Files.size(tempDll) > 200000) ||
                                      (Files.exists(homeDll) && Files.size(homeDll) > 200000);
            
            if (correctDllExists) {
                // DLL is correct, no cleanup needed
                return;
            }
            
            // Only clean if DLL is wrong or missing - delete wrong size DLLs
            if (Files.exists(tempDll) && Files.size(tempDll) < 200000) {
                try {
                    Files.delete(tempDll);
                    log.debug("Deleted wrong architecture DLL from temp");
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            if (Files.exists(homeDll) && Files.size(homeDll) < 200000) {
                try {
                    Files.delete(homeDll);
                    log.debug("Deleted wrong architecture DLL from home");
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore - not critical
        }
    }
    
    /**
     * Recursively delete directory immediately with aggressive retries
     */
    private static void deleteDirectoryRecursiveImmediate(File directory) {
        if (!directory.exists()) {
            return;
        }
        
        // Try multiple times with different strategies
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteDirectoryRecursiveImmediate(file);
                        } else {
                            // Try to delete file with retries
                            for (int i = 0; i < 3; i++) {
                                if (file.delete()) {
                                    break;
                                }
                                try {
                                    Thread.sleep(20);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                }
                
                // Try to delete directory
                if (directory.delete()) {
                    return; // Success
                }
                
                // Brief wait before retry
                if (attempt < 2) {
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                if (attempt < 2) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
    
    /**
     * Recursively delete a directory
     */
    private static void deleteDirectory(File directory) {
        if (!directory.exists()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    try {
                        file.delete();
                    } catch (Exception e) {
                        // Ignore - file might be locked
                    }
                }
            }
        }
        
        try {
            directory.delete();
        } catch (Exception e) {
            // Ignore - directory might be locked
        }
    }
    
    /**
     * 1. AUTO-DETECT Arduino Mega COM port
     * 
     * Searches for Arduino Mega by checking common port descriptors.
     * Returns the first matching port found.
     */
    public SerialPort detectArduinoPort() {
        log.info("Scanning for Arduino Mega...");
        
        SerialPort[] ports;
        try {
            ports = SerialPort.getCommPorts();
        } catch (UnsatisfiedLinkError e) {
            String osArch = System.getProperty("os.arch");
            log.error("Failed to load jSerialComm native library: " + e.getMessage());
            log.error("System architecture: " + osArch);
            log.error("Cannot detect Arduino port - jSerialComm not initialized");
            log.error("Run .\\fix_jserialcomm.ps1 or .\\fix_jserialcomm.bat to fix this issue");
            return null;
        } catch (Exception e) {
            log.error("Error detecting Arduino port: " + e.getMessage(), e);
            return null;
        }
        
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
     * Uses reflection to ensure SerialPort class is only loaded after architecture is fixed
     */
    public List<String> getAvailablePorts() {
        // Verify DLL exists before attempting to use jSerialComm
        String tempDir = System.getProperty("java.io.tmpdir");
        String userHome = System.getProperty("user.home");
        Path tempDll = Paths.get(tempDir, "jSerialComm", "2.10.4", "jSerialComm.dll");
        Path homeDll = Paths.get(userHome, ".jSerialComm", "2.10.4", "jSerialComm.dll");
        
        boolean dllExists = Files.exists(tempDll) || Files.exists(homeDll);
        if (dllExists) {
            try {
                if (Files.exists(tempDll)) {
                    long size = Files.size(tempDll);
                    log.info("DLL found in temp: {} ({} bytes)", tempDll, size);
                }
                if (Files.exists(homeDll)) {
                    long size = Files.size(homeDll);
                    log.info("DLL found in home: {} ({} bytes)", homeDll, size);
                }
            } catch (Exception e) {
                log.warn("Could not check DLL size: {}", e.getMessage());
            }
        } else {
            log.warn("DLL not found in expected locations. jSerialComm will try to extract it.");
        }
        
        // Quick check: only clean if DLL doesn't exist or is wrong size
        // DLL copying is done in App.main() before Application.launch()
        cleanJSerialCommCacheImmediately();
        
        // Brief wait only if cleanup was needed
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            log.info("Attempting to get serial ports...");
            log.info("System architecture: {}", System.getProperty("os.arch"));
            
            // Use reflection to call SerialPort.getCommPorts() to ensure class is loaded only now
            Class<?> serialPortClass = Class.forName("com.fazecast.jSerialComm.SerialPort");
            java.lang.reflect.Method getCommPortsMethod = serialPortClass.getMethod("getCommPorts");
            SerialPort[] ports = (SerialPort[]) getCommPortsMethod.invoke(null);
            
            log.info("Successfully got {} ports", ports.length);
            
            List<String> portNames = new ArrayList<>();
            
            for (SerialPort port : ports) {
                portNames.add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
            }
            
            return portNames;
        } catch (UnsatisfiedLinkError e) {
            String osArch = System.getProperty("os.arch");
            String userName = System.getProperty("user.name");
            log.error("Failed to load jSerialComm native library: " + e.getMessage());
            log.error("System architecture: " + osArch);
            log.error("This may be due to Windows update changing file permissions or architecture mismatch.");
            log.error("");
            log.error("SOLUTION - Run one of these scripts to fix:");
            log.error("  1) PowerShell (Recommended): .\\fix_jserialcomm.ps1");
            log.error("     Right-click -> Run as Administrator if folders are locked");
            log.error("  2) Batch script: .\\fix_jserialcomm.bat");
            log.error("");
            log.error("Or manually delete these folders:");
            log.error("  - C:\\Users\\" + userName + "\\AppData\\Local\\Temp\\jSerialComm");
            log.error("  - C:\\Users\\" + userName + "\\.jSerialComm");
            log.error("");
            log.error("Then restart the application. jSerialComm will re-extract the correct DLL.");
            return new ArrayList<>(); // Return empty list instead of crashing
        } catch (ClassNotFoundException e) {
            log.error("SerialPort class not found: " + e.getMessage());
            // Fallback to direct call
            return getAvailablePortsDirect();
        } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException | IllegalAccessException e) {
            log.error("Error using reflection to get ports: " + e.getMessage());
            // Fallback to direct call
            return getAvailablePortsDirect();
        } catch (Exception e) {
            log.error("Error getting available ports: " + e.getMessage(), e);
            // Fallback to direct call
            return getAvailablePortsDirect();
        }
    }
    
    /**
     * Fallback method to get ports using direct SerialPort call
     */
    private List<String> getAvailablePortsDirect() {
        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            List<String> portNames = new ArrayList<>();
            for (SerialPort port : ports) {
                portNames.add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
            }
            return portNames;
        } catch (UnsatisfiedLinkError e) {
            String osArch = System.getProperty("os.arch");
            String userName = System.getProperty("user.name");
            log.error("Failed to load jSerialComm native library (direct call): " + e.getMessage());
            log.error("System architecture: " + osArch);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error getting available ports (direct call): " + e.getMessage(), e);
            return new ArrayList<>();
        }
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
        
        SerialPort port;
        try {
            port = SerialPort.getCommPort(portName);
        } catch (UnsatisfiedLinkError e) {
            String osArch = System.getProperty("os.arch");
            log.error("Failed to load jSerialComm native library: " + e.getMessage());
            log.error("System architecture: " + osArch);
            log.error("Cannot connect - jSerialComm not initialized");
            log.error("Run .\\fix_jserialcomm.ps1 or .\\fix_jserialcomm.bat to fix this issue");
            notifyError("jSerialComm not initialized. Check console for details. Run fix_jserialcomm.ps1 to fix.");
            return false;
        } catch (Exception e) {
            log.error("Error getting port: " + e.getMessage(), e);
            notifyError("Error getting port: " + e.getMessage());
            return false;
        }
        
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
                 port.getSystemPortName(), BAUD_RATE, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortParameters(BAUD_RATE, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
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


