package org.example;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.openqa.selenium.WebDriver;

/**
 * Enhanced Similo with Dynamic Weight Adjustment
 * 
 * Features:
 * - Contribution Analysis: Tracks how often each attribute contributes to successful matches
 * - Stability Factor: Evaluates attribute stability over time
 * - Context-based Adjustment: Different weights for different contexts (e-commerce needs high name/id weight, forms need high text weight)
 * - Temporal Decay: Reduces influence of old data, prioritizes recent patterns
 * 
 * Logic:
 * - Collects contribution score for each match
 * - Calculates average contribution via sliding window (1000 recent matches)
 * - Updates weight: new_weight = old_weight × (1 + learning_rate × (contribution - baseline))
 * - Applies constraints: keeps weights in [0.1, 3.0] to avoid overfitting
 */
public class DynamicWeightSimilo extends Similo {
    
    // Dynamic weights (initialized from parent's WEIGHTS)
    private double[] dynamicWeights;
    
    // Contribution tracking for each attribute
    private Map<String, ContributionTracker> contributionTrackers;
    
    // Stability tracking for each attribute
    private Map<String, StabilityTracker> stabilityTrackers;
    
    // Context detection
    private String currentContext = "default";
    
    // Learning parameters
    private static final double LEARNING_RATE = 0.1;
    private static final double BASELINE_CONTRIBUTION = 0.5;
    private static final double MIN_WEIGHT = 0.1;
    private static final double MAX_WEIGHT = 3.0;
    private static final int SLIDING_WINDOW_SIZE = 1000;
    
    // Attribute names (from parent)
    private static final String[] LOCATORS = {"tag", "class", "name", "id", "href", "alt", "xpath", "idxpath", "is_button", "location", "area", "shape", "visible_text", "neighbor_text"};
    
    // Initial weights (from parent)
    private static final double[] INITIAL_WEIGHTS = {1.5, 0.5, 1.5, 1.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.5, 1.5};
    
    // Context-based weight modifiers
    private Map<String, Map<String, Double>> contextModifiers;
    
    /**
     * Inner class to track contribution of each attribute
     */
    private class ContributionTracker {
        private LinkedList<Double> contributions; // Sliding window of contribution scores
        private int successfulMatches = 0;
        private int totalMatches = 0;
        
        public ContributionTracker() {
            this.contributions = new LinkedList<>();
        }
        
        public void addContribution(double score, boolean wasSuccessful) {
            contributions.add(score);
            totalMatches++;
            if (wasSuccessful) {
                successfulMatches++;
            }
            
            // Maintain sliding window
            if (contributions.size() > SLIDING_WINDOW_SIZE) {
                contributions.removeFirst();
            }
        }
        
        public double getAverageContribution() {
            if (contributions.isEmpty()) {
                return BASELINE_CONTRIBUTION;
            }
            double sum = 0;
            for (double contrib : contributions) {
                sum += contrib;
            }
            return sum / contributions.size();
        }
        
        public double getSuccessRate() {
            if (totalMatches == 0) return 0.5;
            return (double) successfulMatches / totalMatches;
        }
    }
    
    /**
     * Inner class to track stability of each attribute
     */
    private class StabilityTracker {
        private LinkedList<String> values; // Recent values
        private int changeCount = 0;
        
        public StabilityTracker() {
            this.values = new LinkedList<>();
        }
        
        public void addValue(String value) {
            if (!values.isEmpty() && !values.getLast().equals(value)) {
                changeCount++;
            }
            values.add(value);
            
            // Maintain sliding window
            if (values.size() > 100) {
                values.removeFirst();
            }
        }
        
        public double getStabilityScore() {
            if (values.size() < 2) return 1.0;
            // Lower change rate = higher stability
            double changeRate = (double) changeCount / values.size();
            return 1.0 - Math.min(changeRate, 1.0);
        }
    }
    
    public DynamicWeightSimilo() {
        super();
        initializeDynamicWeights();
    }
    
    public DynamicWeightSimilo(WebDriver webDriver) {
        super(webDriver);
        initializeDynamicWeights();
    }
    
    public DynamicWeightSimilo(WebDriver webDriver, String propertiesFolder) {
        super(webDriver, propertiesFolder);
        initializeDynamicWeights();
    }
    
    public DynamicWeightSimilo(WebDriver webDriver, String propertiesFolder, String javascriptFilename) {
        super(webDriver, propertiesFolder, javascriptFilename);
        initializeDynamicWeights();
    }
    
    private void initializeDynamicWeights() {
        // Initialize dynamic weights from base weights
        dynamicWeights = Arrays.copyOf(INITIAL_WEIGHTS, INITIAL_WEIGHTS.length);
        
        // Initialize contribution trackers
        contributionTrackers = new ConcurrentHashMap<>();
        for (String locator : LOCATORS) {
            contributionTrackers.put(locator, new ContributionTracker());
        }
        
        // Initialize stability trackers
        stabilityTrackers = new ConcurrentHashMap<>();
        for (String locator : LOCATORS) {
            stabilityTrackers.put(locator, new StabilityTracker());
        }
        
        // Initialize context modifiers
        initializeContextModifiers();
    }
    
    private void initializeContextModifiers() {
        contextModifiers = new HashMap<>();
        
        // E-commerce context: emphasize text, name, id
        Map<String, Double> ecommerceModifiers = new HashMap<>();
        ecommerceModifiers.put("visible_text", 1.5);
        ecommerceModifiers.put("name", 1.3);
        ecommerceModifiers.put("id", 1.3);
        ecommerceModifiers.put("class", 0.8);
        contextModifiers.put("ecommerce", ecommerceModifiers);
        
        // Form context: emphasize name, id, placeholder
        Map<String, Double> formModifiers = new HashMap<>();
        formModifiers.put("name", 1.5);
        formModifiers.put("id", 1.5);
        formModifiers.put("visible_text", 1.2);
        formModifiers.put("class", 0.7);
        contextModifiers.put("form", formModifiers);
        
        // Default context: no modifications
        contextModifiers.put("default", new HashMap<>());
    }
    
    /**
     * Set the current context for weight adjustment
     * @param context Context name ("ecommerce", "form", "default")
     */
    public void setContext(String context) {
        if (contextModifiers.containsKey(context)) {
            this.currentContext = context;
            System.out.println("Context switched to: " + context);
        }
    }
    
    /**
     * Get current context
     */
    public String getContext() {
        return currentContext;
    }
    
    /**
     * Override similarity calculation to use dynamic weights and track contributions
     */
    @Override
    public double calcSimilarityScore(Locator targetWidget, Locator candidateWidget) {
        double similarityScore = 0;
        int index = 0;
        Map<String, Double> attributeContributions = new HashMap<>();
        
        for (String locator : LOCATORS) {
            double baseWeight = dynamicWeights[index];
            
            // Apply context-based modifier
            double contextModifier = 1.0;
            Map<String, Double> modifiers = contextModifiers.get(currentContext);
            if (modifiers != null && modifiers.containsKey(locator)) {
                contextModifier = modifiers.get(locator);
            }
            
            // Apply stability factor
            StabilityTracker stabilityTracker = stabilityTrackers.get(locator);
            double stabilityFactor = stabilityTracker != null ? stabilityTracker.getStabilityScore() : 1.0;
            
            // Final weight = base × context × stability
            double weight = baseWeight * contextModifier * (0.5 + 0.5 * stabilityFactor);
            
            double similarity = 0;
            String targetValue = targetWidget.getMetadata(locator);
            String candidateValue = candidateWidget.getMetadata(locator);
            
            if (targetValue != null && candidateValue != null) {
                // Track stability
                if (stabilityTracker != null) {
                    stabilityTracker.addValue(candidateValue);
                }
                
                // Calculate similarity using parent's logic
                int similarityFunction = getSimilarityFunction(index);
                similarity = calculateAttributeSimilarity(targetValue, candidateValue, similarityFunction, index);
                
                // Track contribution
                double contribution = similarity * weight;
                attributeContributions.put(locator, contribution);
                similarityScore += contribution;
            }
            
            index++;
        }
        
        // Store contributions for later weight update
        candidateWidget.putMetadata("attribute_contributions", attributeContributions.toString());
        
        return similarityScore;
    }
    
    /**
     * Calculate attribute similarity based on similarity function type
     */
    private double calculateAttributeSimilarity(String targetValue, String candidateValue, 
                                                int similarityFunction, int attributeIndex) {
        double similarity = 0;
        
        if (similarityFunction == 0) {
            // Equal comparison
            similarity = targetValue.equalsIgnoreCase(candidateValue) ? 1.0 : 0.0;
        } else if (similarityFunction == 1) {
            // String similarity
            similarity = ((double) stringSimilarity(targetValue, candidateValue, 100)) / 100;
        } else if (similarityFunction == 2) {
            // Integer similarity
            similarity = ((double) integerSimilarity(targetValue, candidateValue, 1000)) / 1000;
        } else if (similarityFunction == 3) {
            // Location similarity (2D distance)
            // Use parent's implementation
            similarity = 0.5; // Simplified for now
        } else if (similarityFunction == 4) {
            // Neighbor text similarity
            similarity = ((double) neighborTextSimilarity(targetValue, candidateValue, 100)) / 100;
        }
        
        return similarity;
    }
    
    /**
     * Get similarity function type for attribute index
     */
    private int getSimilarityFunction(int index) {
        final int[] SIMILARITY_FUNCTION = {0, 1, 0, 0, 1, 1, 1, 1, 0, 3, 2, 2, 1, 4};
        return SIMILARITY_FUNCTION[index];
    }
    
    /**
     * Record a successful match and update weights
     */
    public void recordSuccessfulMatch(Locator targetWidget, Locator matchedWidget) {
        for (int i = 0; i < LOCATORS.length; i++) {
            String locator = LOCATORS[i];
            String targetValue = targetWidget.getMetadata(locator);
            String matchedValue = matchedWidget.getMetadata(locator);
            
            ContributionTracker tracker = contributionTrackers.get(locator);
            if (tracker != null) {
                double contribution = 0;
                if (targetValue != null && matchedValue != null) {
                    // Calculate how much this attribute contributed
                    int similarityFunction = getSimilarityFunction(i);
                    contribution = calculateAttributeSimilarity(targetValue, matchedValue, similarityFunction, i);
                }
                tracker.addContribution(contribution, true);
            }
        }
        
        // Update weights based on contributions
        updateWeights();
    }
    
    /**
     * Record a failed match
     */
    public void recordFailedMatch(Locator targetWidget) {
        for (String locator : LOCATORS) {
            ContributionTracker tracker = contributionTrackers.get(locator);
            if (tracker != null) {
                tracker.addContribution(0, false);
            }
        }
    }
    
    /**
     * Update weights based on contribution analysis
     */
    private void updateWeights() {
        for (int i = 0; i < LOCATORS.length; i++) {
            String locator = LOCATORS[i];
            ContributionTracker tracker = contributionTrackers.get(locator);
            
            if (tracker != null) {
                double avgContribution = tracker.getAverageContribution();
                double successRate = tracker.getSuccessRate();
                
                // Update weight: new_weight = old_weight × (1 + learning_rate × (contribution - baseline))
                double oldWeight = dynamicWeights[i];
                double adjustment = LEARNING_RATE * (avgContribution - BASELINE_CONTRIBUTION) * successRate;
                double newWeight = oldWeight * (1 + adjustment);
                
                // Apply constraints [MIN_WEIGHT, MAX_WEIGHT]
                newWeight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, newWeight));
                
                dynamicWeights[i] = newWeight;
            }
        }
    }
    
    /**
     * Get current dynamic weights
     */
    public double[] getDynamicWeights() {
        return Arrays.copyOf(dynamicWeights, dynamicWeights.length);
    }
    
    /**
     * Get weight for specific attribute
     */
    public double getWeight(String attributeName) {
        for (int i = 0; i < LOCATORS.length; i++) {
            if (LOCATORS[i].equals(attributeName)) {
                return dynamicWeights[i];
            }
        }
        return 0.0;
    }
    
    /**
     * Print current weights and statistics
     */
    public void printWeightStatistics() {
        System.out.println("\n=== Dynamic Weight Statistics ===");
        System.out.println("Context: " + currentContext);
        System.out.println("\nAttribute | Weight | Avg Contrib | Success Rate | Stability");
        System.out.println("----------|--------|-------------|--------------|----------");
        
        for (int i = 0; i < LOCATORS.length; i++) {
            String locator = LOCATORS[i];
            double weight = dynamicWeights[i];
            ContributionTracker contribTracker = contributionTrackers.get(locator);
            StabilityTracker stabilityTracker = stabilityTrackers.get(locator);
            
            double avgContrib = contribTracker != null ? contribTracker.getAverageContribution() : 0;
            double successRate = contribTracker != null ? contribTracker.getSuccessRate() : 0;
            double stability = stabilityTracker != null ? stabilityTracker.getStabilityScore() : 0;
            
            System.out.printf("%-13s | %.3f  | %.3f       | %.2f%%       | %.3f\n", 
                locator, weight, avgContrib, successRate * 100, stability);
        }
        System.out.println("================================\n");
    }
    
    /**
     * Save weights to file
     */
    public void saveWeights(String filename) {
        try {
            Properties props = new Properties();
            for (int i = 0; i < LOCATORS.length; i++) {
                props.setProperty(LOCATORS[i], String.valueOf(dynamicWeights[i]));
            }
            props.setProperty("context", currentContext);
            
            FileOutputStream fos = new FileOutputStream(filename);
            props.store(fos, "Dynamic Weights for Similo");
            fos.close();
            
            System.out.println("Weights saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving weights: " + e.getMessage());
        }
    }
    
    /**
     * Load weights from file
     */
    public void loadWeights(String filename) {
        try {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(filename);
            props.load(fis);
            fis.close();
            
            for (int i = 0; i < LOCATORS.length; i++) {
                String value = props.getProperty(LOCATORS[i]);
                if (value != null) {
                    dynamicWeights[i] = Double.parseDouble(value);
                }
            }
            
            String context = props.getProperty("context");
            if (context != null) {
                setContext(context);
            }
            
            System.out.println("Weights loaded from: " + filename);
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error loading weights: " + e.getMessage());
        }
    }
    
    /**
     * Reset weights to initial values
     */
    public void resetWeights() {
        dynamicWeights = Arrays.copyOf(INITIAL_WEIGHTS, INITIAL_WEIGHTS.length);
        contributionTrackers.clear();
        stabilityTrackers.clear();
        for (String locator : LOCATORS) {
            contributionTrackers.put(locator, new ContributionTracker());
            stabilityTrackers.put(locator, new StabilityTracker());
        }
        System.out.println("Weights reset to initial values");
    }
    
    // Helper methods from parent class (simplified implementations)
    private int stringSimilarity(String s1, String s2, int maxScore) {
        if (s1.length() == 0 || s2.length() == 0) return 0;
        if (s1.equalsIgnoreCase(s2)) return maxScore;
        
        int distance = computeLevenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        return (maxLen - distance) * maxScore / maxLen;
    }
    
    private int computeLevenshteinDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
    
    private int integerSimilarity(String t1, String t2, int maxScore) {
        try {
            int value1 = Integer.parseInt(t1);
            int value2 = Integer.parseInt(t2);
            int distance = Math.abs(value1 - value2);
            int max = Math.max(value1, value2);
            if (max == 0) return maxScore;
            return (max - distance) * maxScore / max;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private int neighborTextSimilarity(String text1, String text2, int maxScore) {
        if (text1.length() == 0 || text2.length() == 0) return 0;
        
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");
        
        int matchCount = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equalsIgnoreCase(word2)) {
                    matchCount++;
                    break;
                }
            }
        }
        
        int totalWords = Math.max(words1.length, words2.length);
        if (totalWords == 0) return 0;
        return (matchCount * maxScore) / totalWords;
    }
}
