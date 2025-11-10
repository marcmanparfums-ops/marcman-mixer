package ro.marcman.mixer.adapters.ui.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Utility for loading and applying the MarcmanMixer application icons
 * across stages and dialogs.
 */
public final class IconSupport {

    private static final String[] ICON_PATHS = {
        "/images/mixer.ico",
        "/images/icon.png"
    };

    private static final List<Image> APP_ICONS = loadIcons();

    private IconSupport() {
        // utility
    }

    /**
     * @return immutable list of application icons (may be empty if resources missing)
     */
    public static List<Image> getAppIcons() {
        return APP_ICONS;
    }

    /**
     * Applies the application icons to the provided Stage.
     */
    public static void applyTo(Stage stage) {
        if (!APP_ICONS.isEmpty() && stage != null) {
            stage.getIcons().setAll(APP_ICONS);
        }
    }

    /**
     * Applies the application icons to the window backing the provided dialog.
     */
    public static void applyTo(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }

        // Scene might not yet exist, so listen for changes.
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyTo(newScene.getWindow());
            }
        });

        Scene currentScene = dialog.getDialogPane().getScene();
        if (currentScene != null) {
            applyTo(currentScene.getWindow());
        }
    }

    /**
     * Applies the application icons to an Alert dialog.
     */
    public static void applyTo(Alert alert) {
        applyTo((Dialog<?>) alert);
    }

    private static void applyTo(Window window) {
        if (window instanceof Stage stage) {
            applyTo(stage);
        }
    }

    private static List<Image> loadIcons() {
        List<Image> icons = new ArrayList<>();
        for (String pathWithSlash : ICON_PATHS) {
            int before = icons.size();
            loadFromClasspath(icons, pathWithSlash);

            String normalized = pathWithSlash.startsWith("/") ? pathWithSlash.substring(1) : pathWithSlash;
            boolean isIco = normalized.toLowerCase().endsWith(".ico");

            if (icons.size() == before && !isIco) {
                attemptFilesystemFallback(icons, normalized);
            }
        }

        if (icons.isEmpty()) {
            System.out.println("⚠ IconSupport: niciun fișier de tip ico/png nu a putut fi încărcat.");
        } else {
            System.out.println("✓ IconSupport: încărcate " + icons.size() + " icon-uri pentru ferestre.");
        }
        return Collections.unmodifiableList(icons);
    }

    private static void loadFromClasspath(List<Image> icons, String resourcePath) {
        if (resourcePath != null && resourcePath.toLowerCase().endsWith(".ico")) {
            System.out.println("ℹ IconSupport: se omite încărcarea ICO din classpath pentru JavaFX: " + resourcePath);
            return;
        }

        try (InputStream stream = IconSupport.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                Image image = new Image(stream);
                if (!image.isError()) {
                    icons.add(image);
                    System.out.println("✓ IconSupport: încărcat " + resourcePath + " din classpath.");
                } else {
                    System.out.println("⚠ IconSupport: nu pot decoda " + resourcePath);
                }
            } else {
                System.out.println("ℹ IconSupport: resursa " + resourcePath + " nu a fost găsită în classpath.");
            }
        } catch (Exception ex) {
            System.out.println("⚠ IconSupport: eroare la încărcarea " + resourcePath + " -> " + ex.getMessage());
        }
    }

    private static void attemptFilesystemFallback(List<Image> icons, String relativePath) {
        List<Path> candidates = List.of(
            Path.of("ui", "src", "main", "resources", relativePath),
            Path.of("app", "src", "main", "resources", relativePath),
            Path.of("src", "main", "resources", relativePath),
            Path.of(relativePath)
        );

        for (Path path : candidates) {
            if (Files.exists(path)) {
                try (InputStream stream = Files.newInputStream(path)) {
                    Image image = new Image(stream);
                    if (!image.isError()) {
                        icons.add(image);
                        System.out.println("✓ IconSupport: fallback încărcat " + path.toAbsolutePath());
                        return;
                    }
                } catch (Exception ex) {
                    System.out.println("⚠ IconSupport: eroare la fallback " + path + " -> " + ex.getMessage());
                }
            }
        }
    }
}

