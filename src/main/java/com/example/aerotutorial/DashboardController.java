package com.example.aerotutorial;

import javafx.application.Platform;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class DashboardController {

    @FXML
    private WebView mapView;

    @FXML
    private Label currentAqiLabel, predictedAqiLabel, aqiAlertLabel, locationLabel;

    @FXML
    private LineChart<String, Number> historyChart;

    @FXML
    private TextField searchField;

    @FXML
    private VBox searchHistoryBox;

    @FXML
    private VBox preventiveMeasuresBox;

    @FXML
    private VBox measuresContent;

    @FXML
    private Label animationIndicator;

    // In-memory storage for last 7 days AQI per city
    private final Map<String, List<Integer>> cityAqiHistory = new HashMap<>();

    // Track nodes with tooltips installed
    private final Set<javafx.scene.Node> nodesWithTooltips = new HashSet<>();

    // Search history storage
    private final List<SearchHistoryItem> searchHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 5;
    private static final String SEARCH_HISTORY_FILE = "search_history.dat";

    private String selectedCity = null;
    private double selectedLat, selectedLon;

    @FXML
    public void initialize() {
        setupMap();
        loadSearchHistory();
        updateSearchHistoryDisplay();

        // Add sparkle animation to the indicator
        if (animationIndicator != null) {
            addSparkleAnimation();
        }
    }

    /** Initialize the map and expose Java methods to JS */
    private void setupMap() {
        WebEngine webEngine = mapView.getEngine();
        webEngine.setJavaScriptEnabled(true);

        // Better error handling
        webEngine.setOnError(event -> {
            System.err.println("❌ JS ERROR: " + event.getMessage());
        });

        webEngine.setOnAlert(event -> {
            System.out.println("⚠️ JS ALERT: " + event.getData());
        });

        // Load the map HTML
        String mapUrl = getClass().getResource("/com/example/aerotutorial/map.html").toExternalForm();
        System.out.println("Loading map from: " + mapUrl);
        webEngine.load(mapUrl);

        // Set up Java bridge when page loads
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if ("SUCCEEDED".equals(newState.toString())) {
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("app", this); // Expose Java methods
                    System.out.println("✓ Java bridge established successfully!");

                    // Verify the bridge works
                    Object result = webEngine.executeScript("typeof window.app.onMapClick");
                    System.out.println("Bridge verification - onMapClick type: " + result);
                } catch (Exception e) {
                    System.err.println("❌ Failed to establish Java bridge: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if ("FAILED".equals(newState.toString())) {
                System.err.println("❌ Failed to load map HTML");
            }
        });
    }

    /** Called by JS when user clicks on map */
    public void onMapClick(String city, double lat, double lon) {
        Platform.runLater(() -> {
            // Create unique identifier for each location using coordinates
            selectedCity = String.format("%.2f,%.2f", lat, lon);
            selectedLat = lat;
            selectedLon = lon;

            System.out.println("Map clicked at: " + selectedCity);

            // Update location label with loading text
            locationLabel.setText("Loading location...");
            locationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");

            // Fetch location name in background thread
            new Thread(() -> {
                String locationName = getLocationName(lat, lon);
                Platform.runLater(() -> {
                    locationLabel.setText(locationName);
                    locationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
                });
            }).start();

            // Initialize history for this location if not exists
            if (!cityAqiHistory.containsKey(selectedCity)) {
                List<Integer> newHistory = new ArrayList<>();
                cityAqiHistory.put(selectedCity, newHistory);
                System.out.println("Created new history for location: " + selectedCity);
            } else {
                System.out.println("Existing history size: " + cityAqiHistory.get(selectedCity).size());
            }

            fetchAndDisplayAQI();
        });
    }

    /** Get location name using reverse geocoding (Nominatim API) */
    private String getLocationName(double lat, double lon) {
        try {
            String urlString = String.format(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=10",
                lat, lon
            );
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AeroSafe/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse JSON response to get display name
                String jsonResponse = response.toString();

                // Extract display_name from JSON (simple parsing)
                int displayNameStart = jsonResponse.indexOf("\"display_name\":\"") + 16;
                if (displayNameStart > 15) {
                    int displayNameEnd = jsonResponse.indexOf("\"", displayNameStart);
                    String displayName = jsonResponse.substring(displayNameStart, displayNameEnd);

                    // Shorten the name if it's too long (take first 2-3 parts)
                    String[] parts = displayName.split(",");
                    if (parts.length > 3) {
                        return parts[0].trim() + ", " + parts[1].trim() + ", " + parts[2].trim();
                    }
                    return displayName;
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching location name: " + e.getMessage());
        }

        // Fallback to coordinates if geocoding fails
        return String.format("Lat: %.4f, Lon: %.4f", lat, lon);
    }

    /** Fetch current AQI and update dashboard */
    private void fetchAndDisplayAQI() {
        if (selectedCity == null) {
            currentAqiLabel.setText("Please select a location first");
            return;
        }

        System.out.println("=== Fetching AQI for location: " + selectedCity + " (" + selectedLat + ", " + selectedLon + ") ===");

        // Show loading state
        currentAqiLabel.setText("Fetching AQI...");
        currentAqiLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: gray;");

        // Fetch real AQI from API in background thread
        new Thread(() -> {
            int currentAqi = AQIFetcher.fetchAQI(selectedLat, selectedLon);

            Platform.runLater(() -> {
                if (currentAqi <= 0) {
                    System.out.println("❌ Failed to fetch AQI - API returned: " + currentAqi);
                    currentAqiLabel.setText("Current AQI: N/A");
                    currentAqiLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: gray;");
                    predictedAqiLabel.setText("Predicted AQI: N/A");
                    predictedAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");
                    aqiAlertLabel.setText("Unable to fetch AQI data. Check console for errors.");
                    aqiAlertLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red;");
                    historyChart.getData().clear();
                    return;
                }

                System.out.println("✅ Successfully fetched AQI: " + currentAqi);

                // Display fetched current AQI
                currentAqiLabel.setText("Current Calculate AQI: " + currentAqi);
                currentAqiLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");
                aqiAlertLabel.setText(getAqiAlert(currentAqi));
                aqiAlertLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");

                // Update preventive measures
                updatePreventiveMeasures(currentAqi);

                // Update 7-day history
                List<Integer> history = cityAqiHistory.get(selectedCity);

                // Check if this AQI is already in history (avoid duplicates on same click)
                if (history.isEmpty() || history.get(history.size() - 1) != currentAqi) {
                    if (history.size() >= 7) {
                        int removed = history.remove(0); // remove oldest
                        System.out.println("Removed oldest AQI value: " + removed);
                    }
                    history.add(currentAqi);
                    System.out.println("Added AQI to history: " + currentAqi);
                }

                System.out.println("Current history (size=" + history.size() + "): " + history);

                // Update chart with history
                updateHistoryChart(history);

                // Generate predicted AQI using PredictionEngine
                if (history.size() >= 2) {
                    System.out.println("Generating prediction with " + history.size() + " data points");
                    PredictionEngine.PredictionResult result = PredictionEngine.predictNextDay(history);
                    int predictedAqi = (int) Math.round(result.predicted);

                    System.out.println("Prediction result: " + predictedAqi + " (slope=" + result.slope + ", intercept=" + result.intercept + ")");

                    // Display predicted AQI with styling
                    predictedAqiLabel.setText("Predicted AQI (Tomorrow): " + predictedAqi);
                    predictedAqiLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(predictedAqi) + ";");

                    // Show trend
                    String trend = getTrend(result.slope);
                    predictedAqiLabel.setText(predictedAqiLabel.getText() + " " + trend);
                } else {
                    System.out.println("Not enough data for prediction. Current size: " + history.size());
                    predictedAqiLabel.setText("Predicted AQI: N/A (Need " + (2 - history.size()) + " more data point" + (2 - history.size() > 1 ? "s" : "") + ")");
                    predictedAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");
                }
            });
        }).start();
    }

    /** Update chart with history */
    private void updateHistoryChart(List<Integer> history) {
        historyChart.getData().clear();
        nodesWithTooltips.clear(); // Clear tracking set

        if (history.isEmpty()) {
            System.out.println("No history data to display in chart");
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("AQI History");

        // Date formatter for chart labels (e.g., "Dec 17")
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        LocalDate today = LocalDate.now();

        // Add data points with actual dates
        for (int i = 0; i < history.size(); i++) {
            // Calculate the date: going backwards from today
            int daysAgo = history.size() - 1 - i;
            LocalDate date = today.minusDays(daysAgo);

            // Format: "Dec 17" or "Today" for today's date
            String dayLabel;
            if (daysAgo == 0) {
                dayLabel = "Today (" + date.format(formatter) + ")";
            } else if (daysAgo == 1) {
                dayLabel = "Yesterday (" + date.format(formatter) + ")";
            } else {
                dayLabel = date.format(formatter);
            }

            int aqiValue = history.get(i);
            XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(dayLabel, aqiValue);
            series.getData().add(dataPoint);
            System.out.println("Chart point " + i + ": " + dayLabel + " = " + aqiValue);
        }

        historyChart.getData().add(series);
        historyChart.setLegendVisible(false);

        // Install tooltips after chart is fully rendered - using multiple delayed attempts
        Platform.runLater(() -> installTooltipsWithRetry(series, 0));

        System.out.println("Chart updated with " + history.size() + " data points with actual dates");
    }

    /** Install tooltips with retry mechanism to ensure nodes are ready */
    private void installTooltipsWithRetry(XYChart.Series<String, Number> series, int attempt) {
        if (attempt > 5) {
            System.out.println("Max tooltip installation attempts reached");
            return;
        }

        boolean allNodesReady = true;
        int installedCount = 0;

        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getNode() == null) {
                allNodesReady = false;
                continue;
            }

            // Only install if not already installed
            if (!nodesWithTooltips.contains(data.getNode())) {
                int aqiValue = data.getYValue().intValue();
                String dayLabel = data.getXValue();

                // Create tooltip with AQI value and status
                String aqiStatus = getAqiAlert(aqiValue);
                Tooltip tooltip = new Tooltip(
                    "📅 " + dayLabel + "\n" +
                    "🌡️ AQI: " + aqiValue + "\n" +
                    "📊 Status: " + aqiStatus
                );

                // Style the tooltip
                tooltip.setStyle(
                    "-fx-font-size: 14px; " +
                    "-fx-background-color: rgba(40, 40, 40, 0.95); " +
                    "-fx-text-fill: white; " +
                    "-fx-padding: 12px; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-border-color: " + getAqiColor(aqiValue) + "; " +
                    "-fx-border-width: 2px; " +
                    "-fx-border-radius: 6px;"
                );

                // Show tooltip faster and keep it visible longer
                tooltip.setShowDelay(javafx.util.Duration.millis(200));
                tooltip.setShowDuration(javafx.util.Duration.seconds(30));
                tooltip.setHideDelay(javafx.util.Duration.millis(200));

                // Install tooltip on the node
                Tooltip.install(data.getNode(), tooltip);
                nodesWithTooltips.add(data.getNode()); // Track this node

                // Add visual feedback on hover
                final var node = data.getNode();
                node.setOnMouseEntered(e -> {
                    node.setStyle(
                        "-fx-background-color: " + getAqiColor(aqiValue) + "; " +
                        "-fx-scale-x: 2.0; " +
                        "-fx-scale-y: 2.0; " +
                        "-fx-effect: dropshadow(gaussian, " + getAqiColor(aqiValue) + ", 15, 0.8, 0, 0);"
                    );
                    node.setCursor(javafx.scene.Cursor.HAND);
                });

                node.setOnMouseExited(e -> {
                    node.setStyle("");
                    node.setCursor(javafx.scene.Cursor.DEFAULT);
                });

                installedCount++;
                System.out.println("✓ Tooltip installed for: " + dayLabel + " (AQI: " + aqiValue + ")");
            }
        }

        if (!allNodesReady || installedCount < series.getData().size()) {
            // Not all nodes ready, retry after a delay
            final int nextAttempt = attempt + 1;
            System.out.println("Retry tooltip installation (attempt " + nextAttempt + ")...");
            Platform.runLater(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                installTooltipsWithRetry(series, nextAttempt);
            });
        } else {
            System.out.println("✓ All " + installedCount + " tooltips installed successfully!");
        }
    }

    /** Refresh AQI for the currently selected location */
    @FXML
    private void refreshCurrentLocation() {
        if (selectedCity == null) {
            currentAqiLabel.setText("Please select a location on the map first");
            currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: orange;");
            return;
        }
        System.out.println("🔄 Refreshing AQI for current location: " + selectedCity);
        fetchAndDisplayAQI();
    }

    /** Fetch real 7-day historical data for current location */
    @FXML
    private void addTestData() {
        if (selectedCity == null) {
            currentAqiLabel.setText("Please select a location on the map first");
            currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: orange;");
            return;
        }

        System.out.println("=== Fetching REAL 7-day historical data for location: " + selectedCity + " ===");

        // Show loading state
        currentAqiLabel.setText("Fetching historical data...");
        currentAqiLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #3498db;");
        predictedAqiLabel.setText("Loading...");
        predictedAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");

        new Thread(() -> {
            // Fetch real 7-day historical data from API
            Map<LocalDate, Integer> historicalData = AQIFetcher.fetchHistoricalAQI(selectedLat, selectedLon, 7);

            Platform.runLater(() -> {
                if (historicalData.isEmpty()) {
                    System.out.println("❌ Failed to fetch historical data");
                    currentAqiLabel.setText("Failed to fetch historical data");
                    currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                    predictedAqiLabel.setText("N/A");
                    return;
                }

                // Convert map values to list (already sorted by date)
                List<Integer> history = new ArrayList<>(historicalData.values());
                cityAqiHistory.put(selectedCity, history);

                System.out.println("✅ Successfully loaded " + history.size() + " days of REAL historical data");
                System.out.println("Historical AQI values: " + history);

                // Display current (most recent) AQI
                int currentAqi = history.get(history.size() - 1);
                currentAqiLabel.setText("Current AQI: " + currentAqi + " (Real Data)");
                currentAqiLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");
                aqiAlertLabel.setText(getAqiAlert(currentAqi));
                aqiAlertLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");

                // Update chart with real historical dates
                updateHistoryChart(history);

                // Generate prediction based on real historical trend
                if (history.size() >= 2) {
                    PredictionEngine.PredictionResult result = PredictionEngine.predictNextDay(history);
                    int predictedAqi = (int) Math.round(result.predicted);

                    predictedAqiLabel.setText("Predicted AQI (Tomorrow): " + predictedAqi);
                    predictedAqiLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(predictedAqi) + ";");

                    String trend = getTrend(result.slope);
                    predictedAqiLabel.setText(predictedAqiLabel.getText() + " " + trend);

                    System.out.println("✅ Prediction for tomorrow: " + predictedAqi + " " + trend);
                } else {
                    predictedAqiLabel.setText("Need more historical data for prediction");
                    predictedAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");
                }
            });
        }).start();
    }

    /** Logout and switch to login scene */
    @FXML
    private void logout() {
        try {
            Stage stage = (Stage) mapView.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            Scene loginScene = new Scene(loader.load(), 400, 300);
            stage.setScene(loginScene);
            stage.setTitle("AeroSafe - Login");
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Search for a location by name using geocoding API */
    @FXML
    private void searchLocation() {
        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            currentAqiLabel.setText("Please enter a location to search");
            currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: orange;");
            return;
        }

        System.out.println("🔍 Searching for location: " + query);
        currentAqiLabel.setText("Searching for location...");
        currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #3498db;");

        new Thread(() -> {
            try {
                // Use Nominatim OpenStreetMap geocoding API
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "AeroSafe Desktop App");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONArray results = new JSONArray(response.toString());

                    if (results.length() > 0) {
                        JSONObject location = results.getJSONObject(0);
                        double lat = location.getDouble("lat");
                        double lon = location.getDouble("lon");
                        String displayName = location.getString("display_name");

                        System.out.println("✅ Found location: " + displayName + " (" + lat + ", " + lon + ")");

                        // Add to search history
                        addToSearchHistory(query, lat, lon, displayName);

                        Platform.runLater(() -> {
                            // Update map and fetch AQI
                            selectedCity = query;
                            selectedLat = lat;
                            selectedLon = lon;
                            locationLabel.setText("📍 Location: " + displayName);

                            // Center map on location
                            WebEngine engine = mapView.getEngine();
                            engine.executeScript("map.setView([" + lat + ", " + lon + "], 13);");

                            // Fetch AQI for this location
                            refreshCurrentLocation();

                            // Update search history display
                            updateSearchHistoryDisplay();

                            // Clear search field
                            searchField.clear();
                        });
                    } else {
                        Platform.runLater(() -> {
                            currentAqiLabel.setText("Location not found. Try a different search.");
                            currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                        });
                    }
                } else {
                    System.err.println("❌ Geocoding API error: " + conn.getResponseCode());
                    Platform.runLater(() -> {
                        currentAqiLabel.setText("Search failed. Please try again.");
                        currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    currentAqiLabel.setText("Error searching location");
                    currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                });
            }
        }).start();
    }

    /** Add location to search history */
    private void addToSearchHistory(String query, double lat, double lon, String displayName) {
        SearchHistoryItem item = new SearchHistoryItem(query, lat, lon, displayName);

        // Remove if already exists
        searchHistory.removeIf(existing ->
            existing.query.equalsIgnoreCase(query) ||
            (Math.abs(existing.lat - lat) < 0.01 && Math.abs(existing.lon - lon) < 0.01)
        );

        // Add to beginning
        searchHistory.add(0, item);

        // Keep only last N items
        if (searchHistory.size() > MAX_HISTORY_SIZE) {
            searchHistory.subList(MAX_HISTORY_SIZE, searchHistory.size()).clear();
        }

        // Save to file
        saveSearchHistory();
    }

    /** Update the search history display in the sidebar */
    private void updateSearchHistoryDisplay() {
        searchHistoryBox.getChildren().clear();

        if (searchHistory.isEmpty()) {
            Label emptyLabel = new Label("No recent searches");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-padding: 5;");
            searchHistoryBox.getChildren().add(emptyLabel);
            return;
        }

        for (SearchHistoryItem item : searchHistory) {
            Button historyButton = new Button(item.query);
            historyButton.setMaxWidth(Double.MAX_VALUE);
            historyButton.setStyle(
                "-fx-font-size: 11px; " +
                "-fx-padding: 8; " +
                "-fx-background-color: transparent; " +
                "-fx-text-fill: white; " +
                "-fx-cursor: hand; " +
                "-fx-alignment: CENTER_LEFT; " +
                "-fx-background-radius: 3;"
            );

            // Hover effect
            historyButton.setOnMouseEntered(e ->
                historyButton.setStyle(historyButton.getStyle() + "-fx-background-color: rgba(255,255,255,0.1);")
            );
            historyButton.setOnMouseExited(e ->
                historyButton.setStyle(historyButton.getStyle().replace("-fx-background-color: rgba(255,255,255,0.1);", ""))
            );

            // Click to load location
            historyButton.setOnAction(e -> {
                selectedCity = item.query;
                selectedLat = item.lat;
                selectedLon = item.lon;
                locationLabel.setText("📍 Location: " + item.displayName);

                // Center map
                WebEngine engine = mapView.getEngine();
                engine.executeScript("map.setView([" + item.lat + ", " + item.lon + "], 13);");

                // Fetch AQI
                refreshCurrentLocation();
            });

            searchHistoryBox.getChildren().add(historyButton);
        }
    }

    /** Save search history to file */
    private void saveSearchHistory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SEARCH_HISTORY_FILE))) {
            oos.writeObject(new ArrayList<>(searchHistory));
            System.out.println("✅ Search history saved");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save search history: " + e.getMessage());
        }
    }

    /** Load search history from file */
    private void loadSearchHistory() {
        File file = new File(SEARCH_HISTORY_FILE);
        if (!file.exists()) {
            System.out.println("No search history file found");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            @SuppressWarnings("unchecked")
            List<SearchHistoryItem> loaded = (List<SearchHistoryItem>) ois.readObject();
            searchHistory.addAll(loaded);
            System.out.println("✅ Loaded " + searchHistory.size() + " search history items");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load search history: " + e.getMessage());
        }
    }

    /** Navigate to report issue form */
    @FXML
    private void reportIssue() {
        try {
            Stage stage = (Stage) currentAqiLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("report_issue.fxml"));
            stage.setScene(new Scene(loader.load(), 900, 700));
            stage.centerOnScreen();
            System.out.println("Opening report issue form");
        } catch (Exception e) {
            e.printStackTrace();
            currentAqiLabel.setText("Error opening report form");
            currentAqiLabel.setStyle("-fx-text-fill: red;");
        }
    }

    /** Add sparkle animation to the indicator */
    private void addSparkleAnimation() {
        // Rotation animation
        RotateTransition rotate = new RotateTransition(Duration.seconds(2), animationIndicator);
        rotate.setByAngle(360);
        rotate.setCycleCount(Timeline.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);

        // Scale pulse animation
        ScaleTransition scale = new ScaleTransition(Duration.seconds(1), animationIndicator);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.3);
        scale.setToY(1.3);
        scale.setCycleCount(Timeline.INDEFINITE);
        scale.setAutoReverse(true);

        // Glow effect
        Glow glow = new Glow(0.8);
        animationIndicator.setEffect(glow);

        // Start animations
        rotate.play();
        scale.play();
    }

    /** Update preventive measures with animations */
    private void updatePreventiveMeasures(int aqi) {
        // Fade out animation before updating content
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), measuresContent);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(e -> {
            // Clear and update content
            updateMeasuresContent(aqi);

            // Fade in animation
            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), measuresContent);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        fadeOut.play();

        // Animate the preventive measures box border
        animateBorderPulse(aqi);
    }

    /** Update measures content (called after fade out) */
    private void updateMeasuresContent(int aqi) {
        measuresContent.getChildren().clear();

        String bgColor, textColor, borderColor, headerText;
        List<String> measures = new ArrayList<>();
        List<String> whoAffected = new ArrayList<>();

        // ...existing AQI level logic...
        if (aqi <= 50) {
            // Good - Green
            bgColor = "#d4edda";
            textColor = "#155724";
            borderColor = "#28a745";
            headerText = "✅ Air Quality is Good";

            measures.add("😊 It's a great day to be active outside!");
            measures.add("🏃 Perfect conditions for outdoor exercise and activities");
            measures.add("🪟 Open windows to improve indoor air quality");
            measures.add("🌳 Enjoy outdoor recreational activities");
            measures.add("👶 Safe for all age groups including children and elderly");

            whoAffected.add("Everyone can enjoy normal outdoor activities");

        } else if (aqi <= 100) {
            // Moderate - Yellow
            bgColor = "#fff3cd";
            textColor = "#856404";
            borderColor = "#ffc107";
            headerText = "⚠️ Air Quality is Moderate";

            measures.add("🤔 Consider reducing prolonged or intense outdoor activities");
            measures.add("👶 Sensitive individuals should limit outdoor exposure");
            measures.add("🏃 If you experience symptoms, reduce activity level");
            measures.add("🪟 Keep windows closed if you're sensitive to pollution");
            measures.add("💊 Keep rescue inhalers nearby if you have asthma");

            whoAffected.add("Unusually sensitive people should consider reducing prolonged outdoor exertion");

        } else if (aqi <= 150) {
            // Unhealthy for Sensitive Groups - Orange
            bgColor = "#ffe5cc";
            textColor = "#8b4513";
            borderColor = "#ff7e00";
            headerText = "🔶 Unhealthy for Sensitive Groups";

            measures.add("😷 Sensitive groups should wear N95/KN95 masks outdoors");
            measures.add("🏠 Limit outdoor activities, especially for children and elderly");
            measures.add("🪟 Keep windows and doors closed");
            measures.add("💨 Use air purifiers indoors if available");
            measures.add("🚶 Choose indoor activities over outdoor ones");
            measures.add("💊 People with asthma should follow their action plans");
            measures.add("🏥 Monitor for symptoms like coughing or shortness of breath");

            whoAffected.add("❗ Children, elderly, and people with respiratory/heart conditions should take precautions");
            whoAffected.add("Active adults and children should limit prolonged outdoor exertion");

        } else if (aqi <= 200) {
            // Unhealthy - Red
            bgColor = "#f8d7da";
            textColor = "#721c24";
            borderColor = "#dc3545";
            headerText = "⛔ Air Quality is Unhealthy";

            measures.add("😷 WEAR N95/KN95 MASKS when going outside");
            measures.add("🏠 AVOID outdoor activities - stay indoors as much as possible");
            measures.add("🪟 Keep all windows and doors CLOSED");
            measures.add("💨 USE AIR PURIFIERS with HEPA filters");
            measures.add("🚗 Avoid heavy traffic areas");
            measures.add("🏃 Cancel or reschedule outdoor exercise");
            measures.add("👶 Keep children indoors");
            measures.add("🏥 People with conditions should stay alert for symptoms");
            measures.add("💊 Keep medications readily accessible");
            measures.add("📞 Contact doctor if experiencing symptoms");

            whoAffected.add("🚨 EVERYONE should limit outdoor activities");
            whoAffected.add("❗❗ Sensitive groups should AVOID outdoor activities completely");

        } else if (aqi <= 300) {
            // Very Unhealthy - Purple
            bgColor = "#e8d4f1";
            textColor = "#4a148c";
            borderColor = "#8f3f97";
            headerText = "🚨 Air Quality is Very Unhealthy";

            measures.add("😷 MANDATORY: Wear N95/KN95/FFP2 masks if you must go outside");
            measures.add("🏠 STAY INDOORS - Avoid all outdoor activities");
            measures.add("🚪 Keep ALL windows and doors SEALED");
            measures.add("💨 RUN AIR PURIFIERS continuously");
            measures.add("🧹 Avoid activities that increase indoor pollution (cooking, sweeping)");
            measures.add("🚗 AVOID driving - stay home if possible");
            measures.add("👶 Keep children and elderly strictly indoors");
            measures.add("🏥 Monitor health closely for any respiratory symptoms");
            measures.add("💊 Have emergency medications ready");
            measures.add("📞 Seek medical attention if symptoms worsen");
            measures.add("🌡️ Use damp cloths to reduce dust indoors");
            measures.add("💧 Stay hydrated");

            whoAffected.add("🚨🚨 HEALTH ALERT: Everyone should avoid outdoor activities");
            whoAffected.add("⚠️ Sensitive groups at SERIOUS RISK");
            whoAffected.add("☎️ Call emergency services if experiencing severe symptoms");

        } else {
            // Hazardous - Maroon
            bgColor = "#f5c6cb";
            textColor = "#7e0023";
            borderColor = "#dc3545";
            headerText = "☠️ HAZARDOUS AIR QUALITY";

            measures.add("🚨 EMERGENCY LEVEL - STAY INDOORS AT ALL TIMES");
            measures.add("😷 WEAR N95/FFP3 MASKS even for brief outdoor exposure");
            measures.add("🏠 Create a CLEAN ROOM - seal one room with air purifier");
            measures.add("🚪 SEAL all windows and doors with tape if possible");
            measures.add("💨 Run multiple AIR PURIFIERS at maximum setting");
            measures.add("🧹 MINIMIZE indoor air disturbance");
            measures.add("🚗 DO NOT DRIVE unless absolutely necessary");
            measures.add("👶 PROTECT children and elderly - keep in clean room");
            measures.add("🏥 MONITOR health continuously");
            measures.add("💊 Keep ALL medications within reach");
            measures.add("📞 CALL DOCTOR if ANY symptoms appear");
            measures.add("🚑 Call emergency services (911/999) for breathing difficulties");
            measures.add("💧 Drink plenty of water to help lungs");
            measures.add("🍎 Eat foods rich in antioxidants");
            measures.add("📻 Monitor local news for evacuation orders");

            whoAffected.add("🚨🚨🚨 HEALTH EMERGENCY");
            whoAffected.add("⚠️ SERIOUS RISK FOR EVERYONE");
            whoAffected.add("☎️ MEDICAL EMERGENCY: Seek immediate help if breathing is difficult");
            whoAffected.add("🏥 Consider relocation to cleaner air area");
        }

        // Create animated header with gradient
        VBox headerBox = createAnimatedHeaderBox(headerText, aqi, bgColor, textColor, borderColor);
        measuresContent.getChildren().add(headerBox);

        // Add "Who's Affected" section with animation
        if (!whoAffected.isEmpty()) {
            VBox affectedBox = createAnimatedAffectedBox(whoAffected, textColor, borderColor, bgColor);
            measuresContent.getChildren().add(affectedBox);
        }

        // Add "Recommended Actions" section with staggered animations
        VBox measuresBox = createAnimatedMeasuresBox(measures, textColor);
        measuresContent.getChildren().add(measuresBox);

        // Add emergency contact for hazardous levels
        if (aqi > 200) {
            VBox emergencyBox = createAnimatedEmergencyBox();
            measuresContent.getChildren().add(emergencyBox);
        }

        // Update preventive measures box style with gradient
        String gradient = getGradientForAQI(aqi);
        preventiveMeasuresBox.setStyle(String.format(
            "-fx-background-color: %s; -fx-padding: 25; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0.5, 0, 8); -fx-border-color: %s; -fx-border-width: 3; -fx-border-radius: 15;",
            gradient, borderColor
        ));

        System.out.println("✓ Updated animated preventive measures for AQI: " + aqi);
    }

    /** Create animated header box */
    private VBox createAnimatedHeaderBox(String headerText, int aqi, String bgColor, String textColor, String borderColor) {
        VBox headerBox = new VBox(8);
        headerBox.setStyle(String.format(
            "-fx-background-color: linear-gradient(to right, %s, %s, %s); -fx-padding: 20; -fx-background-radius: 12; -fx-border-color: %s; -fx-border-width: 3; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0, 3);",
            bgColor, adjustBrightness(bgColor, 1.1), bgColor, borderColor
        ));

        Label headerLabel = new Label(headerText);
        headerLabel.setStyle(String.format(
            "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: %s;",
            textColor
        ));

        Label aqiValueLabel = new Label("Current AQI: " + aqi);
        aqiValueLabel.setStyle(String.format(
            "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: %s;",
            textColor
        ));

        headerBox.getChildren().addAll(headerLabel, aqiValueLabel);

        // Scale animation on appear
        headerBox.setScaleX(0.8);
        headerBox.setScaleY(0.8);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(400), headerBox);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        scaleIn.setInterpolator(Interpolator.EASE_OUT);
        scaleIn.play();

        // Add pulsing glow effect for hazardous levels
        if (aqi > 200) {
            addPulsingGlow(headerBox, borderColor);
        }

        return headerBox;
    }

    /** Create animated affected box */
    private VBox createAnimatedAffectedBox(List<String> whoAffected, String textColor, String borderColor, String bgColor) {
        VBox affectedBox = new VBox(10);
        affectedBox.setStyle(String.format(
            "-fx-background-color: linear-gradient(to bottom, white, %s); -fx-padding: 15; -fx-background-radius: 10; -fx-border-color: %s; -fx-border-width: 2; -fx-border-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0.3, 0, 2);",
            bgColor, borderColor
        ));
        affectedBox.setPadding(new Insets(15));

        Label affectedHeader = new Label("👥 Who's Affected:");
        affectedHeader.setStyle(String.format(
            "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: %s;",
            textColor
        ));
        affectedBox.getChildren().add(affectedHeader);

        // Add each affected group with staggered animation
        int delay = 0;
        for (String affected : whoAffected) {
            Label affectedLabel = new Label(affected);
            affectedLabel.setStyle(String.format(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: %s; -fx-padding: 5 0 5 15;",
                textColor
            ));
            affectedLabel.setWrapText(true);
            affectedBox.getChildren().add(affectedLabel);

            // Slide in animation
            addSlideInAnimation(affectedLabel, delay);
            delay += 100;
        }

        return affectedBox;
    }

    /** Create animated measures box */
    private VBox createAnimatedMeasuresBox(List<String> measures, String textColor) {
        VBox measuresBox = new VBox(8);
        measuresBox.setStyle("-fx-padding: 10 0 0 0;");

        Label measuresHeader = new Label("🛡️ Recommended Safety Measures:");
        measuresHeader.setStyle(String.format(
            "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: %s; -fx-padding: 10 0 10 0;",
            textColor
        ));
        measuresBox.getChildren().add(measuresHeader);

        // Add each measure with staggered fade-in and slide animation
        int delay = 0;
        for (String measure : measures) {
            Label measureLabel = new Label("  " + measure);
            measureLabel.setStyle(String.format(
                "-fx-font-size: 14px; -fx-text-fill: %s; -fx-padding: 6 10 6 10; -fx-background-color: rgba(255,255,255,0.7); -fx-background-radius: 8; -fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1; -fx-border-radius: 8;",
                textColor
            ));
            measureLabel.setWrapText(true);
            measureLabel.setMaxWidth(Double.MAX_VALUE);

            // Hover effect
            measureLabel.setOnMouseEntered(event -> {
                measureLabel.setStyle(measureLabel.getStyle() + "-fx-background-color: rgba(52, 152, 219, 0.2); -fx-scale-x: 1.02; -fx-scale-y: 1.02;");
                ScaleTransition hoverScale = new ScaleTransition(Duration.millis(150), measureLabel);
                hoverScale.setToX(1.02);
                hoverScale.setToY(1.02);
                hoverScale.play();
            });

            measureLabel.setOnMouseExited(event -> {
                measureLabel.setStyle(String.format(
                    "-fx-font-size: 14px; -fx-text-fill: %s; -fx-padding: 6 10 6 10; -fx-background-color: rgba(255,255,255,0.7); -fx-background-radius: 8; -fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1; -fx-border-radius: 8;",
                    textColor
                ));
                ScaleTransition hoverScaleBack = new ScaleTransition(Duration.millis(150), measureLabel);
                hoverScaleBack.setToX(1.0);
                hoverScaleBack.setToY(1.0);
                hoverScaleBack.play();
            });

            measuresBox.getChildren().add(measureLabel);

            // Slide and fade in animation
            addSlideAndFadeAnimation(measureLabel, delay);
            delay += 80;
        }

        return measuresBox;
    }

    /** Create animated emergency box */
    private VBox createAnimatedEmergencyBox() {
        VBox emergencyBox = new VBox(8);
        emergencyBox.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #ff6b6b, #ee5a6f); -fx-padding: 18; -fx-background-radius: 12; -fx-border-color: #cc0000; -fx-border-width: 3; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0.5, 0, 4);"
        );
        emergencyBox.setPadding(new Insets(18));

        Label emergencyLabel = new Label("🚑 EMERGENCY CONTACTS");
        emergencyLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label contactsLabel = new Label(
            "Emergency Services: 911 (US) / 999 (UK) / 112 (EU)\n" +
            "Poison Control: 1-800-222-1222 (US)\n" +
            "If experiencing difficulty breathing, chest pain, or severe symptoms, CALL IMMEDIATELY"
        );
        contactsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: white; -fx-padding: 8 0 0 0; -fx-font-weight: bold;");
        contactsLabel.setWrapText(true);

        emergencyBox.getChildren().addAll(emergencyLabel, contactsLabel);

        // Pulsing animation for emergency box
        addPulsingGlow(emergencyBox, "#ff0000");

        // Shake animation to draw attention
        addShakeAnimation(emergencyBox);

        return emergencyBox;
    }

    /** Add slide-in animation */
    private void addSlideInAnimation(Label label, int delayMs) {
        label.setTranslateX(-50);
        label.setOpacity(0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(400), label);
        slide.setFromX(-50);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        slide.setDelay(Duration.millis(delayMs));

        FadeTransition fade = new FadeTransition(Duration.millis(400), label);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMs));

        ParallelTransition parallel = new ParallelTransition(slide, fade);
        parallel.play();
    }

    /** Add slide and fade animation */
    private void addSlideAndFadeAnimation(Label label, int delayMs) {
        label.setTranslateX(-30);
        label.setOpacity(0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(350), label);
        slide.setFromX(-30);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        slide.setDelay(Duration.millis(delayMs));

        FadeTransition fade = new FadeTransition(Duration.millis(350), label);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMs));

        ScaleTransition scale = new ScaleTransition(Duration.millis(350), label);
        scale.setFromX(0.9);
        scale.setFromY(0.9);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setDelay(Duration.millis(delayMs));

        ParallelTransition parallel = new ParallelTransition(slide, fade, scale);
        parallel.play();
    }

    /** Add pulsing glow effect */
    private void addPulsingGlow(VBox box, String color) {
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(color));
        glow.setRadius(20);
        glow.setSpread(0.5);
        box.setEffect(glow);

        Timeline pulse = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 15)),
            new KeyFrame(Duration.seconds(1), new KeyValue(glow.radiusProperty(), 30)),
            new KeyFrame(Duration.seconds(2), new KeyValue(glow.radiusProperty(), 15))
        );
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();
    }

    /** Add shake animation */
    private void addShakeAnimation(VBox box) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(100), box);
        shake.setFromX(0);
        shake.setByX(5);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setDelay(Duration.millis(500));
        shake.play();
    }

    /** Animate border pulse */
    private void animateBorderPulse(int aqi) {
        String color = getAqiColor(aqi);

        Timeline borderPulse = new Timeline(
            new KeyFrame(Duration.ZERO,
                e -> preventiveMeasuresBox.setStyle(preventiveMeasuresBox.getStyle().replaceAll("-fx-border-width: \\d+", "-fx-border-width: 3"))
            ),
            new KeyFrame(Duration.millis(300),
                e -> preventiveMeasuresBox.setStyle(preventiveMeasuresBox.getStyle().replaceAll("-fx-border-width: \\d+", "-fx-border-width: 5"))
            ),
            new KeyFrame(Duration.millis(600),
                e -> preventiveMeasuresBox.setStyle(preventiveMeasuresBox.getStyle().replaceAll("-fx-border-width: \\d+", "-fx-border-width: 3"))
            )
        );
        borderPulse.play();
    }

    /** Get gradient for AQI level */
    private String getGradientForAQI(int aqi) {
        if (aqi <= 50) {
            return "linear-gradient(to bottom right, #d4edda, #c3e6cb, #b1dfbb)";
        } else if (aqi <= 100) {
            return "linear-gradient(to bottom right, #fff3cd, #ffeaa7, #fdcb6e)";
        } else if (aqi <= 150) {
            return "linear-gradient(to bottom right, #ffe5cc, #ffd7a8, #ffc078)";
        } else if (aqi <= 200) {
            return "linear-gradient(to bottom right, #f8d7da, #f5c6cb, #f1b0b7)";
        } else if (aqi <= 300) {
            return "linear-gradient(to bottom right, #e8d4f1, #d7bde2, #c39bd3)";
        } else {
            return "linear-gradient(to bottom right, #f5c6cb, #f1948a, #ec7063)";
        }
    }

    /** Adjust brightness of hex color */
    private String adjustBrightness(String hexColor, double factor) {
        try {
            Color color = Color.web(hexColor);
            return String.format("#%02x%02x%02x",
                (int) Math.min(255, color.getRed() * 255 * factor),
                (int) Math.min(255, color.getGreen() * 255 * factor),
                (int) Math.min(255, color.getBlue() * 255 * factor)
            );
        } catch (Exception e) {
            return hexColor;
        }
    }

    /** Get AQI color based on value */
    private String getAqiColor(int aqi) {
        if (aqi <= 50) return "#00E400";      // Good - Green
        if (aqi <= 100) return "#FFAA00";     // Moderate - Yellow
        if (aqi <= 150) return "#FF7E00";     // Unhealthy for Sensitive - Orange
        if (aqi <= 200) return "#FF0000";     // Unhealthy - Red
        if (aqi <= 300) return "#8F3F97";     // Very Unhealthy - Purple
        return "#7E0023";                      // Hazardous - Maroon
    }

    /** Return AQI alert text */
    private String getAqiAlert(int aqi) {
        if (aqi <= 50) return "Good 👍";
        if (aqi <= 100) return "Moderate 🙂";
        if (aqi <= 150) return "Unhealthy for Sensitive Groups ⚠️";
        if (aqi <= 200) return "Unhealthy 😷";
        if (aqi <= 300) return "Very Unhealthy 🤢";
        return "Hazardous ☠️";
    }

    /** Get trend arrow based on slope */
    private String getTrend(double slope) {
        if (slope > 2) return "↗️ Rising";
        if (slope < -2) return "↘️ Decreasing";
        return "→ Stable";
    }

    /** Search history item class */
    static class SearchHistoryItem implements Serializable {
        private static final long serialVersionUID = 1L;

        String query;
        double lat;
        double lon;
        String displayName;

        SearchHistoryItem(String query, double lat, double lon, String displayName) {
            this.query = query;
            this.lat = lat;
            this.lon = lon;
            this.displayName = displayName;
        }
    }
}
