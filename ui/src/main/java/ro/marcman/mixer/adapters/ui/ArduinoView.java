package ro.marcman.mixer.adapters.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ro.marcman.mixer.serial.SerialListener;
import ro.marcman.mixer.serial.SerialManager;
import ro.marcman.mixer.serial.model.ArduinoCommand;
import ro.marcman.mixer.serial.model.SerialResponse;
import ro.marcman.mixer.adapters.ui.util.IconSupport;

import java.util.List;

/**
 * Procesor communication UI - Built programmatically without FXML
 */
public class ArduinoView extends VBox {
    
    // Lazy initialization to prevent jSerialComm from loading before architecture is fixed
    private SerialManager serialManager;
    
    // UI Components
    private ComboBox<String> portComboBox;
    private Button connectButton;
    private Button disconnectButton;
    private Button refreshButton;
    private Button discoverButton;
    private Button scanButton;
    private TextField commandField;
    private Button sendButton;
    private TextArea responseArea;
    private Label statusLabel;
    private Button heartbeatButton;
    private boolean heartbeatActive = false;
    private Thread heartbeatThread;
    
    public ArduinoView() {
        super(10);
        setPadding(new Insets(15));
        
        buildUI();
        setupSerialListener();
    }
    
    /**
     * Get SerialManager instance with lazy initialization.
     * This ensures jSerialComm classes are only loaded after architecture is fixed.
     */
    public SerialManager getSerialManager() {
        if (serialManager == null) {
            serialManager = new SerialManager();
        }
        return serialManager;
    }
    
    private void buildUI() {
        // Title
        Label titleLabel = new Label("Procesor MASTER Communication");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        // Connection Panel
        TitledPane connectionPane = new TitledPane();
        connectionPane.setText("Connection");
        connectionPane.setCollapsible(false);
        
        VBox connectionBox = new VBox(10);
        connectionBox.setPadding(new Insets(10));
        
        // Port selection row
        HBox portRow = new HBox(10);
        portRow.setAlignment(Pos.CENTER_LEFT);
        Label portLabel = new Label("COM Port:");
        portLabel.setMinWidth(80);
        portComboBox = new ComboBox<>();
        portComboBox.setPromptText("Select port or auto-detect");
        portComboBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(portComboBox, Priority.ALWAYS);
        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> handleRefresh());
        portRow.getChildren().addAll(portLabel, portComboBox, refreshButton);
        
        // Connection buttons row
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> handleConnect());
        
        disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        disconnectButton.setDisable(true);
        disconnectButton.setOnAction(e -> handleDisconnect());
        
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        
        discoverButton = new Button("Discover Slaves");
        discoverButton.setOnAction(e -> handleDiscover());
        
        scanButton = new Button("Scan Network");
        scanButton.setOnAction(e -> handleScan());
        
        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);
        
        heartbeatButton = new Button("LIVE OFF");
        heartbeatButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-weight: bold;");
        heartbeatButton.setOnAction(e -> toggleHeartbeat());
        heartbeatButton.setDisable(true);
        
        buttonRow.getChildren().addAll(connectButton, disconnectButton, separator, 
                                       discoverButton, scanButton, separator2, heartbeatButton);
        
        // Status label
        statusLabel = new Label("Not connected");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5; -fx-background-color: #FFF3E0; " +
                            "-fx-text-fill: #FF9800; -fx-background-radius: 3;");
        
        connectionBox.getChildren().addAll(portRow, buttonRow, statusLabel);
        connectionPane.setContent(connectionBox);
        
        // Command Panel
        TitledPane commandPane = new TitledPane();
        commandPane.setText("Command Console");
        commandPane.setCollapsible(false);
        VBox.setVgrow(commandPane, Priority.ALWAYS);
        
        VBox commandBox = new VBox(10);
        commandBox.setPadding(new Insets(10));
        VBox.setVgrow(commandBox, Priority.ALWAYS);
        
        // Response area
        responseArea = new TextArea();
        responseArea.setEditable(false);
        responseArea.setWrapText(true);
        responseArea.setPrefHeight(500);
        responseArea.setMinHeight(400);
        responseArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; " +
                             "-fx-control-inner-background: #263238; -fx-text-fill: #00FF00;");
        VBox.setVgrow(responseArea, Priority.ALWAYS);
        
        // Command input row
        HBox commandRow = new HBox(10);
        commandRow.setAlignment(Pos.CENTER_LEFT);
        commandField = new TextField();
        commandField.setPromptText("Enter command (e.g., help, scan, ping_uid 0x12345678)");
        HBox.setHgrow(commandField, Priority.ALWAYS);
        commandField.setOnAction(e -> handleSend());
        
        sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        sendButton.setDisable(true);
        sendButton.setOnAction(e -> handleSend());
        commandRow.getChildren().addAll(commandField, sendButton);
        
        // Quick commands
        Label quickLabel = new Label("Quick Commands:");
        quickLabel.setStyle("-fx-font-weight: bold;");
        
        TilePane quickButtons = new TilePane(5, 5);
        String[] quickCmds = {"help", "ver", "discover", "scan", "table", "summary", "eemap"};
        for (String cmd : quickCmds) {
            Button btn = new Button(cmd);
            btn.setStyle("-fx-font-size: 10px;");
            btn.setOnAction(e -> sendQuickCommand(cmd));
            quickButtons.getChildren().add(btn);
        }
        
        commandBox.getChildren().addAll(responseArea, commandRow, quickLabel, quickButtons);
        commandPane.setContent(commandBox);
        
        // Add all to main container
        getChildren().addAll(titleLabel, connectionPane, commandPane);
        
        // Initial port refresh - delay to allow jSerialComm cache cleanup to complete
        // Use Platform.runLater to ensure it runs after App.start() cleanup finishes
        Platform.runLater(() -> {
            try {
                // Additional small delay to ensure cleanup is complete
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handleRefresh();
        });
    }
    
    private void setupSerialListener() {
        getSerialManager().addListener(new SerialListener() {
            @Override
            public void onDataReceived(SerialResponse response) {
                Platform.runLater(() -> {
                    String rawResponse = response.getRawResponse();
                    
                    // Filter out normal CAN recovery messages
                    if (isNormalCanRecovery(rawResponse)) {
                        // Display as INFO, not error - these are NORMAL
                        responseArea.appendText("[CAN INFO] " + rawResponse + "\n");
                    } else {
                        responseArea.appendText(rawResponse + "\n");
                        
                        if (response.isError()) {
                            updateStatus("Error: " + rawResponse, "error");
                        }
                    }
                    
                    responseArea.setScrollTop(Double.MAX_VALUE);
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
                    heartbeatButton.setDisable(false);
                    portComboBox.setDisable(true);
                    
                    responseArea.appendText("=== Connected to " + portName + " at 115200 baud ===\n");
                    getSerialManager().sendCommand(ArduinoCommand.help());
                });
            }
            
            @Override
            public void onDisconnected() {
                Platform.runLater(() -> {
                    // Stop heartbeat if active
                    if (heartbeatActive) {
                        stopHeartbeat();
                    }
                    
                    updateStatus("Disconnected", "warning");
                    connectButton.setDisable(false);
                    disconnectButton.setDisable(true);
                    sendButton.setDisable(true);
                    heartbeatButton.setDisable(true);
                    portComboBox.setDisable(false);
                });
            }
        });
    }
    
    private void handleConnect() {
        String selectedPort = portComboBox.getValue();
        
        if (selectedPort == null || selectedPort.isEmpty()) {
            updateStatus("Auto-detecting Procesor...", "info");
            if (!getSerialManager().connectAuto()) {
                updateStatus("Failed to auto-detect Procesor", "error");
                showAlert(Alert.AlertType.ERROR, "Connection Failed", 
                         "Could not auto-detect Procesor Mega. Please select a port manually.");
            }
        } else {
            String portName = selectedPort.split(" - ")[0];
            updateStatus("Connecting to " + portName + "...", "info");
            
            if (!getSerialManager().connect(portName)) {
                updateStatus("Failed to connect", "error");
                showAlert(Alert.AlertType.ERROR, "Connection Failed", 
                         "Could not connect to " + portName);
            }
        }
    }
    
    private void handleDisconnect() {
        getSerialManager().disconnect();
    }
    
    /**
     * Cleanup method - called when application closes
     * Ensures serial port is properly released
     */
    public void cleanup() {
        System.out.println("ArduinoView cleanup - disconnecting serial port...");
        
        // Stop heartbeat if active
        if (heartbeatActive) {
            stopHeartbeat();
        }
        
        if (serialManager.isConnected()) {
            getSerialManager().disconnect();
            try {
                // Wait a bit to ensure port is released
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        System.out.println("ArduinoView cleanup complete");
    }
    
    private void handleRefresh() {
        try {
            portComboBox.getItems().clear();
            List<String> ports = getSerialManager().getAvailablePorts();
            portComboBox.getItems().addAll(ports);
            
            if (!portComboBox.getItems().isEmpty()) {
                portComboBox.setValue(portComboBox.getItems().get(0));
                updateStatus(portComboBox.getItems().size() + " ports found", "info");
            } else {
                updateStatus("No ports available - jSerialComm may not be initialized. Check console for details.", "warning");
            }
        } catch (Exception e) {
            updateStatus("Error refreshing ports: " + e.getMessage(), "error");
            System.err.println("Error in handleRefresh: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleDiscover() {
        if (getSerialManager().isConnected()) {
            getSerialManager().sendCommand(ArduinoCommand.discover());
        } else {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
        }
    }
    
    private void handleScan() {
        if (getSerialManager().isConnected()) {
            getSerialManager().sendCommand(ArduinoCommand.scan());
        } else {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
        }
    }
    
    private void handleSend() {
        String command = commandField.getText().trim();
        
        if (command.isEmpty()) {
            return;
        }
        
        if (!getSerialManager().isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
            return;
        }
        
        responseArea.appendText("> " + command + "\n");
        getSerialManager().sendRawCommand(command);
        commandField.clear();
    }
    
    private void sendQuickCommand(String command) {
        if (!getSerialManager().isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
            return;
        }
        
        responseArea.appendText("> " + command + "\n");
        getSerialManager().sendRawCommand(command);
    }
    
    private void updateStatus(String message, String type) {
        statusLabel.setText(message);
        
        switch (type) {
            case "success":
                statusLabel.setStyle("-fx-background-color: #E8F5E9; -fx-text-fill: #4CAF50; " +
                                   "-fx-font-weight: bold; -fx-padding: 5; -fx-background-radius: 3;");
                break;
            case "error":
                statusLabel.setStyle("-fx-background-color: #FFEBEE; -fx-text-fill: #f44336; " +
                                   "-fx-font-weight: bold; -fx-padding: 5; -fx-background-radius: 3;");
                break;
            case "warning":
                statusLabel.setStyle("-fx-background-color: #FFF3E0; -fx-text-fill: #FF9800; " +
                                   "-fx-font-weight: bold; -fx-padding: 5; -fx-background-radius: 3;");
                break;
            case "info":
                statusLabel.setStyle("-fx-background-color: #E3F2FD; -fx-text-fill: #2196F3; " +
                                   "-fx-font-weight: bold; -fx-padding: 5; -fx-background-radius: 3;");
                break;
        }
    }
    
    /**
     * Check if a message is a normal CAN recovery message (not an actual error)
     */
    private boolean isNormalCanRecovery(String message) {
        if (message == null) return false;
        
        String msg = message.toLowerCase();
        
        // These are NORMAL messages during CAN auto-recovery
        if (msg.contains("can recover") ||
            msg.contains("eflg=0x40") ||
            msg.contains("eflg before") ||
            msg.contains("eflg after=0x0") ||
            (msg.contains("tec=0") && msg.contains("rec=")) ||
            (msg.contains("rec=") && !msg.contains("rec=127"))) {  // REC < 127 is normal
            return true;
        }
        
        return false;
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
     * Toggle heartbeat (LIVE) on pin 47
     */
    private void toggleHeartbeat() {
        if (heartbeatActive) {
            stopHeartbeat();
        } else {
            startHeartbeat();
        }
    }
    
    /**
     * Start heartbeat thread - sends periodic pulse on pin 47
     */
    private void startHeartbeat() {
        if (!getSerialManager().isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino first.");
            return;
        }
        
        heartbeatActive = true;
        Platform.runLater(() -> {
            heartbeatButton.setText("LIVE ON");
            heartbeatButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        });
        
        responseArea.appendText("> LIVE heartbeat started on pin 47\n");
        
        // Start heartbeat thread
        heartbeatThread = new Thread(() -> {
            try {
                while (heartbeatActive && getSerialManager().isConnected()) {
                    // Send pulse: set pin 47 HIGH, wait, then LOW
                    getSerialManager().sendRawCommand("set 47 1");
                    Thread.sleep(100); // Keep HIGH for 100ms
                    getSerialManager().sendRawCommand("set 47 0");
                    
                    // Wait 1 second before next heartbeat
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                // Thread interrupted, stop heartbeat
                heartbeatActive = false;
            } catch (Exception e) {
                Platform.runLater(() -> {
                    responseArea.appendText("ERROR: Heartbeat failed: " + e.getMessage() + "\n");
                });
                heartbeatActive = false;
            }
            
            // Update button when thread stops
            Platform.runLater(() -> {
                heartbeatButton.setText("LIVE OFF");
                heartbeatButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-weight: bold;");
            });
        });
        
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    /**
     * Stop heartbeat thread
     */
    private void stopHeartbeat() {
        heartbeatActive = false;
        
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
        }
        
        // Turn off pin 47
        if (getSerialManager().isConnected()) {
            getSerialManager().sendRawCommand("set 47 0");
        }
        
        Platform.runLater(() -> {
            heartbeatButton.setText("LIVE OFF");
            heartbeatButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-weight: bold;");
        });
        
        responseArea.appendText("> LIVE heartbeat stopped\n");
    }
    
}

