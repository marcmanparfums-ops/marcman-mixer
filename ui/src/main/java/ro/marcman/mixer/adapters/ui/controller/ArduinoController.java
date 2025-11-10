package ro.marcman.mixer.adapters.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;
import ro.marcman.mixer.serial.SerialListener;
import ro.marcman.mixer.serial.SerialManager;
import ro.marcman.mixer.serial.model.ArduinoCommand;
import ro.marcman.mixer.serial.model.SerialResponse;
import ro.marcman.mixer.adapters.ui.util.IconSupport;

/**
 * Controller for Arduino serial communication interface.
 * 
 * Features:
 * - Auto-detect and connect to Arduino Mega
 * - Send commands to MASTER
 * - Display real-time responses
 * - Manual port selection
 */
@Slf4j
public class ArduinoController {
    
    @FXML private ComboBox<String> portComboBox;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Button refreshButton;
    @FXML private Button discoverButton;
    @FXML private Button scanButton;
    @FXML private TextField commandField;
    @FXML private Button sendButton;
    @FXML private TextArea responseArea;
    @FXML private Label statusLabel;
    
    private final SerialManager serialManager = new SerialManager();
    
    @FXML
    public void initialize() {
        log.info("Initializing Arduino Controller");
        
        // Setup UI state
        disconnectButton.setDisable(true);
        sendButton.setDisable(true);
        
        // Load available ports
        refreshPorts();
        
        // Add serial listener
        serialManager.addListener(new SerialListener() {
            @Override
            public void onDataReceived(SerialResponse response) {
                Platform.runLater(() -> {
                    responseArea.appendText(response.getRawResponse() + "\n");
                    
                    // Auto-scroll to bottom
                    responseArea.setScrollTop(Double.MAX_VALUE);
                    
                    // Highlight errors
                    if (response.isError()) {
                        updateStatus("Error: " + response.getRawResponse(), "error");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    updateStatus("Error: " + error, "error");
                    showAlert(Alert.AlertType.ERROR, "Serial Error", error);
                });
            }
            
            @Override
            public void onConnected(String portName) {
                Platform.runLater(() -> {
                    updateStatus("Connected to " + portName, "success");
                    connectButton.setDisable(true);
                    disconnectButton.setDisable(false);
                    sendButton.setDisable(false);
                    portComboBox.setDisable(true);
                    
                    // Send initial commands
                    serialManager.sendCommand(ArduinoCommand.help());
                });
            }
            
            @Override
            public void onDisconnected() {
                Platform.runLater(() -> {
                    updateStatus("Disconnected", "warning");
                    connectButton.setDisable(false);
                    disconnectButton.setDisable(true);
                    sendButton.setDisable(true);
                    portComboBox.setDisable(false);
                });
            }
        });
    }
    
    @FXML
    private void handleConnect() {
        String selectedPort = portComboBox.getValue();
        
        if (selectedPort == null || selectedPort.isEmpty()) {
            // Try auto-connect
            updateStatus("Auto-detecting Arduino...", "info");
            if (!serialManager.connectAuto()) {
                updateStatus("Failed to auto-detect Arduino", "error");
                showAlert(Alert.AlertType.ERROR, "Connection Failed", 
                         "Could not auto-detect Arduino Mega. Please select a port manually.");
            }
        } else {
            // Extract port name (before the " - " separator)
            String portName = selectedPort.split(" - ")[0];
            updateStatus("Connecting to " + portName + "...", "info");
            
            if (!serialManager.connect(portName)) {
                updateStatus("Failed to connect", "error");
                showAlert(Alert.AlertType.ERROR, "Connection Failed", 
                         "Could not connect to " + portName);
            }
        }
    }
    
    @FXML
    private void handleDisconnect() {
        serialManager.disconnect();
    }
    
    @FXML
    private void handleRefresh() {
        refreshPorts();
    }
    
    @FXML
    private void handleDiscover() {
        if (serialManager.isConnected()) {
            serialManager.sendCommand(ArduinoCommand.discover());
        } else {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
        }
    }
    
    @FXML
    private void handleScan() {
        if (serialManager.isConnected()) {
            serialManager.sendCommand(ArduinoCommand.scan());
        } else {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
        }
    }
    
    @FXML
    private void handleSend() {
        String command = commandField.getText().trim();
        
        if (command.isEmpty()) {
            return;
        }
        
        if (!serialManager.isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
            return;
        }
        
        responseArea.appendText("> " + command + "\n");
        serialManager.sendRawCommand(command);
        commandField.clear();
    }
    
    @FXML
    private void handleCommand(javafx.event.ActionEvent event) {
        Button button = (Button) event.getSource();
        String command = button.getText();
        
        if (!serialManager.isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
            return;
        }
        
        responseArea.appendText("> " + command + "\n");
        serialManager.sendRawCommand(command);
    }
    
    private void refreshPorts() {
        portComboBox.getItems().clear();
        portComboBox.getItems().addAll(serialManager.getAvailablePorts());
        
        if (!portComboBox.getItems().isEmpty()) {
            portComboBox.setValue(portComboBox.getItems().get(0));
        }
        
        updateStatus(portComboBox.getItems().size() + " ports found", "info");
    }
    
    private void updateStatus(String message, String type) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-success", "status-error", 
                                             "status-warning", "status-info");
        statusLabel.getStyleClass().add("status-" + type);
        log.info("Status: {} ({})", message, type);
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        IconSupport.applyTo(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Public API for sending commands from other controllers
     */
    public boolean sendCommand(ArduinoCommand command) {
        if (!serialManager.isConnected()) {
            log.warn("Cannot send command: not connected");
            return false;
        }
        return serialManager.sendCommand(command);
    }
    
    public boolean isConnected() {
        return serialManager.isConnected();
    }
    
    public SerialManager getSerialManager() {
        return serialManager;
    }
}

