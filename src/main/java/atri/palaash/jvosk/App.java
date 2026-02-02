package atri.palaash.jvosk;

import atri.palaash.jvosk.models.ModelManager;
import atri.palaash.jvosk.models.VoskModel;
import atri.palaash.jvosk.ui.MainFrame;
import atri.palaash.jvosk.util.AppPreferences;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        // Set system properties for better macOS integration
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "jvosk");
        
        // Initialize FlatLaf look and feel
        try {
            // Use system-aware dark mode detection
            boolean useSystemTheme = true;
            boolean isDark = AppPreferences.isDarkMode();
            
            if (useSystemTheme && !isDark) {
                // Try to detect system dark mode (works on macOS)
                try {
                    String osTheme = System.getProperty("apple.awt.appearance");
                    if (osTheme != null && osTheme.contains("dark")) {
                        isDark = true;
                    }
                } catch (Exception ignored) {}
            }
            
            if (isDark) {
                UIManager.setLookAndFeel(new FlatMacDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatMacLightLaf());
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to system look and feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            // Initialize model manager with platform-agnostic user directory
            String userHome = System.getProperty("user.home");
            String modelsPath = userHome + File.separator + ".jvosk" + File.separator + "models";
            ModelManager modelManager = new ModelManager(modelsPath);
            
            // Check for model updates in background
            checkForModelUpdates(modelManager);
            
            // Create and show main frame
            MainFrame frame = new MainFrame(modelManager);
            frame.setVisible(true);
        });
    }
    
    private static void checkForModelUpdates(ModelManager modelManager) {
        // Run in background thread
        new Thread(() -> {
            try {
                // Check if there are installed models first
                if (modelManager.getInstalledModels().isEmpty()) {
                    // No models installed - don't bother checking for updates
                    return;
                }
                
                // Small delay to let the UI appear first
                Thread.sleep(2000);
                
                Map<String, VoskModel> updates = modelManager.checkForUpdates();
                
                if (!updates.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        String message = String.format(
                                "Updates available for %d model(s):\n\n%s",
                                updates.size(),
                                String.join("\n", updates.keySet().stream()
                                        .map(name -> "• " + name)
                                        .toList())
                        );
                        
                        JOptionPane.showMessageDialog(
                                null,
                                message,
                                "Model Updates Available",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Silently fail - not critical
                System.err.println("Failed to check for model updates: " + e.getMessage());
            }
        }, "Model-Update-Checker").start();
    }
}
