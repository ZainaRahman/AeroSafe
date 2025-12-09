package com.example.aerotutorial;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardController {

    @FXML
    private WebView mapView;

    @FXML
    private Label currentAqiLabel, predictedAqiLabel, aqiAlertLabel;

    @FXML
    private LineChart<String, Number> historyChart;

    @FXML
    private Button logoutButton;

    // In-memory storage for last 7 days AQI per city
    private final Map<String, List<Integer>> cityAqiHistory = new HashMap<>();

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
            selectedCity = city;
            selectedLat = lat;
            selectedLon = lon;

            // Initialize last 7 days history if not exists
            if (!cityAqiHistory.containsKey(city)) {
                List<Integer> last7Days = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    int simulated = 50 + (int) (Math.random() * 150); // 50-200 AQI
                    last7Days.add(simulated);
                }
                cityAqiHistory.put(city, last7Days);
            }

            fetchAndDisplayAQI();
        });
    }

    /** Fetch current AQI and update dashboard */
    private void fetchAndDisplayAQI() {
        if (selectedCity == null) return;

        int currentAqi = AQIFetcher.fetchAQI(selectedLat, selectedLon);

        if (currentAqi <= 0) {
            currentAqiLabel.setText("Current AQI: N/A");
            predictedAqiLabel.setText("Predicted AQI: N/A");
            aqiAlertLabel.setText("");
            historyChart.getData().clear();
            return;
        }

        currentAqiLabel.setText("Current AQI: " + currentAqi);
        aqiAlertLabel.setText(getAqiAlert(currentAqi));

        // Update 7-day history
        List<Integer> history = cityAqiHistory.get(selectedCity);
        if (history.size() >= 7) history.remove(0); // remove oldest
        history.add(currentAqi);
        cityAqiHistory.put(selectedCity, history);

        // Update chart
        updateHistoryChart(history);

        // Predict next day AQI
        if (history.size() >= 2) {
            PredictionEngine.PredictionResult result = PredictionEngine.predictNextDay(history);
            predictedAqiLabel.setText("Predicted AQI: " + String.format("%.0f", result.predicted));
        } else {
            predictedAqiLabel.setText("Predicted AQI: N/A");
        }
    }

    /** Update chart with history */
    private void updateHistoryChart(List<Integer> history) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(selectedCity);

        for (int i = 0; i < history.size(); i++) {
            series.getData().add(new XYChart.Data<>("Day-" + (i + 1), history.get(i)));
        }

        historyChart.getData().clear();
        historyChart.getData().add(series);
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

    /** Logout and switch to login scene */
    @FXML
    private void logout() {
        try {
            Stage stage = (Stage) mapView.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 300));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
