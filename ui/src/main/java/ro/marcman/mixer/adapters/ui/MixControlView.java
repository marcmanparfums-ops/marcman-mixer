package ro.marcman.mixer.adapters.ui;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.model.Recipe;
import ro.marcman.mixer.core.model.RecipeIngredient;
import ro.marcman.mixer.serial.SerialManager;
import ro.marcman.mixer.sqlite.DatabaseManager;
import ro.marcman.mixer.sqlite.IngredientRepositoryImpl;
import ro.marcman.mixer.sqlite.RecipeRepositoryImpl;
import ro.marcman.mixer.adapters.ui.util.IconSupport;

import java.util.*;
import java.util.stream.Collectors;
import javafx.util.Callback;
import javafx.scene.control.CheckBox;

/**
 * Mix Control UI - Execute recipes automatically
 */
public class MixControlView extends VBox {
    
    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final RecipeRepositoryImpl recipeRepository = new RecipeRepositoryImpl(dbManager);
    private final IngredientRepositoryImpl ingredientRepository = new IngredientRepositoryImpl(dbManager);
    private SerialManager serialManager;
    
    private ComboBox<Recipe> recipeCombo;
    private TableView<RecipeIngredient> executionTable;
    private TextArea logArea;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button executeButton;
    private Button executeParallelButton;
    private Button stopButton;
    private Spinner<Integer> batchSizeSpinner;
    private Label calculatedInfoLabel;
    private Label executionTimeLabel;
    private Label stockWarningLabel;
    private Label stockStatusLabel;
    private VBox stockInfoPanel;
    
    private final Map<RecipeIngredient, BooleanProperty> ingredientSelectionMap = new IdentityHashMap<>();
    
    private boolean executing = false;
    private static final int MAX_BATCH_SIZE = 64;
    private static final int MAX_DURATION_PER_COMMAND = 60000;
    
    public MixControlView(SerialManager serialManager) {
        super(15);
        setPadding(new Insets(15));
        this.serialManager = serialManager;
        
        buildUI();
        loadRecipes();
    }
    
    private void buildUI() {
        // Title
        Label title = new Label("Mix Control - Automated Recipe Execution");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        
        // Recipe selection panel
        TitledPane selectionPane = new TitledPane();
        selectionPane.setText("Recipe Selection");
        selectionPane.setCollapsible(false);
        
        VBox selectionBox = new VBox(10);
        selectionBox.setPadding(new Insets(10));
        
        Label recipeLabel = new Label("Select Recipe:");
        recipeCombo = new ComboBox<>();
        recipeCombo.setPromptText("Choose recipe to execute...");
        recipeCombo.setMaxWidth(Double.MAX_VALUE);
        
        // Custom display for recipes
        recipeCombo.setCellFactory(param -> new ListCell<Recipe>() {
            @Override
            protected void updateItem(Recipe item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s (%d ingredients, %.1fs)", 
                        item.getName(), 
                        item.getIngredientCount(),
                        item.getTotalDuration() / 1000.0));
                }
            }
        });
        
        recipeCombo.setButtonCell(new ListCell<Recipe>() {
            @Override
            protected void updateItem(Recipe item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });
        
        recipeCombo.setOnAction(e -> loadRecipeForExecution());
        
        Button refreshRecipesButton = new Button("Refresh Recipes");
        refreshRecipesButton.setOnAction(e -> loadRecipes());
        
        HBox recipeRow = new HBox(10, recipeLabel, recipeCombo, refreshRecipesButton);
        recipeRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(recipeCombo, Priority.ALWAYS);
        
        // Batch size selection
        Label batchLabel = new Label("Desired Quantity:");
        batchLabel.setStyle("-fx-font-weight: bold;");
        
        batchSizeSpinner = new Spinner<>(1, 10000000, 100, 1);
        batchSizeSpinner.setEditable(true);
        batchSizeSpinner.setPrefWidth(100);
        batchSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateCalculatedInfo();
            updateStockWarning();
            // Refresh table to update PIN, Duration, and Formula columns with scaled values
            if (executionTable != null && executionTable.getItems() != null && !executionTable.getItems().isEmpty()) {
                executionTable.refresh();
            }
        });
        
        Label gramsLabel = new Label("grams");
        
        calculatedInfoLabel = new Label("");
        calculatedInfoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        calculatedInfoLabel.setWrapText(true);
        calculatedInfoLabel.setMaxWidth(260);
        
        executionTimeLabel = new Label("");
        executionTimeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #444;");
        executionTimeLabel.setWrapText(true);
        executionTimeLabel.setMaxWidth(260);
        
        HBox batchRow = new HBox(10, batchLabel, batchSizeSpinner, gramsLabel);
        batchRow.setAlignment(Pos.CENTER_LEFT);
        
        VBox batchInfoBox = new VBox(2, calculatedInfoLabel, executionTimeLabel);
        batchInfoBox.setAlignment(Pos.CENTER_LEFT);
        
        // Stock status panel (always visible, real-time updates)
        stockStatusLabel = new Label("");
        stockStatusLabel.setWrapText(true);
        stockStatusLabel.setMaxWidth(Double.MAX_VALUE);
        stockStatusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 10px;");
        
        stockInfoPanel = new VBox(5);
        stockInfoPanel.setPadding(new Insets(10));
        stockInfoPanel.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #CCCCCC; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        stockInfoPanel.getChildren().add(stockStatusLabel);
        stockInfoPanel.setVisible(false);
        stockInfoPanel.setManaged(false);
        stockInfoPanel.setMinHeight(VBox.USE_PREF_SIZE);
        stockInfoPanel.setMaxWidth(Double.MAX_VALUE);
        
        // Stock warning label (for detailed error messages)
        stockWarningLabel = new Label("");
        stockWarningLabel.setWrapText(true);
        stockWarningLabel.setMaxWidth(Double.MAX_VALUE);
        stockWarningLabel.setVisible(false);
        stockWarningLabel.setManaged(false);
        
        Label noteLabel = new Label("Note: Recipe will be scaled proportionally based on desired quantity.");
        noteLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999; -fx-font-style: italic;");
        
        selectionBox.getChildren().addAll(recipeRow, batchRow, batchInfoBox, stockInfoPanel, stockWarningLabel, noteLabel);
        selectionPane.setContent(selectionBox);
        
        // Execution plan table
        Label planLabel = new Label("Execution Plan:");
        planLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        executionTable = new TableView<>();
        executionTable.setPrefHeight(250);
        executionTable.setEditable(true);
        
        TableColumn<RecipeIngredient, Boolean> includeCol = new TableColumn<>("Include");
        includeCol.setPrefWidth(80);
        includeCol.setCellValueFactory(cellData -> selectionProperty(cellData.getValue()));
        includeCol.setCellFactory(column -> {
            Callback<Integer, ObservableValue<Boolean>> callback = index -> {
                if (index == null || index < 0 || index >= executionTable.getItems().size()) {
                    return new SimpleBooleanProperty(false);
                }
                RecipeIngredient item = executionTable.getItems().get(index);
                return selectionProperty(item);
            };
            CheckBoxTableCell<RecipeIngredient, Boolean> cell = new CheckBoxTableCell<RecipeIngredient, Boolean>(callback) {
                @Override
                public void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    applyCheckboxStyle(this, item, empty);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
        includeCol.setEditable(true);
        includeCol.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<RecipeIngredient, Integer> orderCol = new TableColumn<>("Order");
        orderCol.setCellValueFactory(new PropertyValueFactory<>("sequenceOrder"));
        orderCol.setPrefWidth(60);
        
        TableColumn<RecipeIngredient, String> nameCol = new TableColumn<>("Ingredient");
        nameCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDisplayName()));
        nameCol.setPrefWidth(200);
        
        TableColumn<RecipeIngredient, String> slaveCol = new TableColumn<>("SLAVE");
        slaveCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getSlaveUid() != null ? cellData.getValue().getSlaveUid() : "N/A"));
        slaveCol.setPrefWidth(80);
        
        TableColumn<RecipeIngredient, String> pinCol = new TableColumn<>("PIN (Pump)");
        pinCol.setCellValueFactory(cellData -> {
            RecipeIngredient ri = cellData.getValue();
            Ingredient ing = ri.getIngredient();
            
            if (ing == null) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
            
            Integer pinLarge = ing.getArduinoPin();
            Integer pinSmall = ing.getArduinoPinSmall();
            String uidLarge = ing.getArduinoUid();
            String uidSmall = ing.getArduinoUidSmall();
            
            // Calculate which pump will be used based on current batch size and duration
            String selectedPump = "";
            if (ri.getPulseDuration() != null && batchSizeSpinner != null && recipeCombo.getValue() != null) {
                int desiredBatchSize = batchSizeSpinner.getValue();
                int originalBatchSize = recipeCombo.getValue().getBatchSize() != null ? 
                    recipeCombo.getValue().getBatchSize() : 100;
                double scaleFactor = (double) desiredBatchSize / originalBatchSize;
                
                // Calculate grams for this ingredient using configured msPerGram
                double baseGrams = calculateBaseGramsFromDurationWithConfig(ri, ing);
                double scaledGrams = baseGrams * scaleFactor;
                
                // Determine which pump will be used (same logic as execution)
                Double threshold = ing.getPumpThresholdGrams() != null ? 
                    ing.getPumpThresholdGrams() : 10.0;
                
                if (scaledGrams < threshold) {
                    // Will use SMALL pump
                    if (pinSmall != null) {
                        selectedPump = "Small";
                    } else if (pinLarge != null) {
                        selectedPump = "Large (fallback)";
                    }
                } else {
                    // Will use LARGE pump
                    if (pinLarge != null) {
                        selectedPump = "Large";
                    }
                }
            }
            
            // Build display string
            StringBuilder display = new StringBuilder();
            boolean hasAnyPin = false;
            
            if (pinLarge != null) {
                hasAnyPin = true;
                String pinDisplay = (pinLarge >= 54 && pinLarge <= 69) ? "A" + (pinLarge - 54) : String.valueOf(pinLarge);
                display.append("L:").append(pinDisplay);
                if (uidLarge != null) {
                    display.append("(").append(uidLarge).append(")");
                }
            }
            
            if (pinSmall != null) {
                if (hasAnyPin) display.append(" / ");
                hasAnyPin = true;
                String pinDisplay = (pinSmall >= 54 && pinSmall <= 69) ? "A" + (pinSmall - 54) : String.valueOf(pinSmall);
                display.append("S:").append(pinDisplay);
                if (uidSmall != null) {
                    display.append("(").append(uidSmall).append(")");
                }
            }
            
            if (!hasAnyPin) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
            
            // Add selected pump info if determined
            if (!selectedPump.isEmpty()) {
                display.append(" → ").append(selectedPump);
            }
            
            return new javafx.beans.property.SimpleStringProperty(display.toString());
        });
        pinCol.setPrefWidth(180);
        
        TableColumn<RecipeIngredient, String> durationCol = new TableColumn<>("Duration (ms)");
        durationCol.setCellValueFactory(cellData -> {
            RecipeIngredient ri = cellData.getValue();
            if (ri == null || ri.getPulseDuration() == null) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
            
            // Calculate scaled duration based on current batch size
            if (batchSizeSpinner != null && recipeCombo.getValue() != null) {
                int desiredBatchSize = batchSizeSpinner.getValue();
                int originalBatchSize = recipeCombo.getValue().getBatchSize() != null ? 
                    recipeCombo.getValue().getBatchSize() : 100;
                double scaleFactor = (double) desiredBatchSize / originalBatchSize;
                double scaledDuration = ri.getPulseDuration() * scaleFactor;
                return new javafx.beans.property.SimpleStringProperty(
                    String.format("%.2f", scaledDuration));
            } else {
                // If batch size not available, show original duration with 2 decimals
                return new javafx.beans.property.SimpleStringProperty(
                    String.format("%.2f", (double) ri.getPulseDuration()));
            }
        });
        durationCol.setPrefWidth(120);
        
        TableColumn<RecipeIngredient, String> formulaCol = new TableColumn<>("Formula Calcul");
        formulaCol.setCellValueFactory(cellData -> {
            RecipeIngredient ri = cellData.getValue();
            if (ri == null) {
                return new javafx.beans.property.SimpleStringProperty("N/A");
            }
            
            // Check if pulseDuration is null
            if (ri.getPulseDuration() == null) {
                String ingredientName = ri.getDisplayName();
                return new javafx.beans.property.SimpleStringProperty(
                    String.format("Durata neconfigurată (%s)", ingredientName));
            }
            
            // Calculate scaled duration and grams based on current batch size
            if (batchSizeSpinner != null && recipeCombo.getValue() != null) {
                int desiredBatchSize = batchSizeSpinner.getValue();
                int originalBatchSize = recipeCombo.getValue().getBatchSize() != null ? 
                    recipeCombo.getValue().getBatchSize() : 100;
                double scaleFactor = (double) desiredBatchSize / originalBatchSize;
                
                // Calculate scaled duration and grams using ingredient's msPerGram configuration
                double scaledDuration = ri.getPulseDuration() * scaleFactor;
                Ingredient ing = ri.getIngredient();
                
                // First calculate base grams using configured msPerGram
                double baseGrams = calculateBaseGramsFromDurationWithConfig(ri, ing);
                double scaledGrams = baseGrams * scaleFactor;
                
                // Now recalculate with scaled duration to get correct ms/g for scaled values
                // Determine which pump will be used for scaled quantity
                Double threshold = (ing != null && ing.getPumpThresholdGrams() != null) ? 
                    ing.getPumpThresholdGrams() : 10.0;
                
                Integer msPerGram = null;
                if (scaledGrams < threshold && ing != null) {
                    // Will use SMALL pump
                    msPerGram = ing.getMsPerGramSmall();
                    if (msPerGram == null) {
                        msPerGram = ing.getMsPerGramLarge(); // Fallback to Large
                    }
                } else if (ing != null) {
                    // Will use LARGE pump
                    msPerGram = ing.getMsPerGramLarge();
                }
                
                // Recalculate grams using configured msPerGram and scaled duration
                if (msPerGram != null && msPerGram > 0) {
                    scaledGrams = scaledDuration / (double) msPerGram;
                }
                
                // Build formula: "duration ms / grams g = ms/g"
                if (scaledGrams > 0.0001) { // Use small threshold instead of > 0
                    double calculatedMsPerGram = scaledDuration / scaledGrams;
                    String formula = String.format("%.2f ms / %.2f g = %.2f ms/g", 
                        scaledDuration, scaledGrams, calculatedMsPerGram);
                    return new javafx.beans.property.SimpleStringProperty(formula);
                } else if (scaledDuration > 0) {
                    // Duration exists but grams are too small
                    String formula = String.format("%.2f ms / %.4f g = N/A", 
                        scaledDuration, scaledGrams);
                    return new javafx.beans.property.SimpleStringProperty(formula);
                }
            }
            
            // Fallback: calculate from original duration using ingredient's msPerGram
            Ingredient ing = ri.getIngredient();
            double baseGrams = calculateBaseGramsFromDurationWithConfig(ri, ing);
            
            if (baseGrams > 0.0001) { // Use small threshold instead of > 0
                double msPerGram = (double) ri.getPulseDuration() / baseGrams;
                String formula = String.format("%.2f ms / %.2f g = %.2f ms/g", 
                    (double) ri.getPulseDuration(), baseGrams, msPerGram);
                return new javafx.beans.property.SimpleStringProperty(formula);
            } else if (ri.getPulseDuration() > 0) {
                // Duration exists but grams are too small
                String formula = String.format("%.2f ms / %.4f g = N/A", 
                    (double) ri.getPulseDuration(), baseGrams);
                return new javafx.beans.property.SimpleStringProperty(formula);
            }
            
            return new javafx.beans.property.SimpleStringProperty("Durata neconfigurată");
        });
        formulaCol.setPrefWidth(250);
        
        TableColumn<RecipeIngredient, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty("Ready"));
        statusCol.setPrefWidth(150);
        
        executionTable.getColumns().addAll(includeCol, orderCol, nameCol, slaveCol, pinCol, durationCol, formulaCol, statusCol);
        
        // Control buttons
        HBox controlButtons = new HBox(10);
        controlButtons.setAlignment(Pos.CENTER);
        controlButtons.setPadding(new Insets(10, 0, 10, 0));
        
        executeButton = new Button("▶ Execute Sequential");
        executeButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 20;");
        executeButton.setDisable(true);
        executeButton.setOnAction(e -> executeRecipe());
        
        executeParallelButton = new Button("⚡ Execute Parallel");
        executeParallelButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 20;");
        executeParallelButton.setDisable(true);
        executeParallelButton.setOnAction(e -> executeRecipeParallel());
        
        stopButton = new Button("⬛ STOP");
        stopButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 20;");
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> stopExecution());
        
        controlButtons.getChildren().addAll(executeButton, executeParallelButton, stopButton);
        
        // Progress
        VBox progressBox = new VBox(5);
        statusLabel = new Label("Ready - Select a recipe to begin");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(25);
        
        progressBox.getChildren().addAll(statusLabel, progressBar);
        
        // Execution log
        Label logLabel = new Label("Execution Log:");
        logLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(320);
        logArea.setStyle("-fx-control-inner-background: #000000; -fx-text-fill: #00FF00; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        logArea.setText("=== MarcmanMixer - Mix Control Log ===\n");
        
        // Add main content with scroll support
        VBox content = new VBox(10,
            title,
            selectionPane,
            planLabel,
            executionTable,
            controlButtons,
            progressBox,
            logLabel,
            logArea
        );
        content.setPadding(new Insets(20));
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        getChildren().add(scrollPane);
        
        setPrefSize(1400, 900);
        setMinSize(1200, 780);
    }
    
    private void loadRecipes() {
        try {
            List<Recipe> recipes = recipeRepository.findAllActive();
            recipeCombo.setItems(FXCollections.observableArrayList(recipes));
            
            if (!recipes.isEmpty()) {
                log("Loaded " + recipes.size() + " recipes");
            } else {
                log("No recipes found. Create recipes in the 'Recipes' tab first.");
            }
            
        } catch (Exception e) {
            log("ERROR: Failed to load recipes: " + e.getMessage());
        }
    }
    
    private void loadRecipeForExecution() {
        Recipe selected = recipeCombo.getValue();
        if (selected == null) {
            ingredientSelectionMap.clear();
            executionTable.getItems().clear();
            executeButton.setDisable(true);
            statusLabel.setText("Ready - Select a recipe to begin");
            return;
        }
        
        // Set default batch size from recipe
        int recipeBatchSize = selected.getBatchSize() != null ? selected.getBatchSize() : 100;
        batchSizeSpinner.getValueFactory().setValue(recipeBatchSize);
        
        log("\n--- Recipe Selected: " + selected.getName() + " ---");
        log("Original batch size: " + recipeBatchSize + " g");
        log("Ingredients: " + selected.getIngredientCount());
        log("Total duration: " + selected.getTotalDuration() + " ms (" + 
            String.format("%.2f", selected.getTotalDuration() / 1000.0) + " seconds)");
        
        // Update calculated info and stock warning will be handled after table population
        
        // Check if all ingredients are configured
        boolean allConfigured = true;
        for (RecipeIngredient ri : selected.getIngredients()) {
            if (ri.getSlaveUid() == null || ri.getArduinoPin() == null) {
                allConfigured = false;
                log("WARNING: " + ri.getDisplayName() + " is NOT configured with SLAVE/PIN!");
            }
            if (ri.getPulseDuration() == null) {
                allConfigured = false;
                log("WARNING: " + ri.getDisplayName() + " has NO pulseDuration configured! Please edit the recipe in the 'Recipes' tab.");
            }
        }
        
        if (!allConfigured) {
            log("ERROR: Some ingredients are not configured. Configure them in 'Ingredients' tab first!");
            showAlert(Alert.AlertType.WARNING, "Configuration Required", 
                     "Some ingredients are not configured with SLAVE/PIN.\n\n" +
                     "Go to 'Ingredients' tab and configure them first.");
        }
        
        // Load to table
        List<RecipeIngredient> ingredientsForTable = new ArrayList<>(selected.getIngredients());
        ingredientSelectionMap.clear();
        for (RecipeIngredient ri : ingredientsForTable) {
            ingredientSelectionMap.put(ri, createSelectionProperty(ri));
        }
        executionTable.setItems(FXCollections.observableArrayList(ingredientsForTable));
        executionTable.refresh();
        
        updateCalculatedInfo();
        // Update stock warning which will also handle button enable/disable
        updateStockWarning();
        
        if (!serialManager.isConnected()) {
            statusLabel.setText("CONNECT to Arduino MASTER first! (Arduino MASTER tab)");
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            executeButton.setDisable(true);
            executeParallelButton.setDisable(true);
        } else if (!allConfigured) {
            statusLabel.setText("Some ingredients not configured - check Ingredients tab");
            statusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            // Buttons will be disabled by updateStockWarning if needed
        } else {
            statusLabel.setText("Ready to execute: " + selected.getName());
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            // Buttons will be enabled by updateStockWarning if stock is sufficient
        }
    }
    
    private void executeRecipe() {
        Recipe selected = recipeCombo.getValue();
        if (selected == null || !serialManager.isConnected()) {
            return;
        }
        
        // Check stock availability BEFORE execution
        int desiredBatchSize = batchSizeSpinner.getValue();
        
        List<RecipeIngredient> selectedIngredients = getSelectedIngredients();
        if (selectedIngredients.isEmpty()) {
            stockInfoPanel.setVisible(true);
            stockInfoPanel.setManaged(true);
            stockInfoPanel.setStyle("-fx-background-color: #FFF3E0; -fx-border-color: #FB8C00; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
            stockStatusLabel.setText("⚠️ Niciun ingredient selectat\n\nSelectează cel puțin un ingredient din listă pentru a putea fabrica rețeta.");
            stockStatusLabel.setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
            stockWarningLabel.setVisible(false);
            stockWarningLabel.setManaged(false);
            stockWarningLabel.setText("");
            executeButton.setDisable(true);
            executeParallelButton.setDisable(true);
            return;
        }
        
        List<String> insufficientStock = checkStockAvailability(selected, desiredBatchSize);
        if (insufficientStock != null) {
            StringBuilder message = new StringBuilder("⚠️ INSUFFICIENT STOCK!\n\n");
            message.append(String.format("Cannot produce %d g of final product.\n\n", desiredBatchSize));
            message.append("The following ingredients have insufficient stock:\n\n");
            for (String item : insufficientStock) {
                message.append("• ").append(item).append("\n");
            }
            message.append("\nPlease update stock quantities in the 'Ingredients' tab before executing.");
            
            log("\n========================================");
            log(String.format("ERROR: INSUFFICIENT STOCK FOR %d g BATCH!", desiredBatchSize));
            log("========================================");
            for (String item : insufficientStock) {
                log("  " + item);
            }
            
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Insufficient Stock", message.toString());
                statusLabel.setText("Execution blocked - Insufficient stock");
                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            });
            return;
        }
        
        executing = true;
        executeButton.setDisable(true);
        executeParallelButton.setDisable(true);
        stopButton.setDisable(false);
        progressBar.setProgress(0);
        
        log("\n========================================");
        log(String.format("STOCK CHECK: PASSED for %d g batch", desiredBatchSize));
        log("========================================");
        log("\n========================================");
        log("STARTING RECIPE EXECUTION: " + selected.getName());
        log(String.format("Final product quantity: %d g", desiredBatchSize));
        log("========================================");
        ExecutionEstimates estimates = calculateExecutionEstimates(selected, desiredBatchSize);
        if (estimates.sequentialMs > 0 || estimates.parallelMs > 0) {
            if (estimates.sequentialMs > 0) {
                log("Estimated total runtime (sequential): " + formatDuration(estimates.sequentialMs));
            }
            if (estimates.parallelMs > 0) {
                log("Estimated total runtime (parallel): " + formatDuration(estimates.parallelMs));
            }
        }
        
        List<RecipeIngredient> ingredientsToExecute = new ArrayList<>(selectedIngredients);
        
        // Execute in background thread to not block UI
        new Thread(() -> {
            try {
                List<RecipeIngredient> ingredients = ingredientsToExecute;
                int totalSteps = ingredients.size();
                
                // Calculate scale factor for this batch
                int originalBatchSize = selected.getBatchSize() != null ? selected.getBatchSize() : 100;
                double scaleFactor = (double) desiredBatchSize / originalBatchSize;
                
                log(String.format("Scale factor: %.2f (producing %d g from %d g recipe)", 
                    scaleFactor, desiredBatchSize, originalBatchSize));
                
                // Ensure any previous batches are cleared
                serialManager.sendRawCommand("batchabort");

                for (int i = 0; i < ingredients.size(); i++) {
                    if (!executing) {
                        Platform.runLater(() -> log("EXECUTION STOPPED BY USER"));
                        break;
                    }
                    
                    RecipeIngredient ri = ingredients.get(i);
                    final int currentStep = i + 1;
                    
                    Platform.runLater(() -> {
                        statusLabel.setText(String.format("Executing step %d/%d: %s", 
                            currentStep, totalSteps, ri.getDisplayName()));
                        progressBar.setProgress((double) currentStep / totalSteps);
                    });
                    
                    // Execute ingredient with scaled duration via batch protocol
                    boolean stepOk = executeIngredient(ri, currentStep, totalSteps, scaleFactor);
                    if (!stepOk) {
                        Platform.runLater(() -> {
                            statusLabel.setText(String.format("Step %d FAILED - execution halted", currentStep));
                            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        });
                        executing = false;
                        serialManager.sendRawCommand("batchabort");
                        break;
                    }
                    
                }
                
                if (executing) {
                    // Consume stock AFTER successful execution
                    final int finalDesiredBatch = desiredBatchSize;
                    consumeStock(selected, ingredients, finalDesiredBatch);
                    
                    Platform.runLater(() -> {
                        log("\n========================================");
                        log("RECIPE EXECUTION COMPLETE!");
                        log("========================================");
                        statusLabel.setText("Execution complete: " + selected.getName());
                        statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        progressBar.setProgress(1.0);
                        
                        showAlert(Alert.AlertType.INFORMATION, "Success", 
                                 String.format("Recipe '%s' executed successfully!\n\n" +
                                 "Produced: %d g of final product\n" +
                                 "Stock quantities have been updated.", 
                                 selected.getName(), finalDesiredBatch));
                    });
                }
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log("ERROR: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Execution Error", 
                             "Failed to execute recipe: " + e.getMessage());
                });
            } finally {
                Platform.runLater(() -> {
                    executing = false;
                    executeButton.setDisable(false);
                    executeParallelButton.setDisable(false);
                    stopButton.setDisable(true);
                });
            }
        }).start();
    }
    
    private boolean executeIngredient(RecipeIngredient ri, int step, int total, double scaleFactor) {
        String ingredientName = ri.getDisplayName();
        
        log(String.format("\n[Step %d/%d] %s", step, total, ingredientName));
        
        String slaveUid = ri.getSlaveUid();
        Integer pinLarge = ri.getArduinoPin();
        Integer pinSmall = ri.getIngredient() != null ? ri.getIngredient().getArduinoPinSmall() : null;
        Integer baseDuration = ri.getPulseDuration();
        
        log("  SLAVE: " + (slaveUid != null ? slaveUid : "N/A"));
        
        if (slaveUid == null || baseDuration == null) {
            log("  ERROR: Ingredient not properly configured!");
            return false;
        }
        
        // Scale duration based on desired batch size
        int scaledDuration = (int) Math.round(baseDuration * scaleFactor);
        
        // Calculate grams using ingredient's msPerGram configuration
        RecipeIngredient tempRi = RecipeIngredient.builder()
            .pulseDuration(baseDuration)
            .ingredient(ri.getIngredient())
            .build();
        double baseGrams = calculateBaseGramsFromDurationWithConfig(tempRi, ri.getIngredient());
        double scaledGrams = baseGrams * scaleFactor;
        
        log(String.format("  Base Duration: %d ms (%.2f g)", baseDuration, baseGrams));
        log(String.format("  Scaled Duration: %d ms (%.2f g) [scale: %.2f]", scaledDuration, scaledGrams, scaleFactor));
        
        // ⚠️ DUAL PUMP SELECTION: Choose pump based on quantity
        Integer selectedPin = null;
        String pumpType = "";
        
        // Get threshold from ingredient configuration
        Double threshold = ri.getIngredient() != null && ri.getIngredient().getPumpThresholdGrams() != null ?
            ri.getIngredient().getPumpThresholdGrams() : 10.0;
        
        if (scaledGrams < threshold) {
            // Use SMALL pump for quantities below threshold
            if (pinSmall != null) {
                selectedPin = pinSmall;
                pumpType = "SMALL";
                log(String.format("  🔸 AUTO-SELECT: SMALL pump (quantity %.2f g < %.2f g threshold)", scaledGrams, threshold));
            } else if (pinLarge != null) {
                // Fallback to large pump if small not configured
                selectedPin = pinLarge;
                pumpType = "LARGE (fallback)";
                log(String.format("  ⚠️ WARNING: SMALL pump not configured, using LARGE pump for %.2f g", scaledGrams));
            } else {
                log("  ERROR: No pump configured for this ingredient!");
                return false;
            }
        } else {
            // Use LARGE pump for quantities >= 10g
            if (pinLarge != null) {
                selectedPin = pinLarge;
                pumpType = "LARGE";
                log(String.format("  🔹 AUTO-SELECT: LARGE pump (quantity %.2f g >= 10g)", scaledGrams));
            } else {
                log("  ERROR: LARGE pump not configured!");
                return false;
            }
        }
        
        String pinDisplay = "N/A";
        if (selectedPin != null) {
            pinDisplay = (selectedPin >= 54 && selectedPin <= 69) ? "A" + (selectedPin - 54) : String.valueOf(selectedPin);
        }
        log(String.format("  PIN: %s (%d) - %s PUMP", pinDisplay, selectedPin, pumpType));
        
        if (scaledDuration <= 0) {
            log("  WARNING: Scaled duration <= 0ms. Skipping command.");
            return true;
        }
        
        String uidFormatted = normalizeUid(slaveUid);
        int remaining = scaledDuration;
        int partIndex = 1;
        int totalParts = (int) Math.ceil((double) scaledDuration / MAX_DURATION_PER_COMMAND);
        
        while (remaining > 0 && executing) {
            int chunkDuration = Math.min(remaining, MAX_DURATION_PER_COMMAND);
            String suffix = totalParts > 1 ? String.format(" (part %d/%d)", partIndex, totalParts) : "";
            String batchPrepCommand = String.format("batchprep %s %d:%d", uidFormatted, selectedPin, chunkDuration);
            
            log(String.format("  [BATCH] PREPARE%s -> %s", suffix, batchPrepCommand));
            boolean prepSent = serialManager.sendRawCommand(batchPrepCommand);
            if (!prepSent) {
                log("  ERROR: Failed to send batchprep command!");
                return false;
            }
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) { }
            
            log("  [BATCH] EXECUTE -> batchrun");
            boolean runSent = serialManager.sendRawCommand("batchrun");
            if (!runSent) {
                log("  ERROR: Failed to send batchrun command! Aborting lot.");
                serialManager.sendRawCommand("batchabort");
                return false;
            }
            
            log(String.format("  Status: Segment executat (%d ms)%s", chunkDuration, suffix));
            
            try {
                Thread.sleep(chunkDuration + 200);
            } catch (InterruptedException ignored) { }
            
            remaining -= chunkDuration;
            partIndex++;
        }
        
        return true;
    }
    
    private void executeRecipeParallel() {
        Recipe selected = recipeCombo.getValue();
        if (selected == null || selected.getIngredients() == null || selected.getIngredients().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Recipe", "Please select a recipe with ingredients.");
            return;
        }
        
        if (serialManager == null || !serialManager.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Not Connected", "Please connect to Arduino MASTER first!");
            return;
        }
        
        List<RecipeIngredient> selectedIngredients = getSelectedIngredients();
        if (selectedIngredients.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Ingredients Selected", "Select at least one ingredient before executing.");
            return;
        }
        
        // Check stock availability BEFORE execution
        int desiredBatchSize = batchSizeSpinner.getValue();
        List<String> insufficientStock = checkStockAvailability(selected, desiredBatchSize);
        if (insufficientStock != null) {
            StringBuilder message = new StringBuilder("⚠️ INSUFFICIENT STOCK!\n\n");
            message.append(String.format("Cannot produce %d g of final product.\n\n", desiredBatchSize));
            message.append("The following ingredients have insufficient stock:\n\n");
            for (String item : insufficientStock) {
                message.append("• ").append(item).append("\n");
            }
            message.append("\nPlease update stock quantities in the 'Ingredients' tab before executing.");
            
            logArea.clear();
            log("\n========================================");
            log(String.format("ERROR: INSUFFICIENT STOCK FOR %d g BATCH!", desiredBatchSize));
            log("========================================");
            for (String item : insufficientStock) {
                log("  " + item);
            }
            
            showAlert(Alert.AlertType.ERROR, "Insufficient Stock", message.toString());
            statusLabel.setText("Execution blocked - Insufficient stock");
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            return;
        }
        
        List<RecipeIngredient> ingredients = selectedIngredients.stream()
            .sorted(Comparator.comparingInt(ri -> ri.getSequenceOrder() != null ? ri.getSequenceOrder() : 0))
            .collect(Collectors.toList());
        
        // Disable buttons
        executeButton.setDisable(true);
        executeParallelButton.setDisable(true);
        stopButton.setDisable(false);
        executing = true;
        
        // Clear log
        logArea.clear();
        log("========================================");
        log(String.format("STOCK CHECK: PASSED for %d g batch", desiredBatchSize));
        log("========================================");
        log("========================================");
        log("PARALLEL RECIPE EXECUTION: " + selected.getName());
        log(String.format("Final product quantity: %d g", desiredBatchSize));
        log("========================================");
        log("Mode: ALL INGREDIENTS SIMULTANEOUSLY");
        log(String.format("Ingredients: %d", ingredients.size()));
        log(String.format("Max duration: %d ms", ingredients.stream()
            .mapToInt(ri -> ri.getPulseDuration() != null ? ri.getPulseDuration() : 0)
            .max().orElse(0)));
        log("========================================\n");
        
        // Execute in background thread
        new Thread(() -> {
            try {
                // Calculate scale factor for this batch
                int originalBatchSize = selected.getBatchSize() != null ? selected.getBatchSize() : 100;
                double scaleFactor = (double) desiredBatchSize / originalBatchSize;
                
                log(String.format("Scale factor: %.2f (producing %d g from %d g recipe)", 
                    scaleFactor, desiredBatchSize, originalBatchSize));
                ExecutionEstimates estimates = calculateExecutionEstimates(selected, desiredBatchSize);
                if (estimates.parallelMs > 0 || estimates.sequentialMs > 0) {
                    if (estimates.parallelMs > 0) {
                        log("Estimated total runtime (parallel): " + formatDuration(estimates.parallelMs));
                    }
                    if (estimates.sequentialMs > 0) {
                        log("Equivalent sequential runtime: " + formatDuration(estimates.sequentialMs));
                    }
                }
                log(String.format("Preparing BATCH commands (grouped by UID, max %d per batch)...\n", MAX_BATCH_SIZE));
                serialManager.sendRawCommand("batchabort");
                
                // Helper class to store command data
                class CommandData {
                    RecipeIngredient ingredient;
                    String slaveUid;
                    int pin;
                    int duration;
                    String ingredientName;
                    
                    CommandData(RecipeIngredient ri, String uid, int p, int d, String name) {
                        ingredient = ri;
                        slaveUid = uid;
                        pin = p;
                        duration = d;
                        ingredientName = name;
                    }
                }
                
                // Step 1: Prepare all commands and group by UID
                List<CommandData> allCommands = new ArrayList<>();
                
                for (RecipeIngredient ri : ingredients) {
                    if (!executing) break;
                    
                    String ingredientName = ri.getDisplayName();
                    String slaveUid = ri.getSlaveUid();
                    Integer pinLarge = ri.getArduinoPin();
                    Integer pinSmall = ri.getIngredient() != null ? ri.getIngredient().getArduinoPinSmall() : null;
                    Integer baseDuration = ri.getPulseDuration();
                    
                    if (slaveUid == null || baseDuration == null) {
                        log(String.format("⚠️ SKIP: %s (not configured)", ingredientName));
                        continue;
                    }
                    
                    // Scale duration
                    int scaledDuration = (int) Math.round(baseDuration * scaleFactor);
                    
                    // Calculate grams for pump selection
                    RecipeIngredient tempRi = RecipeIngredient.builder()
                        .pulseDuration(baseDuration)
                        .ingredient(ri.getIngredient())
                        .build();
                    double baseGrams = calculateBaseGramsFromDurationWithConfig(tempRi, ri.getIngredient());
                    double scaledGrams = baseGrams * scaleFactor;
                    
                    // Select pump based on quantity
                    Integer selectedPin = null;
                    Double threshold = ri.getIngredient() != null && ri.getIngredient().getPumpThresholdGrams() != null ?
                        ri.getIngredient().getPumpThresholdGrams() : 10.0;
                    
                    if (scaledGrams < threshold) {
                        selectedPin = pinSmall != null ? pinSmall : pinLarge;
                    } else {
                        selectedPin = pinLarge;
                    }
                    
                    if (selectedPin == null) {
                        log(String.format("⚠️ SKIP: %s (no pump configured)", ingredientName));
                        continue;
                    }
                    
                    if (scaledDuration <= 0) {
                        log(String.format("⚠️ SKIP: %s (scaled duration <= 0)", ingredientName));
                        continue;
                    }
                    
                    int remaining = scaledDuration;
                    int partIndex = 1;
                    int totalParts = (int) Math.ceil((double) scaledDuration / MAX_DURATION_PER_COMMAND);
                    while (remaining > 0) {
                        int chunkDuration = Math.min(remaining, MAX_DURATION_PER_COMMAND);
                        String namePart = totalParts > 1
                            ? String.format("%s [part %d/%d]", ingredientName, partIndex, totalParts)
                            : ingredientName;
                        allCommands.add(new CommandData(ri, slaveUid, selectedPin, chunkDuration, namePart));
                        remaining -= chunkDuration;
                        partIndex++;
                    }
                }
                
                // Step 2: Group commands by UID
                Map<String, List<CommandData>> commandsByUid = allCommands.stream()
                    .collect(Collectors.groupingBy(cmd -> cmd.slaveUid));
                
                log(String.format("Grouped %d commands into %d UID groups\n", allCommands.size(), commandsByUid.size()));
                
                // Step 3: Send batch commands (max 64 per batch)
                int totalBatches = 0;
                int sentCount = 0;
                int maxDuration = 0;
                
                for (Map.Entry<String, List<CommandData>> entry : commandsByUid.entrySet()) {
                    if (!executing) break;
                    
                    String uid = entry.getKey();
                    List<CommandData> uidCommands = entry.getValue();
                    
                    log(String.format("UID %s: %d commands", uid, uidCommands.size()));
                    
                    // Split into batches of max MAX_BATCH_SIZE
                    // Example: 120 commands → Batch 1 (64 commands) + Batch 2 (56 commands)
                    int numBatchesForUid = (uidCommands.size() + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;
                    log(String.format("  Will split into %d batch(es) of max %d commands each", 
                        numBatchesForUid, MAX_BATCH_SIZE));
                    
                    for (int batchStart = 0; batchStart < uidCommands.size(); batchStart += MAX_BATCH_SIZE) {
                        if (!executing) break;
                        
                        int batchEnd = Math.min(batchStart + MAX_BATCH_SIZE, uidCommands.size());
                        List<CommandData> batch = uidCommands.subList(batchStart, batchEnd);
                        
                        totalBatches++;
                        final int batchNumForUid = (batchStart / MAX_BATCH_SIZE) + 1;
                        final int numBatchesForUidFinal = numBatchesForUid;
                        log(String.format("  Batch %d/%d for UID %s: %d commands (items %d-%d)", 
                            batchNumForUid, numBatchesForUidFinal, uid, batch.size(), batchStart, batchEnd - 1));
                        
                        // Build batch command: batchprep <uid> <pin1>:<duration1> ...
                        String uidFormatted = normalizeUid(uid);
                        StringBuilder batchCmd = new StringBuilder("batchprep " + uidFormatted);

                        for (CommandData cmd : batch) {
                            batchCmd.append(" ").append(cmd.pin).append(":").append(cmd.duration);
                            sentCount++;
                            if (cmd.duration > maxDuration) {
                                maxDuration = cmd.duration;
                            }
                            log(String.format("    - %s: PIN %d, %d ms", cmd.ingredientName, cmd.pin, cmd.duration));
                        }

                        // Send batch preparation command
                        String command = batchCmd.toString();
                        log(String.format("  Command: %s", command));

                        boolean sent = serialManager.sendRawCommand(command);
                        if (sent) {
                            log(String.format("  ✅ Lot %d/%d pregătit (%d comenzi)",
                                batchNumForUid, numBatchesForUidFinal, batch.size()));
                        } else {
                            log(String.format("  ❌ Pregătirea lotului %d/%d a eșuat!",
                                batchNumForUid, numBatchesForUidFinal));
                            serialManager.sendRawCommand("batchabort");
                            executing = false;
                            Platform.runLater(() -> {
                                statusLabel.setText("Batch preparation failed");
                                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            });
                            return;
                        }

                        Thread.sleep(50);

                        // Update progress
                        final int currentBatches = totalBatches;
                        final int totalItems = allCommands.size();
                        final int sentCountFinal = sentCount;
                        Platform.runLater(() -> {
                            double progress = Math.min((double) sentCountFinal / totalItems, 1.0);
                            progressBar.setProgress(progress);
                            statusLabel.setText(String.format("Sent batch %d (%d/%d commands)...", 
                                currentBatches, sentCountFinal, totalItems));
                        });
                    }
                }
                
                if (executing) {
                    log(">>> Execut batchrun (execuție simultană)...");
                    boolean runSent = serialManager.sendRawCommand("batchrun");
                    if (!runSent) {
                        log("❌ batchrun a eșuat - trimit batchabort");
                        serialManager.sendRawCommand("batchabort");
                        executing = false;
                        Platform.runLater(() -> {
                            statusLabel.setText("Batchrun failed");
                            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        });
                        return;
                    }
                    Thread.sleep(100);
                }

                if (executing) {
                    log(String.format("\n========================================"));
                    log(String.format("ALL COMMANDS SENT! (%d commands)", sentCount));
                    log(String.format("Max pumping time: %d ms (%.2f seconds)", maxDuration, maxDuration/1000.0));
                    log("========================================");
                    log("Waiting for longest pump to complete...");
                    
                    // Wait for the longest duration to complete
                    Thread.sleep(maxDuration + 500);
                    
                    // Consume stock AFTER successful execution
                    final int finalDesiredBatch = desiredBatchSize;
                    consumeStock(selected, ingredients, finalDesiredBatch);
                    
                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        statusLabel.setText("Parallel execution complete!");
                        statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    });
                    
                    log("\n========================================");
                    log("PARALLEL EXECUTION COMPLETE!");
                    log(String.format("Produced: %d g of final product", finalDesiredBatch));
                    log("========================================");
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Execution stopped");
                        statusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                    });
                }
                
            } catch (InterruptedException e) {
                log("ERROR: Execution interrupted: " + e.getMessage());
            } finally {
                executing = false;
                Platform.runLater(() -> {
                    executeButton.setDisable(false);
                    executeParallelButton.setDisable(false);
                    stopButton.setDisable(true);
                });
            }
        }).start();
    }
    
    private void stopExecution() {
        executing = false;
        log("\nSTOP requested - halting execution...");
        statusLabel.setText("Execution stopped");
        statusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        if (serialManager != null && serialManager.isConnected()) {
            serialManager.sendRawCommand("batchabort");
        }
    }
    
    private static class ExecutionEstimates {
        long sequentialMs;
        long parallelMs;
    }

    private ExecutionEstimates calculateExecutionEstimates(Recipe recipe, int desiredBatchSize) {
        ExecutionEstimates estimates = new ExecutionEstimates();
        if (recipe == null || recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            return estimates;
        }
        
        List<RecipeIngredient> ingredientsToConsider = recipe.getIngredients().stream()
            .filter(this::isIngredientSelected)
            .collect(Collectors.toList());
        if (ingredientsToConsider.isEmpty()) {
            return estimates;
        }
        
        int originalBatchSize = recipe.getBatchSize() != null ? recipe.getBatchSize() : 100;
        double scaleFactor = (double) desiredBatchSize / originalBatchSize;
        
        Map<String, List<Integer>> durationsByUid = new HashMap<>();
        int segmentCount = 0;
        
        for (RecipeIngredient ri : ingredientsToConsider) {
            Integer baseDuration = ri.getPulseDuration();
            String uid = ri.getSlaveUid();
            if (baseDuration == null || uid == null) {
                continue;
            }
            int scaled = (int) Math.round(baseDuration * scaleFactor);
            if (scaled < 0) {
                scaled = 0;
            }
            if (scaled == 0) continue;

            int remaining = scaled;
            while (remaining > 0) {
                int chunk = Math.min(remaining, MAX_DURATION_PER_COMMAND);
                estimates.sequentialMs += chunk;
                durationsByUid.computeIfAbsent(uid, k -> new ArrayList<>()).add(chunk);
                remaining -= chunk;
                segmentCount++;
            }
        }

        if (segmentCount > 0) {
            estimates.sequentialMs += (long) segmentCount * 200L;
        }

        long maxParallel = 0;
        for (List<Integer> durations : durationsByUid.values()) {
            if (durations.isEmpty()) {
                continue;
            }
            long uidTotal = 0;
            for (int i = 0; i < durations.size(); i += MAX_BATCH_SIZE) {
                int end = Math.min(i + MAX_BATCH_SIZE, durations.size());
                int batchMax = durations.subList(i, end).stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
                uidTotal += batchMax;
            }
            maxParallel = Math.max(maxParallel, uidTotal);
        }
        estimates.parallelMs = maxParallel;
        return estimates;
    }
    
    private String formatDuration(long ms) {
        if (ms <= 0) return "0 s";
        double seconds = ms / 1000.0;
        if (seconds < 60) {
            return String.format("%.1f s", seconds);
        }
        double minutes = seconds / 60.0;
        if (minutes < 60) {
            return String.format("%.1f min", minutes);
        }
        double hours = minutes / 60.0;
        return String.format("%.2f h", hours);
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        IconSupport.applyTo(alert);
        alert.showAndWait();
    }
    
    public void setSerialManager(SerialManager serialManager) {
        this.serialManager = serialManager;
        // Reload recipe if one is selected
        if (recipeCombo.getValue() != null) {
            loadRecipeForExecution();
        }
    }
    
    /**
     * Check if there is enough stock for all ingredients in the recipe
     * @param recipe The recipe to check
     * @param desiredBatchSize The desired final product quantity in grams
     * @return null if stock is sufficient, otherwise a list of insufficient ingredients
     */
    private List<String> checkStockAvailability(Recipe recipe, int desiredBatchSize) {
        List<String> insufficient = new ArrayList<>();
        
        int originalBatchSize = recipe.getBatchSize() != null ? recipe.getBatchSize() : 100;
        double scaleFactor = (double) desiredBatchSize / originalBatchSize;
        
        log(String.format("Stock check - Original batch: %d g, Desired: %d g, Scale factor: %.2f", 
            originalBatchSize, desiredBatchSize, scaleFactor));
        
        for (RecipeIngredient ri : recipe.getIngredients()) {
            if (!isIngredientSelected(ri)) {
                continue;
            }
            try {
                // Load ingredient from database to get current stock
                Ingredient ingredient = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
                if (ingredient == null) {
                    insufficient.add(ri.getDisplayName() + " (NOT FOUND in database)");
                    continue;
                }
                
                // Calculate required grams for this ingredient (scaled) using msPerGram configuration
                double baseGrams = calculateBaseGramsFromDurationWithConfig(ri, ingredient);
                double requiredGrams = baseGrams * scaleFactor;
                
                double availableStock = ingredient.getStockQuantity() != null ? ingredient.getStockQuantity() : 0.0;
                
                if (availableStock < requiredGrams) {
                    insufficient.add(String.format("%s (Need: %.2f g, Available: %.2f g, Missing: %.2f g)",
                        ri.getDisplayName(), requiredGrams, availableStock, requiredGrams - availableStock));
                }
                
            } catch (Exception e) {
                insufficient.add(ri.getDisplayName() + " (ERROR: " + e.getMessage() + ")");
            }
        }
        
        return insufficient.isEmpty() ? null : insufficient;
    }
    
    /**
     * Consume stock for all ingredients in the recipe after successful execution
     * @param recipe The recipe that was executed
     * @param desiredBatchSize The actual quantity produced in grams
     */
    private void consumeStock(Recipe recipe, List<RecipeIngredient> executedIngredients, int desiredBatchSize) {
        log("\n--- Updating stock quantities ---");
        
        if (executedIngredients == null || executedIngredients.isEmpty()) {
            log("No ingredients selected for execution; skipping stock update.");
            return;
        }
        
        int originalBatchSize = recipe.getBatchSize() != null ? recipe.getBatchSize() : 100;
        double scaleFactor = (double) desiredBatchSize / originalBatchSize;
        
        log(String.format("Consuming stock for %d g final product (scale factor: %.2f)", 
            desiredBatchSize, scaleFactor));
        
        for (RecipeIngredient ri : executedIngredients) {
            try {
                // Load ingredient from database
                Ingredient ingredient = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
                if (ingredient == null) {
                    log("WARNING: Ingredient " + ri.getDisplayName() + " not found in database");
                    continue;
                }
                
                // Calculate consumed grams (scaled) using msPerGram configuration
                double baseGrams = calculateBaseGramsFromDurationWithConfig(ri, ingredient);
                double consumedGrams = baseGrams * scaleFactor;
                
                double currentStock = ingredient.getStockQuantity() != null ? ingredient.getStockQuantity() : 0.0;
                double newStock = currentStock - consumedGrams;
                
                // Don't allow negative stock (should have been caught by checkStockAvailability)
                if (newStock < 0) {
                    newStock = 0;
                }
                
                ingredient.setStockQuantity(newStock);
                ingredientRepository.save(ingredient);
                
                log(String.format("  %s: %.2f g -> %.2f g (consumed %.2f g)",
                    ri.getDisplayName(), currentStock, newStock, consumedGrams));
                
            } catch (Exception e) {
                log("ERROR updating stock for " + ri.getDisplayName() + ": " + e.getMessage());
            }
        }
        
        log("Stock quantities updated successfully!");
    }
    
    private void updateCalculatedInfo() {
        Recipe selected = recipeCombo.getValue();
        if (selected == null) {
            calculatedInfoLabel.setText("");
            executionTimeLabel.setText("");
            return;
        }
        
        int desiredBatch = batchSizeSpinner.getValue();
        int originalBatch = selected.getBatchSize() != null ? selected.getBatchSize() : 100;
        double scaleFactor = (double) desiredBatch / originalBatch;
        
        StringBuilder info = new StringBuilder();
        if (Math.abs(scaleFactor - 1.0) < 0.01) {
            info.append("Original recipe size");
        } else {
            info.append(String.format("%.1f%% of original %d g recipe", 
                scaleFactor * 100, originalBatch));
        }
        
        ExecutionEstimates estimates = calculateExecutionEstimates(selected, desiredBatch);
        List<String> runtimeParts = new ArrayList<>();
        if (getSelectedIngredients().isEmpty()) {
            executionTimeLabel.setText("Selectează ingrediente pentru a calcula durata execuției.");
            return;
        }
        if (estimates.sequentialMs > 0) {
            runtimeParts.add("Sequential: " + formatDuration(estimates.sequentialMs));
        }
        if (estimates.parallelMs > 0) {
            runtimeParts.add("Parallel: " + formatDuration(estimates.parallelMs));
        }
        
        calculatedInfoLabel.setText(info.toString());
        if (!runtimeParts.isEmpty()) {
            executionTimeLabel.setText(String.join(" | ", runtimeParts));
        } else {
            executionTimeLabel.setText("");
        }
    }
    
    /**
     * Update stock status panel and warning label based on current recipe and batch size
     * Also controls execute button state based on stock availability and configuration
     * Displays real-time stock information
     */
    private void updateStockWarning() {
        Recipe selected = recipeCombo.getValue();
        if (selected == null || batchSizeSpinner == null) {
            stockInfoPanel.setVisible(false);
            stockInfoPanel.setManaged(false);
            stockWarningLabel.setVisible(false);
            stockWarningLabel.setManaged(false);
            stockStatusLabel.setText("");
            stockWarningLabel.setText("");
            return;
        }
        
        // First check if all ingredients are configured
        boolean allConfigured = true;
        for (RecipeIngredient ri : selected.getIngredients()) {
            if (ri.getSlaveUid() == null || ri.getArduinoPin() == null) {
                allConfigured = false;
                break;
            }
        }
        
        // Check if serial is connected
        boolean isConnected = serialManager != null && serialManager.isConnected();
        
        // Check stock availability
        int desiredBatchSize = batchSizeSpinner.getValue();
        
        List<RecipeIngredient> selectedIngredients = getSelectedIngredients();
        if (selectedIngredients.isEmpty()) {
            stockInfoPanel.setVisible(true);
            stockInfoPanel.setManaged(true);
            stockInfoPanel.setStyle("-fx-background-color: #FFF3E0; -fx-border-color: #FB8C00; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
            stockStatusLabel.setText("⚠️ Niciun ingredient selectat\n\nSelectează cel puțin un ingredient din listă pentru a putea fabrica rețeta.");
            stockStatusLabel.setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
            stockWarningLabel.setVisible(false);
            stockWarningLabel.setManaged(false);
            stockWarningLabel.setText("");
            executeButton.setDisable(true);
            executeParallelButton.setDisable(true);
            return;
        }
        
        List<String> insufficientStock = checkStockAvailability(selected, desiredBatchSize);
        
        // Calculate max producible quantity (always calculate this for display)
        int maxProducible = calculateMaxProducibleQuantity(selected);
        
        // Always show stock info panel when recipe is selected
        stockInfoPanel.setVisible(true);
        stockInfoPanel.setManaged(true);
        stockInfoPanel.setMinHeight(VBox.USE_PREF_SIZE);
        
        // Force layout update
        stockInfoPanel.requestLayout();
        
        if (insufficientStock == null || insufficientStock.isEmpty()) {
            // Stock is sufficient - show positive status
            StringBuilder statusText = new StringBuilder();
            statusText.append("✅ STOC SUFICIENT\n\n");
            statusText.append(String.format("Cantitate cerută: %d g\n", desiredBatchSize));
            statusText.append(String.format("Stoc disponibil: SUFICIENT pentru cantitatea cerută\n\n"));
            // Always show max producible, even if 0
            statusText.append(String.format("Cantitate maximă fabricabilă: %d g\n", maxProducible));
            if (maxProducible > 0) {
                statusText.append("(din stocul actual al tuturor ingredientelor)");
            } else {
                statusText.append("(nu există stoc suficient sau ingredientele nu au durată configurată)");
            }
            
            String finalStatusText = statusText.toString();
            
            // Set text and style directly (we're already on JavaFX thread)
            stockStatusLabel.setText(finalStatusText);
            stockStatusLabel.setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
            stockInfoPanel.setStyle("-fx-background-color: #E8F5E9; -fx-border-color: #4CAF50; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
            
            // Hide detailed warning
            stockWarningLabel.setVisible(false);
            stockWarningLabel.setManaged(false);
            stockWarningLabel.setText("");
            
            // Enable execute buttons only if connected and all configured
            boolean canExecute = isConnected && allConfigured;
            executeButton.setDisable(!canExecute);
            executeParallelButton.setDisable(!canExecute);
        } else {
            // Stock is insufficient - show warning status
            StringBuilder statusText = new StringBuilder();
            statusText.append("⚠️ STOC INSUFICIENT\n\n");
            statusText.append(String.format("Cantitate cerută: %d g\n", desiredBatchSize));
            statusText.append(String.format("Stoc disponibil: INSUFICIENT pentru cantitatea cerută\n\n"));
            // Always show max producible
            statusText.append(String.format("Cantitate maximă fabricabilă: %d g\n", maxProducible));
            if (maxProducible > 0) {
                statusText.append("(din stocul actual al ingredientelor)\n\n");
            } else {
                statusText.append("(nu există stoc suficient pentru niciun ingredient)\n\n");
            }
            
            // Add insufficient ingredients list to main panel
            if (insufficientStock != null && !insufficientStock.isEmpty()) {
                statusText.append("Ingrediente cu stoc insuficient:\n");
                for (String item : insufficientStock) {
                    statusText.append("  • ").append(item).append("\n");
                }
            }
            
            String finalStatusText = statusText.toString();
            
            // Set text and style directly (we're already on JavaFX thread)
            stockStatusLabel.setText(finalStatusText);
            stockStatusLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-weight: bold;");
            stockInfoPanel.setStyle("-fx-background-color: #FFEBEE; -fx-border-color: #F44336; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
            
            // Show detailed warning with additional info
            StringBuilder warningText = new StringBuilder();
            warningText.append("⚠️ ATENȚIE: Actualizează stocurile în tab-ul 'Ingredients' înainte de execuție.\n\n");
            warningText.append("Cantitatea cerută nu poate fi produsă cu stocul actual.\n");
            warningText.append("Vezi lista de ingrediente de mai sus pentru detalii.");
            
            stockWarningLabel.setText(warningText.toString());
            stockWarningLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #C62828; -fx-font-weight: bold; " +
                "-fx-background-color: #FFEBEE; -fx-padding: 10px; -fx-border-color: #F44336; -fx-border-width: 1px; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px;");
            stockWarningLabel.setVisible(true);
            stockWarningLabel.setManaged(true);
            
            // Disable execute buttons
            executeButton.setDisable(true);
            executeParallelButton.setDisable(true);
        }
    }
    
    private BooleanProperty createSelectionProperty(RecipeIngredient ri) {
        SimpleBooleanProperty prop = new SimpleBooleanProperty(true);
        prop.addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            updateCalculatedInfo();
            updateStockWarning();
            if (executionTable != null) {
                executionTable.refresh();
            }
        }));
        return prop;
    }
    
    private BooleanProperty selectionProperty(RecipeIngredient ri) {
        if (ri == null) {
            return new SimpleBooleanProperty(false);
        }
        return ingredientSelectionMap.computeIfAbsent(ri, this::createSelectionProperty);
    }
    
    private void applyCheckboxStyle(CheckBoxTableCell<RecipeIngredient, Boolean> cell, Boolean item, boolean empty) {
        if (cell == null) {
            return;
        }
        
        javafx.scene.Node graphic = cell.getGraphic();
        
        // Reset styles by default
        cell.setStyle("");
        if (graphic instanceof CheckBox) {
            ((CheckBox) graphic).setStyle("");
        }
        
        if (empty || graphic == null || !(graphic instanceof CheckBox)) {
            return;
        }
        
        CheckBox checkBox = (CheckBox) graphic;
        
        if (Boolean.TRUE.equals(item)) {
            checkBox.setStyle("-fx-background-color: #C8E6C9; -fx-border-color: #388E3C; -fx-mark-color: #1B5E20; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2;");
            cell.setStyle("-fx-background-color: #E8F5E9;");
        } else {
            checkBox.setStyle("-fx-background-color: #FFCDD2; -fx-border-color: #C62828; -fx-mark-color: #B71C1C; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2;");
            cell.setStyle("-fx-background-color: #FFEBEE;");
        }
    }
    
    private boolean isIngredientSelected(RecipeIngredient ri) {
        return selectionProperty(ri).get();
    }
    
    private List<RecipeIngredient> getSelectedIngredients() {
        if (executionTable == null || executionTable.getItems() == null) {
            return Collections.emptyList();
        }
        return executionTable.getItems().stream()
            .filter(this::isIngredientSelected)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate grams from duration using ingredient's msPerGram configuration
     * Uses msPerGramLarge or msPerGramSmall based on calculated grams and threshold
     */
    private double calculateGramsFromDurationWithConfig(RecipeIngredient ri, double scaledGrams, Ingredient ingredient) {
        if (ingredient == null || ri.getPulseDuration() == null) {
            // Fallback to default calculation
            return ro.marcman.mixer.core.services.QuantityCalculator.calculateGramsFromDuration(ri.getPulseDuration());
        }
        
        // Determine which pump will be used
        Double threshold = ingredient.getPumpThresholdGrams() != null ? 
            ingredient.getPumpThresholdGrams() : 10.0;
        
        Integer msPerGram = null;
        if (scaledGrams < threshold) {
            // Will use SMALL pump
            msPerGram = ingredient.getMsPerGramSmall();
            if (msPerGram == null) {
                msPerGram = ingredient.getMsPerGramLarge(); // Fallback to Large
            }
        } else {
            // Will use LARGE pump
            msPerGram = ingredient.getMsPerGramLarge();
        }
        
        // If msPerGram is not configured, fallback to default
        if (msPerGram == null || msPerGram <= 0) {
            return ro.marcman.mixer.core.services.QuantityCalculator.calculateGramsFromDuration(ri.getPulseDuration());
        }
        
        // Calculate grams using configured msPerGram: grams = duration / msPerGram
        return ri.getPulseDuration() / (double) msPerGram;
    }
    
    /**
     * Calculate grams from duration using ingredient's msPerGram configuration (for base duration, no scaling)
     */
    private double calculateBaseGramsFromDurationWithConfig(RecipeIngredient ri, Ingredient ingredient) {
        if (ingredient == null || ri.getPulseDuration() == null) {
            // Fallback to default calculation
            return ro.marcman.mixer.core.services.QuantityCalculator.calculateGramsFromDuration(ri.getPulseDuration());
        }
        
        // First calculate with default to determine which pump to use
        double baseGramsDefault = ro.marcman.mixer.core.services.QuantityCalculator.calculateGramsFromDuration(ri.getPulseDuration());
        
        // Determine which pump will be used
        Double threshold = ingredient.getPumpThresholdGrams() != null ? 
            ingredient.getPumpThresholdGrams() : 10.0;
        
        Integer msPerGram = null;
        if (baseGramsDefault < threshold) {
            // Will use SMALL pump
            msPerGram = ingredient.getMsPerGramSmall();
            if (msPerGram == null) {
                msPerGram = ingredient.getMsPerGramLarge(); // Fallback to Large
            }
        } else {
            // Will use LARGE pump
            msPerGram = ingredient.getMsPerGramLarge();
        }
        
        // If msPerGram is not configured, fallback to default
        if (msPerGram == null || msPerGram <= 0) {
            return baseGramsDefault;
        }
        
        // Calculate grams using configured msPerGram: grams = duration / msPerGram
        return ri.getPulseDuration() / (double) msPerGram;
    }
    
    /**
     * Calculate the maximum producible quantity based on available stock
     * @param recipe The recipe to check
     * @return Maximum quantity in grams that can be produced with current stock
     */
    private int calculateMaxProducibleQuantity(Recipe recipe) {
        if (recipe == null || recipe.getIngredients().isEmpty()) {
            return 0;
        }
        
        if (getSelectedIngredients().isEmpty()) {
            return 0;
        }
        
        int originalBatchSize = recipe.getBatchSize() != null ? recipe.getBatchSize() : 100;
        
        // For each ingredient, calculate how much of the recipe we can produce
        double minRatio = Double.MAX_VALUE;
        
        for (RecipeIngredient ri : recipe.getIngredients()) {
            if (!isIngredientSelected(ri)) {
                continue;
            }
            try {
                // Load ingredient from database to get current stock
                Ingredient ingredient = ingredientRepository.findById(ri.getIngredientId()).orElse(null);
                if (ingredient == null) {
                    continue; // Skip instead of returning 0
                }
                
                // Check if pulseDuration is set
                if (ri.getPulseDuration() == null || ri.getPulseDuration() <= 0) {
                    continue; // Skip ingredients without duration
                }
                
                // Calculate grams needed for original recipe batch using ingredient's msPerGram configuration
                double baseGrams = calculateBaseGramsFromDurationWithConfig(ri, ingredient);
                
                if (baseGrams <= 0) {
                    continue; // Skip if no grams needed
                }
                
                // Get available stock
                double availableStock = ingredient.getStockQuantity() != null ? ingredient.getStockQuantity() : 0.0;
                
                // Calculate how many batches we can produce with this ingredient
                // ratio = availableStock / baseGrams
                double ratio = availableStock / baseGrams;
                
                // The minimum ratio determines the maximum producible quantity
                if (ratio < minRatio) {
                    minRatio = ratio;
                }
                
            } catch (Exception e) {
                // Silently skip ingredients with errors
            }
        }
        
        if (minRatio == Double.MAX_VALUE || minRatio <= 0) {
            return 0;
        }
        
        // Calculate maximum producible quantity: minRatio * originalBatchSize
        int maxProducible = (int) Math.floor(minRatio * originalBatchSize);
        
        // Ensure at least 1 gram if we have some stock
        if (maxProducible < 1 && minRatio > 0) {
            maxProducible = 1;
        }
        
        return maxProducible;
    }
    
    /**
     * Normalize UID format to ensure it's in 0x... format
     * Handles formats like: "1", "0x1", "0X1", "0x0001", etc.
     */
    private String normalizeUid(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return "0x0";
        }
        
        String trimmed = uid.trim();
        
        // If already starts with 0x or 0X, return as is
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return trimmed;
        }
        
        // Try to parse as hex number
        try {
            // Remove any leading zeros and parse
            String clean = trimmed.replaceFirst("^0+", "");
            if (clean.isEmpty()) clean = "0";
            int value = Integer.parseInt(clean, 16);
            return "0x" + Integer.toHexString(value);
        } catch (NumberFormatException e) {
            // If not a valid hex number, try decimal
            try {
                int value = Integer.parseInt(trimmed);
                return "0x" + Integer.toHexString(value);
            } catch (NumberFormatException e2) {
                // If still fails, just add 0x prefix
                return "0x" + trimmed;
            }
        }
    }
}

