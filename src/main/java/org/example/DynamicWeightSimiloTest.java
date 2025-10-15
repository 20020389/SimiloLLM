package org.example;

import java.util.ArrayList;
import java.util.List;

/**
 * Test class to demonstrate Dynamic Weight Similo functionality
 * 
 * This test simulates various matching scenarios to show how weights adapt over time
 */
public class DynamicWeightSimiloTest {
    
    public static void main(String[] args) {
        System.out.println("=== Dynamic Weight Similo Test ===\n");
        
        // Create instance without WebDriver (for testing purposes)
        DynamicWeightSimilo similo = new DynamicWeightSimilo();
        
        // Test 1: Initial weights
        System.out.println("Test 1: Initial Weights");
        similo.printWeightStatistics();
        
        // Test 2: Simulate e-commerce scenario
        System.out.println("\nTest 2: E-commerce Context");
        testEcommerceScenario(similo);
        
        // Test 3: Simulate form scenario
        System.out.println("\nTest 3: Form Context");
        testFormScenario(similo);
        
        // Test 4: Test weight persistence
        System.out.println("\nTest 4: Weight Persistence");
        testWeightPersistence(similo);
        
        // Test 5: Test stability tracking
        System.out.println("\nTest 5: Stability Tracking");
        testStabilityTracking(similo);
        
        System.out.println("\n=== All Tests Completed ===");
    }
    
    /**
     * Test e-commerce scenario where product names and visible text are important
     */
    private static void testEcommerceScenario(DynamicWeightSimilo similo) {
        similo.setContext("ecommerce");
        
        // Create mock locators for product elements
        Locator targetProduct = createProductLocator("iPhone 15 Pro", "product-123", "btn-buy");
        
        // Simulate successful matches where visible_text was key
        for (int i = 0; i < 50; i++) {
            Locator matchedProduct = createProductLocator("iPhone 15 Pro", "product-" + i, "btn-buy-" + i);
            similo.recordSuccessfulMatch(targetProduct, matchedProduct);
        }
        
        System.out.println("After 50 successful product matches:");
        similo.printWeightStatistics();
        
        double visibleTextWeight = similo.getWeight("visible_text");
        double nameWeight = similo.getWeight("name");
        System.out.println("✓ visible_text weight increased to: " + String.format("%.3f", visibleTextWeight));
        System.out.println("✓ name weight: " + String.format("%.3f", nameWeight));
    }
    
    /**
     * Test form scenario where name and id attributes are crucial
     */
    private static void testFormScenario(DynamicWeightSimilo similo) {
        similo.resetWeights();
        similo.setContext("form");
        
        // Create mock locators for form elements
        Locator targetInput = createFormLocator("email", "email-input", "Enter your email");
        
        // Simulate successful matches where name and id were key
        for (int i = 0; i < 50; i++) {
            Locator matchedInput = createFormLocator("email", "email-input", "Email address " + i);
            similo.recordSuccessfulMatch(targetInput, matchedInput);
        }
        
        System.out.println("After 50 successful form field matches:");
        similo.printWeightStatistics();
        
        double nameWeight = similo.getWeight("name");
        double idWeight = similo.getWeight("id");
        System.out.println("✓ name weight increased to: " + String.format("%.3f", nameWeight));
        System.out.println("✓ id weight increased to: " + String.format("%.3f", idWeight));
    }
    
    /**
     * Test saving and loading weights
     */
    private static void testWeightPersistence(DynamicWeightSimilo similo) {
        String filename = "test_weights.properties";
        
        // Save current weights
        similo.saveWeights(filename);
        
        // Get current weights
        double[] weightsBefore = similo.getDynamicWeights();
        
        // Reset weights
        similo.resetWeights();
        
        // Load weights
        similo.loadWeights(filename);
        
        // Compare
        double[] weightsAfter = similo.getDynamicWeights();
        
        boolean match = true;
        for (int i = 0; i < weightsBefore.length; i++) {
            if (Math.abs(weightsBefore[i] - weightsAfter[i]) > 0.001) {
                match = false;
                break;
            }
        }
        
        if (match) {
            System.out.println("✓ Weights successfully saved and loaded");
        } else {
            System.out.println("✗ Weight persistence test failed");
        }
        
        similo.printWeightStatistics();
    }
    
    /**
     * Test stability tracking with changing attributes
     */
    private static void testStabilityTracking(DynamicWeightSimilo similo) {
        similo.resetWeights();
        similo.setContext("default");
        
        // Create target with stable attributes
        Locator target = new Locator();
        target.putMetadata("tag", "button");
        target.putMetadata("name", "submit-btn");
        target.putMetadata("visible_text", "Submit");
        
        // Simulate matches with stable name but changing text
        for (int i = 0; i < 30; i++) {
            Locator matched = new Locator();
            matched.putMetadata("tag", "button");
            matched.putMetadata("name", "submit-btn"); // Stable
            matched.putMetadata("visible_text", "Submit " + (i % 5)); // Changes frequently
            
            similo.recordSuccessfulMatch(target, matched);
        }
        
        System.out.println("After 30 matches with varying stability:");
        similo.printWeightStatistics();
        
        double nameWeight = similo.getWeight("name");
        double textWeight = similo.getWeight("visible_text");
        
        System.out.println("✓ name (stable) weight: " + String.format("%.3f", nameWeight));
        System.out.println("✓ visible_text (unstable) weight: " + String.format("%.3f", textWeight));
        System.out.println("✓ Stable attributes should have higher effective weights");
    }
    
    /**
     * Helper: Create a product locator
     */
    private static Locator createProductLocator(String visibleText, String id, String className) {
        Locator locator = new Locator();
        locator.putMetadata("tag", "div");
        locator.putMetadata("class", className);
        locator.putMetadata("id", id);
        locator.putMetadata("visible_text", visibleText);
        locator.putMetadata("xpath", "//div[@id='" + id + "']");
        locator.putMetadata("x", "100");
        locator.putMetadata("y", "200");
        locator.putMetadata("width", "300");
        locator.putMetadata("height", "50");
        locator.putMetadata("area", "15000");
        locator.putMetadata("shape", "600");
        return locator;
    }
    
    /**
     * Helper: Create a form input locator
     */
    private static Locator createFormLocator(String name, String id, String placeholder) {
        Locator locator = new Locator();
        locator.putMetadata("tag", "input");
        locator.putMetadata("name", name);
        locator.putMetadata("id", id);
        locator.putMetadata("visible_text", placeholder);
        locator.putMetadata("xpath", "//input[@name='" + name + "']");
        locator.putMetadata("x", "50");
        locator.putMetadata("y", "100");
        locator.putMetadata("width", "200");
        locator.putMetadata("height", "30");
        locator.putMetadata("area", "6000");
        locator.putMetadata("shape", "667");
        return locator;
    }
    
    /**
     * Demonstrate the full workflow
     */
    public static void demonstrateFullWorkflow() {
        System.out.println("\n=== Full Workflow Demonstration ===\n");
        
        DynamicWeightSimilo similo = new DynamicWeightSimilo();
        
        // Step 1: Start with default weights
        System.out.println("Step 1: Initial state");
        similo.printWeightStatistics();
        
        // Step 2: Run tests in e-commerce context
        System.out.println("Step 2: Learning from e-commerce interactions");
        similo.setContext("ecommerce");
        
        for (int i = 0; i < 100; i++) {
            Locator target = createProductLocator("Product " + (i % 10), "prod-" + i, "product-card");
            Locator matched = createProductLocator("Product " + (i % 10), "prod-" + (i + 1), "product-card-alt");
            similo.recordSuccessfulMatch(target, matched);
        }
        
        similo.printWeightStatistics();
        
        // Step 3: Switch to form context
        System.out.println("Step 3: Learning from form interactions");
        similo.setContext("form");
        
        for (int i = 0; i < 100; i++) {
            Locator target = createFormLocator("field-" + (i % 5), "input-" + i, "Enter value");
            Locator matched = createFormLocator("field-" + (i % 5), "input-" + i, "Different placeholder");
            similo.recordSuccessfulMatch(target, matched);
        }
        
        similo.printWeightStatistics();
        
        // Step 4: Save the learned weights
        System.out.println("Step 4: Saving learned weights");
        similo.saveWeights("learned_weights.properties");
        
        System.out.println("\n✓ Workflow complete - weights have been adapted based on usage patterns");
    }
}
