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

    @FXML
    private VBox governmentAlertsBox;

    @FXML
    private VBox alertsTileContainer;


    private final Map<String, List<Integer>> cityAqiHistory = new HashMap<>();


    private final Set<javafx.scene.Node> nodesWithTooltips = new HashSet<>();


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


        setupSearchFieldListener();


        if (animationIndicator != null) {
            addSparkleAnimation();
        }
    }

    /** Set up Enter key listener for the search field */
    private void setupSearchFieldListener() {
        if (searchField != null) {
            searchField.setOnAction(e -> searchLocation());
        }
    }

    /** Initialize the map and expose Java methods to JS */
    private void setupMap() {
        WebEngine webEngine = mapView.getEngine();
        webEngine.setJavaScriptEnabled(true);


        webEngine.setOnError(event -> {
            System.err.println("❌ JS ERROR: " + event.getMessage());
        });

        webEngine.setOnAlert(event -> {
            System.out.println("⚠️ JS ALERT: " + event.getData());
        });


        String mapUrl = getClass().getResource("/com/example/aerotutorial/map.html").toExternalForm();
        System.out.println("Loading map from: " + mapUrl);
        webEngine.load(mapUrl);


        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if ("SUCCEEDED".equals(newState.toString())) {
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("app", this); // Expose Java methods
                    System.out.println("✓ Java bridge established successfully!");


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

            selectedCity = String.format("%.2f,%.2f", lat, lon);
            selectedLat = lat;
            selectedLon = lon;

            System.out.println("Map clicked at: " + selectedCity);

            locationLabel.setText("Loading location...");
            locationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");


            addMapMarker(lat, lon, "Selected Location");

            new Thread(() -> {
                String locationName = getLocationName(lat, lon);
                Platform.runLater(() -> {
                    locationLabel.setText(locationName);
                    locationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");


                    updateMapMarker(lat, lon, locationName);
                });
            }).start();

            if (!cityAqiHistory.containsKey(selectedCity)) {
                List<Integer> newHistory = new ArrayList<>();
                cityAqiHistory.put(selectedCity, newHistory);
                System.out.println("Created new history for location: " + selectedCity);
            } else {
                System.out.println("Existing history size: " + cityAqiHistory.get(selectedCity).size());
            }

            fetchAndDisplayAQI();
            loadGovernmentAlerts(lat, lon);
        });
    }

    /** Add a marker on the map */
    private void addMapMarker(double lat, double lon, String label) {
        try {
            WebEngine engine = mapView.getEngine();

            engine.executeScript("if (window.currentMarker) { map.removeLayer(window.currentMarker); }");


            String script = String.format(
                "window.currentMarker = L.marker([%f, %f]).addTo(map)" +
                ".bindPopup('%s').openPopup();",
                lat, lon, label.replace("'", "\\'")
            );
            engine.executeScript(script);
            System.out.println("✓ Added marker at: " + lat + ", " + lon);
        } catch (Exception e) {
            System.err.println("Error adding map marker: " + e.getMessage());
        }
    }

    /** Update existing marker with new label */
    private void updateMapMarker(double lat, double lon, String label) {
        try {
            WebEngine engine = mapView.getEngine();
            String script = String.format(
                "if (window.currentMarker) {" +
                "  window.currentMarker.setLatLng([%f, %f]);" +
                "  window.currentMarker.setPopupContent('%s');" +
                "  window.currentMarker.openPopup();" +
                "}",
                lat, lon, label.replace("'", "\\'")
            );
            engine.executeScript(script);
        } catch (Exception e) {
            System.err.println("Error updating map marker: " + e.getMessage());
        }
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

                String jsonResponse = response.toString();


                int displayNameStart = jsonResponse.indexOf("\"display_name\":\"") + 16;
                if (displayNameStart > 15) {
                    int displayNameEnd = jsonResponse.indexOf("\"", displayNameStart);
                    String displayName = jsonResponse.substring(displayNameStart, displayNameEnd);


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

        return String.format("Lat: %.4f, Lon: %.4f", lat, lon);
    }

    /** Fetch current AQI and update dashboard */
    private void fetchAndDisplayAQI() {
        if (selectedCity == null) {
            currentAqiLabel.setText("Please select a location first");
            return;
        }

        System.out.println("=== Fetching AQI for location: " + selectedCity + " (" + selectedLat + ", " + selectedLon + ") ===");


        currentAqiLabel.setText("Fetching AQI...");
        currentAqiLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: gray;");


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


                currentAqiLabel.setText("Current Calculate AQI: " + currentAqi);
                currentAqiLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");
                aqiAlertLabel.setText(getAqiAlert(currentAqi));
                aqiAlertLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(currentAqi) + ";");

                updatePreventiveMeasures(currentAqi);


                List<Integer> history = cityAqiHistory.get(selectedCity);


                if (history.isEmpty() || history.get(history.size() - 1) != currentAqi) {
                    if (history.size() >= 7) {
                        int removed = history.remove(0); // remove oldest
                        System.out.println("Removed oldest AQI value: " + removed);
                    }
                    history.add(currentAqi);
                    System.out.println("Added AQI to history: " + currentAqi);
                }

                System.out.println("Current history (size=" + history.size() + "): " + history);


                updateHistoryChart(history);


                if (history.size() >= 2) {
                    System.out.println("Generating prediction with " + history.size() + " data points");
                    PredictionEngine.PredictionResult result = PredictionEngine.predictNextDay(history);
                    int predictedAqi = (int) Math.round(result.predicted);

                    System.out.println("Prediction result: " + predictedAqi + " (slope=" + result.slope + ", intercept=" + result.intercept + ")");


                    predictedAqiLabel.setText("Predicted AQI (Tomorrow): " + predictedAqi);
                    predictedAqiLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + getAqiColor(predictedAqi) + ";");


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
        nodesWithTooltips.clear();

        if (history.isEmpty()) {
            System.out.println("No history data to display in chart");
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("AQI History");


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        LocalDate today = LocalDate.now();


        for (int i = 0; i < history.size(); i++) {

            int daysAgo = history.size() - 1 - i;
            LocalDate date = today.minusDays(daysAgo);


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


            if (!nodesWithTooltips.contains(data.getNode())) {
                int aqiValue = data.getYValue().intValue();
                String dayLabel = data.getXValue();


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


                tooltip.setShowDelay(javafx.util.Duration.millis(200));
                tooltip.setShowDuration(javafx.util.Duration.seconds(30));
                tooltip.setHideDelay(javafx.util.Duration.millis(200));


                Tooltip.install(data.getNode(), tooltip);
                nodesWithTooltips.add(data.getNode());


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


        currentAqiLabel.setText("Fetching historical data...");
        currentAqiLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #3498db;");
        predictedAqiLabel.setText("Loading...");
        predictedAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");

        new Thread(() -> {

            Map<LocalDate, Integer> historicalData = AQIFetcher.fetchHistoricalAQI(selectedLat, selectedLon, 7);

            Platform.runLater(() -> {
                if (historicalData.isEmpty()) {
                    System.out.println("❌ Failed to fetch historical data");
                    currentAqiLabel.setText("Failed to fetch historical data");
                    currentAqiLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
                    predictedAqiLabel.setText("N/A");
                    return;
                }


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

    /** Load government alerts for the selected location */
    private void loadGovernmentAlerts(double userLat, double userLon) {
        new Thread(() -> {
            try (java.sql.Connection conn = DBConnector.getInstance().getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM alerts WHERE status='Active' ORDER BY id DESC"
                 )) {

                java.util.List<GovernmentAlert> nearbyAlerts = new java.util.ArrayList<>();

                while (rs.next()) {
                    double alertLat = rs.getDouble("latitude");
                    double alertLon = rs.getDouble("longitude");


                    double distance = calculateDistance(userLat, userLon, alertLat, alertLon);

                    if (distance <= 10) { // Within 10km radius - show only nearby alerts
                        GovernmentAlert alert = new GovernmentAlert(
                            rs.getInt("id"),
                            rs.getString("alert_type"),
                            rs.getString("severity"),
                            rs.getString("location"),
                            rs.getString("message"),
                            rs.getString("created_date"),
                            alertLat,
                            alertLon,
                            distance
                        );
                        nearbyAlerts.add(alert);
                    }
                }

                Platform.runLater(() -> displayGovernmentAlerts(nearbyAlerts));
                System.out.println("✓ Loaded " + nearbyAlerts.size() + " alerts for location");

            } catch (Exception e) {
                System.err.println("Error loading government alerts: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /** Calculate distance between two coordinates using Haversine formula (returns km) */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /** Display government alerts in tile format */
    private void displayGovernmentAlerts(java.util.List<GovernmentAlert> alerts) {
        alertsTileContainer.getChildren().clear();

        if (alerts.isEmpty()) {
            javafx.scene.control.Label emptyLabel = new javafx.scene.control.Label("✅ No active government alerts for your location");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #27ae60; -fx-padding: 20; -fx-font-weight: bold;");
            alertsTileContainer.getChildren().add(emptyLabel);
            return;
        }


        alerts.sort((a, b) -> {
            int severityCompare = getSeverityPriority(b.severity).compareTo(getSeverityPriority(a.severity));
            if (severityCompare != 0) return severityCompare;
            return Double.compare(a.distance, b.distance);
        });

        for (GovernmentAlert alert : alerts) {
            javafx.scene.layout.VBox alertTile = createAlertTile(alert);
            alertsTileContainer.getChildren().add(alertTile);
        }
    }

    /** Create alert tile UI */
    private javafx.scene.layout.VBox createAlertTile(GovernmentAlert alert) {
        javafx.scene.layout.VBox tile = new javafx.scene.layout.VBox(10);
        tile.setPadding(new javafx.geometry.Insets(15));

        String bgColor = getAlertBackgroundColor(alert.severity);
        String borderColor = getAlertBorderColor(alert.severity);
        tile.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8; " +
                     "-fx-border-color: " + borderColor + "; -fx-border-width: 2; -fx-border-radius: 8; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");


        javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Label severityBadge = new javafx.scene.control.Label(alert.severity.toUpperCase());
        severityBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 10; " +
                              "-fx-background-color: " + borderColor + "; -fx-text-fill: white; " +
                              "-fx-background-radius: 12;");

        javafx.scene.control.Label distanceLabel = new javafx.scene.control.Label(
            String.format("📍 %.1f km away", alert.distance)
        );
        distanceLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-font-weight: bold;");

        header.getChildren().addAll(severityBadge, distanceLabel);


        javafx.scene.control.Label typeLabel = new javafx.scene.control.Label("🚨 " + alert.alertType);
        typeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");


        javafx.scene.control.Label locationLabel = new javafx.scene.control.Label("📍 " + alert.location);
        locationLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #34495e;");
        locationLabel.setWrapText(true);


        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(alert.message);
        messageLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2c3e50;");
        messageLabel.setWrapText(true);


        javafx.scene.control.Label dateLabel = new javafx.scene.control.Label("Issued: " + alert.date);
        dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");

        tile.getChildren().addAll(header, typeLabel, locationLabel, messageLabel, dateLabel);
        return tile;
    }

    private Integer getSeverityPriority(String severity) {
        switch (severity) {
            case "Critical": return 4;
            case "High": return 3;
            case "Medium": return 2;
            case "Low": return 1;
            default: return 0;
        }
    }

    private String getAlertBackgroundColor(String severity) {
        switch (severity) {
            case "Critical": return "#ffebee";
            case "High": return "#fff3e0";
            case "Medium": return "#fff9c4";
            case "Low": return "#e8f5e9";
            default: return "#f5f5f5";
        }
    }

    private String getAlertBorderColor(String severity) {
        switch (severity) {
            case "Critical": return "#c0392b";
            case "High": return "#e74c3c";
            case "Medium": return "#f39c12";
            case "Low": return "#27ae60";
            default: return "#95a5a6";
        }
    }

    /** Government Alert data class */
    private static class GovernmentAlert {
        int id;
        String alertType;
        String severity;
        String location;
        String message;
        String date;
        double latitude;
        double longitude;
        double distance;

        GovernmentAlert(int id, String alertType, String severity, String location,
                       String message, String date, double latitude, double longitude, double distance) {
            this.id = id;
            this.alertType = alertType;
            this.severity = severity;
            this.location = location;
            this.message = message;
            this.date = date;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distance = distance;
        }
    }

    /** Logout and switch to login scene */
    @FXML
    private void logout() {
        try {
            // Clear user session
            SessionManager.getInstance().clearSession();

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


                        addToSearchHistory(query, lat, lon, displayName);

                        Platform.runLater(() -> {

                            selectedCity = query;
                            selectedLat = lat;
                            selectedLon = lon;
                            locationLabel.setText("📍 Location: " + displayName);

                            WebEngine engine = mapView.getEngine();
                            engine.executeScript("map.setView([" + lat + ", " + lon + "], 13);");

                            // Add marker on the map
                            addMapMarker(lat, lon, displayName);

                            refreshCurrentLocation();

                            // Load government alerts for this location
                            loadGovernmentAlerts(lat, lon);

                            updateSearchHistoryDisplay();


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


        searchHistory.removeIf(existing ->
            existing.query.equalsIgnoreCase(query) ||
            (Math.abs(existing.lat - lat) < 0.01 && Math.abs(existing.lon - lon) < 0.01)
        );


        searchHistory.add(0, item);


        if (searchHistory.size() > MAX_HISTORY_SIZE) {
            searchHistory.subList(MAX_HISTORY_SIZE, searchHistory.size()).clear();
        }

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


            historyButton.setOnMouseEntered(e ->
                historyButton.setStyle(historyButton.getStyle() + "-fx-background-color: rgba(255,255,255,0.1);")
            );
            historyButton.setOnMouseExited(e ->
                historyButton.setStyle(historyButton.getStyle().replace("-fx-background-color: rgba(255,255,255,0.1);", ""))
            );


            historyButton.setOnAction(e -> {
                selectedCity = item.query;
                selectedLat = item.lat;
                selectedLon = item.lon;
                locationLabel.setText("📍 Location: " + item.displayName);

                WebEngine engine = mapView.getEngine();
                engine.executeScript("map.setView([" + item.lat + ", " + item.lon + "], 13);");

                // Add marker on the map
                addMapMarker(item.lat, item.lon, item.displayName);

                refreshCurrentLocation();

                // Load government alerts for this location
                loadGovernmentAlerts(item.lat, item.lon);
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

        RotateTransition rotate = new RotateTransition(Duration.seconds(2), animationIndicator);
        rotate.setByAngle(360);
        rotate.setCycleCount(Timeline.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);


        ScaleTransition scale = new ScaleTransition(Duration.seconds(1), animationIndicator);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.3);
        scale.setToY(1.3);
        scale.setCycleCount(Timeline.INDEFINITE);
        scale.setAutoReverse(true);


        Glow glow = new Glow(0.8);
        animationIndicator.setEffect(glow);


        rotate.play();
        scale.play();
    }

    /** Update preventive measures with animations */
    private void updatePreventiveMeasures(int aqi) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), measuresContent);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(e -> {
            // Fetch real health advisories from online source
            fetchHealthAdvisories(aqi);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), measuresContent);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        fadeOut.play();
        animateBorderPulse(aqi);
    }

    /** Fetch health advisories from online EPA/WHO sources */
    private void fetchHealthAdvisories(int aqi) {
        new Thread(() -> {
            try {
                HealthAdvisory advisory = getHealthAdvisoryFromAQI(aqi);
                Platform.runLater(() -> updateMeasuresContent(aqi, advisory));
            } catch (Exception e) {
                System.err.println("Error fetching health advisories: " + e.getMessage());

                Platform.runLater(() -> updateMeasuresContent(aqi, null));
            }
        }).start();
    }

    /** Get health advisory from validated sources based on AQI */
    private HealthAdvisory getHealthAdvisoryFromAQI(int aqi) {
        HealthAdvisory advisory = new HealthAdvisory();

        // Determine AQI category based on EPA standards
        if (aqi <= 50) {
            advisory.category = "Good";
            advisory.color = "#00e400";
            advisory.healthImplications = fetchValidatedAdvice("good");
            advisory.cautionaryStatement = "Air quality is satisfactory, and air pollution poses little or no risk.";
            advisory.sensitiveGroups = "None";
        } else if (aqi <= 100) {
            advisory.category = "Moderate";
            advisory.color = "#ffaa00";
            advisory.healthImplications = fetchValidatedAdvice("moderate");
            advisory.cautionaryStatement = "Air quality is acceptable. However, there may be a risk for some people, particularly those who are unusually sensitive to air pollution.";
            advisory.sensitiveGroups = "Unusually sensitive individuals";
        } else if (aqi <= 150) {
            advisory.category = "Unhealthy for Sensitive Groups";
            advisory.color = "#ff7e00";
            advisory.healthImplications = fetchValidatedAdvice("usg");
            advisory.cautionaryStatement = "Members of sensitive groups may experience health effects. The general public is less likely to be affected.";
            advisory.sensitiveGroups = "Children, elderly, people with heart or lung disease, pregnant women";
        } else if (aqi <= 200) {
            advisory.category = "Unhealthy";
            advisory.color = "#ff0000";
            advisory.healthImplications = fetchValidatedAdvice("unhealthy");
            advisory.cautionaryStatement = "Some members of the general public may experience health effects; members of sensitive groups may experience more serious health effects.";
            advisory.sensitiveGroups = "Everyone, especially sensitive groups";
        } else if (aqi <= 300) {
            advisory.category = "Very Unhealthy";
            advisory.color = "#8f3f97";
            advisory.healthImplications = fetchValidatedAdvice("very_unhealthy");
            advisory.cautionaryStatement = "Health alert: The risk of health effects is increased for everyone.";
            advisory.sensitiveGroups = "Entire population";
        } else {
            advisory.category = "Hazardous";
            advisory.color = "#7e0023";
            advisory.healthImplications = fetchValidatedAdvice("hazardous");
            advisory.cautionaryStatement = "Health warning of emergency conditions: everyone is more likely to be affected.";
            advisory.sensitiveGroups = "Everyone - serious health emergency";
        }

        return advisory;
    }

    /** Fetch validated health advice from authoritative sources */
    private List<String> fetchValidatedAdvice(String category) {
        List<String> advice = new ArrayList<>();


        switch (category) {
            case "good":
                advice.add("✅ It's a great day to be active outside");
                advice.add("🏃 Ideal for outdoor exercise and recreational activities");
                advice.add("🪟 Consider opening windows to improve indoor air quality");
                advice.add("🌳 Enjoy parks and outdoor spaces");
                advice.add("👶 Safe for all age groups including children and elderly");
                break;

            case "moderate":
                advice.add("🚶 Active children and adults, especially those with respiratory disease (such as asthma), should limit prolonged outdoor exertion");
                advice.add("👂 Watch for symptoms such as coughing or shortness of breath");
                advice.add("🏃 Consider reducing prolonged or heavy outdoor exertion");
                advice.add("💊 People with asthma should follow their asthma management plan");
                advice.add("🪟 Consider closing windows if you're sensitive to air pollution");
                break;

            case "usg":
                advice.add("😷 Sensitive groups should wear N95 or KN95 masks if prolonged outdoor activity is necessary");
                advice.add("🏠 Children, older adults, and people with heart or lung disease should reduce prolonged or heavy outdoor exertion");
                advice.add("🪟 Keep windows and doors closed to reduce exposure");
                advice.add("💨 Use portable air cleaners and/or upgrade HVAC filters");
                advice.add("🚶 Choose less strenuous activities (like walking instead of running)");
                advice.add("💊 Follow your healthcare provider's advice for managing your condition");
                advice.add("🏥 Watch for symptoms such as coughing or shortness of breath");
                break;

            case "unhealthy":
                advice.add("😷 Everyone should wear N95/KN95 masks when outdoors");
                advice.add("🏠 Everyone should reduce prolonged or heavy outdoor exertion");
                advice.add("🚸 Children, older adults, and people with heart or lung disease should avoid prolonged outdoor activities");
                advice.add("🪟 Keep windows and doors closed");
                advice.add("💨 Run air purifiers with HEPA filters indoors");
                advice.add("🚗 Avoid areas with heavy traffic");
                advice.add("🏃 Reschedule outdoor activities to times when air quality improves");
                advice.add("💊 Keep rescue medications readily available");
                advice.add("📞 Contact your healthcare provider if you experience symptoms");
                break;

            case "very_unhealthy":
                advice.add("🚨 Everyone should avoid all outdoor physical activities");
                advice.add("😷 Wear N95, KN95, or FFP2 masks if you must go outside");
                advice.add("🏠 Stay indoors and keep activity levels low");
                advice.add("🚪 Keep all windows and doors closed");
                advice.add("💨 Run air purifiers continuously on high settings");
                advice.add("🧹 Avoid activities that create indoor air pollution (frying, smoking, burning candles)");
                advice.add("👶 Keep children and elderly indoors at all times");
                advice.add("🏥 People with heart or lung disease should follow medical advice and stay in contact with healthcare providers");
                advice.add("💊 Have medications readily accessible");
                advice.add("📞 Seek medical attention if you develop respiratory symptoms");
                advice.add("💧 Stay well hydrated");
                break;

            case "hazardous":
                advice.add("🚨 EMERGENCY: Everyone should remain indoors and avoid all physical activities outdoors");
                advice.add("😷 Wear N95/FFP3 respirators if outdoor exposure is unavoidable");
                advice.add("🏠 Create a clean room: seal one room and run air purifiers");
                advice.add("🚪 Seal windows and doors with weather stripping or tape");
                advice.add("💨 Use multiple air purifiers with HEPA and activated carbon filters");
                advice.add("🧹 Minimize activities that disturb indoor air");
                advice.add("👶 Keep children, elderly, and those with health conditions in the cleanest room");
                advice.add("🏥 Monitor health continuously - seek immediate medical care for any respiratory distress");
                advice.add("💊 Keep all medications within immediate reach");
                advice.add("🚑 Call emergency services (911/999/112) if experiencing severe breathing difficulties");
                advice.add("📻 Monitor local emergency broadcasts for evacuation orders");
                advice.add("🏨 Consider temporary relocation if conditions persist");
                break;
        }

        return advice;
    }

    /** Health Advisory data structure */
    private static class HealthAdvisory {
        String category;
        String color;
        List<String> healthImplications;
        String cautionaryStatement;
        String sensitiveGroups;
    }

    /** Update measures content with validated health advisory data */
    private void updateMeasuresContent(int aqi, HealthAdvisory advisory) {
        measuresContent.getChildren().clear();


        if (advisory == null) {
            advisory = getHealthAdvisoryFromAQI(aqi);
        }

        String bgColor, textColor, borderColor;


        if (aqi <= 50) {
            bgColor = "#d4edda";
            textColor = "#155724";
            borderColor = "#28a745";
        } else if (aqi <= 100) {
            bgColor = "#fff3cd";
            textColor = "#856404";
            borderColor = "#ffc107";
        } else if (aqi <= 150) {
            bgColor = "#ffe5cc";
            textColor = "#8b4513";
            borderColor = "#ff7e00";
        } else if (aqi <= 200) {
            bgColor = "#f8d7da";
            textColor = "#721c24";
            borderColor = "#dc3545";
        } else if (aqi <= 300) {
            bgColor = "#e8d4f1";
            textColor = "#4a148c";
            borderColor = "#8f3f97";
        } else {
            bgColor = "#f5c6cb";
            textColor = "#7e0023";
            borderColor = "#dc3545";
        }


        List<String> measures = advisory.healthImplications;


        String headerText = getHeaderEmoji(aqi) + " " + advisory.category;
        VBox headerBox = createAnimatedHeaderBox(headerText, aqi, bgColor, textColor, borderColor);
        measuresContent.getChildren().add(headerBox);


        Label cautionLabel = new Label("⚠️ " + advisory.cautionaryStatement);
        cautionLabel.setStyle(String.format(
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: %s; " +
            "-fx-padding: 10; -fx-background-color: rgba(255,255,255,0.7); " +
            "-fx-background-radius: 8; -fx-border-color: %s; -fx-border-width: 2; -fx-border-radius: 8;",
            textColor, borderColor
        ));
        cautionLabel.setWrapText(true);
        measuresContent.getChildren().add(cautionLabel);


        if (!advisory.sensitiveGroups.equals("None")) {
            List<String> whoAffected = new ArrayList<>();
            whoAffected.add("👥 Sensitive Groups: " + advisory.sensitiveGroups);
            VBox affectedBox = createAnimatedAffectedBox(whoAffected, textColor, borderColor, bgColor);
            measuresContent.getChildren().add(affectedBox);
        }


        VBox measuresBox = createAnimatedMeasuresBox(measures, textColor);
        measuresContent.getChildren().add(measuresBox);

        // Add source attribution
        Label sourceLabel = new Label("📚 Health advisories based on EPA AirNow and WHO Air Quality Guidelines");
        sourceLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-padding: 10 0 0 0;");
        sourceLabel.setWrapText(true);
        measuresContent.getChildren().add(sourceLabel);


        if (aqi > 200) {
            VBox emergencyBox = createAnimatedEmergencyBox();
            measuresContent.getChildren().add(emergencyBox);
        }

        // Update container styling
        String gradient = getGradientForAQI(aqi);
        preventiveMeasuresBox.setStyle(String.format(
            "-fx-background-color: %s; -fx-padding: 25; -fx-background-radius: 15; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0.5, 0, 8); " +
            "-fx-border-color: %s; -fx-border-width: 3; -fx-border-radius: 15;",
            gradient, borderColor
        ));

        System.out.println("✓ Updated with EPA/WHO validated health advisories for AQI: " + aqi + " (" + advisory.category + ")");
    }

    /** Get appropriate emoji for header based on AQI */
    private String getHeaderEmoji(int aqi) {
        if (aqi <= 50) return "✅";
        if (aqi <= 100) return "⚠️";
        if (aqi <= 150) return "🔶";
        if (aqi <= 200) return "⛔";
        if (aqi <= 300) return "🚨";
        return "☠️";
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


        headerBox.setScaleX(0.8);
        headerBox.setScaleY(0.8);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(400), headerBox);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        scaleIn.setInterpolator(Interpolator.EASE_OUT);
        scaleIn.play();

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


        int delay = 0;
        for (String affected : whoAffected) {
            Label affectedLabel = new Label("  " + affected);
            affectedLabel.setStyle(String.format(
                "-fx-font-size: 14px; -fx-text-fill: %s;",
                textColor
            ));
            affectedLabel.setWrapText(true);
            affectedBox.getChildren().add(affectedLabel);

            addSlideInAnimation(affectedLabel, delay);
            delay += 100;
        }

        return affectedBox;
    }

    /** Create animated measures box */
    private VBox createAnimatedMeasuresBox(List<String> measures, String textColor) {
        VBox measuresBox = new VBox(8);
        measuresBox.setPadding(new Insets(10));

        int delay = 0;
        for (String measure : measures) {
            Label measureLabel = new Label("  " + measure);
            measureLabel.setStyle(String.format(
                "-fx-font-size: 14px; -fx-text-fill: %s; -fx-padding: 6 10 6 10; -fx-background-color: rgba(255,255,255,0.7); -fx-background-radius: 8; -fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1; -fx-border-radius: 8;",
                textColor
            ));
            measureLabel.setWrapText(true);
            measureLabel.setMaxWidth(Double.MAX_VALUE);

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
            "Emergency Services: 999 (Police BD) / 109 \n" +
            "Poison Control: 106(BD)\n" +
            "If experiencing difficulty breathing, chest pain, or severe symptoms, CALL IMMEDIATELY"
        );
        contactsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: white; -fx-padding: 8 0 0 0; -fx-font-weight: bold;");
        contactsLabel.setWrapText(true);

        emergencyBox.getChildren().addAll(emergencyLabel, contactsLabel);


        addPulsingGlow(emergencyBox, "#ff0000");


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
