package ro.marcman.mixer.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ro.marcman.mixer.adapters.ui.ArduinoView;
import ro.marcman.mixer.adapters.ui.IngredientsView;
import ro.marcman.mixer.adapters.ui.RecipesView;
import ro.marcman.mixer.adapters.ui.MixControlView;
import ro.marcman.mixer.adapters.ui.PinMapperView;

/**
 * Main Application class for MarcmanMixer.
 * 
 * Integrates:
 * - Procesor MASTER communication via USB-serial
 * - Perfume recipe management
 * - Ingredient database
 * - Automated mixing control
 */
public class App extends Application {
    
    private static final String APP_TITLE = "MarcmanMixer - Parfum Management System";
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;
    
    private ArduinoView arduinoView;  // Keep reference for cleanup
    private static ArduinoView staticArduinoViewRef;  // Static reference for shutdown hook
    
    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println("Starting MarcmanMixer application");
            
            // Create tab pane for different sections
            TabPane tabPane = new TabPane();
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            
            // Arduino Communication Tab - Full functional UI
            Tab arduinoTab = new Tab("Procesor MASTER");
            arduinoView = new ArduinoView();
            staticArduinoViewRef = arduinoView;  // Keep static reference for shutdown hook
            arduinoTab.setContent(arduinoView);
            
            // Ingredients Tab - FULL FUNCTIONAL with database integration
            Tab ingredientsTab = new Tab("Ingredients IFRA");
            IngredientsView ingredientsView = new IngredientsView();
            ingredientsView.setSerialManager(arduinoView.getSerialManager());
            ingredientsTab.setContent(ingredientsView);
            
            // Auto-refresh ingredients when tab is selected
            ingredientsTab.setOnSelectionChanged(e -> {
                if (ingredientsTab.isSelected()) {
                    ingredientsView.refreshIngredients();
                }
            });
            
            // Recipes Tab - FULL FUNCTIONAL
            Tab recipesTab = new Tab("Recipes");
            RecipesView recipesView = new RecipesView();
            recipesView.setSerialManager(arduinoView.getSerialManager());
            recipesTab.setContent(recipesView);
            
            // Mix Control Tab - FULL FUNCTIONAL
            Tab mixTab = new Tab("Mix Control");
            MixControlView mixControlView = new MixControlView(arduinoView.getSerialManager());
            mixTab.setContent(mixControlView);
            
            // Pin Mapper Tab - Shows pin allocation status
            Tab pinMapperTab = new Tab("Pin Mapper");
            PinMapperView pinMapperView = new PinMapperView();
            pinMapperView.setSerialManager(arduinoView.getSerialManager());
            pinMapperTab.setContent(pinMapperView);
            
            // Auto-refresh pin mapper when tab is selected
            pinMapperTab.setOnSelectionChanged(e -> {
                if (pinMapperTab.isSelected()) {
                    pinMapperView.refreshData();
                }
            });
            
            tabPane.getTabs().addAll(arduinoTab, ingredientsTab, recipesTab, mixTab, pinMapperTab);
            
            // Create scene
            Scene scene = new Scene(tabPane, WINDOW_WIDTH, WINDOW_HEIGHT);
            
            // Setup stage
            primaryStage.setTitle(APP_TITLE);
            
            // Set application icon
            try {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/images/icon.png"));
                primaryStage.getIcons().add(icon);
                System.out.println("✓ Application icon loaded successfully");
            } catch (Exception e) {
                System.out.println("⚠ No application icon found (app/src/main/resources/images/icon.png)");
                System.out.println("  Application will run with default Java icon.");
            }
            
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Application closing - cleanup starting...");
                
                // Cleanup Arduino connection
                if (arduinoView != null) {
                    try {
                        arduinoView.cleanup();
                        System.out.println("Arduino connection closed");
                    } catch (Exception e) {
                        System.err.println("Error during Arduino cleanup: " + e.getMessage());
                    }
                }
                
                System.out.println("Cleanup complete - exiting");
            });
            
            primaryStage.show();
            
            System.out.println("Application started successfully");
            
        } catch (Exception e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void stop() throws Exception {
        System.out.println("JavaFX stop() called - final cleanup...");
        performCleanup();
        super.stop();
    }
    
    private void performCleanup() {
        if (arduinoView != null) {
            try {
                System.out.println("Performing cleanup...");
                arduinoView.cleanup();
                System.out.println("Cleanup complete");
            } catch (Exception e) {
                System.err.println("Error during cleanup: " + e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) {
        // Add shutdown hook to ensure cleanup ALWAYS runs
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("=== SHUTDOWN HOOK TRIGGERED ===");
            if (staticArduinoViewRef != null) {
                try {
                    staticArduinoViewRef.cleanup();
                    System.out.println("Shutdown hook cleanup complete");
                } catch (Exception e) {
                    System.err.println("Error in shutdown hook: " + e.getMessage());
                }
            }
            // Wait to ensure port is released
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Ignore
            }
            System.out.println("=== SHUTDOWN COMPLETE ===");
        }));
        
        launch(args);
    }
}


