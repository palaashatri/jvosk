package atri.palaash.jvosk.models;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

/**
 * Registry for fetching and parsing Vosk models from the official models page.
 */
public class ModelRegistry {
    
    private static final String MODELS_PAGE_URL = "https://alphacephei.com/vosk/models";
    private static final String MODELS_BASE_URL = "https://alphacephei.com/vosk/models/";
    private static final int TIMEOUT_MS = 10000;
    
    private List<VoskModel> models = new ArrayList<>();
    private Map<String, List<VoskModel>> modelsByLanguage = new HashMap<>();
    private long lastFetchTime = 0;
    
    /**
     * Fetch models from the official Vosk models page.
     * @param forceRefresh if true, fetch even if recently cached
     * @return list of all available models
     */
    public List<VoskModel> fetchModels(boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        
        // Use cache if less than 1 hour old
        if (!forceRefresh && !models.isEmpty() && (now - lastFetchTime) < 3600000) {
            return new ArrayList<>(models);
        }
        
        try {
            Document doc = Jsoup.connect(MODELS_PAGE_URL)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; jvosk/1.0)")
                    .get();
            
            models.clear();
            modelsByLanguage.clear();
            
            parseModelsFromPage(doc);
            lastFetchTime = now;
            
            return new ArrayList<>(models);
            
        } catch (IOException e) {
            throw new IOException("Failed to fetch models from " + MODELS_PAGE_URL + ": " + e.getMessage(), e);
        }
    }
    
    private void parseModelsFromPage(Document doc) {
        String currentLanguage = "Unknown";
        String currentSection = "";
        
        // Parse headers and tables
        Elements allElements = doc.select("h2, h3, table");
        
        for (Element element : allElements) {
            if (element.tagName().equals("h2")) {
                String headerText = element.text().trim();
                if (headerText.contains("Punctuation")) {
                    currentSection = "PUNCTUATION";
                } else if (headerText.contains("Speaker")) {
                    currentSection = "SPEAKER_ID";
                } else {
                    currentSection = "MODELS";
                }
            } else if (element.tagName().equals("h3") || element.tagName().equals("h4")) {
                // Extract language from header
                currentLanguage = extractLanguage(element.text());
            } else if (element.tagName().equals("table")) {
                // Parse table rows
                parseTableRows(element, currentLanguage, currentSection);
            }
        }
        
        // Sort models
        Collections.sort(models);
        
        // Group by language
        for (VoskModel model : models) {
            modelsByLanguage.computeIfAbsent(model.getLanguage(), k -> new ArrayList<>()).add(model);
        }
    }
    
    private void parseTableRows(Element table, String language, String section) {
        Elements rows = table.select("tr");
        
        for (Element row : rows) {
            Elements cells = row.select("td");
            
            // Skip header rows or rows with wrong number of cells
            if (cells.size() < 4) continue;
            
            try {
                String modelName = cells.get(0).text().trim();
                String size = cells.get(1).text().trim();
                String accuracy = cells.size() > 2 ? cells.get(2).text().trim() : "";
                String description = cells.size() > 3 ? cells.get(3).text().trim() : "";
                String license = cells.size() > 4 ? cells.get(4).text().trim() : "Unknown";
                
                // Skip if model name is empty or looks like a header
                if (modelName.isEmpty() || modelName.equalsIgnoreCase("Model") || 
                    modelName.equalsIgnoreCase("Name")) {
                    continue;
                }
                
                // Build download URL
                String downloadUrl = MODELS_BASE_URL + modelName + ".zip";
                
                // Determine model type
                VoskModel.ModelType type = determineModelType(modelName, section, size);
                
                VoskModel model = new VoskModel.Builder()
                        .name(modelName)
                        .language(language)
                        .size(size)
                        .accuracy(accuracy)
                        .description(description)
                        .license(license)
                        .downloadUrl(downloadUrl)
                        .type(type)
                        .build();
                
                models.add(model);
                
            } catch (Exception e) {
                // Skip malformed rows
                System.err.println("Failed to parse model row: " + e.getMessage());
            }
        }
    }
    
    private String extractLanguage(String headerText) {
        headerText = headerText.trim();
        
        // Remove "Other" suffix
        headerText = headerText.replaceAll("\\s+Other\\s*$", "");
        
        // Common patterns
        if (headerText.isEmpty() || headerText.equalsIgnoreCase("Model list")) {
            return "Unknown";
        }
        
        return headerText;
    }
    
    private VoskModel.ModelType determineModelType(String modelName, String section, String size) {
        if ("PUNCTUATION".equals(section)) {
            return VoskModel.ModelType.PUNCTUATION;
        }
        if ("SPEAKER_ID".equals(section)) {
            return VoskModel.ModelType.SPEAKER_ID;
        }
        if (modelName.toLowerCase().contains("small")) {
            return VoskModel.ModelType.SMALL;
        }
        
        // Parse size to determine if it's big
        try {
            String sizeUpper = size.toUpperCase();
            if (sizeUpper.contains("G")) {
                double sizeInGb = Double.parseDouble(sizeUpper.replaceAll("[^0-9.]", ""));
                if (sizeInGb >= 0.5) {
                    return VoskModel.ModelType.BIG;
                }
            } else if (sizeUpper.contains("M")) {
                double sizeInMb = Double.parseDouble(sizeUpper.replaceAll("[^0-9.]", ""));
                if (sizeInMb >= 500) {
                    return VoskModel.ModelType.BIG;
                }
            }
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
        
        return VoskModel.ModelType.SMALL;
    }
    
    /**
     * Get models filtered by language.
     */
    public List<VoskModel> getModelsByLanguage(String language) {
        return modelsByLanguage.getOrDefault(language, Collections.emptyList());
    }
    
    /**
     * Get all unique languages available.
     */
    public Set<String> getAvailableLanguages() {
        return new HashSet<>(modelsByLanguage.keySet());
    }
    
    /**
     * Find a model by name.
     */
    public Optional<VoskModel> findModelByName(String name) {
        return models.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst();
    }
}
