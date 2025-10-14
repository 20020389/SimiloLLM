package org.example;

import org.openqa.selenium.WebDriver;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Dynamic Weight Adjustment System for Similo
 * Automatically learns and adjusts attribute weights based on historical matching performance
 */
public class DynamicWeightSimilo extends Similo {

    // Configuration constants
    private static final int HISTORY_WINDOW_SIZE = 1000;  // Number of recent matches to consider
    private static final double LEARNING_RATE = 0.1;      // How fast weights adjust
    private static final double WEIGHT_MIN = 0.1;         // Minimum weight value
    private static final double WEIGHT_MAX = 3.0;         // Maximum weight value
    private static final double STABILITY_THRESHOLD = 0.3; // Variance threshold for stability
    private static final int BATCH_SIZE = 50;             // Batch size for weight updates

    // Core components
    private final WeightManager weightManager;
    private final MatchHistoryManager historyManager;
    private final ContributionAnalyzer contributionAnalyzer;
    private final StabilityAnalyzer stabilityAnalyzer;
    private final ContextManager contextManager;

    // Default attribute names matching Similo's LOCATORS array
    private static final String[] ATTRIBUTE_NAMES = {
            "tag", "class", "name", "id", "href", "alt", "xpath", "idxpath",
            "is_button", "location", "area", "shape", "visible_text", "neighbor_text"
    };

    public DynamicWeightSimilo(WebDriver webDriver, String propertiesFolder) {
        super(webDriver, propertiesFolder);

        this.weightManager = new WeightManager();
        this.historyManager = new MatchHistoryManager();
        this.contributionAnalyzer = new ContributionAnalyzer();
        this.stabilityAnalyzer = new StabilityAnalyzer();
        this.contextManager = new ContextManager();

        // Initialize with default weights from parent Similo
        weightManager.initializeDefaultWeights();

        // Load historical data if exists
        loadHistoricalData();
    }

    /**
     * Enhanced similarity calculation with dynamic weights
     */
    @Override
    public double calcSimilarityScore(Locator targetWidget, Locator candidateWidget) {
        // Get current context
        PageContext context = contextManager.detectContext(candidateWidget);

        // Get adjusted weights for this context
        double[] dynamicWeights = weightManager.getWeightsForContext(context);

        // Calculate similarity with dynamic weights
        double totalScore = 0.0;
        Map<String, Double> attributeContributions = new HashMap<>();

        for (int i = 0; i < ATTRIBUTE_NAMES.length; i++) {
            String attribute = ATTRIBUTE_NAMES[i];
            double weight = dynamicWeights[i];

            // Calculate attribute similarity (using parent's methods)
            double similarity = calculateAttributeSimilarity(
                    targetWidget, candidateWidget, attribute, i
            );

            double contribution = similarity * weight;
            totalScore += contribution;

            // Track contribution for learning
            attributeContributions.put(attribute, contribution);
        }

        // Store contribution data for later analysis
        candidateWidget.putMetadata("attribute_contributions",
                serializeContributions(attributeContributions));
        candidateWidget.putMetadata("context_type", context.getType().toString());

        return totalScore;
    }

    /**
     * Record matching result for learning
     */
    public void recordMatchResult(Locator target, Locator matched,
                                  boolean success, double confidence) {
        MatchRecord record = new MatchRecord(
                target, matched, success, confidence,
                deserializeContributions(matched.getMetadata("attribute_contributions")),
                PageContext.Type.valueOf(matched.getMetadata("context_type"))
        );

        historyManager.addRecord(record);

        // Trigger weight update if enough records collected
        if (historyManager.shouldUpdateWeights()) {
            updateWeights();
        }
    }

    /**
     * Main weight update logic
     */
    private void updateWeights() {
        List<MatchRecord> recentMatches = historyManager.getRecentMatches(BATCH_SIZE);

        if (recentMatches.isEmpty()) {
            return;
        }

        // Group matches by context
        Map<PageContext.Type, List<MatchRecord>> contextGroups =
                recentMatches.stream()
                        .collect(Collectors.groupingBy(MatchRecord::getContextType));

        // Update weights for each context
        for (Map.Entry<PageContext.Type, List<MatchRecord>> entry : contextGroups.entrySet()) {
            PageContext.Type contextType = entry.getKey();
            List<MatchRecord> matches = entry.getValue();

            // Analyze contributions
            Map<String, Double> avgContributions =
                    contributionAnalyzer.analyzeContributions(matches);

            // Analyze stability
            Map<String, Double> stabilityScores =
                    stabilityAnalyzer.analyzeStability(matches);

            // Update weights
            double[] currentWeights = weightManager.getWeights(contextType);
            double[] newWeights = calculateNewWeights(
                    currentWeights, avgContributions, stabilityScores
            );

            weightManager.updateWeights(contextType, newWeights);
        }

        // Persist updated weights
        saveWeights();
    }

    /**
     * Calculate new weights based on contribution and stability analysis
     */
    private double[] calculateNewWeights(double[] currentWeights,
                                         Map<String, Double> contributions,
                                         Map<String, Double> stability) {
        double[] newWeights = new double[currentWeights.length];

        for (int i = 0; i < ATTRIBUTE_NAMES.length; i++) {
            String attribute = ATTRIBUTE_NAMES[i];
            double currentWeight = currentWeights[i];

            // Get contribution score (0 to 1, where 0.5 is baseline)
            double contribution = contributions.getOrDefault(attribute, 0.5);

            // Get stability score (0 to 1, where 1 is most stable)
            double stabilityScore = stability.getOrDefault(attribute, 1.0);

            // Calculate weight adjustment
            double adjustment = LEARNING_RATE * (contribution - 0.5);

            // Apply stability factor (unstable attributes get reduced weight)
            if (stabilityScore < STABILITY_THRESHOLD) {
                adjustment *= stabilityScore;
            }

            // Update weight with constraints
            double newWeight = currentWeight * (1 + adjustment);
            newWeight = Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, newWeight));

            newWeights[i] = newWeight;
        }

        return newWeights;
    }

    /**
     * Weight Manager - Manages weights for different contexts
     */
    private class WeightManager {
        private final Map<PageContext.Type, double[]> contextWeights;
        private double[] defaultWeights;

        public WeightManager() {
            this.contextWeights = new ConcurrentHashMap<>();
            initializeDefaultWeights();
        }

        public void initializeDefaultWeights() {
            // Use Similo's default WEIGHTS array
            this.defaultWeights = new double[]{
                    1.5, 0.5, 1.5, 1.5, 0.5, 0.5, 0.5, 0.5,
                    0.5, 0.5, 0.5, 0.5, 1.5, 1.5
            };

            // Initialize context-specific weights
            for (PageContext.Type type : PageContext.Type.values()) {
                contextWeights.put(type, defaultWeights.clone());
            }
        }

        public double[] getWeights(PageContext.Type contextType) {
            return contextWeights.getOrDefault(contextType, defaultWeights).clone();
        }

        public double[] getWeightsForContext(PageContext context) {
            double[] weights = getWeights(context.getType());

            // Apply context-specific adjustments
            if (context.hasHighFormDensity()) {
                // Boost name and id for forms
                weights[2] *= 1.3;  // name
                weights[3] *= 1.3;  // id
            }

            if (context.hasRepeatingPatterns()) {
                // Boost structural attributes for lists/grids
                weights[0] *= 1.2;  // tag
                weights[1] *= 1.2;  // class
            }

            return weights;
        }

        public void updateWeights(PageContext.Type contextType, double[] newWeights) {
            contextWeights.put(contextType, newWeights.clone());
        }
    }

    /**
     * Match History Manager - Manages historical match records
     */
    private class MatchHistoryManager {
        private final LinkedList<MatchRecord> history;
        private int updateCounter = 0;

        public MatchHistoryManager() {
            this.history = new LinkedList<>();
        }

        public synchronized void addRecord(MatchRecord record) {
            history.addLast(record);

            // Maintain window size
            while (history.size() > HISTORY_WINDOW_SIZE) {
                history.removeFirst();
            }

            updateCounter++;
        }

        public boolean shouldUpdateWeights() {
            return updateCounter >= BATCH_SIZE;
        }

        public List<MatchRecord> getRecentMatches(int count) {
            updateCounter = 0;  // Reset counter

            return history.stream()
                    .skip(Math.max(0, history.size() - count))
                    .collect(Collectors.toList());
        }

        public List<MatchRecord> getMatchesInTimeWindow(int hours) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);

            return history.stream()
                    .filter(r -> r.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Contribution Analyzer - Analyzes attribute contributions to successful matches
     */
    private class ContributionAnalyzer {

        public Map<String, Double> analyzeContributions(List<MatchRecord> matches) {
            Map<String, Double> totalContributions = new HashMap<>();
            Map<String, Integer> successCounts = new HashMap<>();

            for (MatchRecord match : matches) {
                if (match.isSuccess()) {
                    Map<String, Double> contributions = match.getAttributeContributions();

                    for (Map.Entry<String, Double> entry : contributions.entrySet()) {
                        String attribute = entry.getKey();
                        double contribution = entry.getValue();

                        totalContributions.merge(attribute, contribution, Double::sum);
                        successCounts.merge(attribute, 1, Integer::sum);
                    }
                }
            }

            // Calculate average contributions
            Map<String, Double> avgContributions = new HashMap<>();
            for (String attribute : ATTRIBUTE_NAMES) {
                int count = successCounts.getOrDefault(attribute, 1);
                double total = totalContributions.getOrDefault(attribute, 0.0);

                // Normalize to 0-1 range where 0.5 is baseline
                double avg = total / count;
                double normalized = Math.tanh(avg - 0.5) * 0.5 + 0.5;

                avgContributions.put(attribute, normalized);
            }

            return avgContributions;
        }
    }

    /**
     * Stability Analyzer - Analyzes attribute stability over time
     */
    private class StabilityAnalyzer {

        public Map<String, Double> analyzeStability(List<MatchRecord> matches) {
            Map<String, List<Double>> attributeValues = new HashMap<>();

            // Collect values for each attribute
            for (MatchRecord match : matches) {
                for (Map.Entry<String, Double> entry :
                        match.getAttributeContributions().entrySet()) {
                    attributeValues.computeIfAbsent(entry.getKey(),
                            k -> new ArrayList<>()).add(entry.getValue());
                }
            }

            // Calculate stability scores
            Map<String, Double> stabilityScores = new HashMap<>();

            for (String attribute : ATTRIBUTE_NAMES) {
                List<Double> values = attributeValues.getOrDefault(
                        attribute, Collections.emptyList()
                );

                if (values.size() < 2) {
                    stabilityScores.put(attribute, 1.0);  // Assume stable if not enough data
                    continue;
                }

                // Calculate coefficient of variation
                double mean = values.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                double variance = values.stream()
                        .mapToDouble(v -> Math.pow(v - mean, 2))
                        .average()
                        .orElse(0.0);

                double cv = mean > 0 ? Math.sqrt(variance) / mean : 0;

                // Convert to stability score (0 to 1, where 1 is most stable)
                double stability = 1.0 / (1.0 + cv);
                stabilityScores.put(attribute, stability);
            }

            return stabilityScores;
        }
    }

    /**
     * Context Manager - Detects and manages page contexts
     */
    private class ContextManager {

        public PageContext detectContext(Locator element) {
            PageContext context = new PageContext();

            // Analyze element and its metadata to determine context
            String tag = element.getMetadata("tag");
            String className = element.getMetadata("class");
            String text = element.getMetadata("visible_text");

            // Simple heuristics for context detection
            if (containsFormElements(tag)) {
                context.setType(PageContext.Type.FORM);
            } else if (containsEcommerceSignals(text, className)) {
                context.setType(PageContext.Type.ECOMMERCE);
            } else if (containsListSignals(tag, className)) {
                context.setType(PageContext.Type.LIST);
            } else {
                context.setType(PageContext.Type.GENERAL);
            }

            return context;
        }

        private boolean containsFormElements(String tag) {
            return tag != null &&
                    (tag.contains("input") || tag.contains("textarea") ||
                            tag.contains("select") || tag.contains("form"));
        }

        private boolean containsEcommerceSignals(String text, String className) {
            if (text != null) {
                String lowerText = text.toLowerCase();
                if (lowerText.contains("price") || lowerText.contains("cart") ||
                        lowerText.contains("buy") || lowerText.contains("$")) {
                    return true;
                }
            }

            if (className != null) {
                String lowerClass = className.toLowerCase();
                return lowerClass.contains("product") || lowerClass.contains("price") ||
                        lowerClass.contains("cart");
            }

            return false;
        }

        private boolean containsListSignals(String tag, String className) {
            if (tag != null && (tag.contains("li") || tag.contains("tr"))) {
                return true;
            }

            if (className != null) {
                String lowerClass = className.toLowerCase();
                return lowerClass.contains("list") || lowerClass.contains("item") ||
                        lowerClass.contains("row");
            }

            return false;
        }
    }

    /**
     * Data Classes
     */
    private static class MatchRecord {
        private final Locator target;
        private final Locator matched;
        private final boolean success;
        private final double confidence;
        private final Map<String, Double> attributeContributions;
        private final PageContext.Type contextType;
        private final LocalDateTime timestamp;

        public MatchRecord(Locator target, Locator matched, boolean success,
                           double confidence, Map<String, Double> contributions,
                           PageContext.Type contextType) {
            this.target = target;
            this.matched = matched;
            this.success = success;
            this.confidence = confidence;
            this.attributeContributions = contributions;
            this.contextType = contextType;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public Map<String, Double> getAttributeContributions() {
            return attributeContributions;
        }

        public PageContext.Type getContextType() {
            return contextType;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    private static class PageContext {
        public enum Type {
            GENERAL, FORM, ECOMMERCE, LIST, DASHBOARD
        }

        private Type type = Type.GENERAL;
        private boolean highFormDensity = false;
        private boolean repeatingPatterns = false;

        // Getters and setters
        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public boolean hasHighFormDensity() {
            return highFormDensity;
        }

        public boolean hasRepeatingPatterns() {
            return repeatingPatterns;
        }
    }

    /**
     * Persistence methods
     */
    private void saveWeights() {
        try {
            File file = new File(Objects
                    .requireNonNull(CSVReader.class.getClassLoader().getResource("dynamic_weights.dat")).getFile());
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(file.toPath()))) {
                oos.writeObject(weightManager.contextWeights);
            }
        } catch (IOException e) {
            System.err.println("Failed to save weights: " + e.getMessage());
        }
    }

    private void loadHistoricalData() {
        File weightsFile = new File(Objects
                .requireNonNull(CSVReader.class.getClassLoader().getResource("dynamic_weights.dat")).getFile());
        if (weightsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    Files.newInputStream(weightsFile.toPath()))) {
                Map<PageContext.Type, double[]> loaded =
                        (Map<PageContext.Type, double[]>) ois.readObject();
                weightManager.contextWeights.putAll(loaded);
            } catch (Exception e) {
                System.err.println("Failed to load weights: " + e.getMessage());
            }
        }
    }

    /**
     * Utility methods
     */
    private String serializeContributions(Map<String, Double> contributions) {
        return contributions.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private Map<String, Double> deserializeContributions(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Double> contributions = new HashMap<>();
        for (String pair : serialized.split(",")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                contributions.put(parts[0], Double.parseDouble(parts[1]));
            }
        }
        return contributions;
    }

    /**
     * Calculate similarity for a specific attribute
     */
    private double calculateAttributeSimilarity(Locator target, Locator candidate,
                                                String attribute, int index) {
        String targetValue = target.getMetadata(attribute);
        String candidateValue = candidate.getMetadata(attribute);

        if (targetValue == null || candidateValue == null) {
            return 0.0;
        }

        // Use parent's similarity functions based on attribute type
        int similarityFunction =0;

        switch (similarityFunction) {
            case 0: // Equal similarity
                return targetValue.equals(candidateValue) ? 1.0 : 0.0;

            case 1: // String similarity
                return calculateStringSimilarity(targetValue, candidateValue);

            case 2: // Integer similarity
                return calculateIntegerSimilarity(targetValue, candidateValue);

            case 3: // Location similarity
                return calculateLocationSimilarity(target, candidate);

            case 4: // Neighbor text similarity
                return calculateNeighborTextSimilarity(targetValue, candidateValue);

            default:
                return 0.0;
        }
    }

    private double calculateStringSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        // Levenshtein distance normalized
        int distance = computeLevenshteinDistance(s1.toLowerCase(), s2.toLowerCase());
        int maxLen = Math.max(s1.length(), s2.length());

        return maxLen > 0 ? 1.0 - ((double) distance / maxLen) : 0.0;
    }

    private double calculateIntegerSimilarity(String v1, String v2) {
        try {
            int i1 = Integer.parseInt(v1);
            int i2 = Integer.parseInt(v2);
            int diff = Math.abs(i1 - i2);
            int max = Math.max(i1, i2);

            return max > 0 ? 1.0 - ((double) diff / max) : 1.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double calculateLocationSimilarity(Locator target, Locator candidate) {
        try {
            int x1 = Integer.parseInt(target.getMetadata("x"));
            int y1 = Integer.parseInt(target.getMetadata("y"));
            int x2 = Integer.parseInt(candidate.getMetadata("x"));
            int y2 = Integer.parseInt(candidate.getMetadata("y"));

            double distance = Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));

            // Normalize (assume max distance of 500px for high similarity)
            return Math.max(0, 1.0 - (distance / 500.0));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double calculateNeighborTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;

        Set<String> words1 = new HashSet<>(Arrays.asList(text1.toLowerCase().split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.toLowerCase().split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // Placeholder for Levenshtein distance computation
    private int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(dp[i - 1][j - 1] + cost,
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Public API for monitoring and debugging
     */
    public Map<String, Double> getCurrentWeights(PageContext.Type contextType) {
        double[] weights = weightManager.getWeights(contextType);
        Map<String, Double> result = new HashMap<>();

        for (int i = 0; i < ATTRIBUTE_NAMES.length; i++) {
            result.put(ATTRIBUTE_NAMES[i], weights[i]);
        }

        return result;
    }

    public void printWeightAnalysis() {
        System.out.println("=== Dynamic Weight Analysis ===");

        for (PageContext.Type type : PageContext.Type.values()) {
            System.out.println("\nContext: " + type);
            Map<String, Double> weights = getCurrentWeights(type);

            weights.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .forEach(e -> System.out.printf("  %-15s: %.3f\n", e.getKey(), e.getValue()));
        }
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_matches", historyManager.history.size());
        stats.put("recent_matches_24h",
                historyManager.getMatchesInTimeWindow(24).size());
        stats.put("contexts_tracked", PageContext.Type.values().length);

        // Success rate
        long successCount = historyManager.history.stream()
                .filter(MatchRecord::isSuccess)
                .count();
        double successRate = historyManager.history.isEmpty() ? 0 :
                (double) successCount / historyManager.history.size();
        stats.put("success_rate", successRate);

        return stats;
    }
}