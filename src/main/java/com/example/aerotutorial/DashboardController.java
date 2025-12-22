package com.example.aerotutorial;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DashboardController {

    @FXML
    private WebView mapView;

    @FXML
    private Label currentAqiLabel, predictedAqiLabel, aqiAlertLabel, locationLabel;

    @FXML
    private LineChart<String, Number> historyChart;

    // In-memory storage for last 7 days AQI per city
    private final Map<String, List<Integer>> cityAqiHistory = new HashMap<>();

    // Track nodes with tooltips installed
    private final Set<javafx.scene.Node> nodesWithTooltips = new HashSet<>();

    private String selectedCity = null;
    private double selectedLat, selectedLon;

    @FXML
    public void initialize() {
        setupMap();
    }

    /** Initialize the map and expose Java methods to JS */
    private void setupMap() {
        WebEngine webEngine = mapView.getEngine();
        mapView.getEngine().setJavaScriptEnabled(true);
        mapView.getEngine().setOnError(event -> {
            System.out.println("JS ERROR: " + event.getMessage());
        });
        mapView.getEngine().setOnAlert(event -> {
            System.out.println("JS ALERT: " + event.getData());
        });

        webEngine.load(getClass().getResource("/com/example/aerotutorial/map.html").toExternalForm());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if ("SUCCEEDED".equals(newState.toString())) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("app", this); // Expose Java methods
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
                currentAqiLabel.setText("Current AQI: " + currentAqi);
                currentAqiLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");
                aqiAlertLabel.setText(getAqiAlert(currentAqi));
                aqiAlertLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");

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
}
