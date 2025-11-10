package ro.marcman.mixer.adapters.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import ro.marcman.mixer.core.model.Ingredient;
import ro.marcman.mixer.core.model.Recipe;
import ro.marcman.mixer.core.model.RecipeIngredient;
import ro.marcman.mixer.core.services.IngredientMatcher;
import ro.marcman.mixer.core.services.PdfRecipeParser;
import ro.marcman.mixer.core.services.QuantityCalculator;
import ro.marcman.mixer.sqlite.DatabaseManager;
import ro.marcman.mixer.sqlite.IngredientRepositoryImpl;
import ro.marcman.mixer.sqlite.RecipeRepositoryImpl;
import ro.marcman.mixer.adapters.ui.util.IconSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Recipes management UI
 */
public class RecipesView extends VBox {
    
    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final RecipeRepositoryImpl recipeRepository = new RecipeRepositoryImpl(dbManager);
    private final IngredientRepositoryImpl ingredientRepository = new IngredientRepositoryImpl(dbManager);
    private ro.marcman.mixer.serial.SerialManager serialManager;
    
    private TableView<Recipe> recipesTable;
    private ObservableList<Recipe> allRecipes;
    
    public RecipesView() {
        super(10);
        setPadding(new Insets(15));
        
        buildUI();
        loadRecipes();
    }
    
    private void buildUI() {
        // Title
        Label title = new Label("Perfume Recipes");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        
        // Action buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        buttonBar.setPadding(new Insets(10, 0, 10, 0));
        
        Button newButton = new Button("+ New Recipe");
        newButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        newButton.setOnAction(e -> showRecipeDialog(null));
        
        Button editButton = new Button("Edit Recipe");
        editButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        editButton.setOnAction(e -> {
            Recipe selected = recipesTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showRecipeDialog(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a recipe to edit.");
            }
        });
        
        Button deleteButton = new Button("Delete Recipe");
        deleteButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> deleteSelectedRecipe());
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> loadRecipes());
        
        Button importPdfButton = new Button("📄 Import from PDF");
        importPdfButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        importPdfButton.setOnAction(e -> importRecipeFromPdf());
        
        buttonBar.getChildren().addAll(newButton, editButton, deleteButton, refreshButton, importPdfButton);
        
        // Recipes table
        recipesTable = new TableView<>();
        VBox.setVgrow(recipesTable, Priority.ALWAYS);
        
        TableColumn<Recipe, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);
        
        TableColumn<Recipe, String> nameCol = new TableColumn<>("Recipe Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(250);
        
        TableColumn<Recipe, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(150);
        
        TableColumn<Recipe, Integer> ingredientsCol = new TableColumn<>("Ingredients");
        ingredientsCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getIngredientCount()));
        ingredientsCol.setPrefWidth(100);
        
        TableColumn<Recipe, Integer> durationCol = new TableColumn<>("Total Duration (ms)");
        durationCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getTotalDuration()));
        durationCol.setPrefWidth(150);
        
        TableColumn<Recipe, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(300);
        
        recipesTable.getColumns().addAll(idCol, nameCol, categoryCol, ingredientsCol, durationCol, descCol);
        
        // Double-click to edit
        recipesTable.setRowFactory(tv -> {
            TableRow<Recipe> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showRecipeDialog(row.getItem());
                }
            });
            return row;
        });
        
        // Info label
        Label infoLabel = new Label("Double-click to edit | Select + buttons to manage recipes");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        getChildren().addAll(title, buttonBar, recipesTable, infoLabel);
    }
    
    private void loadRecipes() {
        try {
            List<Recipe> recipes = recipeRepository.findAll();
            
            allRecipes = FXCollections.observableArrayList(recipes);
            recipesTable.setItems(allRecipes);
            
        } catch (Exception e) {
            System.err.println("Error loading recipes: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load recipes: " + e.getMessage());
        }
    }
    
    private void showRecipeDialog(Recipe existingRecipe) {
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle(existingRecipe == null ? "New Recipe" : "Edit Recipe");
        dialog.setHeaderText(existingRecipe == null ? "Create a new perfume recipe" : "Edit: " + existingRecipe.getName());
        
        // Main container
        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));
        mainBox.setPrefWidth(700);
        mainBox.setPrefHeight(600);
        
        // Recipe basic info
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(10);
        
        Label nameLabel = new Label("Recipe Name:*");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g., 'Fresh Citrus Cologne', 'Evening Rose'");
        nameField.setPrefWidth(400);
        if (existingRecipe != null) nameField.setText(existingRecipe.getName());
        
        Label categoryLabel = new Label("Category:");
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll("Eau de Cologne", "Eau de Toilette", "Eau de Parfum", 
                                       "Parfum", "Custom", "Experimental");
        categoryCombo.setEditable(true);
        categoryCombo.setPromptText("Select or enter category");
        if (existingRecipe != null) categoryCombo.setValue(existingRecipe.getCategory());
        
        Label descLabel = new Label("Description:");
        TextArea descArea = new TextArea();
        descArea.setPromptText("Description of the fragrance...");
        descArea.setPrefRowCount(2);
        if (existingRecipe != null) descArea.setText(existingRecipe.getDescription());
        
        Label batchSizeLabel = new Label("Batch Size (g):*");
        TextField batchSizeField = new TextField();
        batchSizeField.setPromptText("e.g., 100");
        batchSizeField.setPrefWidth(150);
        Label batchHintLabel = new Label("(Used for % → ms conversion: 1g = 20ms)");
        batchHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        HBox batchBox = new HBox(10, batchSizeField, batchHintLabel);
        batchBox.setAlignment(Pos.CENTER_LEFT);
        if (existingRecipe != null && existingRecipe.getBatchSize() != null) {
            batchSizeField.setText(String.valueOf(existingRecipe.getBatchSize()));
        } else {
            batchSizeField.setText("100"); // Default 100g
        }
        
        infoGrid.add(nameLabel, 0, 0);
        infoGrid.add(nameField, 1, 0);
        infoGrid.add(categoryLabel, 0, 1);
        infoGrid.add(categoryCombo, 1, 1);
        infoGrid.add(batchSizeLabel, 0, 2);
        infoGrid.add(batchBox, 1, 2);
        infoGrid.add(descLabel, 0, 3);
        infoGrid.add(descArea, 1, 3);
        
        // Ingredients selection
        Label ingredientsLabel = new Label("Recipe Ingredients:");
        ingredientsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Table for recipe ingredients
        TableView<RecipeIngredient> ingredientsTable = new TableView<>();
        ingredientsTable.setPrefHeight(300);
        
        TableColumn<RecipeIngredient, String> ingNameCol = new TableColumn<>("Ingredient");
        ingNameCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDisplayName()));
        ingNameCol.setPrefWidth(200);
        
        TableColumn<RecipeIngredient, String> ingCasCol = new TableColumn<>("CAS");
        ingCasCol.setCellValueFactory(cellData -> {
            Ingredient ing = cellData.getValue().getIngredient();
            return new javafx.beans.property.SimpleStringProperty(
                ing != null ? ing.getCasNumber() : "");
        });
        ingCasCol.setPrefWidth(100);
        
        TableColumn<RecipeIngredient, String> percentageCol = new TableColumn<>("Percentage");
        percentageCol.setCellValueFactory(cellData -> {
            RecipeIngredient ri = cellData.getValue();
            if (ri.getUnit() != null && ri.getUnit().equals("%") && ri.getQuantity() != null) {
                return new javafx.beans.property.SimpleStringProperty(String.format("%.2f%%", ri.getQuantity()));
            }
            return new javafx.beans.property.SimpleStringProperty("—");
        });
        percentageCol.setPrefWidth(100);
        
        TableColumn<RecipeIngredient, String> gramsCol = new TableColumn<>("Grams");
        gramsCol.setCellValueFactory(cellData -> {
            RecipeIngredient ri = cellData.getValue();
            try {
                int batchSize = Integer.parseInt(batchSizeField.getText());
                if (batchSize > 0) {
                    if (ri.getUnit() != null && ri.getUnit().equals("%") && ri.getQuantity() != null) {
                        double grams = QuantityCalculator.calculateGramsFromPercentage(ri.getQuantity(), batchSize);
                        return new javafx.beans.property.SimpleStringProperty(QuantityCalculator.formatGrams(grams));
                    } else if (ri.getPulseDuration() != null) {
                        double grams = QuantityCalculator.calculateGramsFromDuration(ri.getPulseDuration());
                        return new javafx.beans.property.SimpleStringProperty(QuantityCalculator.formatGrams(grams));
                    }
                }
            } catch (Exception e) {
                // Batch size not valid or error in calculation, skip
            }
            return new javafx.beans.property.SimpleStringProperty("—");
        });
        gramsCol.setPrefWidth(80);
        
        TableColumn<RecipeIngredient, Integer> durationCol = new TableColumn<>("Duration (ms)");
        durationCol.setCellValueFactory(new PropertyValueFactory<>("pulseDuration"));
        durationCol.setPrefWidth(100);
        
        TableColumn<RecipeIngredient, Integer> orderCol = new TableColumn<>("Order");
        orderCol.setCellValueFactory(new PropertyValueFactory<>("sequenceOrder"));
        orderCol.setPrefWidth(70);
        
        TableColumn<RecipeIngredient, String> slaveCol = new TableColumn<>("SLAVE");
        slaveCol.setCellValueFactory(cellData -> {
            Ingredient ing = cellData.getValue().getIngredient();
            return new javafx.beans.property.SimpleStringProperty(
                ing != null && ing.getArduinoUid() != null ? ing.getArduinoUid() : "N/A");
        });
        slaveCol.setPrefWidth(80);
        
        TableColumn<RecipeIngredient, String> pinCol = new TableColumn<>("PIN");
        pinCol.setCellValueFactory(cellData -> {
            Ingredient ing = cellData.getValue().getIngredient();
            if (ing != null && ing.getArduinoPin() != null) {
                int pin = ing.getArduinoPin();
                String display = (pin >= 54 && pin <= 69) ? "A" + (pin - 54) : String.valueOf(pin);
                return new javafx.beans.property.SimpleStringProperty(display);
            }
            return new javafx.beans.property.SimpleStringProperty("N/A");
        });
        pinCol.setPrefWidth(70);
        
        ingredientsTable.getColumns().addAll(ingNameCol, ingCasCol, percentageCol, gramsCol, durationCol, orderCol, slaveCol, pinCol);
        
        // Refresh table when batch size changes
        batchSizeField.textProperty().addListener((obs, oldVal, newVal) -> {
            ingredientsTable.refresh();
        });
        
        // Populate existing ingredients
        ObservableList<RecipeIngredient> recipeIngredients = FXCollections.observableArrayList();
        if (existingRecipe != null && existingRecipe.getIngredients() != null) {
            recipeIngredients.addAll(existingRecipe.getIngredients());
        }
        ingredientsTable.setItems(recipeIngredients);
        
        // Buttons for ingredient management
        HBox ingredientButtons = new HBox(10);
        ingredientButtons.setAlignment(Pos.CENTER_LEFT);
        
        Button addIngredientButton = new Button("+ Add Ingredient");
        addIngredientButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addIngredientButton.setOnAction(e -> {
            try {
                int currentBatchSize = Integer.parseInt(batchSizeField.getText());
                showAddIngredientDialog(recipeIngredients, currentBatchSize);
            } catch (NumberFormatException ex) {
                showAlert(Alert.AlertType.WARNING, "Invalid Batch Size", 
                         "Please enter a valid batch size before adding ingredients.");
            }
        });
        
        Button editIngredientButton = new Button("✎ Edit Duration");
        editIngredientButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        editIngredientButton.setOnAction(e -> {
            RecipeIngredient selected = ingredientsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditDurationDialog(selected, ingredientsTable);
            }
        });
        
        Button removeIngredientButton = new Button("- Remove");
        removeIngredientButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeIngredientButton.setOnAction(e -> {
            RecipeIngredient selected = ingredientsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                recipeIngredients.remove(selected);
                reorderIngredients(recipeIngredients);
            }
        });
        
        Button moveUpButton = new Button("↑ Move Up");
        moveUpButton.setOnAction(e -> moveIngredient(ingredientsTable, recipeIngredients, -1));
        
        Button moveDownButton = new Button("↓ Move Down");
        moveDownButton.setOnAction(e -> moveIngredient(ingredientsTable, recipeIngredients, 1));
        
        ingredientButtons.getChildren().addAll(addIngredientButton, editIngredientButton, removeIngredientButton, 
                                               moveUpButton, moveDownButton);
        
        // Total duration display
        Label totalLabel = new Label();
        totalLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        updateTotalDuration(totalLabel, recipeIngredients);
        
        // Update total when table changes
        recipeIngredients.addListener((javafx.collections.ListChangeListener<RecipeIngredient>) c -> {
            updateTotalDuration(totalLabel, recipeIngredients);
            ingredientsTable.refresh();
        });
        
        // Add all to main box
        mainBox.getChildren().addAll(
            infoGrid,
            new Separator(),
            ingredientsLabel,
            ingredientsTable,
            ingredientButtons,
            totalLabel
        );
        
        ScrollPane scrollPane = new ScrollPane(mainBox);
        scrollPane.setFitToWidth(true);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Handle save
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Validate
                    if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Recipe name is required!");
                        return;
                    }
                    
                    if (recipeIngredients.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Add at least one ingredient!");
                        return;
                    }
                    
                    // Validate batch size
                    int batchSize;
                    try {
                        batchSize = Integer.parseInt(batchSizeField.getText());
                        if (batchSize <= 0) {
                            showAlert(Alert.AlertType.ERROR, "Validation Error", "Batch size must be positive!");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Batch size must be a valid number!");
                        return;
                    }
                    
                    // Create or update recipe
                    Recipe recipe = existingRecipe != null ? existingRecipe : new Recipe();
                    recipe.setName(nameField.getText().trim());
                    recipe.setDescription(descArea.getText());
                    recipe.setCategory(categoryCombo.getValue());
                    recipe.setBatchSize(batchSize);
                    recipe.setActive(true);
                    recipe.setIngredients(new ArrayList<>(recipeIngredients));
                    
                    // Save to database
                    Recipe saved = recipeRepository.save(recipe);
                    
                    if (saved != null) {
                        showAlert(Alert.AlertType.INFORMATION, "Success", 
                                 "Recipe '" + saved.getName() + "' saved successfully!");
                        loadRecipes();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to save recipe!");
                    }
                    
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to save recipe: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void showAddIngredientDialog(ObservableList<RecipeIngredient> recipeIngredients, int batchSize) {
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle("Add Ingredient to Recipe");
        dialog.setHeaderText("Search and select ingredient");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(650);
        content.setPrefHeight(500);
        
        // Load all ingredients
        List<Ingredient> allIngredients = ingredientRepository.findAll();
        
        // Search field
        Label searchLabel = new Label("Search Ingredient:");
        TextField searchField = new TextField();
        searchField.setPromptText("Type to search by name or CAS number...");
        searchField.setPrefWidth(600);
        searchField.setStyle("-fx-font-size: 14px;");
        
        // Table for ingredient selection (instead of dropdown!)
        Label tableLabel = new Label("Available Ingredients:");
        TableView<Ingredient> ingredientTable = new TableView<>();
        ingredientTable.setPrefHeight(250);
        
        TableColumn<Ingredient, String> nameCol = new TableColumn<>("Ingredient");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);
        
        TableColumn<Ingredient, String> casCol = new TableColumn<>("CAS Number");
        casCol.setCellValueFactory(new PropertyValueFactory<>("casNumber"));
        casCol.setPrefWidth(120);
        
        TableColumn<Ingredient, String> slaveCol = new TableColumn<>("SLAVE");
        slaveCol.setCellValueFactory(cellData -> {
            String uid = cellData.getValue().getArduinoUid();
            return new javafx.beans.property.SimpleStringProperty(uid != null ? uid : "N/A");
        });
        slaveCol.setPrefWidth(80);
        
        TableColumn<Ingredient, String> pinCol = new TableColumn<>("PIN");
        pinCol.setCellValueFactory(cellData -> {
            Integer pin = cellData.getValue().getArduinoPin();
            if (pin != null) {
                String display = (pin >= 54 && pin <= 69) ? "A" + (pin - 54) : String.valueOf(pin);
                return new javafx.beans.property.SimpleStringProperty(display);
            }
            return new javafx.beans.property.SimpleStringProperty("N/A");
        });
        pinCol.setPrefWidth(70);
        
        TableColumn<Ingredient, String> configCol = new TableColumn<>("Configured");
        configCol.setCellValueFactory(cellData -> {
            Ingredient ing = cellData.getValue();
            String status = (ing.getArduinoUid() != null && ing.getArduinoPin() != null) ? "YES" : "NO";
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        configCol.setPrefWidth(90);
        
        ingredientTable.getColumns().addAll(nameCol, casCol, slaveCol, pinCol, configCol);
        
        // Observable list for filtering
        ObservableList<Ingredient> filteredIngredients = FXCollections.observableArrayList(allIngredients);
        ingredientTable.setItems(filteredIngredients);
        
        // Search filter - REAL-TIME
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                filteredIngredients.setAll(allIngredients);
            } else {
                String searchText = newVal.toLowerCase();
                List<Ingredient> filtered = allIngredients.stream()
                    .filter(ing -> 
                        (ing.getName() != null && ing.getName().toLowerCase().contains(searchText)) ||
                        (ing.getCasNumber() != null && ing.getCasNumber().toLowerCase().contains(searchText)) ||
                        (ing.getCategory() != null && ing.getCategory().toLowerCase().contains(searchText))
                    )
                    .collect(java.util.stream.Collectors.toList());
                filteredIngredients.setAll(filtered);
            }
        });
        
        // Info label
        Label infoLabel = new Label("Type to filter | Double-click or select + OK to add");
        infoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
        
        // Selected ingredient reference
        final Ingredient[] selectedIngredient = {null};
        
        ingredientTable.setRowFactory(tv -> {
            TableRow<Ingredient> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    selectedIngredient[0] = row.getItem();
                    dialog.setResult(ButtonType.OK);
                    dialog.close();
                }
            });
            return row;
        });
        
        ingredientTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectedIngredient[0] = newVal;
        });
        
        Separator sep1 = new Separator();
        
        // Input mode selection
        Label modeLabel = new Label("Input Mode:");
        modeLabel.setStyle("-fx-font-weight: bold;");
        ToggleGroup inputModeGroup = new ToggleGroup();
        RadioButton percentageRadio = new RadioButton("Percentage (%)");
        RadioButton manualRadio = new RadioButton("Manual Duration (ms)");
        percentageRadio.setToggleGroup(inputModeGroup);
        manualRadio.setToggleGroup(inputModeGroup);
        percentageRadio.setSelected(true); // Default to percentage
        HBox modeBox = new HBox(20, percentageRadio, manualRadio);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        
        // Percentage input (visible when percentage mode)
        Label percentageLabel = new Label("Percentage (%):*");
        TextField percentageField = new TextField();
        percentageField.setPromptText("e.g., 25.5");
        percentageField.setPrefWidth(150);
        Label percentageCalcLabel = new Label("");
        percentageCalcLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #2196F3;");
        HBox percentageBox = new HBox(10, percentageField, percentageCalcLabel);
        percentageBox.setAlignment(Pos.CENTER_LEFT);
        
        // Manual duration input (visible when manual mode)
        Label durationLabel = new Label("Pumping Duration (ms):*");
        TextField durationField = new TextField();
        durationField.setPromptText("e.g., 1000");
        durationField.setPrefWidth(150);
        Label durationCalcLabel = new Label("");
        durationCalcLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        HBox durationBox = new HBox(10, durationField, durationCalcLabel);
        durationBox.setAlignment(Pos.CENTER_LEFT);
        
        // Batch info
        Label batchInfoLabel = new Label(String.format("Batch Size: %dg | Conversion: 1g = 20ms", batchSize));
        batchInfoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        
        // Auto-calculation when percentage changes
        percentageField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                double percent = Double.parseDouble(newVal);
                int duration = QuantityCalculator.calculateDurationFromPercentage(percent, batchSize);
                double grams = QuantityCalculator.calculateGramsFromPercentage(percent, batchSize);
                percentageCalcLabel.setText(String.format("→ %.2fg = %dms", grams, duration));
                percentageCalcLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            } catch (Exception e) {
                percentageCalcLabel.setText("");
            }
        });
        
        // Auto-calculation when manual duration changes
        durationField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                if (newVal == null || newVal.trim().isEmpty()) {
                    durationCalcLabel.setText("");
                    return;
                }
                double durationValue = Double.parseDouble(newVal);
                int duration = (int) Math.round(durationValue);
                double grams = QuantityCalculator.calculateGramsFromDuration(duration);
                double percent = QuantityCalculator.calculatePercentageFromDuration(duration, batchSize);
                durationCalcLabel.setText(String.format("= %.2fg (%.2f%%)", grams, percent));
            } catch (Exception e) {
                durationCalcLabel.setText("");
            }
        });
        
        // Toggle visibility based on mode
        percentageLabel.setVisible(true);
        percentageBox.setVisible(true);
        durationLabel.setVisible(false);
        durationBox.setVisible(false);
        
        percentageRadio.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            percentageLabel.setVisible(isNowSelected);
            percentageBox.setVisible(isNowSelected);
            durationLabel.setVisible(!isNowSelected);
            durationBox.setVisible(!isNowSelected);
        });
        
        // Warning if ingredient not configured
        Label warningLabel = new Label();
        warningLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
        warningLabel.setVisible(false);
        
        // Update warning when selection changes
        ingredientTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                if (newVal.getArduinoUid() == null || newVal.getArduinoPin() == null) {
                    warningLabel.setText("WARNING: This ingredient is not configured with SLAVE/PIN!");
                    warningLabel.setVisible(true);
                } else {
                    warningLabel.setVisible(false);
                }
            }
        });
        
        content.getChildren().addAll(
            searchLabel, searchField, infoLabel,
            tableLabel, ingredientTable, warningLabel,
            sep1,
            modeLabel, modeBox,
            new Separator(),
            batchInfoLabel,
            percentageLabel, percentageBox,
            durationLabel, durationBox
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    Ingredient selected = selectedIngredient[0];
                    if (selected == null) {
                        showAlert(Alert.AlertType.ERROR, "Validation Error", "Please select an ingredient from the table!");
                        return;
                    }
                    
                    // Calculate duration based on input mode
                    int duration;
                    double percentage = 0;
                    
                    if (percentageRadio.isSelected()) {
                        // Percentage mode
                        if (percentageField.getText() == null || percentageField.getText().trim().isEmpty()) {
                            showAlert(Alert.AlertType.ERROR, "Validation Error", "Percentage is required!");
                            return;
                        }
                        percentage = Double.parseDouble(percentageField.getText().trim());
                        if (percentage <= 0 || percentage > 100) {
                            showAlert(Alert.AlertType.ERROR, "Validation Error", "Percentage must be between 0 and 100!");
                            return;
                        }
                        // Calculate duration from percentage
                        duration = QuantityCalculator.calculateDurationFromPercentage(percentage, batchSize);
                    } else {
                        // Manual duration mode
                        if (durationField.getText() == null || durationField.getText().trim().isEmpty()) {
                            showAlert(Alert.AlertType.ERROR, "Validation Error", "Duration is required!");
                            return;
                        }
                        // Accept decimal values and round to nearest integer
                        double durationValue = Double.parseDouble(durationField.getText().trim());
                        if (durationValue <= 0) {
                            showAlert(Alert.AlertType.ERROR, "Validation Error", "Duration must be positive!");
                            return;
                        }
                        duration = (int) Math.round(durationValue);
                        if (duration <= 0) {
                            showAlert(Alert.AlertType.ERROR, "Validation Error", "Duration must be positive!");
                            return;
                        }
                        // Calculate percentage from duration for storage
                        percentage = QuantityCalculator.calculatePercentageFromDuration(duration, batchSize);
                    }
                    
                    // Create recipe ingredient
                    RecipeIngredient ri = RecipeIngredient.builder()
                        .ingredientId(selected.getId())
                        .pulseDuration(duration)
                        .quantity(percentage)  // Store percentage in quantity field
                        .unit("%")  // Mark as percentage
                        .sequenceOrder(recipeIngredients.size())  // Add at end
                        .ingredient(selected)
                        .build();
                    
                    recipeIngredients.add(ri);
                    
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter valid numbers!");
                }
            }
        });
    }
    
    private void moveIngredient(TableView<RecipeIngredient> table, 
                                ObservableList<RecipeIngredient> list, int direction) {
        int selectedIndex = table.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        
        int newIndex = selectedIndex + direction;
        if (newIndex < 0 || newIndex >= list.size()) return;
        
        RecipeIngredient item = list.remove(selectedIndex);
        list.add(newIndex, item);
        table.getSelectionModel().select(newIndex);
        
        reorderIngredients(list);
    }
    
    private void reorderIngredients(ObservableList<RecipeIngredient> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSequenceOrder(i);
        }
    }
    
    private void updateTotalDuration(Label label, ObservableList<RecipeIngredient> ingredients) {
        int total = ingredients.stream()
            .filter(ri -> ri.getPulseDuration() != null)
            .mapToInt(RecipeIngredient::getPulseDuration)
            .sum();
        
        double totalSeconds = total / 1000.0;
        label.setText(String.format("Total pumping time: %d ms (%.2f seconds) | %d ingredients", 
                                   total, totalSeconds, ingredients.size()));
    }
    
    private void deleteSelectedRecipe() {
        Recipe selected = recipesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a recipe to delete.");
            return;
        }
        
        Alert confirm = createAlert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete recipe: " + selected.getName() + "?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                recipeRepository.deleteById(selected.getId());
                showAlert(Alert.AlertType.INFORMATION, "Deleted", 
                         "Recipe '" + selected.getName() + "' deleted successfully!");
                loadRecipes();
            }
        });
    }
    
    private void importRecipeFromPdf() {
        // Step 1: Select PDF file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF Recipe File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        
        File pdfFile = fileChooser.showOpenDialog(getScene().getWindow());
        if (pdfFile == null) {
            return; // User cancelled
        }
        
        // Step 2: Parse PDF
        PdfRecipeParser parser = new PdfRecipeParser();
        List<PdfRecipeParser.PdfIngredient> pdfIngredients;
        
        try {
            pdfIngredients = parser.parseIngredientsFromFile(pdfFile);
            
            if (pdfIngredients.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Ingredients Found", 
                         "Could not extract any ingredients from the PDF file.\n\n" +
                         "Please ensure the PDF contains ingredient names, CAS numbers, or quantities.");
                return;
            }
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "PDF Parse Error", 
                     "Failed to read PDF file: " + e.getMessage());
            return;
        }
        
        // Step 3: Match with database ingredients
        List<Ingredient> allIngredients = ingredientRepository.findAll();
        IngredientMatcher matcher = new IngredientMatcher();
        List<IngredientMatcher.MatchedIngredient> matches = matcher.matchIngredients(pdfIngredients, allIngredients);
        
        // Step 4: Verify configuration and prepare for editing
        // DO NOT auto-add missing ingredients - user will complete them manually
        List<IngredientConfigurationInfo> configInfos = new ArrayList<>();
        
        for (IngredientMatcher.MatchedIngredient match : matches) {
            Ingredient dbIngredient = match.getDbIngredient();
                PdfRecipeParser.PdfIngredient pdfIng = match.getPdfIngredient();
                
            // Skip if name is empty
            if (pdfIng.getName() == null || pdfIng.getName().trim().isEmpty()) {
                continue;
            }
            
            if (dbIngredient != null) {
                // Ingredient exists in database - check configuration
                boolean isConfigured = isIngredientFullyConfigured(dbIngredient);
                List<String> missingFields = getMissingConfigurationFields(dbIngredient);
                
                configInfos.add(new IngredientConfigurationInfo(
                    match,
                    dbIngredient,
                        pdfIng,
                    true, // exists in DB
                    isConfigured,
                    missingFields
                ));
            } else {
                // Ingredient NOT in database - user must provide all data
                configInfos.add(new IngredientConfigurationInfo(
                    match,
                    null, // will be created
                    pdfIng,
                    false, // not in DB
                    false, // not configured (doesn't exist)
                    Arrays.asList("All fields required") // all fields needed
                ));
            }
        }
        
        // Step 5: Show configuration review and editing dialog
        showPdfImportConfigurationDialog(pdfFile.getName(), configInfos);
    }
    
    private void showPdfImportReviewDialog(String pdfFileName, List<IngredientMatcher.MatchedIngredient> matches, int autoAddedCount) {
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle("Review PDF Import - " + pdfFileName);
        dialog.setHeaderText("Review and confirm ingredients extracted from PDF");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(1000);
        content.setPrefHeight(650);
        
        // Summary stats
        long exactMatches = matches.stream().filter(m -> 
            m.getMatchType() == IngredientMatcher.MatchedIngredient.MatchType.EXACT_CAS || 
            m.getMatchType() == IngredientMatcher.MatchedIngredient.MatchType.EXACT_NAME).count();
        long fuzzyMatches = matches.stream().filter(m -> 
            m.getMatchType() == IngredientMatcher.MatchedIngredient.MatchType.FUZZY_NAME).count();
        long noMatches = matches.stream().filter(m -> 
            m.getMatchType() == IngredientMatcher.MatchedIngredient.MatchType.NO_MATCH).count();
        
        String statsText = String.format(
            "Found %d ingredients: %d exact matches, %d fuzzy matches, %d not found",
            matches.size(), exactMatches, fuzzyMatches, noMatches
        );
        
        if (autoAddedCount > 0) {
            statsText += String.format("\n✅ Auto-added %d missing ingredients to database!", autoAddedCount);
        }
        
        Label statsLabel = new Label(statsText);
        statsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        // Info label for missing ingredients
        if (noMatches > 0) {
            Label infoLabel = new Label("⚠ You can manually add remaining missing ingredients");
            infoLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-style: italic;");
            content.getChildren().add(infoLabel);
        }
        
        // Table for matched ingredients
        ObservableList<IngredientMatcher.MatchedIngredient> matchList = FXCollections.observableArrayList(matches);
        TableView<IngredientMatcher.MatchedIngredient> matchTable = new TableView<>();
        matchTable.setItems(matchList);
        
        TableColumn<IngredientMatcher.MatchedIngredient, String> pdfNameCol = new TableColumn<>("PDF Name");
        pdfNameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getPdfIngredient().getName()));
        pdfNameCol.setPrefWidth(200);
        
        TableColumn<IngredientMatcher.MatchedIngredient, String> casCol = new TableColumn<>("CAS Number");
        casCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getPdfIngredient().getCasNumber() != null ? 
            data.getValue().getPdfIngredient().getCasNumber() : "-"));
        casCol.setPrefWidth(120);
        
        TableColumn<IngredientMatcher.MatchedIngredient, String> matchedNameCol = new TableColumn<>("Matched Ingredient");
        matchedNameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getDbIngredient() != null ? 
            data.getValue().getDbIngredient().getName() : "NOT FOUND"));
        matchedNameCol.setPrefWidth(250);
        
        TableColumn<IngredientMatcher.MatchedIngredient, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getStatusText()));
        statusCol.setPrefWidth(180);
        
        TableColumn<IngredientMatcher.MatchedIngredient, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(data -> {
            PdfRecipeParser.PdfIngredient pdf = data.getValue().getPdfIngredient();
            String text = "";
            if (pdf.getPercentage() != null) {
                text = pdf.getPercentage() + "%";
            } else if (pdf.getAmount() != null) {
                text = pdf.getAmount() + " " + pdf.getUnit();
            } else {
                text = "-";
            }
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        amountCol.setPrefWidth(100);
        
        matchTable.getColumns().addAll(pdfNameCol, casCol, matchedNameCol, statusCol, amountCol);
        
        // Recipe name input
        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label("Recipe Name:");
        nameLabel.setPrefWidth(120);
        TextField recipeNameField = new TextField();
        recipeNameField.setPromptText("Enter recipe name...");
        recipeNameField.setPrefWidth(300);
        recipeNameField.setText(pdfFileName.replace(".pdf", "").replaceAll("[^a-zA-Z0-9\\s]", " ").trim());
        nameBox.getChildren().addAll(nameLabel, recipeNameField);
        
        // Default duration input
        HBox durationBox = new HBox(10);
        durationBox.setAlignment(Pos.CENTER_LEFT);
        Label durationLabel = new Label("Default Duration:");
        durationLabel.setPrefWidth(120);
        TextField durationField = new TextField("1000");
        durationField.setPromptText("Pumping duration (ms)");
        durationField.setPrefWidth(150);
        Label durationHint = new Label("(milliseconds per ingredient)");
        durationHint.setStyle("-fx-text-fill: gray;");
        durationBox.getChildren().addAll(durationLabel, durationField, durationHint);
        
        content.getChildren().addAll(statsLabel, matchTable, nameBox, durationBox);
        
        dialog.getDialogPane().setContent(content);
        
        // Handle OK button
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                createRecipeFromMatches(recipeNameField.getText(), matches, durationField.getText());
            }
        });
    }
    
    private void createRecipeFromMatches(String recipeName, List<IngredientMatcher.MatchedIngredient> matches, String defaultDurationStr) {
        // Validate recipe name
        if (recipeName == null || recipeName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Name", "Please enter a recipe name.");
            return;
        }
        
        // Validate default duration
        int defaultDurationValue = 1000;
        try {
            defaultDurationValue = Integer.parseInt(defaultDurationStr);
            if (defaultDurationValue <= 0) {
                showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Duration must be positive.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Duration must be a number.");
            return;
        }
        
        final int defaultDuration = defaultDurationValue; // Make final for lambda
        
        // Filter only matched ingredients
        List<IngredientMatcher.MatchedIngredient> validMatches = matches.stream()
            .filter(m -> m.getDbIngredient() != null)
            .collect(Collectors.toList());
        
        if (validMatches.isEmpty()) {
            String errorMsg = "No ingredients could be matched with the database.\n" +
                            "Cannot create recipe.\n\n" +
                            "Possible reasons:\n" +
                            "  • Ingredient names don't match database entries\n" +
                            "  • CAS numbers don't match\n" +
                            "  • Auto-import failed\n\n" +
                            "Please check the review dialog for details.";
            showAlert(Alert.AlertType.WARNING, "No Matches", errorMsg);
            return;
        }
        
        // Show confirmation
        int skipped = matches.size() - validMatches.size();
        String confirmMsg = String.format(
            "Create recipe '%s' with %d ingredients?\n\n" +
            (skipped > 0 ? "Note: %d ingredients were not found in database and will be skipped." : ""),
            recipeName, validMatches.size(), skipped
        );
        
        Alert confirm = createAlert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Recipe Creation");
        confirm.setHeaderText("Create Recipe from PDF");
        confirm.setContentText(confirmMsg);
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // First, check which ingredients don't have Arduino configuration
                List<Ingredient> unconfiguredIngredients = validMatches.stream()
                    .map(IngredientMatcher.MatchedIngredient::getDbIngredient)
                    .filter(ing -> ing.getArduinoUid() == null || ing.getArduinoPin() == null)
                    .collect(Collectors.toList());
                
                if (!unconfiguredIngredients.isEmpty()) {
                    // Show pin allocation dialog
                    Alert pinAlert = createAlert(Alert.AlertType.CONFIRMATION);
                    pinAlert.setTitle("Arduino Configuration Required");
                    pinAlert.setHeaderText(String.format("%d ingredients need Arduino configuration", unconfiguredIngredients.size()));
                    pinAlert.setContentText("Would you like to configure SLAVE UID and PIN for these ingredients now?");
                    
                    pinAlert.showAndWait().ifPresent(pinResponse -> {
                        if (pinResponse == ButtonType.OK) {
                            // Show pin allocation dialog
                            showBulkPinAllocationDialog(unconfiguredIngredients, () -> {
                                // After pin allocation, create the recipe
                                createAndSaveRecipeFromMatches(recipeName, validMatches, defaultDuration);
                            });
                        } else {
                            // Create recipe without pin allocation
                            createAndSaveRecipeFromMatches(recipeName, validMatches, defaultDuration);
                        }
                    });
                } else {
                    // All ingredients configured, create recipe directly
                    createAndSaveRecipeFromMatches(recipeName, validMatches, defaultDuration);
                }
            }
        });
    }
    
    private void showEditDurationDialog(RecipeIngredient ingredient, TableView<RecipeIngredient> table) {
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle("Edit Ingredient - " + ingredient.getDisplayName());
        dialog.setHeaderText("Edit pumping duration and quantity");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Ingredient info (read-only)
        Label infoLabel = new Label("Ingredient: " + ingredient.getDisplayName());
        infoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        GridPane.setColumnSpan(infoLabel, 2);
        grid.add(infoLabel, 0, 0);
        
        // Pumping duration
        Label durationLabel = new Label("Pumping Duration (ms):");
        TextField durationField = new TextField();
        durationField.setText(ingredient.getPulseDuration() != null ? 
                             ingredient.getPulseDuration().toString() : "1000");
        durationField.setPromptText("Duration in milliseconds");
        grid.add(durationLabel, 0, 1);
        grid.add(durationField, 1, 1);
        
        // Quantity
        Label quantityLabel = new Label("Quantity:");
        TextField quantityField = new TextField();
        quantityField.setText(ingredient.getQuantity() != null ? 
                             ingredient.getQuantity().toString() : "");
        quantityField.setPromptText("Optional quantity");
        grid.add(quantityLabel, 0, 2);
        grid.add(quantityField, 1, 2);
        
        // Unit
        Label unitLabel = new Label("Unit:");
        ComboBox<String> unitCombo = new ComboBox<>();
        unitCombo.getItems().addAll("%", "ml", "g", "mg", "drops");
        unitCombo.setValue(ingredient.getUnit() != null ? ingredient.getUnit() : "%");
        grid.add(unitLabel, 0, 3);
        grid.add(unitCombo, 1, 3);
        
        // Notes
        Label notesLabel = new Label("Notes:");
        TextField notesField = new TextField();
        notesField.setText(ingredient.getNotes() != null ? ingredient.getNotes() : "");
        notesField.setPromptText("Optional notes");
        grid.add(notesLabel, 0, 4);
        grid.add(notesField, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Update duration - accept both integer and decimal values
                    String durationText = durationField.getText().trim();
                    if (durationText.isEmpty()) {
                        showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Duration is required.");
                        return;
                    }
                    
                    double durationValue = Double.parseDouble(durationText);
                    if (durationValue <= 0) {
                        showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Duration must be positive.");
                        return;
                    }
                    
                    // Round to nearest integer (miliseconds must be integer)
                    Integer newDuration = (int) Math.round(durationValue);
                    if (newDuration <= 0) {
                        showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Duration must be positive.");
                        return;
                    }
                    
                    ingredient.setPulseDuration(newDuration);
                    
                    // Update quantity (optional)
                    if (!quantityField.getText().trim().isEmpty()) {
                        Double newQuantity = Double.parseDouble(quantityField.getText());
                        ingredient.setQuantity(newQuantity);
                    } else {
                        ingredient.setQuantity(null);
                    }
                    
                    // Update unit
                    ingredient.setUnit(unitCombo.getValue());
                    
                    // Update notes
                    ingredient.setNotes(notesField.getText().trim().isEmpty() ? null : notesField.getText().trim());
                    
                    // Refresh table
                    table.refresh();
                    
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", 
                             "Duration and Quantity must be valid numbers!");
                }
            }
        });
    }
    
    private void showAddMissingIngredientDialog(IngredientMatcher.MatchedIngredient match, ObservableList<IngredientMatcher.MatchedIngredient> matchList) {
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle("Add Missing Ingredient");
        dialog.setHeaderText("Add new ingredient to database");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // Name
        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField(match.getPdfIngredient().getName());
        nameField.setPrefWidth(300);
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        
        // CAS Number
        Label casLabel = new Label("CAS Number:");
        TextField casField = new TextField(match.getPdfIngredient().getCasNumber() != null ? 
                                          match.getPdfIngredient().getCasNumber() : "");
        casField.setPromptText("000-00-0");
        grid.add(casLabel, 0, 1);
        grid.add(casField, 1, 1);
        
        // Category
        Label categoryLabel = new Label("Category:");
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll("Essential Oil", "Aroma Chemical", "Natural Extract", "Synthetic", "Other");
        categoryCombo.setValue("Aroma Chemical");
        grid.add(categoryLabel, 0, 2);
        grid.add(categoryCombo, 1, 2);
        
        // IFRA Status
        Label statusLabel = new Label("IFRA Status:");
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Approved", "Restricted", "Banned", "Unknown");
        statusCombo.setValue("Unknown");
        grid.add(statusLabel, 0, 3);
        grid.add(statusCombo, 1, 3);
        
        // Notes
        Label notesLabel = new Label("Notes:");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Optional notes...");
        notesArea.setPrefRowCount(3);
        notesArea.setPrefWidth(300);
        grid.add(notesLabel, 0, 4);
        grid.add(notesArea, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Invalid Input", "Ingredient name is required.");
                    return;
                }
                
                // Create new ingredient
                Ingredient newIngredient = Ingredient.builder()
                    .name(name)
                    .casNumber(casField.getText().trim().isEmpty() ? null : casField.getText().trim())
                    .category(categoryCombo.getValue())
                    .ifraStatus(statusCombo.getValue())
                    .description(notesArea.getText().trim().isEmpty() ? null : notesArea.getText().trim())
                    .active(true)
                    .build();
                
                try {
                    // Save to database
                    Ingredient saved = ingredientRepository.save(newIngredient);
                    
                    // Update the match - create new MatchedIngredient with the saved ingredient
                    IngredientMatcher.MatchedIngredient updatedMatch = new IngredientMatcher.MatchedIngredient(
                        match.getPdfIngredient(),
                        saved,
                        IngredientMatcher.MatchedIngredient.MatchType.EXACT_NAME,
                        1.0
                    );
                    
                    int index = matchList.indexOf(match);
                    matchList.set(index, updatedMatch);
                    
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Ingredient '" + saved.getName() + "' added to database!");
                    
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Save Error", 
                             "Failed to save ingredient: " + e.getMessage());
                }
            }
        });
    }
    
    private void showBulkPinAllocationDialog(List<Ingredient> ingredients, Runnable onComplete) {
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle("Configure Arduino - Bulk Pin Allocation");
        dialog.setHeaderText(String.format("Allocate SLAVE UID and PIN for %d ingredients", ingredients.size()));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefWidth(750);
        scrollPane.setPrefHeight(500);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label infoLabel = new Label("Configure Arduino settings for each ingredient:");
        infoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        Label hintLabel = new Label("💡 Enter SLAVE UID (e.g., 0x1, 0x2) and PIN (e.g., 12, A0) for each ingredient");
        hintLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        
        content.getChildren().addAll(infoLabel, hintLabel);
        
        // Create a form row for each ingredient
        for (Ingredient ing : ingredients) {
            VBox ingredientBox = new VBox(5);
            ingredientBox.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #f9f9f9;");
            
            Label nameLabel = new Label(ing.getName());
            nameLabel.setStyle("-fx-font-weight: bold;");
            
            HBox fieldsBox = new HBox(15);
            fieldsBox.setAlignment(Pos.CENTER_LEFT);
            
            // SLAVE UID field
            VBox uidBox = new VBox(3);
            Label uidLabel = new Label("SLAVE UID:");
            uidLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            TextField uidField = new TextField(ing.getArduinoUid() != null ? ing.getArduinoUid() : "");
            uidField.setPromptText("0x1, 0x2, etc.");
            uidField.setPrefWidth(150);
            uidField.textProperty().addListener((obs, oldVal, newVal) -> {
                ing.setArduinoUid(newVal.trim().isEmpty() ? null : newVal.trim());
            });
            uidBox.getChildren().addAll(uidLabel, uidField);
            
            // PIN field
            VBox pinBox = new VBox(3);
            Label pinLabel = new Label("Arduino PIN:");
            pinLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            TextField pinField = new TextField(ing.getArduinoPin() != null ? String.valueOf(ing.getArduinoPin()) : "");
            pinField.setPromptText("2-69 or A0-A15");
            pinField.setPrefWidth(150);
            pinField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.trim().isEmpty()) {
                    ing.setArduinoPin(null);
                    pinField.setStyle("");
                } else {
                    try {
                        String text = newVal.trim();
                        if (text.toUpperCase().startsWith("A")) {
                            int analogPin = Integer.parseInt(text.substring(1));
                            ing.setArduinoPin(54 + analogPin);
                            pinField.setStyle("-fx-border-color: green;");
                        } else {
                            ing.setArduinoPin(Integer.parseInt(text));
                            pinField.setStyle("-fx-border-color: green;");
                        }
                    } catch (NumberFormatException ex) {
                        pinField.setStyle("-fx-border-color: red;");
                    }
                }
            });
            pinBox.getChildren().addAll(pinLabel, pinField);
            
            fieldsBox.getChildren().addAll(uidBox, pinBox);
            ingredientBox.getChildren().addAll(nameLabel, fieldsBox);
            
            content.getChildren().add(ingredientBox);
        }
        
        scrollPane.setContent(content);
        dialog.getDialogPane().setContent(scrollPane);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Save all configurations
                int savedCount = 0;
                for (Ingredient ing : ingredients) {
                    try {
                        ingredientRepository.save(ing); // save() works for both insert and update
                        savedCount++;
                    } catch (Exception e) {
                        System.err.println("Failed to update ingredient: " + e.getMessage());
                    }
                }
                
                showAlert(Alert.AlertType.INFORMATION, "Configuration Saved", 
                         "Arduino configuration saved for " + savedCount + " ingredients.");
                
                // Call the completion callback
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
    
    private void createAndSaveRecipeFromMatches(String recipeName, List<IngredientMatcher.MatchedIngredient> validMatches, int defaultDuration) {
        // Determine batch size
        int batchSize = 100; // Default
        double totalPercentage = 0;
        int ingredientsWithPercentage = 0;
        
        // Calculate total percentage from PDF
        for (IngredientMatcher.MatchedIngredient match : validMatches) {
            Double percentage = match.getPdfIngredient().getPercentage();
            if (percentage != null) {
                totalPercentage += percentage;
                ingredientsWithPercentage++;
            }
        }
        
        // If we have percentages, use them; otherwise use default duration
        boolean usePercentages = ingredientsWithPercentage > 0;
        
        // If total percentage is close to 100, we can calculate batch size accurately
        if (usePercentages && totalPercentage > 90 && totalPercentage <= 100) {
            batchSize = 100; // Standard 100g batch
        }
        
        // Create recipe
        Recipe recipe = Recipe.builder()
            .name(recipeName)
            .category("Imported")
            .description("Imported from PDF")
            .batchSize(batchSize)
            .active(true)
            .build();
        
        // Add ingredients
        List<RecipeIngredient> recipeIngredients = new ArrayList<>();
        int order = 0;
        
        for (IngredientMatcher.MatchedIngredient match : validMatches) {
            PdfRecipeParser.PdfIngredient pdfIng = match.getPdfIngredient();
            Ingredient dbIng = match.getDbIngredient();
            
            // Calculate duration from percentage if available
            int duration;
            double percentage = 0;
            
            if (pdfIng.getPercentage() != null) {
                // Use percentage from PDF
                percentage = pdfIng.getPercentage();
                duration = QuantityCalculator.calculateDurationFromPercentage(percentage, batchSize);
            } else {
                // Use default duration
                duration = defaultDuration;
                // Calculate percentage from default duration
                percentage = QuantityCalculator.calculatePercentageFromDuration(duration, batchSize);
            }
            
            RecipeIngredient ri = RecipeIngredient.builder()
                .ingredientId(dbIng.getId())
                .pulseDuration(duration)
                .quantity(percentage)  // Store percentage
                .unit("%")  // Mark as percentage
                .sequenceOrder(order++)
                .ingredient(dbIng)
                .build();
            
            recipeIngredients.add(ri);
        }
        
        recipe.setIngredients(recipeIngredients);
        
        // Save to database
        try {
            Recipe saved = recipeRepository.save(recipe);
            
            if (saved != null) {
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                         String.format("Recipe '%s' created with %d ingredients!", 
                                      saved.getName(), recipeIngredients.size()));
                loadRecipes();
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to save recipe to database.");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Save Error", 
                     "Failed to save recipe: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private Alert createAlert(Alert.AlertType type) {
        Alert alert = new Alert(type);
        IconSupport.applyTo(alert);
        return alert;
    }

    private <T> Dialog<T> createDialog() {
        Dialog<T> dialog = new Dialog<>();
        IconSupport.applyTo(dialog);
        return dialog;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = createAlert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Check if ingredient has all required Arduino configuration fields
     */
    private boolean isIngredientFullyConfigured(Ingredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        
        // If ingredient is associated with a master, it's considered configured
        if (ingredient.getMasterIngredientId() != null) {
            return true;
        }
        
        // At least one pin must be set (large OR small)
        boolean hasPin = (ingredient.getArduinoPin() != null) || (ingredient.getArduinoPinSmall() != null);
        
        // At least one UID must be set (for the pin that is set)
        boolean hasUid = false;
        if (ingredient.getArduinoPin() != null) {
            hasUid = ingredient.getArduinoUid() != null && !ingredient.getArduinoUid().trim().isEmpty();
        }
        if (!hasUid && ingredient.getArduinoPinSmall() != null) {
            hasUid = ingredient.getArduinoUidSmall() != null && !ingredient.getArduinoUidSmall().trim().isEmpty();
        }
        
        return hasPin && hasUid;
    }
    
    /**
     * Get list of missing configuration fields
     */
    private List<String> getMissingConfigurationFields(Ingredient ingredient) {
        List<String> missing = new ArrayList<>();
        
        if (ingredient == null) {
            missing.add("Ingredient not in database - all fields required");
            return missing;
        }
        
        // If ingredient is associated with a master, no fields are missing
        if (ingredient.getMasterIngredientId() != null) {
            return missing;
        }
        
        if (ingredient.getArduinoPin() == null && ingredient.getArduinoPinSmall() == null) {
            missing.add("At least one PIN (Large or Small) or associate with existing ingredient");
        }
        
        if (ingredient.getArduinoPin() != null) {
            if (ingredient.getArduinoUid() == null || ingredient.getArduinoUid().trim().isEmpty()) {
                missing.add("SLAVE UID (for Large Pump)");
            }
        }
        
        if (ingredient.getArduinoPinSmall() != null) {
            if (ingredient.getArduinoUidSmall() == null || ingredient.getArduinoUidSmall().trim().isEmpty()) {
                missing.add("SLAVE UID Small (for Small Pump)");
            }
        }
        
        return missing;
    }
    
    /**
     * Configuration info for PDF import ingredients
     */
    private static class IngredientConfigurationInfo {
        IngredientMatcher.MatchedIngredient match; // can be updated when associating/disassociating
        Ingredient dbIngredient; // null if not in DB - can be updated after creation
        final PdfRecipeParser.PdfIngredient pdfIngredient;
        boolean existsInDb; // can be updated when associating/disassociating
        boolean isConfigured; // can be updated after configuration
        List<String> missingFields; // can be updated after configuration
        
        IngredientConfigurationInfo(IngredientMatcher.MatchedIngredient match, Ingredient dbIngredient,
                                   PdfRecipeParser.PdfIngredient pdfIngredient, boolean existsInDb,
                                   boolean isConfigured, List<String> missingFields) {
            this.match = match;
            this.dbIngredient = dbIngredient;
            this.pdfIngredient = pdfIngredient;
            this.existsInDb = existsInDb;
            this.isConfigured = isConfigured;
            this.missingFields = missingFields;
        }
    }
    
    /**
     * Update statistics label with current configuration status
     */
    private void updateStatsLabel(Label statsLabel, List<IngredientConfigurationInfo> configInfos) {
        long inDb = configInfos.stream().filter(c -> c.existsInDb).count();
        long configured = configInfos.stream().filter(c -> c.isConfigured).count();
        long needsConfig = configInfos.stream().filter(c -> !c.isConfigured).count();
        long notInDb = configInfos.stream().filter(c -> !c.existsInDb).count();
        
        String statsText = String.format(
            "📊 Found %d ingredients:\n" +
            "  ✓ In database: %d (%d configured, %d need configuration)\n" +
            "  ⚠ Not in database: %d (need complete setup)",
            configInfos.size(), inDb, configured, needsConfig, notInDb
        );
        statsLabel.setText(statsText);
    }
    
    /**
     * Show dialog for reviewing and configuring ingredients from PDF import
     */
    private void showPdfImportConfigurationDialog(String pdfFileName, List<IngredientConfigurationInfo> configInfos) {
        if (configInfos.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Ingredients", 
                     "No valid ingredients found in PDF.");
            return;
        }
        
        Dialog<ButtonType> dialog = createDialog();
        dialog.setTitle("Configure PDF Ingredients - " + pdfFileName);
        dialog.setHeaderText("Review and configure ingredients before creating recipe");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(1100);
        content.setPrefHeight(700);
        
        // Summary stats
        long inDb = configInfos.stream().filter(c -> c.existsInDb).count();
        long configured = configInfos.stream().filter(c -> c.isConfigured).count();
        long needsConfig = configInfos.stream().filter(c -> !c.isConfigured).count();
        long notInDb = configInfos.stream().filter(c -> !c.existsInDb).count();
        
        // More detailed breakdown for correct warning calculation
        long inDbButNeedsConfig = configInfos.stream().filter(c -> c.existsInDb && !c.isConfigured).count();
        long notInDbAtAll = configInfos.stream().filter(c -> !c.existsInDb).count();
        
        String statsText = String.format(
            "📊 Found %d ingredients:\n" +
            "  ✓ In database: %d (%d configured, %d need configuration)\n" +
            "  ⚠ Not in database: %d (need complete setup)",
            configInfos.size(), inDb, configured, needsConfig, notInDb
        );
        
        Label statsLabel = new Label(statsText);
        statsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        // Warning if any ingredients are not configured
        // Calculate total correctly: ingredients that need config are either in DB but not configured OR not in DB
        long totalNeedingConfig = inDbButNeedsConfig + notInDbAtAll;
        if (totalNeedingConfig > 0) {
            Label warningLabel = new Label(
                "⚠ WARNING: " + totalNeedingConfig + " ingredient(s) need configuration!\n" +
                "Click 'Edit' to configure before creating recipe."
            );
            warningLabel.setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold; -fx-font-size: 12px;");
            content.getChildren().add(warningLabel);
        }
        
        // Table with ingredients
        TableView<IngredientConfigurationInfo> table = new TableView<>();
        ObservableList<IngredientConfigurationInfo> items = FXCollections.observableArrayList(configInfos);
        table.setItems(items);
        table.setPrefHeight(400);
        
        // Columns
        TableColumn<IngredientConfigurationInfo, String> pdfNameCol = new TableColumn<>("PDF Name");
        pdfNameCol.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().pdfIngredient.getName()));
        pdfNameCol.setPrefWidth(200);
        
        TableColumn<IngredientConfigurationInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> {
            IngredientConfigurationInfo info = data.getValue();
            String status;
            if (!info.existsInDb) {
                status = "⚠ NOT IN DB";
            } else {
                // If configured, show configured status regardless of match type
                if (info.isConfigured) {
                    status = "✓ Configured";
                } else {
                    // Not configured yet - show needs review or needs config
                    if (info.match.getMatchType() == IngredientMatcher.MatchedIngredient.MatchType.FUZZY_NAME ||
                        (info.match.getMatchType() == IngredientMatcher.MatchedIngredient.MatchType.EXACT_CAS && 
                         !info.pdfIngredient.getName().trim().equalsIgnoreCase(info.dbIngredient.getName().trim()))) {
                        status = "⚠ Needs Review";
                    } else {
                        status = "⚠ Needs Config";
                    }
                }
            }
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        statusCol.setPrefWidth(150);
        
        TableColumn<IngredientConfigurationInfo, String> dbNameCol = new TableColumn<>("Database Name");
        dbNameCol.setCellValueFactory(data -> {
            IngredientConfigurationInfo info = data.getValue();
            String name = info.dbIngredient != null ? info.dbIngredient.getName() : "— (will be created)";
            return new javafx.beans.property.SimpleStringProperty(name);
        });
        dbNameCol.setPrefWidth(200);
        
        TableColumn<IngredientConfigurationInfo, String> missingCol = new TableColumn<>("Missing Fields");
        missingCol.setCellValueFactory(data -> {
            IngredientConfigurationInfo info = data.getValue();
            String missing = String.join(", ", info.missingFields);
            if (missing.isEmpty() && info.isConfigured) {
                missing = "None";
            }
            return new javafx.beans.property.SimpleStringProperty(missing);
        });
        missingCol.setPrefWidth(250);
        
        TableColumn<IngredientConfigurationInfo, String> pinCol = new TableColumn<>("Pins (UID:PIN)");
        pinCol.setCellValueFactory(data -> {
            IngredientConfigurationInfo info = data.getValue();
            if (info.dbIngredient == null) return new javafx.beans.property.SimpleStringProperty("—");
            String pins = "";
            if (info.dbIngredient.getArduinoPin() != null) {
                String uid = info.dbIngredient.getArduinoUid() != null ? info.dbIngredient.getArduinoUid() : "?";
                pins += "Large: " + uid + ":" + info.dbIngredient.getArduinoPin();
            }
            if (info.dbIngredient.getArduinoPinSmall() != null) {
                if (!pins.isEmpty()) pins += ", ";
                String uid = info.dbIngredient.getArduinoUidSmall() != null ? info.dbIngredient.getArduinoUidSmall() : "?";
                pins += "Small: " + uid + ":" + info.dbIngredient.getArduinoPinSmall();
            }
            return new javafx.beans.property.SimpleStringProperty(pins.isEmpty() ? "None" : pins);
        });
        pinCol.setPrefWidth(220);
        
        TableColumn<IngredientConfigurationInfo, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(data -> {
            PdfRecipeParser.PdfIngredient pdf = data.getValue().pdfIngredient;
            String text = "";
            if (pdf.getPercentage() != null) {
                text = pdf.getPercentage() + "%";
            } else if (pdf.getAmount() != null) {
                text = pdf.getAmount() + " " + (pdf.getUnit() != null ? pdf.getUnit() : "");
            } else {
                text = "-";
            }
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        amountCol.setPrefWidth(100);
        
        table.getColumns().addAll(pdfNameCol, statusCol, dbNameCol, missingCol, pinCol, amountCol);
        
        // Edit button
        Button editButton = new Button("⚙️ Edit/Configure Selected");
        editButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        editButton.setOnAction(e -> {
            IngredientConfigurationInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editIngredientConfiguration(selected, dialog);
                // After editing, reload ingredient from DB and update status
                if (selected.dbIngredient != null && selected.dbIngredient.getId() != null) {
                    ingredientRepository.findById(selected.dbIngredient.getId()).ifPresent(loaded -> {
                        selected.dbIngredient = loaded;
                        // Re-verify configuration status using loaded ingredient
                        selected.isConfigured = isIngredientFullyConfigured(loaded);
                        selected.missingFields = getMissingConfigurationFields(loaded);
                    });
                }
                // Update stats and refresh table
                updateStatsLabel(statsLabel, configInfos);
                table.refresh();
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an ingredient to configure.");
            }
        });
        
        // Associate/Disassociate button for FUZZY matches or NOT IN DB
        Button associateButton = new Button("🔗 Change Match");
        associateButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        associateButton.setTooltip(new Tooltip("Change the matched ingredient for fuzzy matches or choose different ingredient"));
        associateButton.setOnAction(e -> {
            IngredientConfigurationInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ButtonType result = showAssociateIngredientDialog(selected, items);
                
                // After association, reload ingredient from DB and update status if dialog returned OK
                if (result == ButtonType.OK && selected.dbIngredient != null) {
                    // If ingredient has ID, reload from DB to ensure all fields are synced
                    if (selected.dbIngredient.getId() != null) {
                        ingredientRepository.findById(selected.dbIngredient.getId()).ifPresent(loaded -> {
                            selected.dbIngredient = loaded;
                            // Re-verify configuration status using the reloaded ingredient
                            selected.isConfigured = isIngredientFullyConfigured(loaded);
                            selected.missingFields = getMissingConfigurationFields(loaded);
                            // Ensure existsInDb is set correctly
                            selected.existsInDb = true;
                        });
                    }
                }
                
                // Always update stats and refresh table after dialog closes
                updateStatsLabel(statsLabel, configInfos);
                
                // Force table refresh to show updated status
                int selectedIndex = table.getSelectionModel().getSelectedIndex();
                
                // Update the item in the ObservableList to trigger change notifications
                if (selectedIndex >= 0 && selectedIndex < items.size()) {
                    // Create a new instance to force change detection, or update in place
                    items.set(selectedIndex, selected);
                }
                
                // Force immediate refresh on JavaFX thread
                Platform.runLater(() -> {
                    // Refresh all cells in the table
                    table.refresh();
                    
                    // Also try to refresh individual columns by accessing them
                    for (TableColumn<IngredientConfigurationInfo, ?> col : table.getColumns()) {
                        col.setVisible(false);
                        col.setVisible(true);
                    }
                    
                    // Re-select the same row to ensure it's visible
                    if (selectedIndex >= 0 && selectedIndex < table.getItems().size()) {
                        table.getSelectionModel().clearAndSelect(selectedIndex);
                        table.scrollTo(selectedIndex);
                    }
                });
            } else {
                showAlert(Alert.AlertType.WARNING, "No Selection", 
                         "Please select an ingredient to change match.");
            }
        });
        
        HBox buttonBox = new HBox(10, editButton, associateButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        // Recipe name input
        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label("Recipe Name:");
        nameLabel.setPrefWidth(120);
        TextField recipeNameField = new TextField();
        recipeNameField.setPromptText("Enter recipe name...");
        recipeNameField.setPrefWidth(300);
        recipeNameField.setText(pdfFileName.replace(".pdf", "").replaceAll("[^a-zA-Z0-9\\s]", " ").trim());
        nameBox.getChildren().addAll(nameLabel, recipeNameField);
        
        content.getChildren().addAll(statsLabel, table, buttonBox, nameBox);
        dialog.getDialogPane().setContent(content);
        
        // Handle OK button - verify all are configured before allowing recipe creation
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                // Check if all ingredients are configured
                List<IngredientConfigurationInfo> notConfigured = configInfos.stream()
                    .filter(c -> !c.isConfigured)
                    .collect(Collectors.toList());
                
                if (!notConfigured.isEmpty()) {
                    StringBuilder warning = new StringBuilder();
                    warning.append("⚠ Cannot create recipe!\n\n");
                    warning.append(notConfigured.size()).append(" ingredient(s) are not fully configured:\n\n");
                    for (int i = 0; i < Math.min(5, notConfigured.size()); i++) {
                        IngredientConfigurationInfo info = notConfigured.get(i);
                        warning.append("  • ").append(info.pdfIngredient.getName());
                        if (!info.missingFields.isEmpty()) {
                            warning.append(" - Missing: ").append(String.join(", ", info.missingFields));
                        }
                        warning.append("\n");
                    }
                    if (notConfigured.size() > 5) {
                        warning.append("  ... and ").append(notConfigured.size() - 5).append(" more\n");
                    }
                    warning.append("\nPlease configure all ingredients before creating recipe.");
                    
                    showAlert(Alert.AlertType.WARNING, "Configuration Required", warning.toString());
                    return null; // Don't close dialog
                }
                
                // Save all configured ingredients to database before creating recipe
                for (IngredientConfigurationInfo configInfo : configInfos) {
                    if (configInfo.isConfigured && configInfo.dbIngredient != null) {
                        try {
                            // ALWAYS save configured ingredients to ensure they're persisted
                            Ingredient saved = ingredientRepository.save(configInfo.dbIngredient);
                            // Reload to ensure all fields are synced
                            Ingredient reloaded = ingredientRepository.findById(saved.getId()).orElse(saved);
                            configInfo.dbIngredient = reloaded;
                            configInfo.existsInDb = true;
                            // Re-verify configuration
                            configInfo.isConfigured = isIngredientFullyConfigured(reloaded);
                            configInfo.missingFields = getMissingConfigurationFields(reloaded);
                        } catch (Exception e) {
                            System.err.println("Error saving ingredient: " + configInfo.dbIngredient.getName() + " - " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                
                // All configured - proceed with recipe creation
                String recipeName = recipeNameField.getText().trim();
                if (recipeName.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Invalid Name", "Please enter a recipe name.");
                    return null;
                }
                
                // Convert configInfos to matches and create recipe
                createRecipeFromConfigInfos(recipeName, configInfos);
                return ButtonType.OK;
            }
            return buttonType;
        });
        
        dialog.showAndWait();
    }
    
    /**
     * Show dialog to associate or disassociate a fuzzy-matched ingredient
     */
    private ButtonType showAssociateIngredientDialog(IngredientConfigurationInfo configInfo, ObservableList<IngredientConfigurationInfo> configInfosList) {
        Dialog<ButtonType> associateDialog = createDialog();
        associateDialog.setTitle("Associate/Disassociate Ingredient");
        
        final IngredientConfigurationInfo finalConfigInfo = configInfo;
        String pdfName = finalConfigInfo.pdfIngredient.getName();
        String pdfCas = finalConfigInfo.pdfIngredient.getCasNumber();
        String currentMatch = finalConfigInfo.dbIngredient != null ? finalConfigInfo.dbIngredient.getName() : "None";
        
        associateDialog.setHeaderText("PDF Ingredient: " + pdfName + 
                                     (pdfCas != null ? " (CAS: " + pdfCas + ")" : ""));
        associateDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);
        
        Label currentMatchLabel = new Label("Currently matched to: " + currentMatch);
        currentMatchLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        Label searchLabel = new Label("Search for different ingredient:");
        TextField searchField = new TextField();
        searchField.setPromptText("Type ingredient name or CAS to search...");
        searchField.setPrefWidth(500);
        
        // List of all ingredients (display name + CAS)
        // IMPORTANT: Only show ingredients with matching CAS number
        ListView<String> ingredientsList = new ListView<>();
        ingredientsList.setPrefHeight(300);
        
        List<String> allIngredientNames = new ArrayList<>();
        List<Ingredient> allIngredientsList = new ArrayList<>();
        List<Ingredient> filteredIngredientsList = new ArrayList<>(); // Only ingredients with matching CAS
        
        // Warning label if CAS number doesn't match
        Label casWarningLabel = new Label();
        casWarningLabel.setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold; -fx-font-size: 12px;");
        casWarningLabel.setWrapText(true);
        
        try {
            allIngredientsList = ingredientRepository.findAll();
            
            // CRITICAL: Filter ingredients - ONLY show those with matching CAS number
            if (pdfCas != null && !pdfCas.trim().isEmpty()) {
                String pdfCasTrimmed = pdfCas.trim();
                
                // STRICT FILTER: Only ingredients with EXACT CAS match (case-insensitive)
                filteredIngredientsList = allIngredientsList.stream()
                    .filter(ing -> {
                        String ingCas = ing.getCasNumber();
                        if (ingCas == null || ingCas.trim().isEmpty()) {
                            return false; // Skip ingredients without CAS
                        }
                        String ingCasTrimmed = ingCas.trim();
                        return pdfCasTrimmed.equalsIgnoreCase(ingCasTrimmed);
                    })
                    .collect(Collectors.toList());
                
                if (filteredIngredientsList.isEmpty()) {
                    casWarningLabel.setText("⚠ WARNING: No ingredients found with CAS number: " + pdfCas + "\n" +
                                          "Association is NOT RECOMMENDED. Ingredients must have matching CAS numbers.\n\n" +
                                          "Only ingredients with identical CAS numbers can be associated.");
                    ingredientsList.setDisable(true);
                } else {
                    casWarningLabel.setText("✓ Showing ONLY ingredients with matching CAS number: " + pdfCas + 
                                          " (" + filteredIngredientsList.size() + " found)\n" +
                                          "Ingredients with different CAS numbers are NOT shown.");
                    ingredientsList.setDisable(false);
                }
            } else {
                // PDF ingredient has no CAS number - show warning
                casWarningLabel.setText("⚠ WARNING: PDF ingredient has no CAS number.\n" +
                                      "Cannot safely associate without matching CAS number.\n\n" +
                                      "Please ensure the PDF ingredient has a CAS number before associating.\n" +
                                      "Association is BLOCKED for safety.");
                ingredientsList.setDisable(true);
                filteredIngredientsList = new ArrayList<>(); // Empty list
            }
            
            // Build display names ONLY from filtered ingredients (with matching CAS)
            // CRITICAL: Never add ingredients without CAS matching
            allIngredientNames = filteredIngredientsList.stream()
                .map(ing -> {
                    String name = ing.getName();
                    String cas = ing.getCasNumber();
                    if (cas != null && !cas.trim().isEmpty()) {
                        return name + " (CAS: " + cas + ")";
                    }
                    return name;
                })
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
            
            // CRITICAL: Clear list first, then add ONLY filtered ingredients
            ingredientsList.getItems().clear();
            ingredientsList.getItems().addAll(allIngredientNames);
        } catch (Exception e) {
            System.err.println("Error loading ingredients: " + e.getMessage());
            casWarningLabel.setText("ERROR: Failed to load ingredients from database.");
        }
        
        // Filter list when typing (search by name or CAS)
        // IMPORTANT: Only search within filtered ingredients (with matching CAS)
        // Make these final for use in lambdas
        final String finalPdfCas = pdfCas;
        final List<Ingredient> finalFilteredIngredients = filteredIngredientsList;
        final List<String> finalAllIngredientNames = allIngredientNames;
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                ingredientsList.getItems().clear();
                ingredientsList.getItems().addAll(finalAllIngredientNames);
            } else {
                String filter = newVal.toLowerCase();
                List<String> filtered = finalFilteredIngredients.stream()
                    .map(ing -> {
                        boolean nameMatch = ing.getName() != null && ing.getName().toLowerCase().contains(filter);
                        boolean casMatch = ing.getCasNumber() != null && ing.getCasNumber().toLowerCase().contains(filter);
                        if (nameMatch || casMatch) {
                            String name = ing.getName();
                            String cas = ing.getCasNumber();
                            if (cas != null && !cas.trim().isEmpty()) {
                                return name + " (CAS: " + cas + ")";
                            }
                            return name;
                        }
                        return null;
                    })
                    .filter(name -> name != null)
                    .sorted()
                    .collect(Collectors.toList());
                ingredientsList.getItems().clear();
                ingredientsList.getItems().addAll(filtered);
            }
        });
        
        Label infoLabel = new Label("Select an ingredient from the list (only ingredients with matching CAS number are shown).");
        infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        infoLabel.setWrapText(true);
        
        content.getChildren().addAll(currentMatchLabel, casWarningLabel, searchLabel, searchField, ingredientsList, infoLabel);
        associateDialog.getDialogPane().setContent(content);
        
        // Get the OK button and disable it if no matching CAS ingredients found
        Button okButton = (Button) associateDialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null && (finalFilteredIngredients.isEmpty() || finalPdfCas == null || finalPdfCas.trim().isEmpty())) {
            okButton.setDisable(true);
            okButton.setTooltip(new Tooltip("Cannot associate: No ingredients with matching CAS number found"));
        }
        
        // Enable/disable OK button based on selection
        ingredientsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (okButton != null) {
                boolean canAssociate = newVal != null && 
                                      !finalFilteredIngredients.isEmpty() && 
                                      finalPdfCas != null && 
                                      !finalPdfCas.trim().isEmpty();
                okButton.setDisable(!canAssociate);
            }
        });
        
        associateDialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                // CRITICAL VALIDATION: Check CAS number match before allowing association
                if (finalPdfCas == null || finalPdfCas.trim().isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "CAS Number Required", 
                            "Cannot associate: PDF ingredient must have a CAS number.\n\n" +
                            "Please ensure the PDF ingredient has a valid CAS number before associating.");
                    return null; // Don't close dialog
                }
                
                if (finalFilteredIngredients.isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "No Matching Ingredients", 
                            "Cannot associate: No ingredients found with matching CAS number: " + finalPdfCas + "\n\n" +
                            "Association is only allowed for ingredients with identical CAS numbers.");
                    return null; // Don't close dialog
                }
                
                String selectedName = ingredientsList.getSelectionModel().getSelectedItem();
                
                // Disassociate if no selection (but only if we have matching ingredients available)
                if (selectedName == null) {
                    // Allow disassociation
                    finalConfigInfo.dbIngredient = null;
                    finalConfigInfo.existsInDb = false;
                    finalConfigInfo.isConfigured = false;
                    finalConfigInfo.missingFields = Arrays.asList("All fields required");
                    finalConfigInfo.match = new IngredientMatcher.MatchedIngredient(
                        finalConfigInfo.pdfIngredient, null, 
                        IngredientMatcher.MatchedIngredient.MatchType.NO_MATCH, 0.0);
                    return ButtonType.OK;
                }
                
                // Associate with selected ingredient
                try {
                    // Extract name from display string (remove CAS part if present)
                    final String selectedNameCleaned;
                    if (selectedName.contains(" (CAS:")) {
                        selectedNameCleaned = selectedName.substring(0, selectedName.indexOf(" (CAS:"));
                    } else {
                        selectedNameCleaned = selectedName;
                    }
                    
                    Ingredient selectedIng = finalFilteredIngredients.stream()
                        .filter(ing -> selectedNameCleaned.equals(ing.getName()))
                        .findFirst()
                        .orElse(null);
                    
                    if (selectedIng != null) {
                        // VALIDATION: Double-check CAS number match before associating
                        String selectedCas = selectedIng.getCasNumber();
                        if (selectedCas == null || selectedCas.trim().isEmpty() || 
                            !finalPdfCas.trim().equalsIgnoreCase(selectedCas.trim())) {
                            showAlert(Alert.AlertType.ERROR, "CAS Number Mismatch", 
                                    "ERROR: Selected ingredient has different CAS number!\n\n" +
                                    "PDF CAS: " + finalPdfCas + "\n" +
                                    "Selected CAS: " + (selectedCas != null ? selectedCas : "NULL") + "\n\n" +
                                    "Association cancelled for safety.");
                            return null; // Don't close dialog
                        }
                        // Check if we need to create a new ingredient to associate
                        // Only if the PDF ingredient is NOT already in database
                        if (!finalConfigInfo.existsInDb || finalConfigInfo.dbIngredient == null) {
                            // Create new ingredient and associate it with the selected one
                            Ingredient newIngredient = Ingredient.builder()
                                .name(finalConfigInfo.pdfIngredient.getName().trim())
                                .casNumber(finalConfigInfo.pdfIngredient.getCasNumber() != null ? 
                                         finalConfigInfo.pdfIngredient.getCasNumber().trim() : null)
                                .category("Imported")
                                .ifraStatus("Unknown")
                                .description("Imported from PDF: " + finalConfigInfo.pdfIngredient.getName())
                                .masterIngredientId(selectedIng.getId())
                                .active(true)
                                .build();
                            
                            // Save the new ingredient with association
                            try {
                                Ingredient saved = ingredientRepository.save(newIngredient);
                                // Reload from DB to ensure all fields are properly synced
                                Ingredient reloaded = ingredientRepository.findById(saved.getId())
                                    .orElse(saved);
                                finalConfigInfo.dbIngredient = reloaded;
                                finalConfigInfo.existsInDb = true;
                                // Verify configuration status using the reloaded ingredient
                                boolean isConfigured = isIngredientFullyConfigured(reloaded);
                                finalConfigInfo.isConfigured = isConfigured;
                                finalConfigInfo.missingFields = getMissingConfigurationFields(reloaded);
                            } catch (Exception e) {
                                System.err.println("ERROR saving associated ingredient: " + e.getMessage());
                                e.printStackTrace();
                                showAlert(Alert.AlertType.ERROR, "Error", 
                                         "Failed to save associated ingredient: " + e.getMessage());
                                return null;
                            }
                        } else {
                            // Update existing ingredient to associate it
                            finalConfigInfo.dbIngredient.setMasterIngredientId(selectedIng.getId());
                            try {
                                Ingredient saved = ingredientRepository.save(finalConfigInfo.dbIngredient);
                                // Reload from DB to ensure all fields are properly synced
                                Ingredient reloaded = ingredientRepository.findById(saved.getId())
                                    .orElse(saved);
                                finalConfigInfo.dbIngredient = reloaded;
                                // Verify configuration status using the reloaded ingredient
                                boolean isConfigured = isIngredientFullyConfigured(reloaded);
                                finalConfigInfo.isConfigured = isConfigured;
                                finalConfigInfo.missingFields = getMissingConfigurationFields(reloaded);
                            } catch (Exception e) {
                                System.err.println("ERROR updating association: " + e.getMessage());
                                e.printStackTrace();
                                showAlert(Alert.AlertType.ERROR, "Error", 
                                         "Failed to update association: " + e.getMessage());
                                return null;
                            }
                        }
                        
                        // Determine match type
                        IngredientMatcher.MatchedIngredient.MatchType matchType;
                        if (finalPdfCas != null && finalPdfCas.equalsIgnoreCase(selectedIng.getCasNumber())) {
                            matchType = IngredientMatcher.MatchedIngredient.MatchType.EXACT_CAS;
                        } else if (pdfName.equalsIgnoreCase(selectedNameCleaned)) {
                            matchType = IngredientMatcher.MatchedIngredient.MatchType.EXACT_NAME;
                        } else {
                            matchType = IngredientMatcher.MatchedIngredient.MatchType.FUZZY_NAME;
                        }
                        
                        finalConfigInfo.match = new IngredientMatcher.MatchedIngredient(
                            finalConfigInfo.pdfIngredient, finalConfigInfo.dbIngredient, matchType, 
                            matchType == IngredientMatcher.MatchedIngredient.MatchType.EXACT_CAS ? 1.0 : 0.85);
                        
                        return ButtonType.OK;
                    }
                } catch (Exception e) {
                    System.err.println("Error associating ingredient: " + e.getMessage());
                }
            }
            return buttonType;
        });
        
        Optional<ButtonType> result = associateDialog.showAndWait();
        return result.orElse(ButtonType.CANCEL);
    }
    
    /**
     * Edit/configure an ingredient from PDF import
     */
    private void editIngredientConfiguration(IngredientConfigurationInfo configInfo, Dialog<?> parentDialog) {
        // This will open a dialog similar to Arduino configuration in IngredientsView
        // For now, we'll use a simplified version
        // TODO: Could reuse the Arduino configuration dialog from IngredientsView
        
        Dialog<ButtonType> editDialog = createDialog();
        editDialog.setTitle("Configure Ingredient");
        editDialog.setHeaderText("Configure: " + configInfo.pdfIngredient.getName());
        editDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        VBox grid = new VBox(10);
        grid.setPadding(new Insets(20));
        
        // Use final array reference to allow modification in lambda
        final Ingredient[] ingredientRef = new Ingredient[1];
        ingredientRef[0] = configInfo.dbIngredient;
        final boolean isNew = (ingredientRef[0] == null);
        
        if (isNew) {
            // Create new ingredient from PDF data
            ingredientRef[0] = Ingredient.builder()
                .name(configInfo.pdfIngredient.getName().trim())
                .casNumber(configInfo.pdfIngredient.getCasNumber() != null ? 
                         configInfo.pdfIngredient.getCasNumber().trim() : null)
                .category("Imported")
                .ifraStatus("Unknown")
                .description("Imported from PDF: " + configInfo.pdfIngredient.getName())
                .active(true)
                .build();
        }
        
        final Ingredient ingredient = ingredientRef[0];
        
        // Name field (editable for new, read-only for existing)
        HBox nameBox = new HBox(10);
        nameBox.getChildren().add(new Label("Name:"));
        TextField nameField = new TextField(ingredient.getName());
        nameField.setEditable(isNew);
        nameField.setPrefWidth(300);
        nameBox.getChildren().add(nameField);
        
        // CAS Number
        HBox casBox = new HBox(10);
        casBox.getChildren().add(new Label("CAS Number:"));
        TextField casField = new TextField(ingredient.getCasNumber() != null ? ingredient.getCasNumber() : "");
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
        editDialog.getDialogPane().setContent(grid);
        
        editDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Update ingredient with values from form
                    ingredient.setName(nameField.getText().trim());
                    ingredient.setCasNumber(casField.getText().trim().isEmpty() ? null : casField.getText().trim());
                    
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
                        // If yes, it's a replacement, not a new allocation
                        boolean isReplacement = false;
                        if (ingredient.getId() != null) {
                            try {
                                Optional<Ingredient> existingIng = ingredientRepository.findById(ingredient.getId());
                                if (existingIng.isPresent()) {
                                    Ingredient dbIng = existingIng.get();
                                    // Check if existing ingredient has any pin on this UID (Large or Small)
                                    if (ingredient.getArduinoUid().equals(dbIng.getArduinoUid()) && 
                                        dbIng.getArduinoPin() != null) {
                                        isReplacement = true; // Replacing Large pin on same UID
                                    } else if (ingredient.getArduinoUid().equals(dbIng.getArduinoUidSmall()) && 
                                               dbIng.getArduinoPinSmall() != null) {
                                        isReplacement = true; // Ingredient already has Small pin on this UID
                                    }
                                }
                            } catch (Exception e) {
                                // If we can't check, assume it's new to be safe
                            }
                        }
                        
                        // If ingredient doesn't already have a pin on this UID, it's a new allocation
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
                        
                        // Check if this ingredient already has a pin allocated to this UID in the database
                        boolean isReplacement = false;
                        if (ingredient.getId() != null) {
                            try {
                                Optional<Ingredient> existingIng = ingredientRepository.findById(ingredient.getId());
                                if (existingIng.isPresent()) {
                                    Ingredient dbIng = existingIng.get();
                                    // Check if existing ingredient has any pin on this UID (Large or Small)
                                    if (ingredient.getArduinoUidSmall().equals(dbIng.getArduinoUidSmall()) && 
                                        dbIng.getArduinoPinSmall() != null) {
                                        isReplacement = true; // Replacing Small pin on same UID
                                    } else if (ingredient.getArduinoUidSmall().equals(dbIng.getArduinoUid()) && 
                                               dbIng.getArduinoPin() != null) {
                                        isReplacement = true; // Ingredient already has Large pin on this UID
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
                    
                    // Validate configuration
                    List<String> validationErrors = validateIngredientConfiguration(ingredient);
                    if (!validationErrors.isEmpty()) {
                        showAlert(Alert.AlertType.WARNING, "Configuration Invalid", 
                                 "Please fix the following:\n" + String.join("\n", validationErrors));
                        return;
                    }
                    
                    // Save ingredient
                    Ingredient saved = ingredientRepository.save(ingredient);
                    
                    // Reload from DB to ensure all fields are properly synced
                    Ingredient reloaded = ingredientRepository.findById(saved.getId())
                        .orElse(saved);
                    
                    // Update configInfo (use array reference pattern for lambda)
                    final IngredientConfigurationInfo finalConfigInfo = configInfo;
                    finalConfigInfo.dbIngredient = reloaded;
                    // Verify configuration status using the reloaded ingredient
                    finalConfigInfo.isConfigured = isIngredientFullyConfigured(reloaded);
                    finalConfigInfo.missingFields = getMissingConfigurationFields(reloaded);
                    
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                             "Ingredient configured and saved successfully!");
                    
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", 
                             "Please enter valid numbers for SLAVE ID and PIN.");
                } catch (Exception e) {
                    String errorMessage = "Failed to save ingredient: " + e.getMessage();
                    if (e.getCause() != null) {
                        errorMessage += "\n\nCause: " + e.getCause().getMessage();
                    }
                    // Check for common SQL errors
                    if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint")) {
                        errorMessage += "\n\n⚠ A CAS number already exists in the database with this value.";
                    }
                    if (e.getMessage() != null && e.getMessage().contains("NOT NULL constraint")) {
                        errorMessage += "\n\n⚠ A required field is missing.";
                    }
                    showAlert(Alert.AlertType.ERROR, "Error Saving Ingredient", errorMessage);
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * Validate ingredient configuration
     */
    private List<String> validateIngredientConfiguration(Ingredient ingredient) {
        List<String> errors = new ArrayList<>();
        
        if (ingredient.getName() == null || ingredient.getName().trim().isEmpty()) {
            errors.add("Name is required");
        }
        
        // At least one pin must be set
        if (ingredient.getArduinoPin() == null && ingredient.getArduinoPinSmall() == null) {
            errors.add("At least one PIN must be set (Large or Small)");
        }
        
        // If Large PIN is set, UID must be set
        if (ingredient.getArduinoPin() != null) {
            if (ingredient.getArduinoUid() == null || ingredient.getArduinoUid().trim().isEmpty()) {
                errors.add("SLAVE UID is required when Large Pump PIN is set");
            }
        }
        
        // If Small PIN is set, UID Small must be set
        if (ingredient.getArduinoPinSmall() != null) {
            if (ingredient.getArduinoUidSmall() == null || ingredient.getArduinoUidSmall().trim().isEmpty()) {
                errors.add("SLAVE UID Small is required when Small Pump PIN is set");
            }
        }
        
        return errors;
    }
    
    /**
     * Create recipe from configured ingredients
     */
    private void createRecipeFromConfigInfos(String recipeName, List<IngredientConfigurationInfo> configInfos) {
        try {
            // Convert configInfos to matches (only configured ones)
            List<IngredientMatcher.MatchedIngredient> validMatches = new ArrayList<>();
            
            for (IngredientConfigurationInfo configInfo : configInfos) {
                if (configInfo.isConfigured && configInfo.dbIngredient != null) {
                    // Create match with configured ingredient
                    IngredientMatcher.MatchedIngredient match = new IngredientMatcher.MatchedIngredient(
                        configInfo.pdfIngredient,
                        configInfo.dbIngredient,
                        IngredientMatcher.MatchedIngredient.MatchType.EXACT_NAME,
                        1.0
                    );
                    validMatches.add(match);
                }
            }
            
            if (validMatches.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Valid Ingredients", 
                         "No configured ingredients to create recipe.");
                return;
            }
            
            // Create recipe (reuse existing logic)
            createRecipeFromMatches(recipeName, validMatches, "1000");
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", 
                     "Failed to create recipe: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Maximum number of pins available per Arduino UID (60 pins per Arduino Mega after excluding restricted pins)
    private static final int MAX_PINS_PER_UID = 60;
    
    /**
     * Check if a PIN is restricted (cannot be used)
     */
    private boolean isPinRestricted(Integer pin) {
        if (pin == null) {
            return false;
        }
        // Restricted pins: 0, 1, 2, 13, 20, 21, 50, 51, 52, 53
        // (system pins, SPI, I2C, serial, etc.)
        return pin == 0 || pin == 1 || pin == 2 || pin == 13 || 
               pin == 20 || pin == 21 || pin == 50 || pin == 51 || 
               pin == 52 || pin == 53;
    }
    
    /**
     * Count how many pins are currently allocated to a specific UID
     * @param uid The Arduino UID to check (e.g., "0x1")
     * @param excludeId Ingredient ID to exclude from count (current ingredient being edited)
     * @return Number of pins already allocated to this UID
     */
    private int countPinsForUid(String uid, Long excludeId) {
        if (uid == null || uid.trim().isEmpty()) {
            return 0;
        }
        
        try {
            // Use findAllWithoutMasterApply to avoid double-counting ingredients with master configuration
            List<Ingredient> allIngredients = ingredientRepository.findAllWithoutMasterApply();
            int count = 0;
            List<String> debugMatches = new ArrayList<>();
            
            for (Ingredient ing : allIngredients) {
                // Skip current ingredient being edited
                if (excludeId != null && ing.getId() != null && ing.getId().equals(excludeId)) {
                    continue;
                }
                
                // Count Large pump pin if UID matches
                if (uid.equals(ing.getArduinoUid()) && ing.getArduinoPin() != null) {
                    count++;
                    debugMatches.add(ing.getName() + " (Large, pin=" + ing.getArduinoPin() + ", masterId=" + ing.getMasterIngredientId() + ")");
                }
                
                // Count Small pump pin if UID matches
                if (uid.equals(ing.getArduinoUidSmall()) && ing.getArduinoPinSmall() != null) {
                    count++;
                    debugMatches.add(ing.getName() + " (Small, pin=" + ing.getArduinoPinSmall() + ", masterId=" + ing.getMasterIngredientId() + ")");
                }
            }
            
            System.out.println("Counting pins for UID " + uid + ": found " + count + " pins");
            if (!debugMatches.isEmpty()) {
                System.out.println("Matches: " + String.join(", ", debugMatches));
            }
            
            return count;
        } catch (Exception e) {
            System.err.println("Error counting pins for UID: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Get list of restricted pin ranges
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
                // A15 = pin 69, which is the maximum
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
     * @param uidLarge Large pump UID (can be null)
     * @param pinLarge Large pump PIN (can be null)
     * @param uidSmall Small pump UID (can be null)
     * @param excludeId Ingredient ID to exclude from search (current ingredient being edited)
     * @param pinSmall Optional Small pump PIN for direct check
     * @return Found ingredient or null
     */
    private Ingredient findIngredientByUidAndPin(String uidLarge, Integer pinLarge, 
                                                String uidSmall, Long excludeId, Integer... pinSmall) {
        try {
            List<Ingredient> allIngredients = ingredientRepository.findAll();
            
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
    
    public void setSerialManager(ro.marcman.mixer.serial.SerialManager serialManager) {
        this.serialManager = serialManager;
    }
}


