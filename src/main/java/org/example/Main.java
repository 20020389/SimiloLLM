package org.example;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Main {
    public static void main(String[] args) {
        Conversation conv = new Conversation();
        String response = conv.addMessage("Hello gpt");

        System.out.println("Hello and welcome! => " + response);

        for (int i = 1; i <= 5; i++) {
            System.out.println("i = " + i);
        }
    }
}