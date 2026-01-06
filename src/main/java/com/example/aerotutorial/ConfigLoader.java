package com.example.aerotutorial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class ConfigLoader {
    private static final Properties properties = new Properties();
    private static boolean loaded = false;


    static void loadConfig() {
        if (loaded) return;

        try (InputStream input = ConfigLoader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {

            if (input == null) {
                System.err.println("❌ ERROR: config.properties file not found!");
                System.err.println("Please create config.properties from config.properties.template");
                System.err.println("Instructions:");
                System.err.println("1. Copy src/main/resources/config.properties.template");
                System.err.println("2. Rename it to config.properties");
                System.err.println("3. Replace YOUR_API_KEY_HERE with your actual API key");
                throw new RuntimeException("config.properties not found");
            }

            properties.load(input);
            loaded = true;
            System.out.println("✅ Configuration loaded successfully");

        } catch (IOException e) {
            System.err.println("❌ ERROR: Failed to load config.properties");
            e.printStackTrace();
            throw new RuntimeException("Failed to load configuration", e);
        }
    }


    public static String getApiKey() {
        if (!loaded) {
            loadConfig();
        }

        String apiKey = properties.getProperty("openweather.api.key");

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            System.err.println("❌ ERROR: API key not configured properly!");
            System.err.println("Please edit config.properties and add your real API key");
            throw new RuntimeException("API key not configured");
        }

        return apiKey.trim();
    }


    public static String getProperty(String key) {
        if (!loaded) {
            loadConfig();
        }
        return properties.getProperty(key);
    }


    public static String getProperty(String key, String defaultValue) {
        if (!loaded) {
            loadConfig();
        }
        return properties.getProperty(key, defaultValue);
    }
}

