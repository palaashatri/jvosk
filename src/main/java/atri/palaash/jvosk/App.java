package atri.palaash.jvosk;

import atri.palaash.jvosk.ui.MainFrame;
import atri.palaash.jvosk.util.AppPreferences;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.*;

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
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
