package ro.marcman.mixer.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import ro.marcman.mixer.adapters.ui.ArduinoView;
import ro.marcman.mixer.adapters.ui.IngredientsView;
import ro.marcman.mixer.adapters.ui.RecipesView;
import ro.marcman.mixer.adapters.ui.MixControlView;
import ro.marcman.mixer.adapters.ui.PinMapperView;
import ro.marcman.mixer.adapters.ui.util.IconSupport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
            
            // NOTE: DLL copying and cleanup is done in main() before Application.launch()
            // to prevent SerialManager static initializer from deleting the correct DLL
            
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
            if (IconSupport.getAppIcons().isEmpty()) {
                System.out.println("⚠ No application icon found at /images/mixer.ico or /images/icon.png");
                System.out.println("  Application will run with default Java icon.");
            } else {
                IconSupport.applyTo(primaryStage);
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
    
    /**
     * Clean jSerialComm cache directories to force correct DLL extraction
     * This must be called BEFORE any SerialManager or SerialPort usage
     */
    private static void cleanJSerialCommCache() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String userHome = System.getProperty("user.home");
            
            // Clean Temp folder - try multiple times
            String tempJSerialComm = tempDir + File.separator + "jSerialComm";
            Path tempPath = Paths.get(tempJSerialComm);
            for (int attempt = 0; attempt < 5; attempt++) {
                if (Files.exists(tempPath)) {
                    try {
                        deleteDirectoryRecursive(tempPath.toFile());
                        // Brief wait to ensure deletion completes
                        Thread.sleep(50);
                        if (!Files.exists(tempPath)) {
                            System.out.println("Cleaned jSerialComm temp directory: " + tempJSerialComm);
                            break;
                        }
                    } catch (Exception e) {
                        if (attempt < 4) {
                            try {
                                Thread.sleep(100); // Brief wait before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        } else {
                            System.out.println("Warning: Could not clean temp directory after 5 attempts: " + e.getMessage());
                        }
                    }
                } else {
                    break; // Already deleted
                }
            }
            
            // Clean Home folder - try multiple times
            String homeJSerialComm = userHome + File.separator + ".jSerialComm";
            Path homePath = Paths.get(homeJSerialComm);
            for (int attempt = 0; attempt < 5; attempt++) {
                if (Files.exists(homePath)) {
                    try {
                        deleteDirectoryRecursive(homePath.toFile());
                        // Brief wait to ensure deletion completes
                        Thread.sleep(50);
                        if (!Files.exists(homePath)) {
                            System.out.println("Cleaned jSerialComm home directory: " + homeJSerialComm);
                            break;
                        }
                    } catch (Exception e) {
                        if (attempt < 4) {
                            try {
                                Thread.sleep(100); // Brief wait before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        } else {
                            System.out.println("Warning: Could not clean home directory after 5 attempts: " + e.getMessage());
                        }
                    }
                } else {
                    break; // Already deleted
                }
            }
            
            System.out.println("jSerialComm cache cleanup complete. Correct DLL will be extracted on first use.");
        } catch (Exception e) {
            System.err.println("Error cleaning jSerialComm cache: " + e.getMessage());
        }
    }
    
    /**
     * Copy the correct AMD64/x86_64 DLL from jSerialComm JAR to cache directories.
     * This ensures the correct DLL is used even if jSerialComm detects wrong architecture.
     * Also deletes any wrong architecture DLLs (aarch64, armv7) to prevent jSerialComm from using them.
     */
    private static void copyCorrectDllFromJar() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (!osName.contains("windows")) {
                // Only needed for Windows
                return;
            }
            
            // FAST CHECK: If correct DLL already exists, skip expensive operations
            String tempDir = System.getProperty("java.io.tmpdir");
            String userHome = System.getProperty("user.home");
            Path tempDllPath = Paths.get(tempDir, "jSerialComm", "2.10.4", "jSerialComm.dll");
            Path homeDllPath = Paths.get(userHome, ".jSerialComm", "2.10.4", "jSerialComm.dll");
            
            // Check if correct DLL already exists (x86_64 is ~208KB)
            boolean correctDllExists = false;
            try {
                if (Files.exists(tempDllPath) && Files.size(tempDllPath) > 200000) {
                    correctDllExists = true;
                } else if (Files.exists(homeDllPath) && Files.size(homeDllPath) > 200000) {
                    correctDllExists = true;
                }
            } catch (Exception e) {
                // Ignore - will check again below
            }
            
            if (correctDllExists) {
                // DLL is correct, just clean wrong ones if any
                deleteWrongArchitectureDlls();
                return;
            }
            
            // CRITICAL: Delete ALL wrong architecture DLLs first
            // jSerialComm may have extracted aarch64 DLLs if it detected wrong architecture
            deleteWrongArchitectureDlls();
            
            // Find jSerialComm JAR in Maven repository
            String mavenRepo = userHome + File.separator + ".m2" + File.separator + "repository";
            String jSerialCommJar = mavenRepo + File.separator + "com" + File.separator + "fazecast" + 
                                    File.separator + "jSerialComm" + File.separator + "2.10.4" + 
                                    File.separator + "jSerialComm-2.10.4.jar";
            
            File jarFile = new File(jSerialCommJar);
            if (!jarFile.exists()) {
                System.out.println("jSerialComm JAR not found at: " + jSerialCommJar);
                System.out.println("Will rely on jSerialComm's automatic DLL extraction.");
                return;
            }
            
            System.out.println("Extracting correct AMD64/x86_64 DLL from JAR...");
            
            // Extract DLL from JAR
            String dllPathInJar = null;
            try (ZipFile zipFile = new ZipFile(jarFile)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    // Look for x86_64 DLL (AMD64)
                    if (entryName.contains("Windows") && entryName.contains("x86_64") && 
                        entryName.endsWith("jSerialComm.dll")) {
                        dllPathInJar = entryName;
                        break;
                    }
                }
            }
            
            if (dllPathInJar == null) {
                System.out.println("WARNING: AMD64/x86_64 DLL not found in JAR. Will rely on jSerialComm's automatic extraction.");
                return;
            }
            
            System.out.println("Found DLL in JAR: " + dllPathInJar);
            
            // Extract DLL to temporary location
            Path tempDll = Files.createTempFile("jSerialComm_", ".dll");
            try (ZipFile zipFile = new ZipFile(jarFile);
                 InputStream is = zipFile.getInputStream(zipFile.getEntry(dllPathInJar))) {
                Files.copy(is, tempDll, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Copy DLL to cache directories (reuse variables from fast check above)
            String tempJSerialComm = tempDir + File.separator + "jSerialComm" + File.separator + "2.10.4";
            String homeJSerialComm = userHome + File.separator + ".jSerialComm" + File.separator + "2.10.4";
            
            // Create directories if they don't exist
            Path tempTargetDir = Paths.get(tempJSerialComm);
            Path homeTargetDir = Paths.get(homeJSerialComm);
            Files.createDirectories(tempTargetDir);
            Files.createDirectories(homeTargetDir);
            
            // Copy DLL to both locations with retry logic
            Path tempTargetDll = tempTargetDir.resolve("jSerialComm.dll");
            Path homeTargetDll = homeTargetDir.resolve("jSerialComm.dll");
            
            // Delete existing DLLs first (they might be locked or wrong architecture)
            deleteFileWithRetries(tempTargetDll, 3);
            deleteFileWithRetries(homeTargetDll, 3);
            
            // Brief wait to ensure deletion completes (reduced for faster startup)
            Thread.sleep(50);
            
            // Copy DLL to temp location with retries
            boolean tempCopied = copyFileWithRetries(tempDll, tempTargetDll, 3);
            boolean homeCopied = copyFileWithRetries(tempDll, homeTargetDll, 3);
            
            // Clean up temporary file
            Files.deleteIfExists(tempDll);
            
            if (tempCopied && homeCopied) {
                System.out.println("Successfully copied AMD64 DLL to:");
                System.out.println("  - " + tempTargetDll);
                System.out.println("  - " + homeTargetDll);
                
                // Verify DLL was copied correctly
                if (Files.exists(tempTargetDll) && Files.size(tempTargetDll) > 100000) {
                    long size = Files.size(tempTargetDll);
                    System.out.println("DLL verification: OK (size: " + size + " bytes)");
                    
                    // CRITICAL: Set DLL to read-only to prevent jSerialComm from overwriting it
                    // jSerialComm may try to extract wrong DLL if it detects wrong architecture
                    try {
                        tempTargetDll.toFile().setReadOnly();
                        homeTargetDll.toFile().setReadOnly();
                        System.out.println("DLL set to read-only to prevent overwrite");
                    } catch (Exception e) {
                        System.err.println("WARNING: Could not set DLL to read-only: " + e.getMessage());
                    }
                } else {
                    System.err.println("WARNING: DLL verification failed - size is too small!");
                }
            } else {
                System.err.println("WARNING: Failed to copy DLL to one or both locations.");
                if (!tempCopied) {
                    System.err.println("  Failed to copy to: " + tempTargetDll);
                }
                if (!homeCopied) {
                    System.err.println("  Failed to copy to: " + homeTargetDll);
                }
                throw new IOException("Failed to copy DLL to cache directories");
            }
            
        } catch (Exception e) {
            System.err.println("Error copying DLL from JAR: " + e.getMessage());
            e.printStackTrace();
            // Don't fail application startup if DLL copy fails
            // jSerialComm will try to extract DLL automatically
        }
    }
    
    /**
     * Delete all wrong architecture DLLs (aarch64, armv7) from jSerialComm cache.
     * This prevents jSerialComm from trying to load the wrong DLL.
     * Also deletes the entire version directory to ensure clean state.
     */
    private static void deleteWrongArchitectureDlls() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String userHome = System.getProperty("user.home");
            
            System.out.println("Deleting all jSerialComm DLLs to ensure clean state...");
            
            // Delete entire version directory from temp to ensure clean state
            Path tempJSerialComm = Paths.get(tempDir, "jSerialComm", "2.10.4");
            if (Files.exists(tempJSerialComm)) {
                try {
                    deleteDirectoryRecursive(tempJSerialComm.toFile());
                    System.out.println("Deleted temp jSerialComm version directory: " + tempJSerialComm);
                } catch (Exception e) {
                    System.err.println("Could not delete temp directory: " + e.getMessage());
                    // Try to delete DLLs individually
                    Files.walkFileTree(tempJSerialComm, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String fileName = file.getFileName().toString().toLowerCase();
                            if (fileName.endsWith(".dll")) {
                                try {
                                    long size = Files.size(file);
                                    System.out.println("Deleting DLL: " + file + " (size: " + size + " bytes)");
                                    deleteFileWithRetries(file, 3);
                                } catch (IOException e) {
                                    // Ignore
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
            
            // Delete entire version directory from home to ensure clean state
            Path homeJSerialComm = Paths.get(userHome, ".jSerialComm", "2.10.4");
            if (Files.exists(homeJSerialComm)) {
                try {
                    deleteDirectoryRecursive(homeJSerialComm.toFile());
                    System.out.println("Deleted home jSerialComm version directory: " + homeJSerialComm);
                } catch (Exception e) {
                    System.err.println("Could not delete home directory: " + e.getMessage());
                    // Try to delete DLLs individually
                    Files.walkFileTree(homeJSerialComm, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String fileName = file.getFileName().toString().toLowerCase();
                            if (fileName.endsWith(".dll")) {
                                try {
                                    long size = Files.size(file);
                                    System.out.println("Deleting DLL: " + file + " (size: " + size + " bytes)");
                                    deleteFileWithRetries(file, 3);
                                } catch (IOException e) {
                                    // Ignore
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
            
            // Wait a bit to ensure deletions complete
            Thread.sleep(500);
            
            System.out.println("Cleanup complete. Ready to copy correct DLL.");
            
        } catch (Exception e) {
            System.err.println("Error deleting wrong architecture DLLs: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Delete a file with retry logic
     */
    private static void deleteFileWithRetries(Path file, int maxAttempts) {
        if (!Files.exists(file)) {
            return; // Already deleted
        }
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Files.delete(file);
                if (!Files.exists(file)) {
                    return; // Successfully deleted
                }
            } catch (IOException e) {
                if (attempt < maxAttempts - 1) {
                    try {
                        Thread.sleep(50 * (attempt + 1)); // Reduced backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // Last attempt failed - mark for deletion on exit
                    try {
                        file.toFile().deleteOnExit();
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
            }
        }
    }
    
    /**
     * Copy a file with retry logic
     */
    private static boolean copyFileWithRetries(Path source, Path target, int maxAttempts) {
        // Ensure target directory exists
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            System.err.println("Failed to create target directory: " + target.getParent() + " - " + e.getMessage());
            return false;
        }
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // Delete target first if it exists
                if (Files.exists(target)) {
                    try {
                        // Try to make file writable first
                        target.toFile().setWritable(true);
                        Files.delete(target);
                        // No sleep needed - deletion is immediate
                    } catch (IOException e) {
                        System.err.println("Attempt " + (attempt + 1) + ": Could not delete existing file: " + e.getMessage());
                        // Continue anyway - try to overwrite
                    }
                }
                
                // Copy file using streams for better control
                try (java.io.InputStream in = Files.newInputStream(source);
                     java.io.OutputStream out = Files.newOutputStream(target, 
                         java.nio.file.StandardOpenOption.CREATE, 
                         java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, 
                         java.nio.file.StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                }
                
                // Verify copy was successful
                if (Files.exists(target)) {
                    long sourceSize = Files.size(source);
                    long targetSize = Files.size(target);
                    if (targetSize > 0 && targetSize == sourceSize) {
                        System.out.println("Successfully copied file (attempt " + (attempt + 1) + "): " + target);
                        return true; // Successfully copied
                    } else {
                        System.err.println("File copied but size mismatch: source=" + sourceSize + ", target=" + targetSize);
                    }
                }
            } catch (IOException e) {
                System.err.println("Attempt " + (attempt + 1) + "/" + maxAttempts + " failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (attempt < maxAttempts - 1) {
                    try {
                        Thread.sleep(100 * (attempt + 1)); // Reduced backoff
                        // Try to delete target before retry
                        if (Files.exists(target)) {
                            try {
                                target.toFile().setWritable(true);
                                Files.delete(target);
                            } catch (Exception ex) {
                                // Ignore - will try again
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    System.err.println("Failed to copy file after " + maxAttempts + " attempts: " + e.getMessage());
                    System.err.println("  Source: " + source);
                    System.err.println("  Target: " + target);
                    System.err.println("  Target exists: " + Files.exists(target));
                    if (Files.exists(target)) {
                        try {
                            System.err.println("  Target size: " + Files.size(target));
                            System.err.println("  Target writable: " + target.toFile().canWrite());
                        } catch (Exception ex) {
                            // Ignore
                        }
                    }
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Recursively delete a directory using NIO for better reliability
     */
    private static void deleteDirectoryRecursive(File directory) {
        if (!directory.exists()) {
            return;
        }
        
        try {
            Path path = directory.toPath();
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // Ignore - file might be locked, try to delete on exit
                        file.toFile().deleteOnExit();
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        // Ignore - directory might be locked, try to delete on exit
                        dir.toFile().deleteOnExit();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Fallback: try simple deletion if NIO fails
            try {
                if (directory.exists()) {
                    File[] files = directory.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isDirectory()) {
                                deleteDirectoryRecursive(file);
                            } else {
                                file.delete();
                            }
                        }
                    }
                    directory.delete();
                }
            } catch (Exception ex) {
                // Ignore - directory might be locked
            }
        }
    }
    
    
    public static void main(String[] args) {
        // CRITICAL: Fix architecture detection BEFORE loading ANY classes that use jSerialComm
        // This must happen before launch() because JavaFX classes may trigger class loading
        // Java 25 sometimes reports wrong architecture (aarch64) after Windows updates
        fixArchitectureDetection();
        
        // CRITICAL: Copy correct DLL BEFORE any SerialManager class is loaded
        // SerialManager static initializer runs when class is first loaded, so we must copy DLL first
        copyCorrectDllFromJar();
        
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
    
    /**
     * Fix architecture detection for jSerialComm on Windows.
     * This MUST be called before any classes using jSerialComm are loaded.
     * Java 25 sometimes reports wrong architecture (aarch64) on AMD64 systems after Windows updates.
     * 
     * jSerialComm uses internal methods to detect architecture that may not respect os.arch property.
     * We need to ensure the correct architecture is detected by:
     * 1. Setting os.arch property
     * 2. Setting system properties that jSerialComm might check
     * 3. Aggressively cleaning jSerialComm cache folders
     */
    private static void fixArchitectureDetection() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        
        if (!osName.contains("windows")) {
            return; // Only fix for Windows
        }
        
        String osArch = System.getProperty("os.arch", "");
        String archEnv = System.getenv("PROCESSOR_ARCHITECTURE");
        String archEnv64 = System.getenv("PROCESSOR_ARCHITEW6432");
        
        // Check if system is actually AMD64
        boolean isAmd64System = (archEnv != null && archEnv.equals("AMD64")) || 
                               (archEnv64 != null && archEnv64.equals("AMD64"));
        
        // Check if Java reports wrong architecture
        boolean wrongArch = osArch.equals("aarch64") || osArch.contains("arm");
        
        if (isAmd64System && wrongArch) {
            System.out.println("========================================");
            System.out.println("  ARCHITECTURE MISMATCH DETECTED!");
            System.out.println("========================================");
            System.out.println("  Java reports: " + osArch);
            System.out.println("  System is actually: AMD64");
            System.out.println("  This will cause jSerialComm to extract wrong DLL");
            System.out.println("  Applying multiple fixes...");
            
            // Fix 1: Force set os.arch property BEFORE any jSerialComm class loading
            System.setProperty("os.arch", "amd64");
            
            // Fix 2: Set additional system properties that jSerialComm might check
            // jSerialComm may use sun.arch.data.model or other properties
            try {
                System.setProperty("sun.arch.data.model", "64");
                System.setProperty("java.vm.name", System.getProperty("java.vm.name", "").replace("aarch64", "amd64"));
            } catch (Exception e) {
                // Ignore if we can't set these
            }
            
            // Verify os.arch was set
            String newArch = System.getProperty("os.arch");
            if (newArch.equals("amd64")) {
                System.out.println("  ✓ Successfully set os.arch=amd64");
            } else {
                System.err.println("  ✗ WARNING: Failed to set os.arch! Still: " + newArch);
            }
            
            System.out.println("  NOTE: jSerialComm may still detect wrong architecture internally.");
            System.out.println("  Cache folders will be aggressively cleaned to force re-extraction.");
            System.out.println("========================================");
            System.out.println();
        } else if (isAmd64System) {
            // System is AMD64 and Java reports correctly
            System.out.println("Architecture check: AMD64 system detected correctly (os.arch=" + osArch + ")");
        }
    }

}


