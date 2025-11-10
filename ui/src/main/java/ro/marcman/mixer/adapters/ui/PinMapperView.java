package ro.marcman.mixer.adapters.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.ports.repository.IngredientRepository;
import ro.marcman.mixer.serial.SerialListener;
import ro.marcman.mixer.serial.SerialManager;
import ro.marcman.mixer.serial.model.ArduinoCommand;
import ro.marcman.mixer.serial.model.SerialResponse;
import ro.marcman.mixer.sqlite.DatabaseManager;
import ro.marcman.mixer.sqlite.IngredientRepositoryImpl;
import ro.marcman.mixer.adapters.ui.util.IconSupport;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pin Mapper View - Displays UID pin allocation status
 * Shows which pins are allocated (red) and free (green) for each processor
 */
public class PinMapperView extends VBox {
    
    private final DatabaseManager dbManager;
    private final IngredientRepository repository;
    private SerialManager serialManager;
    
    private TableView<NodeInfo> nodeTable;
    private Label statusLabel;
    private Button refreshButton;
    private VBox pinGridContainer;
    private Map<String, List<PinInfo>> pinAllocations = new HashMap<>();
    
    // Data structures (using simple property classes for JavaFX compatibility)
    private static class NodeInfo {
        private final int id;
        private final String uid;
        private final String firmware;
        
        public NodeInfo(int id, String uid, String firmware) {
            this.id = id;
            this.uid = uid;
            this.firmware = firmware;
        }
        
        public int getId() { return id; }
        public String getUid() { return uid; }
        public String getFirmware() { return firmware; }
    }
    
    private static class PinInfo {
        private final int pin;
        private final boolean allocated;
        private final String ingredientName;
        
        public PinInfo(int pin, boolean allocated, String ingredientName) {
            this.pin = pin;
            this.allocated = allocated;
            this.ingredientName = ingredientName;
        }
        
        public int getPin() { return pin; }
        public boolean isAllocated() { return allocated; }
        public String getIngredientName() { return ingredientName != null ? ingredientName : ""; }
    }
    
    public PinMapperView() {
        super(10);
        setPadding(new Insets(15));
        
        // Initialize database
        this.dbManager = DatabaseManager.getInstance();
        this.repository = new IngredientRepositoryImpl(dbManager);
        
        buildUI();
    }
    
    public void setSerialManager(SerialManager serialManager) {
        this.serialManager = serialManager;
        setupSerialListener();
    }
    
    private void buildUI() {
        // Title
        Label title = new Label("🗺️ Pin Mapper - Processor Status");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        
        // Status bar
        HBox statusBox = new HBox(20);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(10));
        statusBox.setStyle("-fx-background-color: #E3F2FD; -fx-background-radius: 5;");
        
        statusLabel = new Label("Click Refresh to scan network");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle("-fx-font-weight: bold; -fx-background-color: #2196F3; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> refreshData());
        
        statusBox.getChildren().addAll(statusLabel, refreshButton);
        
        // Node table
        nodeTable = new TableView<>();
        nodeTable.setPrefWidth(300);
        
        // ID column
        TableColumn<NodeInfo, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getId())));
        idCol.setPrefWidth(60);
        
        // UID column
        TableColumn<NodeInfo, String> uidCol = new TableColumn<>("UID");
        uidCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUid()));
        uidCol.setPrefWidth(120);
        
        // Firmware column
        TableColumn<NodeInfo, String> fwCol = new TableColumn<>("Firmware");
        fwCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getFirmware()));
        fwCol.setPrefWidth(100);
        
        nodeTable.getColumns().addAll(idCol, uidCol, fwCol);
        VBox.setVgrow(nodeTable, Priority.ALWAYS);
        
        // Pin grid container
        pinGridContainer = new VBox(10);
        pinGridContainer.setPadding(new Insets(10));
        
        ScrollPane scrollPane = new ScrollPane(pinGridContainer);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Use HBox for table + pin grid
        HBox contentBox = new HBox(10);
        nodeTable.setPrefWidth(280);
        contentBox.getChildren().addAll(nodeTable, scrollPane);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        
        // Layout
        getChildren().addAll(title, statusBox, contentBox);
    }
    
    private StringBuilder scanResponseBuffer = new StringBuilder();
    private boolean isWaitingForScanResponse = false;
    
    private void setupSerialListener() {
        if (serialManager == null) return;
        
        serialManager.addListener(new SerialListener() {
            @Override
            public void onDataReceived(SerialResponse response) {
                String rawResponse = response.getRawResponse();
                if (rawResponse == null) return;
                
                // Only accumulate if we're waiting for a discover response
                if (isWaitingForScanResponse) {
                    scanResponseBuffer.append(rawResponse).append("\n");
                    
                    // Check if this is the last line of the discover response
                    if (rawResponse.contains("Online count:")) {
                        Platform.runLater(() -> {
                            processScanResponse(response, scanResponseBuffer.toString());
                            scanResponseBuffer.setLength(0); // Clear buffer
                            isWaitingForScanResponse = false; // Reset flag
                        });
                    }
                }
            }
            
            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + error);
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #F44336;");
                });
            }
            
            @Override
            public void onConnected(String portName) {
                // Connection handled elsewhere
            }
            
            @Override
            public void onDisconnected() {
                // Disconnection handled elsewhere
            }
        });
    }
    
    private void processScanResponse(SerialResponse response, String fullResponse) {
        if (fullResponse == null || fullResponse.trim().isEmpty()) {
            return;
        }
        
        // Parse scan response to extract node information
        // Expected format: "ID X: UID=0xY FW=Z.Z"
        Pattern nodePattern = Pattern.compile("ID\\s+(\\d+):\\s+UID=(\\S+)\\s+FW=(\\S+)");
        Matcher matcher = nodePattern.matcher(fullResponse);
        
        List<NodeInfo> nodes = new ArrayList<>();
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            String uid = matcher.group(2);
            String firmware = matcher.group(3);
            nodes.add(new NodeInfo(id, uid, firmware));
        }
        
        // Check for "Online count"
        Pattern countPattern = Pattern.compile("Online count:\\s*(\\d+)");
        Matcher countMatcher = countPattern.matcher(fullResponse);
        if (countMatcher.find()) {
            int onlineCount = Integer.parseInt(countMatcher.group(1));
            statusLabel.setText("Online nodes: " + onlineCount);
            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        }
        
        // Update table and pin grids
        if (!nodes.isEmpty()) {
            ObservableList<NodeInfo> nodeList = FXCollections.observableArrayList(nodes);
            nodeTable.setItems(nodeList);
            
            // Load pin allocations from database and build grids
            loadPinAllocations(nodes);
        }
    }
    
    private void loadPinAllocations(List<NodeInfo> nodes) {
        pinGridContainer.getChildren().clear();
        pinAllocations.clear();
        
        try {
            // Load all ingredients from database WITHOUT master config to avoid double-counting
            List<Ingredient> allIngredients = repository.findAllWithoutMasterApply();
            
            // Build allocation map for each UID
            for (NodeInfo node : nodes) {
                List<PinInfo> pins = new ArrayList<>();
                
                // Create pin info for each possible pin (0-69)
                for (int pin = 0; pin <= 69; pin++) {
                    PinInfo pinInfo = checkPinAllocation(pin, node.getUid(), allIngredients);
                    pins.add(pinInfo);
                }
                
                pinAllocations.put(node.getUid(), pins);
                
                // Create grid for this UID
                createPinGrid(node, pins);
            }
            
        } catch (Exception e) {
            System.err.println("Error loading pin allocations: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private PinInfo checkPinAllocation(int pin, String uid, List<Ingredient> ingredients) {
        for (Ingredient ing : ingredients) {
            // Check Large pump
            if (uid != null && uid.equals(ing.getArduinoUid()) && pin == ing.getArduinoPin()) {
                return new PinInfo(pin, true, ing.getName());
            }
            // Check Small pump
            if (uid != null && uid.equals(ing.getArduinoUidSmall()) && pin == ing.getArduinoPinSmall()) {
                return new PinInfo(pin, true, ing.getName());
            }
        }
        return new PinInfo(pin, false, null);
    }
    
    private void createPinGrid(NodeInfo node, List<PinInfo> pins) {
        // Title
        Label title = new Label("UID " + node.getUid() + " (ID " + node.getId() + ", FW " + node.getFirmware() + ")");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 5;");
        
        // Create grid for pins (7 columns x 10 rows for 0-69)
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 5;");
        
        for (int pin = 0; pin <= 69; pin++) {
            PinInfo pinInfo = pins.get(pin);
            int row = pin / 10;
            int col = pin % 10;
            
            Label pinLabel = new Label(String.valueOf(pin));
            pinLabel.setPrefSize(40, 40);
            pinLabel.setAlignment(Pos.CENTER);
            pinLabel.setStyle("-fx-border-color: #999; -fx-border-radius: 3; -fx-background-radius: 3;");
            
            // Set color and tooltip based on allocation
            if (pinInfo.isAllocated()) {
                pinLabel.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-weight: bold; " +
                                "-fx-border-color: #999; -fx-border-radius: 3; -fx-background-radius: 3;");
                pinLabel.setTooltip(new Tooltip("Allocated to: " + pinInfo.getIngredientName()));
            } else {
                pinLabel.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; " +
                                "-fx-border-color: #999; -fx-border-radius: 3; -fx-background-radius: 3;");
                pinLabel.setTooltip(new Tooltip("Free"));
            }
            
            grid.add(pinLabel, col, row);
        }
        
        // Add to container
        VBox nodeBox = new VBox(5);
        nodeBox.getChildren().addAll(title, grid);
        pinGridContainer.getChildren().add(nodeBox);
    }
    
    public void refreshData() {
        if (serialManager == null || !serialManager.isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", 
                     "Please connect to Arduino MASTER first.");
            return;
        }
        
        statusLabel.setText("Scanning network...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #FF9800;");
        
        // Clear existing data
        nodeTable.getItems().clear();
        pinGridContainer.getChildren().clear();
        
        // Clear scan response buffer and set flag before starting new discover
        scanResponseBuffer.setLength(0);
        isWaitingForScanResponse = true;
        
        // Send discover command to get UID/ID/FW info
        serialManager.sendCommand(ArduinoCommand.discover());
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        IconSupport.applyTo(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
