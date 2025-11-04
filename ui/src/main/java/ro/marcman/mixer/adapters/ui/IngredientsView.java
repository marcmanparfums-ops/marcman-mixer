package ro.marcman.mixer.adapters.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.ports.repository.IngredientRepository;
import ro.marcman.mixer.serial.SerialManager;
import ro.marcman.mixer.sqlite.DatabaseManager;
import ro.marcman.mixer.sqlite.IngredientRepositoryImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ingredients view - displays all IFRA ingredients from database
 */
public class IngredientsView extends VBox {
    
    private final DatabaseManager dbManager;
    private final IngredientRepository repository;
    private SerialManager serialManager;
    
    private TableView<Ingredient> table;
    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private Label statsLabel;
    private ObservableList<Ingredient> allIngredients;
    
    public IngredientsView() {
        super(10);
        setPadding(new Insets(15));
        
        // Initialize database
        this.dbManager = DatabaseManager.getInstance();
        this.repository = new IngredientRepositoryImpl(dbManager);
        
        buildUI();
        loadIngredients();
    }
    
    public void setSerialManager(SerialManager serialManager) {
        this.serialManager = serialManager;
    }
    
    private void buildUI() {
        // Title
        Label title = new Label("📊 IFRA Ingredients Database");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        
        // Stats bar
        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-background-color: #E3F2FD; -fx-background-radius: 5;");
        statsLabel = new Label("Loading...");
        statsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        statsBox.getChildren().add(statsLabel);
        
        // Search and filter bar
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("🔍 Search:");
        searchField = new TextField();
        searchField.setPromptText("Search by name, CAS, category...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, old, newVal) -> filterIngredients());
        
        Label categoryLabel = new Label("Category:");
        categoryFilter = new ComboBox<>();
        categoryFilter.setPromptText("All categories");
        categoryFilter.setPrefWidth(200);
        categoryFilter.valueProperty().addListener((obs, old, newVal) -> filterIngredients());
        
        Button clearButton = new Button("Clear Filters");
        clearButton.setOnAction(e -> clearFilters());
        
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        refreshButton.setTooltip(new Tooltip("Reload all ingredients from database to see latest changes"));
        refreshButton.setOnAction(e -> {
            // Force database connection reset to ensure fresh data
            dbManager.resetConnection();
            // Clear existing items first
            table.getItems().clear();
            allIngredients = null;
            // Reload from database
            loadIngredients();
            // Force a complete refresh
            table.refresh();
            Platform.runLater(() -> {
                table.refresh();
                showAlert(Alert.AlertType.INFORMATION, "Refreshed", 
                         "Ingredients reloaded from database.\n" +
                         "All data is now up to date.\n\n" +
                         "If stock values still don't update, please restart the application.");
            });
        });
        
        Button clearAllButton = new Button("🗑️ Clear All");
        clearAllButton.setStyle("-fx-font-weight: bold; -fx-background-color: #F44336; -fx-text-fill: white;");
        clearAllButton.setOnAction(e -> clearAllIngredients());
        
        Button deleteUnconfiguredButton = new Button("🗑️ Delete No Pins");
        deleteUnconfiguredButton.setStyle("-fx-font-weight: bold; -fx-background-color: #FF5722; -fx-text-fill: white;");
        deleteUnconfiguredButton.setTooltip(new Tooltip("Delete all ingredients that do NOT have Arduino pins set (arduino_pin and arduino_pin_small are null)"));
        deleteUnconfiguredButton.setOnAction(e -> {
            deleteNoPinsIngredients();
        });
        
        filterBar.getChildren().addAll(searchLabel, searchField, categoryLabel, 
                                       categoryFilter, clearButton, refreshButton);
        
        // Separate action buttons bar for delete operations
        HBox deleteButtonsBar = new HBox(10);
        deleteButtonsBar.setAlignment(Pos.CENTER_LEFT);
        deleteButtonsBar.setPadding(new Insets(5, 0, 5, 0));
        deleteButtonsBar.getChildren().addAll(deleteUnconfiguredButton, clearAllButton);
        
        // Table
        table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // AMORSEAZA button column (first column, left side)
        TableColumn<Ingredient, Ingredient> amorseazaCol = new TableColumn<>("AMORSEAZA");
        amorseazaCol.setPrefWidth(120);
        amorseazaCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        amorseazaCol.setCellFactory(col -> new TableCell<Ingredient, Ingredient>() {
            private final Button amorseazaButton = new Button("AMORSEAZA");
            
            {
                amorseazaButton.setStyle("-fx-font-weight: bold; -fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 11px;");
                amorseazaButton.setPrefWidth(100);
                amorseazaButton.setMinWidth(100);
                amorseazaButton.setMaxWidth(100);
            }
            
            @Override
            protected void updateItem(Ingredient item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    // Re-create the button action handler for this specific ingredient
                    final Ingredient currentIngredient = item; // Capture item in final variable
                    amorseazaButton.setOnAction(e -> {
                        e.consume(); // Prevent row selection
                        if (currentIngredient != null) {
                            amorseazaIngredient(currentIngredient);
                        }
                    });
                    
                    // Enable button only if ingredient has at least one pin configured
                    boolean hasPins = (item.getArduinoPin() != null) || (item.getArduinoPinSmall() != null);
                    boolean shouldDisable = !hasPins || serialManager == null || !serialManager.isConnected();
                    amorseazaButton.setDisable(shouldDisable);
                    
                    setGraphic(amorseazaButton);
                    setAlignment(Pos.CENTER);
                }
            }
        });
        
        // ID column
        TableColumn<Ingredient, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);
        
        // Name column
        TableColumn<Ingredient, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(300);
        
        // CAS Number column
        TableColumn<Ingredient, String> casCol = new TableColumn<>("CAS Number");
        casCol.setCellValueFactory(new PropertyValueFactory<>("casNumber"));
        casCol.setPrefWidth(120);
        casCol.setStyle("-fx-font-family: 'Courier New';");
        
        // Category column
        TableColumn<Ingredient, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(150);
        
        // IFRA NCS column
        TableColumn<Ingredient, String> ncsCol = new TableColumn<>("IFRA NCS");
        ncsCol.setCellValueFactory(new PropertyValueFactory<>("ifraNaturalsCategory"));
        ncsCol.setPrefWidth(100);
        
        // Stock column
        TableColumn<Ingredient, Double> stockCol = new TableColumn<>("Stock (g)");
        stockCol.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        stockCol.setPrefWidth(100);
        stockCol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        stockCol.setCellFactory(col -> new TableCell<Ingredient, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("");
                } else {
                    setText(String.format("%.2f g", item));
                    // Color code based on stock level
                    if (item < 10) {
                        setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;"); // Red for low stock
                    } else if (item < 50) {
                        setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;"); // Orange for medium stock
                    } else {
                        setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;"); // Green for good stock
                    }
                }
            }
        });
        
        // Description column
        TableColumn<Ingredient, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(300);
        
        table.getColumns().addAll(amorseazaCol, idCol, nameCol, casCol, categoryCol, ncsCol, stockCol, descCol);
        
        // Add event filter to capture all clicks on table for AMORSEAZA buttons
        table.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
            javafx.scene.Node targetNode = event.getPickResult().getIntersectedNode();
            
            // Traverse up to find Button or TableCell
            while (targetNode != null) {
                if (targetNode instanceof TableCell) {
                    @SuppressWarnings("unchecked")
                    TableCell<Ingredient, Ingredient> cell = (TableCell<Ingredient, Ingredient>) targetNode;
                    if (cell.getGraphic() instanceof Button) {
                        // Found the AMORSEAZA button cell - trigger it programmatically
                        event.consume();
                        if (cell.getItem() != null) {
                            amorseazaIngredient(cell.getItem());
                        }
                        return;
                    }
                }
                if (targetNode instanceof Button) {
                    // Regular button click
                    return;
                }
                targetNode = targetNode.getParent();
            }
        });
        
        // Row click handler - show details
        table.setRowFactory(tv -> {
            TableRow<Ingredient> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                // Handle double click for details
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showIngredientDetails(row.getItem());
                }
            });
            return row;
        });
        
        // Action buttons bar
        HBox actionBar = new HBox(10);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setPadding(new Insets(5, 0, 5, 0));
        
        Button editArduinoButton = new Button("Configure Arduino");
        editArduinoButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        editArduinoButton.setOnAction(e -> {
            Ingredient selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showArduinoConfigDialog(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an ingredient to configure.");
            }
        });
        
        Button updateStockButton = new Button("📦 Update Stock");
        updateStockButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        updateStockButton.setOnAction(e -> {
            Ingredient selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showUpdateStockDialog(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an ingredient first.");
            }
        });
        
        Button viewDetailsButton = new Button("View Details");
        viewDetailsButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        viewDetailsButton.setOnAction(e -> {
            Ingredient selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showIngredientDetails(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an ingredient first.");
            }
        });
        
        actionBar.getChildren().addAll(editArduinoButton, updateStockButton, viewDetailsButton);
        
        // Info label
        Label infoLabel = new Label("Double-click for details | Select + 'Configure Arduino' to assign SLAVE/PIN");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        // Add all to container
        getChildren().addAll(title, statsBox, filterBar, deleteButtonsBar, table, actionBar, infoLabel);
    }
    
    private void loadIngredients() {
        try {
            List<Ingredient> ingredients = repository.findAll();
            
            // Create completely new ObservableList to ensure changes are detected
            ObservableList<Ingredient> newList = FXCollections.observableArrayList(ingredients);
            
            // Clear existing items and set new ones to force complete refresh
            table.getItems().clear();
            allIngredients = newList;
            table.setItems(allIngredients);
            
            // Force refresh of all visible cells to update displayed values
            table.refresh();
            
            // Populate category filter
            List<String> categories = ingredients.stream()
                .map(Ingredient::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            
            categoryFilter.getItems().clear();
            categoryFilter.getItems().add("All categories");
            categoryFilter.getItems().addAll(categories);
            categoryFilter.setValue("All categories");
            
            // Update stats
            updateStats(ingredients);
            
        } catch (Exception e) {
            System.err.println("ERROR loading ingredients: " + e.getMessage());
            e.printStackTrace();
            showError("Error loading ingredients", e.getMessage());
            
            // Show placeholder with error
            String dbPath = DatabaseManager.getInstance().getDatabasePath();
            Label errorLabel = new Label("⚠️ Error loading ingredients from database:\n" + e.getMessage() +
                "\n\nDatabase location: " + dbPath + "\n" +
                "Try running: data\\import_ifra_full.bat");
            errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red;");
            getChildren().add(errorLabel);
        }
    }
    
    private void filterIngredients() {
        if (allIngredients == null) return;
        
        String searchText = searchField.getText().toLowerCase();
        String selectedCategory = categoryFilter.getValue();
        
        List<Ingredient> filtered = allIngredients.stream()
            .filter(ing -> {
                // Category filter
                if (selectedCategory != null && !selectedCategory.equals("All categories")) {
                    if (ing.getCategory() == null || !ing.getCategory().equals(selectedCategory)) {
                        return false;
                    }
                }
                
                // Search filter
                if (!searchText.isEmpty()) {
                    String name = ing.getName() != null ? ing.getName().toLowerCase() : "";
                    String cas = ing.getCasNumber() != null ? ing.getCasNumber().toLowerCase() : "";
                    String cat = ing.getCategory() != null ? ing.getCategory().toLowerCase() : "";
                    String ncs = ing.getIfraNaturalsCategory() != null ? ing.getIfraNaturalsCategory().toLowerCase() : "";
                    String desc = ing.getDescription() != null ? ing.getDescription().toLowerCase() : "";
                    
                    return name.contains(searchText) || 
                           cas.contains(searchText) || 
                           cat.contains(searchText) ||
                           ncs.contains(searchText) ||
                           desc.contains(searchText);
                }
                
                return true;
            })
            .collect(Collectors.toList());
        
        table.setItems(FXCollections.observableArrayList(filtered));
        updateStats(filtered);
    }
    
    private void clearFilters() {
        searchField.clear();
        categoryFilter.setValue("All categories");
        table.setItems(allIngredients);
        updateStats(allIngredients);
    }
    
    private void updateStats(List<Ingredient> ingredients) {
        long total = ingredients.size();
        long withNcs = ingredients.stream()
            .filter(i -> i.getIfraNaturalsCategory() != null && !i.getIfraNaturalsCategory().isEmpty())
            .count();
        long synthetic = ingredients.stream()
            .filter(i -> "Synthetic".equals(i.getCategory()))
            .count();
        
        statsLabel.setText(String.format(
            "📊 Total: %d ingredients  |  🧪 Synthetic: %d  |  🌿 Natural (NCS): %d  |  " +
            "📋 Showing: %d of %d",
            allIngredients != null ? allIngredients.size() : 0,
            synthetic,
            withNcs,
            total,
            allIngredients != null ? allIngredients.size() : 0
        ));
    }
    
    private void showIngredientDetails(Ingredient ingredient) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ingredient Details");
        alert.setHeaderText(ingredient.getName());
        
        StringBuilder content = new StringBuilder();
        content.append("🔬 CAS Number: ").append(ingredient.getCasNumber()).append("\n\n");
        content.append("📂 Category: ").append(ingredient.getCategory()).append("\n");
        
        if (ingredient.getIfraNaturalsCategory() != null && !ingredient.getIfraNaturalsCategory().isEmpty()) {
            content.append("🌿 IFRA NCS Category: ").append(ingredient.getIfraNaturalsCategory()).append("\n");
        }
        
        content.append("\n📝 Description:\n").append(
            ingredient.getDescription() != null ? ingredient.getDescription() : "N/A"
        ).append("\n");
        
        content.append("\n🔌 Arduino Integration:\n");
        content.append("  UID: ").append(ingredient.getArduinoUid() != null ? ingredient.getArduinoUid() : "Not assigned").append("\n");
        content.append("  Pin: ").append(ingredient.getArduinoPin() != null ? ingredient.getArduinoPin() : "Not assigned").append("\n");
        content.append("  Default Duration: ").append(ingredient.getDefaultDuration() != null ? ingredient.getDefaultDuration() + "ms" : "Not set").append("\n");
        
        if (ingredient.getStockQuantity() != null) {
            content.append("\n📦 Stock: ").append(ingredient.getStockQuantity()).append(" ")
                .append(ingredient.getUnit() != null ? ingredient.getUnit() : "units").append("\n");
        }
        
        if (ingredient.getCostPerUnit() != null) {
            content.append("💰 Cost: ").append(ingredient.getCostPerUnit()).append(" per ")
                .append(ingredient.getUnit() != null ? ingredient.getUnit() : "unit").append("\n");
        }
        
        alert.setContentText(content.toString());
        alert.showAndWait();
    }
    
    /**
     * Check if a pin is restricted (system pins)
     */
    private boolean isPinRestricted(Integer pin) {
        if (pin == null) return false;
        return pin == 0 || pin == 1 || pin == 2 || pin == 13 || 
               pin == 20 || pin == 21 || pin == 50 || pin == 51 || 
               pin == 52 || pin == 53;
    }
    
    /**
     * Get restricted pin information
     */
    private String getRestrictedPinInfo() {
        return "Restricted pins: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53 (system pins)\n" +
               "Allowed pins: All other pins (3-12, 14-19, 22-49, 54-69 / A0-A15)";
    }
    
    /**
     * Parse PIN value from string (handles A0-A15 format)
     * Validates that pin is between 0-69 (maximum valid pin for Arduino Mega)
     */
    private Integer parsePinValue(String pinStr) {
        if (pinStr == null || pinStr.trim().isEmpty()) {
            return null;
        }
        try {
            if (pinStr.startsWith("A")) {
                int analogNum = Integer.parseInt(pinStr.substring(1));
                if (analogNum < 0 || analogNum > 15) {
                    return null; // Invalid analog pin
                }
                int pin = 54 + analogNum; // A0 = pin 54 on Mega
                return pin; // Already validated: 54-69 range
            } else {
                int pin = Integer.parseInt(pinStr.trim());
                // Validate pin range: 0-69 (maximum for Arduino Mega)
                if (pin < 0 || pin > 69) {
                    return null; // Out of range
                }
                return pin;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Find ingredient by UID and PIN (check for duplicates)
     * EXCLUDES ingredients with masterIngredientId to allow "duplicates" for CAS associations
     */
    private Ingredient findIngredientByUidAndPin(String uidLarge, Integer pinLarge, 
                                                String uidSmall, Long excludeId, Integer... pinSmall) {
        try {
            List<Ingredient> allIngredients = repository.findAll();
            
            for (Ingredient ing : allIngredients) {
                // Skip current ingredient being edited
                if (excludeId != null && ing.getId() != null && ing.getId().equals(excludeId)) {
                    continue;
                }
                
                // Skip ingredients with masterIngredientId (CAS-associated ingredients)
                // These are allowed to share the same pin as their master
                if (ing.getMasterIngredientId() != null) {
                    continue;
                }
                
                // Check Large Pump match
                if (uidLarge != null && pinLarge != null) {
                    if (uidLarge.equals(ing.getArduinoUid()) && 
                        pinLarge.equals(ing.getArduinoPin())) {
                        return ing;
                    }
                }
                
                // Check Small Pump match
                if (uidSmall != null) {
                    Integer checkPin = pinSmall.length > 0 ? pinSmall[0] : null;
                    if (checkPin != null && uidSmall.equals(ing.getArduinoUidSmall()) && 
                        checkPin.equals(ing.getArduinoPinSmall())) {
                        return ing;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking for duplicate PIN: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    private static final int MAX_PINS_PER_UID = 60;
    
    /**
     * Count how many pins are allocated to a specific UID
     */
    private int countPinsForUid(String uid, Long excludeId) {
        try {
            // Use findAllWithoutMasterApply to avoid double-counting ingredients with master configuration
            List<Ingredient> allIngredients = repository.findAllWithoutMasterApply();
            int count = 0;
            
            for (Ingredient ing : allIngredients) {
                // Skip current ingredient being edited
                if (excludeId != null && ing.getId() != null && ing.getId().equals(excludeId)) {
                    continue;
                }
                
                // Count Large pump pin if UID matches
                if (uid.equals(ing.getArduinoUid()) && ing.getArduinoPin() != null) {
                    count++;
                }
                
                // Count Small pump pin if UID matches
                if (uid.equals(ing.getArduinoUidSmall()) && ing.getArduinoPinSmall() != null) {
                    count++;
                }
            }
            
            return count;
        } catch (Exception e) {
            System.err.println("Error counting pins for UID: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    private void showArduinoConfigDialog(Ingredient ingredient) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configure Ingredient");
        dialog.setHeaderText("Configure: " + ingredient.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox grid = new VBox(10);
        grid.setPadding(new Insets(20));
        
        // Name field (read-only, display only)
        HBox nameBox = new HBox(10);
        nameBox.getChildren().add(new Label("Name:"));
        TextField nameField = new TextField(ingredient.getName());
        nameField.setEditable(false);
        nameField.setPrefWidth(300);
        nameBox.getChildren().add(nameField);
        
        // CAS Number
        HBox casBox = new HBox(10);
        casBox.getChildren().add(new Label("CAS Number:"));
        TextField casField = new TextField(ingredient.getCasNumber() != null ? ingredient.getCasNumber() : "");
        casField.setEditable(false);
        casField.setPrefWidth(300);
        casBox.getChildren().add(casField);
        
        // ========== LARGE PUMP SECTION ==========
        Label largePumpTitle = new Label("🔵 LARGE PUMP Configuration");
        largePumpTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2196F3;");
        
        // SLAVE UID Large
        HBox uidLargeBox = new HBox(10);
        uidLargeBox.getChildren().add(new Label("SLAVE UID:"));
        ComboBox<String> uidLargeCombo = new ComboBox<>();
        uidLargeCombo.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8");
        uidLargeCombo.setEditable(true);
        if (ingredient.getArduinoUid() != null) {
            String uid = ingredient.getArduinoUid().replace("0x", "").replace("0X", "");
            try {
                int slaveId = Integer.parseInt(uid, 16);
                uidLargeCombo.setValue(String.valueOf(slaveId));
            } catch (NumberFormatException e) {
                uidLargeCombo.setValue(ingredient.getArduinoUid());
            }
        }
        uidLargeCombo.setPrefWidth(200);
        uidLargeBox.getChildren().add(uidLargeCombo);
        
        // PIN Large
        HBox pinLargeBox = new HBox(10);
        pinLargeBox.getChildren().add(new Label("PIN:"));
        ComboBox<String> pinLargeCombo = new ComboBox<>();
        for (int i = 0; i < 16; i++) {
            pinLargeCombo.getItems().add("A" + i);
        }
        for (int i = 22; i <= 69; i++) {
            pinLargeCombo.getItems().add(String.valueOf(i));
        }
        pinLargeCombo.setEditable(true);
        if (ingredient.getArduinoPin() != null) {
            int pin = ingredient.getArduinoPin();
            if (pin >= 54 && pin <= 69) {
                pinLargeCombo.setValue("A" + (pin - 54));
            } else {
                pinLargeCombo.setValue(String.valueOf(pin));
            }
        }
        pinLargeCombo.setPrefWidth(200);
        pinLargeBox.getChildren().add(pinLargeCombo);
        
        // Warning label for Large Pump (duplicate + restricted)
        Label warningLargeLabel = new Label();
        warningLargeLabel.setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold; -fx-font-size: 11px;");
        warningLargeLabel.setVisible(false);
        warningLargeLabel.setWrapText(true);
        warningLargeLabel.setMaxWidth(350);
        
        // Real-time validation for Large Pump
        Runnable validateLargePump = () -> {
            String uidStr = uidLargeCombo.getValue();
            String pinStr = pinLargeCombo.getValue();
            
            List<String> warnings = new ArrayList<>();
            
            // Skip validation if ingredient is associated with a master (CAS-associated)
            if (ingredient.getMasterIngredientId() != null) {
                warningLargeLabel.setVisible(false);
                return;
            }
            
            if (pinStr != null && !pinStr.trim().isEmpty()) {
                Integer pin = parsePinValue(pinStr);
                
                if (pin != null) {
                    // Check if pin is restricted
                    if (isPinRestricted(pin)) {
                        warnings.add("⚠ RESTRICTED PIN! Pin " + pin + " cannot be used (restricted: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53)");
                    }
                    
                    // Check for duplicate only if UID is also set
                    if (uidStr != null && !uidStr.trim().isEmpty()) {
                        try {
                            int slaveNum = Integer.parseInt(uidStr.trim());
                            String uid = "0x" + Integer.toHexString(slaveNum);
                            Ingredient duplicate = findIngredientByUidAndPin(uid, pin, null, ingredient.getId());
                            if (duplicate != null) {
                                warnings.add("⚠ DUPLICATE! Already used by: " + duplicate.getName() + 
                                           " (ID: " + duplicate.getId() + ")");
                            }
                        } catch (NumberFormatException e) {
                            // Invalid UID format, skip duplicate check
                        }
                    }
                } else {
                    // Invalid pin format
                    if (!pinStr.trim().isEmpty()) {
                        warnings.add("⚠ INVALID PIN! Use valid pin numbers or A0-A15 (analog)");
                    }
                }
            }
            
            if (!warnings.isEmpty()) {
                warningLargeLabel.setText(String.join("\n", warnings));
                warningLargeLabel.setVisible(true);
            } else {
                warningLargeLabel.setVisible(false);
            }
        };
        
        uidLargeCombo.valueProperty().addListener((obs, old, newVal) -> validateLargePump.run());
        pinLargeCombo.valueProperty().addListener((obs, old, newVal) -> validateLargePump.run());
        
        // Run initial validation
        validateLargePump.run();
        
        // msPerGram Large Pump with Calibration button
        HBox msPerGramLargeBox = new HBox(10);
        msPerGramLargeBox.getChildren().add(new Label("ms/g:"));
        TextField msPerGramLargeField = new TextField();
        msPerGramLargeField.setPromptText("e.g., 20 (ms per gram)");
        msPerGramLargeField.setPrefWidth(200);
        if (ingredient.getMsPerGramLarge() != null) {
            msPerGramLargeField.setText(String.valueOf(ingredient.getMsPerGramLarge()));
        }
        msPerGramLargeBox.getChildren().add(msPerGramLargeField);
        
        // Calibration button for Large pump
        Button calibrateLargeButton = new Button("Calibrare");
        calibrateLargeButton.setStyle("-fx-font-weight: bold; -fx-background-color: #2196F3; -fx-text-fill: white;");
        calibrateLargeButton.setOnAction(e -> {
            String uidStr = uidLargeCombo.getValue();
            String pinStr = pinLargeCombo.getValue();
            String msPerGramStr = msPerGramLargeField.getText().trim();
            
            if (uidStr == null || uidStr.trim().isEmpty() || pinStr == null || pinStr.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Missing Configuration", 
                    "Please configure UID and PIN for Large pump before calibration.");
                return;
            }
            
            if (msPerGramStr.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Missing Value", 
                    "Please enter ms/g value before calibration.");
                return;
            }
            
            try {
                double msPerGram = Double.parseDouble(msPerGramStr);
                int durationMs = (int) msPerGram;
                
                Integer pin = parsePinValue(pinStr);
                if (pin == null) {
                    showAlert(Alert.AlertType.ERROR, "Invalid PIN", 
                        "Invalid PIN format. Please enter a valid pin number.");
                    return;
                }
                
                int slaveNum = Integer.parseInt(uidStr.trim());
                String uid = "0x" + Integer.toHexString(slaveNum);
                
                if (serialManager == null || !serialManager.isConnected()) {
                    showAlert(Alert.AlertType.WARNING, "Not Connected", 
                        "Please connect to Arduino MASTER first.");
                    return;
                }
                
                // Send pulse command
                serialManager.sendCommand(ro.marcman.mixer.serial.model.ArduinoCommand.pulseUid(uid, pin, durationMs));
                
                showAlert(Alert.AlertType.INFORMATION, "Calibration Test", 
                    String.format("Sent pulse command to UID=%s, PIN=%d for %d ms (%.2f ms/g)", 
                        uid, pin, durationMs, msPerGram));
                
            } catch (NumberFormatException ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Value", 
                    "Please enter a valid numeric value for ms/g.");
            }
        });
        msPerGramLargeBox.getChildren().add(calibrateLargeButton);
        
        VBox largePumpGroup = new VBox(8);
        largePumpGroup.setPadding(new Insets(10));
        largePumpGroup.setStyle("-fx-background-color: #E3F2FD; -fx-border-color: #2196F3; -fx-border-width: 1px; -fx-border-radius: 5px;");
        largePumpGroup.getChildren().addAll(largePumpTitle, uidLargeBox, pinLargeBox, msPerGramLargeBox, warningLargeLabel);
        
        // ========== SMALL PUMP SECTION ==========
        Label smallPumpTitle = new Label("🟠 SMALL PUMP Configuration");
        smallPumpTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #FF9800;");
        
        // SLAVE UID Small
        HBox uidSmallBox = new HBox(10);
        uidSmallBox.getChildren().add(new Label("SLAVE UID:"));
        ComboBox<String> uidSmallCombo = new ComboBox<>();
        uidSmallCombo.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8");
        uidSmallCombo.setEditable(true);
        if (ingredient.getArduinoUidSmall() != null) {
            String uid = ingredient.getArduinoUidSmall().replace("0x", "").replace("0X", "");
            try {
                int slaveId = Integer.parseInt(uid, 16);
                uidSmallCombo.setValue(String.valueOf(slaveId));
            } catch (NumberFormatException e) {
                uidSmallCombo.setValue(ingredient.getArduinoUidSmall());
            }
        }
        uidSmallCombo.setPrefWidth(200);
        uidSmallBox.getChildren().add(uidSmallCombo);
        
        // PIN Small
        HBox pinSmallBox = new HBox(10);
        pinSmallBox.getChildren().add(new Label("PIN:"));
        ComboBox<String> pinSmallCombo = new ComboBox<>();
        for (int i = 0; i < 16; i++) {
            pinSmallCombo.getItems().add("A" + i);
        }
        for (int i = 22; i <= 69; i++) {
            pinSmallCombo.getItems().add(String.valueOf(i));
        }
        pinSmallCombo.setEditable(true);
        if (ingredient.getArduinoPinSmall() != null) {
            int pin = ingredient.getArduinoPinSmall();
            if (pin >= 54 && pin <= 69) {
                pinSmallCombo.setValue("A" + (pin - 54));
            } else {
                pinSmallCombo.setValue(String.valueOf(pin));
            }
        }
        pinSmallCombo.setPrefWidth(200);
        pinSmallBox.getChildren().add(pinSmallCombo);
        
        // Warning label for Small Pump (duplicate + restricted)
        Label warningSmallLabel = new Label();
        warningSmallLabel.setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold; -fx-font-size: 11px;");
        warningSmallLabel.setVisible(false);
        warningSmallLabel.setWrapText(true);
        warningSmallLabel.setMaxWidth(350);
        
        // Real-time validation for Small Pump
        Runnable validateSmallPump = () -> {
            String uidStr = uidSmallCombo.getValue();
            String pinStr = pinSmallCombo.getValue();
            
            List<String> warnings = new ArrayList<>();
            
            // Skip validation if ingredient is associated with a master (CAS-associated)
            if (ingredient.getMasterIngredientId() != null) {
                warningSmallLabel.setVisible(false);
                return;
            }
            
            if (pinStr != null && !pinStr.trim().isEmpty()) {
                Integer pin = parsePinValue(pinStr);
                
                if (pin != null) {
                    // Check if pin is restricted
                    if (isPinRestricted(pin)) {
                        warnings.add("⚠ RESTRICTED PIN! Pin " + pin + " cannot be used (restricted: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53)");
                    }
                    
                    // Check for duplicate only if UID is also set
                    if (uidStr != null && !uidStr.trim().isEmpty()) {
                        try {
                            int slaveNum = Integer.parseInt(uidStr.trim());
                            String uid = "0x" + Integer.toHexString(slaveNum);
                            Ingredient duplicate = findIngredientByUidAndPin(null, null, uid, ingredient.getId(), pin);
                            if (duplicate != null) {
                                warnings.add("⚠ DUPLICATE! Already used by: " + duplicate.getName() + 
                                           " (ID: " + duplicate.getId() + ")");
                            }
                        } catch (NumberFormatException e) {
                            // Invalid UID format, skip duplicate check
                        }
                    }
                } else {
                    // Invalid pin format
                    if (!pinStr.trim().isEmpty()) {
                        warnings.add("⚠ INVALID PIN! Use valid pin numbers or A0-A15 (analog)");
                    }
                }
            }
            
            if (!warnings.isEmpty()) {
                warningSmallLabel.setText(String.join("\n", warnings));
                warningSmallLabel.setVisible(true);
            } else {
                warningSmallLabel.setVisible(false);
            }
        };
        
        uidSmallCombo.valueProperty().addListener((obs, old, newVal) -> validateSmallPump.run());
        pinSmallCombo.valueProperty().addListener((obs, old, newVal) -> validateSmallPump.run());
        
        // Run initial validation
        validateSmallPump.run();
        
        // msPerGram Small Pump with Calibration button
        HBox msPerGramSmallBox = new HBox(10);
        msPerGramSmallBox.getChildren().add(new Label("ms/g:"));
        TextField msPerGramSmallField = new TextField();
        msPerGramSmallField.setPromptText("e.g., 20 (ms per gram)");
        msPerGramSmallField.setPrefWidth(200);
        if (ingredient.getMsPerGramSmall() != null) {
            msPerGramSmallField.setText(String.valueOf(ingredient.getMsPerGramSmall()));
        }
        msPerGramSmallBox.getChildren().add(msPerGramSmallField);
        
        // Calibration button for Small pump
        Button calibrateSmallButton = new Button("Calibrare");
        calibrateSmallButton.setStyle("-fx-font-weight: bold; -fx-background-color: #FF9800; -fx-text-fill: white;");
        calibrateSmallButton.setOnAction(e -> {
            String uidStr = uidSmallCombo.getValue();
            String pinStr = pinSmallCombo.getValue();
            String msPerGramStr = msPerGramSmallField.getText().trim();
            
            if (uidStr == null || uidStr.trim().isEmpty() || pinStr == null || pinStr.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Missing Configuration", 
                    "Please configure UID and PIN for Small pump before calibration.");
                return;
            }
            
            if (msPerGramStr.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Missing Value", 
                    "Please enter ms/g value before calibration.");
                return;
            }
            
            try {
                double msPerGram = Double.parseDouble(msPerGramStr);
                int durationMs = (int) msPerGram;
                
                Integer pin = parsePinValue(pinStr);
                if (pin == null) {
                    showAlert(Alert.AlertType.ERROR, "Invalid PIN", 
                        "Invalid PIN format. Please enter a valid pin number.");
                    return;
                }
                
                int slaveNum = Integer.parseInt(uidStr.trim());
                String uid = "0x" + Integer.toHexString(slaveNum);
                
                if (serialManager == null || !serialManager.isConnected()) {
                    showAlert(Alert.AlertType.WARNING, "Not Connected", 
                        "Please connect to Arduino MASTER first.");
                    return;
                }
                
                // Send pulse command
                serialManager.sendCommand(ro.marcman.mixer.serial.model.ArduinoCommand.pulseUid(uid, pin, durationMs));
                
                showAlert(Alert.AlertType.INFORMATION, "Calibration Test", 
                    String.format("Sent pulse command to UID=%s, PIN=%d for %d ms (%.2f ms/g)", 
                        uid, pin, durationMs, msPerGram));
                
            } catch (NumberFormatException ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Value", 
                    "Please enter a valid numeric value for ms/g.");
            }
        });
        msPerGramSmallBox.getChildren().add(calibrateSmallButton);
        
        VBox smallPumpGroup = new VBox(8);
        smallPumpGroup.setPadding(new Insets(10));
        smallPumpGroup.setStyle("-fx-background-color: #FFF3E0; -fx-border-color: #FF9800; -fx-border-width: 1px; -fx-border-radius: 5px;");
        smallPumpGroup.getChildren().addAll(smallPumpTitle, uidSmallBox, pinSmallBox, msPerGramSmallBox, warningSmallLabel);
        
        // ========== THRESHOLD (COMMON) ==========
        Label thresholdTitle = new Label("⚙️ Pump Selection Threshold");
        thresholdTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        HBox thresholdBox = new HBox(10);
        thresholdBox.getChildren().add(new Label("Threshold (g):"));
        TextField thresholdField = new TextField();
        thresholdField.setPromptText("e.g., 10.0 (grams)");
        thresholdField.setPrefWidth(200);
        if (ingredient.getPumpThresholdGrams() != null) {
            thresholdField.setText(String.valueOf(ingredient.getPumpThresholdGrams()));
        }
        thresholdBox.getChildren().add(thresholdField);
        
        VBox thresholdGroup = new VBox(8);
        thresholdGroup.setPadding(new Insets(10));
        thresholdGroup.getChildren().addAll(thresholdTitle, thresholdBox);
        
        Label infoLabel = new Label(
            "⚠ At least one pump must be configured (Large OR Small).\n" +
            "If you set a PIN, you must also set the corresponding SLAVE UID.\n" +
            "Restricted pins: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53 (system pins).\n" +
            "Allowed pins: 3-12, 14-19, 22-49, 54-69 (A0-A15). Maximum pin: 69.\n" +
            "ms/g: Milliseconds needed to pump 1 gram. Threshold: Below uses Small pump, above uses Large pump."
        );
        infoLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 11px;");
        infoLabel.setWrapText(true);
        
        grid.getChildren().addAll(nameBox, casBox, largePumpGroup, smallPumpGroup, thresholdGroup, infoLabel);
        dialog.getDialogPane().setContent(grid);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Parse and set UID Large
                    String uidLargeStr = uidLargeCombo.getValue();
                    if (uidLargeStr != null && !uidLargeStr.trim().isEmpty()) {
                        int slaveNum = Integer.parseInt(uidLargeStr.trim());
                        ingredient.setArduinoUid("0x" + Integer.toHexString(slaveNum));
                    } else {
                        ingredient.setArduinoUid(null);
                    }
                    
                    // Parse and set PIN Large
                    String pinLargeStr = pinLargeCombo.getValue();
                    if (pinLargeStr != null && !pinLargeStr.trim().isEmpty()) {
                        Integer pin = parsePinValue(pinLargeStr);
                        if (pin != null) {
                            // Check if pin exceeds maximum (69)
                            if (pin > 69) {
                                showAlert(Alert.AlertType.ERROR, "Invalid PIN", 
                                         "Pin " + pin + " exceeds maximum allowed (69).\n\n" +
                                         "Valid pin range: 0-69 (for Arduino Mega 2560)");
                                return;
                            }
                            // Check if pin is restricted
                            if (isPinRestricted(pin)) {
                                showAlert(Alert.AlertType.ERROR, "Restricted PIN", 
                                         "Pin " + pin + " is RESTRICTED and cannot be used!\n\n" +
                                         getRestrictedPinInfo());
                                return;
                            }
                            ingredient.setArduinoPin(pin);
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Invalid PIN", 
                                     "Invalid PIN format or out of range: " + pinLargeStr + "\n\n" +
                                     "Valid pins: 0-69 (excluding restricted: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53)\n" +
                                     "Or use A0-A15 for analog pins (54-69)");
                            return;
                        }
                    } else {
                        ingredient.setArduinoPin(null);
                    }
                    
                    // Parse and set UID Small
                    String uidSmallStr = uidSmallCombo.getValue();
                    if (uidSmallStr != null && !uidSmallStr.trim().isEmpty()) {
                        int slaveNum = Integer.parseInt(uidSmallStr.trim());
                        ingredient.setArduinoUidSmall("0x" + Integer.toHexString(slaveNum));
                    } else {
                        ingredient.setArduinoUidSmall(null);
                    }
                    
                    // Parse and set PIN Small
                    String pinSmallStr = pinSmallCombo.getValue();
                    if (pinSmallStr != null && !pinSmallStr.trim().isEmpty()) {
                        Integer pin = parsePinValue(pinSmallStr);
                        if (pin != null) {
                            // Check if pin exceeds maximum (69)
                            if (pin > 69) {
                                showAlert(Alert.AlertType.ERROR, "Invalid PIN", 
                                         "Pin " + pin + " exceeds maximum allowed (69).\n\n" +
                                         "Valid pin range: 0-69 (for Arduino Mega 2560)");
                                return;
                            }
                            // Check if pin is restricted
                            if (isPinRestricted(pin)) {
                                showAlert(Alert.AlertType.ERROR, "Restricted PIN", 
                                         "Pin " + pin + " is RESTRICTED and cannot be used!\n\n" +
                                         getRestrictedPinInfo());
                                return;
                            }
                            ingredient.setArduinoPinSmall(pin);
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Invalid PIN", 
                                     "Invalid PIN format or out of range: " + pinSmallStr + "\n\n" +
                                     "Valid pins: 0-69 (excluding restricted: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53)\n" +
                                     "Or use A0-A15 for analog pins (54-69)");
                            return;
                        }
                    } else {
                        ingredient.setArduinoPinSmall(null);
                    }
                    
                    // Parse and set msPerGram Large
                    String msPerGramLargeStr = msPerGramLargeField.getText().trim();
                    if (!msPerGramLargeStr.isEmpty()) {
                        try {
                            ingredient.setMsPerGramLarge(Integer.parseInt(msPerGramLargeStr));
                        } catch (NumberFormatException e) {
                            showAlert(Alert.AlertType.WARNING, "Invalid Input", 
                                     "ms/g Large Pump must be a valid number.");
                            return;
                        }
                    } else {
                        ingredient.setMsPerGramLarge(null);
                    }
                    
                    // Parse and set msPerGram Small
                    String msPerGramSmallStr = msPerGramSmallField.getText().trim();
                    if (!msPerGramSmallStr.isEmpty()) {
                        try {
                            ingredient.setMsPerGramSmall(Integer.parseInt(msPerGramSmallStr));
                        } catch (NumberFormatException e) {
                            showAlert(Alert.AlertType.WARNING, "Invalid Input", 
                                     "ms/g Small Pump must be a valid number.");
                            return;
                        }
                    } else {
                        ingredient.setMsPerGramSmall(null);
                    }
                    
                    // Parse and set threshold
                    String thresholdStr = thresholdField.getText().trim();
                    if (!thresholdStr.isEmpty()) {
                        try {
                            ingredient.setPumpThresholdGrams(Double.parseDouble(thresholdStr));
                        } catch (NumberFormatException e) {
                            showAlert(Alert.AlertType.WARNING, "Invalid Input", 
                                     "Threshold must be a valid number.");
                            return;
                        }
                    } else {
                        ingredient.setPumpThresholdGrams(null);
                    }
                    
                    // Check for duplicate PIN configurations
                    List<String> duplicateErrors = new ArrayList<>();
                    
                    // Check Large Pump duplicate
                    if (ingredient.getArduinoPin() != null && ingredient.getArduinoUid() != null) {
                        Ingredient duplicateLarge = findIngredientByUidAndPin(
                            ingredient.getArduinoUid(), 
                            ingredient.getArduinoPin(), 
                            null, 
                            ingredient.getId()
                        );
                        if (duplicateLarge != null) {
                            duplicateErrors.add(
                                "Large Pump: PIN " + ingredient.getArduinoPin() + 
                                " on SLAVE " + ingredient.getArduinoUid() + 
                                " is already used by: " + duplicateLarge.getName()
                            );
                        }
                    }
                    
                    // Check Small Pump duplicate
                    if (ingredient.getArduinoPinSmall() != null && ingredient.getArduinoUidSmall() != null) {
                        Ingredient duplicateSmall = findIngredientByUidAndPin(
                            null, 
                            null, 
                            ingredient.getArduinoUidSmall(), 
                            ingredient.getId(),
                            ingredient.getArduinoPinSmall()
                        );
                        if (duplicateSmall != null) {
                            duplicateErrors.add(
                                "Small Pump: PIN " + ingredient.getArduinoPinSmall() + 
                                " on SLAVE " + ingredient.getArduinoUidSmall() + 
                                " is already used by: " + duplicateSmall.getName()
                            );
                        }
                    }
                    
                    if (!duplicateErrors.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Duplicate PIN Configuration", 
                                 "Cannot save: PIN conflicts detected!\n\n" + 
                                 String.join("\n", duplicateErrors));
                        return;
                    }
                    
                    // Check PIN limit per UID
                    List<String> pinLimitErrors = new ArrayList<>();
                    
                    // Check Large Pump UID limit
                    if (ingredient.getArduinoUid() != null && ingredient.getArduinoPin() != null) {
                        int currentPins = countPinsForUid(ingredient.getArduinoUid(), ingredient.getId());
                        
                        // Check if this ingredient already has a pin allocated to this UID in the database
                        boolean isReplacement = false;
                        if (ingredient.getId() != null) {
                            try {
                                // Use findAllWithoutMasterApply to get raw DB data
                                List<Ingredient> allIngredients = repository.findAllWithoutMasterApply();
                                Optional<Ingredient> existingIng = allIngredients.stream()
                                    .filter(ing -> ing.getId() != null && ing.getId().equals(ingredient.getId()))
                                    .findFirst();
                                if (existingIng.isPresent()) {
                                    Ingredient dbIng = existingIng.get();
                                    if (ingredient.getArduinoUid().equals(dbIng.getArduinoUid()) && dbIng.getArduinoPin() != null) {
                                        isReplacement = true;
                                    } else if (ingredient.getArduinoUid().equals(dbIng.getArduinoUidSmall()) && dbIng.getArduinoPinSmall() != null) {
                                        isReplacement = true;
                                    }
                                }
                            } catch (Exception e) {
                                // If we can't check, assume it's new to be safe
                            }
                        }
                        
                        int pinsAfterAdd = isReplacement ? currentPins : currentPins + 1;
                        
                        if (pinsAfterAdd > MAX_PINS_PER_UID) {
                            pinLimitErrors.add(
                                "Large Pump: SLAVE " + ingredient.getArduinoUid() + 
                                " has reached the maximum limit of " + MAX_PINS_PER_UID + " pins.\n" +
                                "Currently allocated: " + currentPins + " pins.\n" +
                                "Cannot add more pins to this Arduino."
                            );
                        }
                    }
                    
                    // Check Small Pump UID limit
                    if (ingredient.getArduinoUidSmall() != null && ingredient.getArduinoPinSmall() != null) {
                        int currentPins = countPinsForUid(ingredient.getArduinoUidSmall(), ingredient.getId());
                        
                        boolean isReplacement = false;
                        if (ingredient.getId() != null) {
                            try {
                                // Use findAllWithoutMasterApply to get raw DB data
                                List<Ingredient> allIngredients = repository.findAllWithoutMasterApply();
                                Optional<Ingredient> existingIng = allIngredients.stream()
                                    .filter(ing -> ing.getId() != null && ing.getId().equals(ingredient.getId()))
                                    .findFirst();
                                if (existingIng.isPresent()) {
                                    Ingredient dbIng = existingIng.get();
                                    if (ingredient.getArduinoUidSmall().equals(dbIng.getArduinoUidSmall()) && dbIng.getArduinoPinSmall() != null) {
                                        isReplacement = true;
                                    } else if (ingredient.getArduinoUidSmall().equals(dbIng.getArduinoUid()) && dbIng.getArduinoPin() != null) {
                                        isReplacement = true;
                                    }
                                }
                            } catch (Exception e) {
                                // If we can't check, assume it's new to be safe
                            }
                        }
                        
                        int pinsAfterAdd = isReplacement ? currentPins : currentPins + 1;
                        
                        if (pinsAfterAdd > MAX_PINS_PER_UID) {
                            pinLimitErrors.add(
                                "Small Pump: SLAVE " + ingredient.getArduinoUidSmall() + 
                                " has reached the maximum limit of " + MAX_PINS_PER_UID + " pins.\n" +
                                "Currently allocated: " + currentPins + " pins.\n" +
                                "Cannot add more pins to this Arduino."
                            );
                        }
                    }
                    
                    if (!pinLimitErrors.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "PIN Limit Exceeded", 
                                 "Cannot save: Maximum pins per Arduino exceeded!\n\n" + 
                                 String.join("\n\n", pinLimitErrors) +
                                 "\n\nEach Arduino can have a maximum of " + MAX_PINS_PER_UID + 
                                 " pins allocated (after excluding restricted system pins).");
                        return;
                    }
                    
                    // Validate: At least one pump must be configured
                    if (ingredient.getArduinoPin() == null && ingredient.getArduinoPinSmall() == null) {
                        showAlert(Alert.AlertType.WARNING, "Configuration Required", 
                                 "At least one pump (Large or Small) must be configured!");
                        return;
                    }
                    
                    // Save to database
                    repository.save(ingredient);
                    
                    // Reload ingredients to show updated data
                    loadIngredients();
                    
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Arduino configuration saved for: " + ingredient.getName() + "\n\n" +
                             "Large Pump: " + (ingredient.getArduinoUid() != null ? ingredient.getArduinoUid() : "Not set") + 
                             " / PIN " + (ingredient.getArduinoPin() != null ? ingredient.getArduinoPin() : "Not set") + "\n" +
                             "Small Pump: " + (ingredient.getArduinoUidSmall() != null ? ingredient.getArduinoUidSmall() : "Not set") + 
                             " / PIN " + (ingredient.getArduinoPinSmall() != null ? ingredient.getArduinoPinSmall() : "Not set"));
                    
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", 
                             "Please enter valid numbers for SLAVE ID, PIN, ms/g, and Threshold.");
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", 
                             "Failed to save ingredient: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private boolean isDeletingNoPins = false; // Flag to prevent concurrent executions
    
    private void deleteNoPinsIngredients() {
        // Prevent multiple concurrent executions
        if (isDeletingNoPins) {
            System.out.println("Delete operation already in progress, ignoring duplicate call");
            return;
        }
        
        isDeletingNoPins = true;
        
        try {
            // Find all ingredients without any Arduino pins set (both arduino_pin and arduino_pin_small are null)
            List<Ingredient> noPins = allIngredients.stream()
                .filter(ing -> 
                    (ing.getArduinoPin() == null) && (ing.getArduinoPinSmall() == null)
                )
                .collect(Collectors.toList());
        
            if (noPins.isEmpty()) {
                isDeletingNoPins = false;
                showAlert(Alert.AlertType.INFORMATION, "No Ingredients Without Pins", 
                         "All ingredients have at least one Arduino pin configured!");
                return;
            }
        
            // Create confirmation dialog
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("⚠️ Delete Ingredients Without Pins");
            confirmDialog.setHeaderText("Delete ingredients without Arduino pins?");
            
            StringBuilder content = new StringBuilder();
            content.append("This will DELETE ").append(noPins.size()).append(" ingredient(s) that do NOT have:\n");
            content.append("  • Large Pump PIN (arduino_pin)\n");
            content.append("  • Small Pump PIN (arduino_pin_small)\n\n");
            content.append("Ingredients with at least one pin set will NOT be deleted.\n\n");
            
            // Show first 10 ingredients without pins as preview
            if (noPins.size() <= 10) {
                content.append("Ingredients to be deleted:\n");
                for (Ingredient ing : noPins) {
                    content.append("  • ").append(ing.getName());
                    if (ing.getCasNumber() != null && !ing.getCasNumber().isEmpty()) {
                        content.append(" (").append(ing.getCasNumber()).append(")");
                    }
                    content.append("\n");
                }
            } else {
                content.append("First 10 ingredients to be deleted:\n");
                for (int i = 0; i < 10; i++) {
                    Ingredient ing = noPins.get(i);
                    content.append("  • ").append(ing.getName());
                    if (ing.getCasNumber() != null && !ing.getCasNumber().isEmpty()) {
                        content.append(" (").append(ing.getCasNumber()).append(")");
                    }
                    content.append("\n");
                }
                content.append("  ... and ").append(noPins.size() - 10).append(" more\n");
            }
            
            content.append("\nThis action CANNOT be undone!\n");
            content.append("Are you sure you want to continue?");
            
            confirmDialog.setContentText(content.toString());
            
            ButtonType yesButton = new ButtonType("Yes, Delete No Pins", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("No, Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            confirmDialog.getButtonTypes().setAll(yesButton, noButton);
            
            confirmDialog.showAndWait().ifPresent(response -> {
                if (response == yesButton) {
                    try {
                        System.out.println("Deleting " + noPins.size() + " ingredients without pins from database...");
                        
                        int deletedCount = 0;
                        int recipeIngredientsDeleted = 0;
                        
                        // Use singleton DatabaseManager to get connection
                        DatabaseManager dbMgr = DatabaseManager.getInstance();
                        
                        // Delete each ingredient without pins
                        for (Ingredient ing : noPins) {
                            try {
                                java.sql.Connection conn = dbMgr.getConnection();
                                
                                // Delete recipe-ingredient links first
                                java.sql.PreparedStatement stmt = conn.prepareStatement(
                                    "DELETE FROM recipe_ingredients WHERE ingredient_id = ?"
                                );
                                stmt.setLong(1, ing.getId());
                                int recipeIngCount = stmt.executeUpdate();
                                recipeIngredientsDeleted += recipeIngCount;
                                stmt.close();
                                
                                // Delete the ingredient
                                stmt = conn.prepareStatement("DELETE FROM ingredients WHERE id = ?");
                                stmt.setLong(1, ing.getId());
                                stmt.executeUpdate();
                                stmt.close();
                                
                                conn.close();
                                
                                deletedCount++;
                                System.out.println("  ✅ Deleted: " + ing.getName() + " (ID: " + ing.getId() + ")");
                                
                            } catch (Exception e) {
                                System.err.println("  ❌ Error deleting ingredient " + ing.getName() + ": " + e.getMessage());
                            }
                        }
                        
                        System.out.println("Successfully deleted " + deletedCount + " ingredients without pins");
                        System.out.println("Also deleted " + recipeIngredientsDeleted + " recipe-ingredient links");
                        
                        // Refresh UI
                        loadIngredients();
                        
                        // Show success message
                        showAlert(Alert.AlertType.INFORMATION, "Success", 
                                 "Ingredients without pins deleted successfully!\n\n" +
                                 "Deleted:\n" +
                                 "  • " + deletedCount + " ingredients (no pins set)\n" +
                                 "  • " + recipeIngredientsDeleted + " recipe-ingredient links\n\n" +
                                 "Remaining ingredients: " + (allIngredients != null ? allIngredients.size() : 0));
                        
                    } catch (Exception e) {
                        System.err.println("Error deleting ingredients without pins: " + e.getMessage());
                        e.printStackTrace();
                        showAlert(Alert.AlertType.ERROR, "Error", 
                                 "Failed to delete ingredients without pins: " + e.getMessage());
                    } finally {
                        isDeletingNoPins = false; // Reset flag when done
                    }
                } else {
                    System.out.println("Delete ingredients without pins cancelled by user");
                    isDeletingNoPins = false; // Reset flag on cancel
                }
            });
        } finally {
            // Ensure flag is reset even if exception occurs
            if (isDeletingNoPins) {
                isDeletingNoPins = false;
            }
        }
    }
    
    private void clearAllIngredients() {
        // Create confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("⚠️ Clear All Ingredients");
        confirmDialog.setHeaderText("This will DELETE ALL ingredients from the database!");
        confirmDialog.setContentText(
            "WARNING: This action will:\n" +
            "  • Delete ALL " + (allIngredients != null ? allIngredients.size() : 0) + " ingredients\n" +
            "  • Delete ALL recipes (they depend on ingredients)\n" +
            "  • Delete ALL recipe-ingredient links\n\n" +
            "This CANNOT be undone!\n\n" +
            "Are you absolutely sure you want to continue?"
        );
        
        ButtonType yesButton = new ButtonType("Yes, Delete All", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("No, Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(yesButton, noButton);
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == yesButton) {
                try {
                    System.out.println("Clearing all ingredients from database...");
                    
                    // Execute SQL to clear all data using correct path
                    // Use singleton DatabaseManager to get connection
                    DatabaseManager dbMgr = DatabaseManager.getInstance();
                    java.sql.Connection conn = dbMgr.getConnection();
                    java.sql.Statement stmt = conn.createStatement();
                    
                    // Delete in correct order (respect foreign keys)
                    int deletedRecipeIngredients = stmt.executeUpdate("DELETE FROM recipe_ingredients");
                    int deletedRecipes = stmt.executeUpdate("DELETE FROM recipes");
                    int deletedIngredients = stmt.executeUpdate("DELETE FROM ingredients");
                    
                    stmt.close();
                    conn.close();
                    
                    System.out.println("Deleted " + deletedRecipeIngredients + " recipe ingredients");
                    System.out.println("Deleted " + deletedRecipes + " recipes");
                    System.out.println("Deleted " + deletedIngredients + " ingredients");
                    
                    // Refresh UI
                    loadIngredients();
                    
                    // Show success message
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Database cleared successfully!\n\n" +
                             "Deleted:\n" +
                             "  • " + deletedIngredients + " ingredients\n" +
                             "  • " + deletedRecipes + " recipes\n" +
                             "  • " + deletedRecipeIngredients + " recipe-ingredient links");
                    
                } catch (Exception e) {
                    System.err.println("Error clearing database: " + e.getMessage());
                    e.printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Error", 
                             "Failed to clear database: " + e.getMessage());
                }
            } else {
                System.out.println("Clear all ingredients cancelled by user");
            }
        });
    }
    
    private void showUpdateStockDialog(Ingredient ingredient) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📦 Update Stock Quantity");
        dialog.setHeaderText("Update stock for: " + ingredient.getName());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField currentStockField = new TextField();
        currentStockField.setEditable(false);
        currentStockField.setPromptText("Current stock");
        double currentStock = ingredient.getStockQuantity() != null ? ingredient.getStockQuantity() : 0.0;
        currentStockField.setText(String.format("%.2f g", currentStock));
        currentStockField.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        TextField addStockField = new TextField();
        addStockField.setPromptText("Quantity to add (grams)");
        
        TextField newStockField = new TextField();
        newStockField.setEditable(false);
        newStockField.setPromptText("New total stock");
        newStockField.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #4CAF50;");
        
        // Update new stock field when add stock changes
        addStockField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                if (newVal != null && !newVal.trim().isEmpty()) {
                    double addAmount = Double.parseDouble(newVal);
                    double newTotal = currentStock + addAmount;
                    newStockField.setText(String.format("%.2f g", newTotal));
                } else {
                    newStockField.setText("");
                }
            } catch (NumberFormatException e) {
                newStockField.setText("Invalid");
            }
        });
        
        Label noteLabel = new Label("Note: Enter positive value to add stock,\nnegative to remove stock.");
        noteLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        grid.add(new Label("Current Stock:"), 0, 0);
        grid.add(currentStockField, 1, 0);
        grid.add(new Label("Add/Remove:"), 0, 1);
        grid.add(addStockField, 1, 1);
        grid.add(new Label("New Total:"), 0, 2);
        grid.add(newStockField, 1, 2);
        grid.add(noteLabel, 0, 3, 2, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    double addAmount = Double.parseDouble(addStockField.getText());
                    double newStock = currentStock + addAmount;
                    
                    if (newStock < 0) {
                        showAlert(Alert.AlertType.ERROR, "Invalid Stock", 
                                 "Stock cannot be negative!");
                        return;
                    }
                    
                    ingredient.setStockQuantity(newStock);
                    repository.save(ingredient);
                    
                    // Reload ingredients from database to ensure consistency
                    loadIngredients();
                    
                    System.out.println("Updated stock for " + ingredient.getName() + 
                                     ": " + currentStock + "g -> " + newStock + "g");
                    
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Stock updated successfully!\n\n" +
                             ingredient.getName() + "\n" +
                             "Previous: " + String.format("%.2f g", currentStock) + "\n" +
                             "Added: " + String.format("%+.2f g", addAmount) + "\n" +
                             "New Total: " + String.format("%.2f g", newStock));
                    
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", 
                             "Please enter a valid number.");
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", 
                             "Failed to update stock: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Trigger prime action for an ingredient - activate configured pins for 3 seconds
     */
    private void amorseazaIngredient(Ingredient ingredient) {
        if (ingredient == null || serialManager == null || !serialManager.isConnected()) {
            showAlert(Alert.AlertType.WARNING, "Nu este conectat", 
                     "Conectează-te la Arduino MASTER în tab-ul 'Procesor MASTER' înainte de a folosi funcția AMORSEAZA.");
            return;
        }
        
        // Check if ingredient has at least one pin configured
        boolean hasLargePin = ingredient.getArduinoPin() != null && ingredient.getArduinoUid() != null;
        boolean hasSmallPin = ingredient.getArduinoPinSmall() != null && ingredient.getArduinoUidSmall() != null;
        
        if (!hasLargePin && !hasSmallPin) {
            showAlert(Alert.AlertType.WARNING, "Pin neconfigurat", 
                     "Ingredientul " + ingredient.getName() + " nu are pinuri configurate.\n" +
                     "Configurează pinurile înainte de a folosi funcția AMORSEAZA.");
            return;
        }
        
        // Duration for prime action: 3 seconds = 3000 ms
        int primeDuration = 3000;
        
        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                // Send command for Large pump if configured
                if (hasLargePin) {
                    int slaveId = parseSlaveIdFromUid(ingredient.getArduinoUid());
                    if (slaveId >= 0) {
                        String command = String.format("pulse %d %d %d", 
                            slaveId, ingredient.getArduinoPin(), primeDuration);
                        serialManager.sendRawCommand(command);
                    }
                }
                
                // Send command for Small pump if configured
                if (hasSmallPin) {
                    int slaveId = parseSlaveIdFromUid(ingredient.getArduinoUidSmall());
                    if (slaveId >= 0) {
                        String command = String.format("pulse %d %d %d", 
                            slaveId, ingredient.getArduinoPinSmall(), primeDuration);
                        serialManager.sendRawCommand(command);
                    }
                }
                
                // Wait for prime duration to complete + small delay
                Thread.sleep(primeDuration + 200);
                
            } catch (InterruptedException e) {
                // Interrupted, ignore
            } catch (Exception e) {
                System.err.println("ERROR during AMORSEAZA: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
        
        showAlert(Alert.AlertType.INFORMATION, "AMORSEAZA", 
                 "Amorsare inițiată pentru: " + ingredient.getName() + "\n\n" +
                 "Durată: 3 secunde\n" +
                 (hasLargePin ? "• LARGE pump: " + ingredient.getArduinoUid() + " - PIN " + ingredient.getArduinoPin() + "\n" : "") +
                 (hasSmallPin ? "• SMALL pump: " + ingredient.getArduinoUidSmall() + " - PIN " + ingredient.getArduinoPinSmall() + "\n" : ""));
    }
    
    /**
     * Parse slave ID from UID string (e.g., "0x1" -> 1, "0xA" -> 10)
     */
    private int parseSlaveIdFromUid(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return -1;
        }
        
        try {
            String uidStr = uid.trim().replace("0x", "").replace("0X", "");
            return Integer.parseInt(uidStr, 16);
        } catch (NumberFormatException e) {
            System.err.println("Invalid UID format: " + uid);
            return -1;
        }
    }
    
    /**
     * Public method to refresh ingredients from database
     * Called automatically when tab is selected
     */
    public void refreshIngredients() {
        // Force database connection reset to ensure fresh data
        dbManager.resetConnection();
        // Clear existing items first
        table.getItems().clear();
        allIngredients = null;
        // Reload from database
        loadIngredients();
        // Force a complete refresh
        table.refresh();
    }
}

