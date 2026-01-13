package com.example.aerotutorial;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class ResearcherDashboardController implements Initializable {


    @FXML private Label welcomeLabel, selectedLocationLabel, statsLabel;
    @FXML private WebView mapView;
    @FXML private TextField locationSearchField, researcherSearchField, publicationSearchField;
    @FXML private StackPane contentPane;


    @FXML private Label pm25Label, pm10Label, no2Label, o3Label, so2Label, coLabel;


    @FXML private ScrollPane dataViewPanel, dataHubPanel, researchersPanel, publicationsPanel;
    @FXML private VBox dataHubTilesContainer, emptyStateBox, statisticsSection;
    @FXML private Label totalRecordsLabel;
    @FXML private PieChart pollutantPieChart;


    @FXML private VBox researchersListBox, publicationsListBox;


    private ObservableList<AirQualityData> dataHubList = FXCollections.observableArrayList();
    private double selectedLat = 23.8103;
    private double selectedLon = 90.4125;
    private String selectedLocation = "Dhaka, Bangladesh";
    private String API_KEY;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        loadApiKey();


        setupMap();


        setupDataHubDisplay();


        loadSampleResearchers();


        loadSamplePublications();


        setupSearchFieldListeners();

        showDataView();
    }

    private void setupSearchFieldListeners() {

        researcherSearchField.setOnAction(e -> searchResearcher());


        publicationSearchField.setOnAction(e -> searchPublications());


        locationSearchField.setOnAction(e -> searchLocation());
    }

    private void loadApiKey() {
        try {
            ConfigLoader.loadConfig();
            API_KEY = ConfigLoader.getProperty("openweather.api.key");
            if (API_KEY != null && !API_KEY.isEmpty()) {
                System.out.println("✓ API Key loaded for researcher dashboard: " + API_KEY.substring(0, 8) + "...");
            } else {
                System.err.println("⚠️ API Key is empty!");
                API_KEY = "";
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load API key: " + e.getMessage());
            e.printStackTrace();
            API_KEY = "";
        }
    }


    private void setupMap() {
        WebEngine engine = mapView.getEngine();
        engine.setJavaScriptEnabled(true);

        engine.setOnError(event -> System.err.println("❌ JS ERROR: " + event.getMessage()));
        engine.setOnAlert(event -> System.out.println("⚠️ JS ALERT: " + event.getData()));

        String mapUrl = getClass().getResource("/com/example/aerotutorial/map.html").toExternalForm();
        engine.load(mapUrl);

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if ("SUCCEEDED".equals(newState.toString())) {
                try {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("app", this);
                    System.out.println("✓ Researcher map bridge established");
                } catch (Exception e) {
                    System.err.println("❌ Failed to establish map bridge: " + e.getMessage());
                }
            }
        });
    }


    public void onMapClick(String city, double lat, double lon) {
        Platform.runLater(() -> {
            selectedLat = lat;
            selectedLon = lon;
            selectedLocation = String.format("%.4f, %.4f", lat, lon);

            System.out.println("📍 Researcher selected location: " + lat + ", " + lon);


            new Thread(() -> {
                String locationName = getLocationName(lat, lon);
                Platform.runLater(() -> {
                    selectedLocation = locationName;
                    selectedLocationLabel.setText("📍 Selected: " + locationName);
                    fetchDetailedPollutantData();
                });
            }).start();
        });
    }


    private String getLocationName(double lat, double lon) {
        try {
            String urlString = String.format(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=10",
                lat, lon
            );
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AeroSafe-Researcher/1.0");
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                if (json.has("display_name")) {
                    String displayName = json.getString("display_name");
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


    @FXML
    private void fetchDetailedPollutantData() {
        System.out.println("🔍 Fetching pollutant data...");
        System.out.println("  API_KEY: " + (API_KEY != null ? (API_KEY.isEmpty() ? "EMPTY" : API_KEY.substring(0, 8) + "...") : "NULL"));

        if (API_KEY == null || API_KEY.isEmpty()) {
            System.err.println("❌ API Key is missing!");
            showAlert("API Key Missing", "Please configure OpenWeatherMap API key in config.properties");
            return;
        }

        selectedLocationLabel.setText("📍 Fetching data for: " + selectedLocation + "...");


        pm25Label.setText("Loading...");
        pm10Label.setText("Loading...");
        no2Label.setText("Loading...");
        o3Label.setText("Loading...");
        so2Label.setText("Loading...");
        coLabel.setText("Loading...");

        new Thread(() -> {
            try {
                String urlStr = String.format(
                    "http://api.openweathermap.org/data/2.5/air_pollution?lat=%.6f&lon=%.6f&appid=%s",
                    selectedLat, selectedLon, API_KEY
                );

                System.out.println("🌐 API URL: " + urlStr.substring(0, urlStr.indexOf("&appid=")) + "&appid=***");

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    JSONArray list = json.getJSONArray("list");

                    if (list.length() > 0) {
                        JSONObject data = list.getJSONObject(0);
                        JSONObject components = data.getJSONObject("components");

                        double pm25 = components.optDouble("pm2_5", 0);
                        double pm10 = components.optDouble("pm10", 0);
                        double no2 = components.optDouble("no2", 0);
                        double o3 = components.optDouble("o3", 0);
                        double so2 = components.optDouble("so2", 0);
                        double co = components.optDouble("co", 0);

                        Platform.runLater(() -> {
                            pm25Label.setText(String.format("%.2f", pm25));
                            pm10Label.setText(String.format("%.2f", pm10));
                            no2Label.setText(String.format("%.2f", no2));
                            o3Label.setText(String.format("%.2f", o3));
                            so2Label.setText(String.format("%.2f", so2));
                            coLabel.setText(String.format("%.2f", co));

                            selectedLocationLabel.setText("📍 " + selectedLocation + " - Data Updated");
                            System.out.println("✓ Pollutant data fetched successfully");
                        });
                    }
                } else {
                    System.err.println("❌ API Error: " + conn.getResponseCode());
                    Platform.runLater(() -> {
                        pm25Label.setText("N/A");
                        pm10Label.setText("N/A");
                        no2Label.setText("N/A");
                        o3Label.setText("N/A");
                        so2Label.setText("N/A");
                        coLabel.setText("N/A");
                        showAlert("API Error", "Failed to fetch data. Check API key and connection.");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    pm25Label.setText("Error");
                    pm10Label.setText("Error");
                    no2Label.setText("Error");
                    o3Label.setText("Error");
                    so2Label.setText("Error");
                    coLabel.setText("Error");
                });
            }
        }).start();
    }


    @FXML
    private void searchLocation() {
        String query = locationSearchField.getText().trim();
        if (query.isEmpty()) {
            showAlert("Empty Search", "Please enter a location to search");
            return;
        }

        new Thread(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "AeroSafe-Researcher/1.0");

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

                        Platform.runLater(() -> {
                            selectedLat = lat;
                            selectedLon = lon;
                            selectedLocation = displayName;

                            WebEngine engine = mapView.getEngine();
                            engine.executeScript("map.setView([" + lat + ", " + lon + "], 13);");

                            selectedLocationLabel.setText("📍 " + displayName);
                            fetchDetailedPollutantData();
                            locationSearchField.clear();
                        });
                    } else {
                        Platform.runLater(() -> showAlert("Not Found", "Location not found. Try a different search."));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Error", "Failed to search location"));
            }
        }).start();
    }


    @FXML
    private void refreshData() {
        fetchDetailedPollutantData();
    }


    @FXML
    private void addToDataHub() {
        try {
            double pm25 = Double.parseDouble(pm25Label.getText().replace(",", ""));
            double pm10 = Double.parseDouble(pm10Label.getText().replace(",", ""));
            double no2 = Double.parseDouble(no2Label.getText().replace(",", ""));
            double o3 = Double.parseDouble(o3Label.getText().replace(",", ""));
            double so2 = Double.parseDouble(so2Label.getText().replace(",", ""));
            double co = Double.parseDouble(coLabel.getText().replace(",", ""));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            AirQualityData data = new AirQualityData(
                timestamp, selectedLocation, pm25, pm10, no2, o3, so2, co
            );

            dataHubList.add(data);


            saveDataToDatabase(data);


            updateDataHubUI();

            showAlert("Success", "Data added to Data Hub successfully!");
            System.out.println("✓ Data added to hub: " + selectedLocation);

        } catch (NumberFormatException e) {
            showAlert("Invalid Data", "Please fetch valid pollutant data first");
        }
    }


    private void saveDataToDatabase(AirQualityData data) {
        try (var conn = DBConnector.getInstance().getConnection();
             var createStmt = conn.createStatement()) {


            String createTable = "CREATE TABLE IF NOT EXISTS research_data(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp TEXT, " +
                    "location TEXT, " +
                    "pm25 REAL, " +
                    "pm10 REAL, " +
                    "no2 REAL, " +
                    "o3 REAL, " +
                    "so2 REAL, " +
                    "co REAL)";
            createStmt.execute(createTable);


            try (var stmt = conn.prepareStatement(
                    "INSERT INTO research_data(timestamp, location, pm25, pm10, no2, o3, so2, co) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setString(1, data.getTimestamp());
                stmt.setString(2, data.getLocation());
                stmt.setDouble(3, data.getPm25());
                stmt.setDouble(4, data.getPm10());
                stmt.setDouble(5, data.getNo2());
                stmt.setDouble(6, data.getO3());
                stmt.setDouble(7, data.getSo2());
                stmt.setDouble(8, data.getCo());
                stmt.executeUpdate();
            }

            System.out.println("✓ Data saved to database");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save to database: " + e.getMessage());
        }
    }

    private void setupDataHubDisplay() {
        loadDataFromDatabase();
        updateDataHubUI();
    }


    private void updateDataHubUI() {
        dataHubTilesContainer.getChildren().clear();

        if (dataHubList.isEmpty()) {
            emptyStateBox.setVisible(true);
            emptyStateBox.setManaged(true);
            statisticsSection.setVisible(false);
            statisticsSection.setManaged(false);
            totalRecordsLabel.setText("Total Records: 0");
        } else {
            emptyStateBox.setVisible(false);
            emptyStateBox.setManaged(false);
            totalRecordsLabel.setText("Total Records: " + dataHubList.size());


            int columns = 3;
            int row = 0;
            HBox currentRow = null;

            for (int i = 0; i < dataHubList.size(); i++) {
                if (i % columns == 0) {
                    currentRow = new HBox(15);
                    currentRow.setAlignment(Pos.TOP_LEFT);
                    dataHubTilesContainer.getChildren().add(currentRow);
                }

                AirQualityData data = dataHubList.get(i);
                VBox tile = createDataTile(data, i);
                HBox.setHgrow(tile, Priority.ALWAYS);
                currentRow.getChildren().add(tile);
            }
        }
    }


    private VBox createDataTile(AirQualityData data, int index) {
        VBox tile = new VBox(10);
        tile.setPadding(new Insets(15));
        tile.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: #3498db; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);"
        );
        tile.setMaxWidth(Double.MAX_VALUE);


        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label indexBadge = new Label("#" + (index + 1));
        indexBadge.setStyle(
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 4 10; " +
            "-fx-background-color: #3498db; " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 12;"
        );

        Label dateLabel = new Label("📅 " + data.getTimestamp());
        dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        header.getChildren().addAll(indexBadge, dateLabel);


        Label locationLabel = new Label("📍 " + data.getLocation());
        locationLabel.setStyle(
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #2c3e50;"
        );
        locationLabel.setWrapText(true);

        Separator separator = new Separator();


        GridPane dataGrid = new GridPane();
        dataGrid.setHgap(10);
        dataGrid.setVgap(8);

        addPollutantRow(dataGrid, 0, "PM2.5", data.getPm25(), "#e74c3c");
        addPollutantRow(dataGrid, 1, "PM10", data.getPm10(), "#e67e22");
        addPollutantRow(dataGrid, 2, "NO₂", data.getNo2(), "#f39c12");
        addPollutantRow(dataGrid, 3, "O₃", data.getO3(), "#3498db");
        addPollutantRow(dataGrid, 4, "SO₂", data.getSo2(), "#9b59b6");
        addPollutantRow(dataGrid, 5, "CO", data.getCo(), "#1abc9c");


        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER);


        Button statsBtn = new Button("📊 Stats");
        statsBtn.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-padding: 6 12; " +
            "-fx-background-color: #9b59b6; " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 5; " +
            "-fx-cursor: hand;"
        );
        statsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statsBtn, Priority.ALWAYS);
        statsBtn.setOnAction(e -> calculateSingleRecordStatistics(data));


        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-padding: 6 12; " +
            "-fx-background-color: #e74c3c; " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 5; " +
            "-fx-cursor: hand;"
        );
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(deleteBtn, Priority.ALWAYS);
        deleteBtn.setOnAction(e -> deleteDataRecord(data));

        buttonBox.getChildren().addAll(statsBtn, deleteBtn);


        tile.setOnMouseEntered(e -> {
            tile.setStyle(
                "-fx-background-color: #ecf0f1; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: #2980b9; " +
                "-fx-border-width: 3; " +
                "-fx-border-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 15, 0, 0, 5);"
            );
        });

        tile.setOnMouseExited(e -> {
            tile.setStyle(
                "-fx-background-color: white; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: #3498db; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);"
            );
        });

        tile.getChildren().addAll(header, locationLabel, separator, dataGrid, buttonBox);
        return tile;
    }


    private void addPollutantRow(GridPane grid, int row, String name, double value, String color) {
        Label nameLabel = new Label(name);
        nameLabel.setStyle(
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + color + ";"
        );

        Label valueLabel = new Label(String.format("%.2f µg/m³", value));
        valueLabel.setStyle(
            "-fx-font-size: 12px; " +
            "-fx-text-fill: #34495e;"
        );

        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }


    private void deleteDataRecord(AirQualityData data) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Record");
        confirm.setHeaderText("Delete this data record?");
        confirm.setContentText("This action cannot be undone.");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            dataHubList.remove(data);
            deleteFromDatabase(data);
            updateDataHubUI();
            System.out.println("✓ Record deleted");
        }
    }


    private void deleteFromDatabase(AirQualityData data) {
        try (var conn = DBConnector.getInstance().getConnection();
             var stmt = conn.prepareStatement(
                 "DELETE FROM research_data WHERE timestamp=? AND location=?"
             )) {
            stmt.setString(1, data.getTimestamp());
            stmt.setString(2, data.getLocation());
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("⚠️ Failed to delete from database: " + e.getMessage());
        }
    }


    private void loadDataFromDatabase() {
        new Thread(() -> {
            try (var conn = DBConnector.getInstance().getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT * FROM research_data ORDER BY id DESC LIMIT 100")) {

                while (rs.next()) {
                    AirQualityData data = new AirQualityData(
                            rs.getString("timestamp"),
                            rs.getString("location"),
                            rs.getDouble("pm25"),
                            rs.getDouble("pm10"),
                            rs.getDouble("no2"),
                            rs.getDouble("o3"),
                            rs.getDouble("so2"),
                            rs.getDouble("co")
                    );
                    Platform.runLater(() -> dataHubList.add(data));
                }
                System.out.println("✓ Loaded " + dataHubList.size() + " records from database");
            } catch (Exception e) {
                System.out.println("No existing data to load: " + e.getMessage());
            }
        }).start();
    }



    private void calculateSingleRecordStatistics(AirQualityData data) {
        double pm25 = data.getPm25();
        double pm10 = data.getPm10();
        double no2 = data.getNo2();
        double o3 = data.getO3();
        double so2 = data.getSo2();
        double co = data.getCo();


        double total = pm25 + pm10 + no2 + o3 + so2 + co;

        String stats = String.format(
                "📊 Individual Record Analysis\n\n" +
                "📍 Location: %s\n" +
                "📅 Timestamp: %s\n\n" +
                "Pollutant Concentrations:\n\n" +
                "🔴 PM2.5:  %.2f µg/m³  (%.1f%%)\n" +
                "🟠 PM10:   %.2f µg/m³  (%.1f%%)\n" +
                "🟡 NO₂:    %.2f µg/m³  (%.1f%%)\n" +
                "🔵 O₃:     %.2f µg/m³  (%.1f%%)\n" +
                "🟣 SO₂:    %.2f µg/m³  (%.1f%%)\n" +
                "🟢 CO:     %.2f µg/m³  (%.1f%%)\n\n" +
                "Total Concentration: %.2f µg/m³\n\n" +
                "💡 The pie chart shows the distribution\nof pollutants for this specific record.",
                data.getLocation(), data.getTimestamp(),
                pm25, (pm25/total)*100,
                pm10, (pm10/total)*100,
                no2, (no2/total)*100,
                o3, (o3/total)*100,
                so2, (so2/total)*100,
                co, (co/total)*100,
                total
        );

        statsLabel.setText(stats);


        statisticsSection.setVisible(true);
        statisticsSection.setManaged(true);


        updatePieChart(pm25, pm10, no2, o3, so2, co);

        System.out.println("✓ Statistics calculated for record: " + data.getLocation() + " at " + data.getTimestamp());
    }




    private void updatePieChart(double pm25, double pm10, double no2, double o3, double so2, double co) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
            new PieChart.Data("PM2.5", pm25),
            new PieChart.Data("PM10", pm10),
            new PieChart.Data("NO₂", no2),
            new PieChart.Data("O₃", o3),
            new PieChart.Data("SO₂", so2),
            new PieChart.Data("CO", co)
        );

        pollutantPieChart.setData(pieChartData);
        pollutantPieChart.setTitle("Average Pollutant Distribution");


        double total = pm25 + pm10 + no2 + o3 + so2 + co;

        for (int i = 0; i < pieChartData.size(); i++) {
            PieChart.Data data = pieChartData.get(i);
            double percentage = (data.getPieValue() / total) * 100;

            data.setName(String.format("%s (%.1f%%)", data.getName(), percentage));
        }


        applyPieChartColors();
    }


    private void applyPieChartColors() {
        Platform.runLater(() -> {
            String[] colors = {"#e74c3c", "#e67e22", "#f39c12", "#3498db", "#9b59b6", "#1abc9c"};

            ObservableList<PieChart.Data> data = pollutantPieChart.getData();
            for (int i = 0; i < data.size() && i < colors.length; i++) {
                final int index = i;
                final PieChart.Data slice = data.get(i);


                if (slice.getNode() != null) {
                    slice.getNode().setStyle("-fx-pie-color: " + colors[index] + ";");
                } else {

                    Platform.runLater(() -> {
                        if (slice.getNode() != null) {
                            slice.getNode().setStyle("-fx-pie-color: " + colors[index] + ";");
                        }
                    });
                }
            }
        });
    }


    @FXML
    private void refreshDataHub() {
        updateDataHubUI();
        showAlert("Refreshed", "Data Hub display has been refreshed.");
    }


    @FXML
    private void clearDataHub() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Data Hub");
        confirm.setHeaderText("Are you sure?");
        confirm.setContentText("This will delete all data from the Data Hub.");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            dataHubList.clear();
            statsLabel.setText("");

            statisticsSection.setVisible(false);
            statisticsSection.setManaged(false);


            updateDataHubUI();

            try (var conn = DBConnector.getInstance().getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM research_data");
                System.out.println("✓ Data Hub cleared");
            } catch (Exception e) {
                System.err.println("⚠️ Failed to clear database: " + e.getMessage());
            }
        }
    }


    @FXML
    private void exportData() {
        if (dataHubList.isEmpty()) {
            showAlert("No Data", "Data Hub is empty. Nothing to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Research Data");
        fileChooser.setInitialFileName("research_data_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(mapView.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                // Header
                writer.println("Timestamp,Location,PM2.5,PM10,NO2,O3,SO2,CO");

                // Data rows
                for (AirQualityData data : dataHubList) {
                    writer.printf("%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            data.getTimestamp(), data.getLocation(),
                            data.getPm25(), data.getPm10(), data.getNo2(),
                            data.getO3(), data.getSo2(), data.getCo());
                }

                showAlert("Success", "Data exported successfully to:\n" + file.getAbsolutePath());
                System.out.println("✓ Data exported to: " + file.getName());
            } catch (IOException e) {
                showAlert("Error", "Failed to export data: " + e.getMessage());
            }
        }
    }


    private void loadSampleResearchers() {
        String[] researchers = {
            "Dr. Sarah Chen|Air Quality Modeling|50 publications|University of California",
            "Prof. Ahmed Hassan|Particulate Matter Analysis|78 publications|Cairo University",
            "Dr. Maria Garcia|Urban Air Pollution|45 publications|Barcelona Institute",
            "Prof. Rajesh Kumar|Industrial Emissions|92 publications|IIT Delhi",
            "Dr. Emily Watson|Climate & Air Quality|67 publications|Oxford University"
        };

        for (String researcherData : researchers) {
            String[] parts = researcherData.split("\\|");
            VBox card = createResearcherCard(parts[0], parts[1], parts[2], parts[3]);
            researchersListBox.getChildren().add(card);
        }
    }


    private VBox createResearcherCard(String name, String specialization, String publications, String institution) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #1abc9c; -fx-border-width: 2; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label specLabel = new Label("🔬 " + specialization);
        specLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        Label pubLabel = new Label("📚 " + publications);
        pubLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1abc9c;");

        Label instLabel = new Label("🏛️ " + institution);
        instLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        Button viewBtn = new Button("👁️ View Profile");
        viewBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6 12; -fx-background-color: #1abc9c; -fx-text-fill: white; -fx-background-radius: 4; -fx-cursor: hand;");
        viewBtn.setOnAction(e -> showAlert("Researcher Profile", "Viewing profile of " + name));

        card.getChildren().addAll(nameLabel, specLabel, pubLabel, instLabel, viewBtn);
        return card;
    }


    @FXML
    private void searchResearcher() {
        String query = researcherSearchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            return;
        }

        for (var node : researchersListBox.getChildren()) {
            VBox card = (VBox) node;
            Label nameLabel = (Label) card.getChildren().get(0);
            Label specLabel = (Label) card.getChildren().get(1);

            boolean matches = nameLabel.getText().toLowerCase().contains(query) ||
                            specLabel.getText().toLowerCase().contains(query);
            card.setVisible(matches);
            card.setManaged(matches);
        }
    }


    private void loadSamplePublications() {
        String[] publications = {
            "Impact of PM2.5 on Urban Health|Dr. Sarah Chen|2024|Nature Climate Change",
            "Long-term Trends in Air Quality|Prof. Ahmed Hassan|2023|Environmental Science",
            "Machine Learning for AQI Prediction|Dr. Maria Garcia|2024|Science Advances",
            "Industrial Pollution Control Strategies|Prof. Rajesh Kumar|2023|Environmental Engineering",
            "Climate Change and Air Quality Nexus|Dr. Emily Watson|2024|Climate Research"
        };

        for (String pubData : publications) {
            String[] parts = pubData.split("\\|");
            VBox card = createPublicationCard(parts[0], parts[1], parts[2], parts[3]);
            publicationsListBox.getChildren().add(card);
        }
    }


    private VBox createPublicationCard(String title, String author, String year, String journal) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e67e22; -fx-border-width: 2; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        titleLabel.setWrapText(true);

        Label authorLabel = new Label("✍️ " + author);
        authorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        Label infoLabel = new Label("📅 " + year + " | 📖 " + journal);
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e67e22;");

        Button viewBtn = new Button("📄 Read Abstract");
        viewBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6 12; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-background-radius: 4; -fx-cursor: hand;");
        viewBtn.setOnAction(e -> showAlert("Publication", "Abstract for: " + title));

        card.getChildren().addAll(titleLabel, authorLabel, infoLabel, viewBtn);
        return card;
    }


    @FXML
    private void searchPublications() {
        String query = publicationSearchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            return;
        }

        for (var node : publicationsListBox.getChildren()) {
            VBox card = (VBox) node;
            Label titleLabel = (Label) card.getChildren().get(0);
            Label authorLabel = (Label) card.getChildren().get(1);

            boolean matches = titleLabel.getText().toLowerCase().contains(query) ||
                            authorLabel.getText().toLowerCase().contains(query);
            card.setVisible(matches);
            card.setManaged(matches);
        }
    }


    @FXML
    private void showDataView() {
        hideAllPanels();
        dataViewPanel.setVisible(true);
    }

    @FXML
    private void showDataHub() {
        hideAllPanels();
        dataHubPanel.setVisible(true);
    }

    @FXML
    private void showResearchers() {
        hideAllPanels();
        researchersPanel.setVisible(true);
    }

    @FXML
    private void showPublications() {
        hideAllPanels();
        publicationsPanel.setVisible(true);
    }

    private void hideAllPanels() {
        dataViewPanel.setVisible(false);
        dataHubPanel.setVisible(false);
        researchersPanel.setVisible(false);
        publicationsPanel.setVisible(false);
    }

    @FXML
    private void logout() {
        try {

            SessionManager.getInstance().clearSession();

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 400));
            System.out.println("Researcher logged out");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    public static class AirQualityData {
        private String timestamp;
        private String location;
        private double pm25, pm10, no2, o3, so2, co;

        public AirQualityData(String timestamp, String location, double pm25, double pm10,
                              double no2, double o3, double so2, double co) {
            this.timestamp = timestamp;
            this.location = location;
            this.pm25 = pm25;
            this.pm10 = pm10;
            this.no2 = no2;
            this.o3 = o3;
            this.so2 = so2;
            this.co = co;
        }

        public String getTimestamp() { return timestamp; }
        public String getLocation() { return location; }
        public double getPm25() { return pm25; }
        public double getPm10() { return pm10; }
        public double getNo2() { return no2; }
        public double getO3() { return o3; }
        public double getSo2() { return so2; }
        public double getCo() { return co; }
    }
}

