package com.example.aerotutorial;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class AdminDashboardController implements Initializable {


    @FXML private Label welcomeLabel, totalUsersLabel, totalResearchersLabel, totalAdminsLabel;
    @FXML private Label totalReportsLabel, goodAqiDaysLabel, moderateAqiDaysLabel, unhealthyAqiDaysLabel;
    @FXML private Label affectedUsersLabel, policyReportLabel;
    @FXML private StackPane contentPane;


    @FXML private ScrollPane usersPanel, reportsPanel, alertsPanel, policyDataPanel;


    @FXML private VBox usersTileContainer;
    @FXML private VBox researchersTileContainer;
    @FXML private VBox adminsTileContainer;


    @FXML private VBox reportsTileContainer;
    @FXML private ComboBox<String> reportStatusFilter;


    @FXML private ComboBox<String> alertTypeCombo, alertSeverityCombo;
    @FXML private WebView alertMapView;
    @FXML private TextField alertLocationSearchField;
    @FXML private Label selectedAlertLocationLabel;
    @FXML private TextArea alertMessageArea;
    @FXML private VBox activeAlertsBox;


    private double selectedAlertLat = 0;
    private double selectedAlertLon = 0;
    private String selectedAlertLocationName = "";


    private ObservableList<User> usersList = FXCollections.observableArrayList();
    private ObservableList<User> researchersList = FXCollections.observableArrayList();
    private ObservableList<User> adminsList = FXCollections.observableArrayList();
    private ObservableList<Report> reportsList = FXCollections.observableArrayList();
    private ObservableList<Alert> alertsList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupReportsFilter();
        setupAlertControls();
        setupAlertMap();
        setupAlertSearchFieldListener();

        loadAllUsers();
        loadAllReports();
        loadActiveAlerts();
        calculatePolicyData();

        showUsers();
    }

    private void setupAlertSearchFieldListener() {
        if (alertLocationSearchField != null) {
            alertLocationSearchField.setOnAction(e -> searchAlertLocation());
        }
    }

    private void setupReportsFilter() {
        reportStatusFilter.getItems().addAll("All", "Pending", "Resolved", "In Progress");
        reportStatusFilter.setValue("All");
    }



    private void setupAlertControls() {
        alertTypeCombo.getItems().addAll(
            "High AQI Alert",
            "Health Advisory",
            "Pollution Warning",
            "Emergency Alert",
            "General Notice"
        );

        alertSeverityCombo.getItems().addAll(
            "Low",
            "Medium",
            "High",
            "Critical"
        );
    }

    private void setupAlertMap() {
        WebEngine webEngine = alertMapView.getEngine();
        webEngine.setJavaScriptEnabled(true);


        String mapUrl = getClass().getResource("/com/example/aerotutorial/map.html").toExternalForm();
        webEngine.load(mapUrl);


        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if ("SUCCEEDED".equals(newState.toString())) {
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("adminApp", this); // Expose Java methods to JavaScript
                    System.out.println("✓ Alert map Java bridge established successfully!");


                    webEngine.executeScript("map.setView([23.8103, 90.4125], 12);");
                } catch (Exception e) {
                    System.err.println("❌ Failed to establish alert map Java bridge: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }


    public void onAlertMapClick(String city, double lat, double lon) {
        Platform.runLater(() -> {
            selectedAlertLat = lat;
            selectedAlertLon = lon;

            System.out.println("Alert location selected: " + lat + ", " + lon);


            new Thread(() -> {
                String locationName = getLocationName(lat, lon);
                selectedAlertLocationName = locationName;
                Platform.runLater(() -> {
                    selectedAlertLocationLabel.setText(locationName + " (" + String.format("%.4f", lat) + ", " + String.format("%.4f", lon) + ")");
                    selectedAlertLocationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                });
            }).start();
        });
    }


    private String getLocationName(double lat, double lon) {
        try {
            String urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lon;
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AeroSafe Desktop App");

            if (conn.getResponseCode() == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                return json.getString("display_name");
            }
        } catch (Exception e) {
            System.err.println("Error fetching location name: " + e.getMessage());
        }
        return String.format("%.4f, %.4f", lat, lon);
    }


    @FXML
    private void searchAlertLocation() {
        String query = alertLocationSearchField.getText().trim();

        if (query.isEmpty()) {
            showAlert("Search Required", "Please enter a location to search");
            return;
        }

        System.out.println("🔍 Searching for alert location: " + query);

        new Thread(() -> {
            try {

                String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1";

                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "AeroSafe Desktop App");

                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    org.json.JSONArray results = new org.json.JSONArray(response.toString());

                    if (results.length() > 0) {
                        org.json.JSONObject location = results.getJSONObject(0);
                        double lat = location.getDouble("lat");
                        double lon = location.getDouble("lon");
                        String displayName = location.getString("display_name");

                        System.out.println("✅ Found location: " + displayName + " (" + lat + ", " + lon + ")");

                        Platform.runLater(() -> {

                            selectedAlertLat = lat;
                            selectedAlertLon = lon;
                            selectedAlertLocationName = displayName;

                            selectedAlertLocationLabel.setText(displayName + " (" + String.format("%.4f", lat) + ", " + String.format("%.4f", lon) + ")");
                            selectedAlertLocationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");


                            WebEngine engine = alertMapView.getEngine();
                            engine.executeScript("map.setView([" + lat + ", " + lon + "], 13);");


                            engine.executeScript(
                                "L.marker([" + lat + ", " + lon + "]).addTo(map)" +
                                ".bindPopup('" + displayName.replace("'", "\\'") + "').openPopup();"
                            );
                        });
                    } else {
                        Platform.runLater(() -> {
                            showAlert("Location Not Found", "Could not find the location: " + query + ". Please try a different search.");
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("Error searching location: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    showAlert("Search Error", "Failed to search location. Please try again.");
                });
            }
        }).start();
    }


    private void loadAllUsers() {
        new Thread(() -> {
            try (Connection conn = DBConnector.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {


                ResultSet rs = stmt.executeQuery("SELECT * FROM users");
                usersList.clear();
                int userCount = 0;
                while (rs.next()) {
                    usersList.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("location")
                    ));
                    userCount++;
                }
                rs.close();
                int finalUserCount = userCount;
                Platform.runLater(() -> {
                    totalUsersLabel.setText(String.valueOf(finalUserCount));
                    displayUserTiles();
                });


                rs = stmt.executeQuery("SELECT * FROM researchers");
                researchersList.clear();
                int researcherCount = 0;
                while (rs.next()) {
                    researchersList.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("location")
                    ));
                    researcherCount++;
                }
                rs.close();
                int finalResearcherCount = researcherCount;
                Platform.runLater(() -> {
                    totalResearchersLabel.setText(String.valueOf(finalResearcherCount));
                    displayResearcherTiles();
                });


                rs = stmt.executeQuery("SELECT * FROM admin");
                adminsList.clear();
                int adminCount = 0;
                while (rs.next()) {
                    adminsList.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("location")
                    ));
                    adminCount++;
                }
                rs.close();
                int finalAdminCount = adminCount;
                Platform.runLater(() -> {
                    totalAdminsLabel.setText(String.valueOf(finalAdminCount));
                    displayAdminTiles();
                });

                System.out.println("✓ Loaded users: " + finalUserCount + ", researchers: " + finalResearcherCount + ", admins: " + finalAdminCount);

            } catch (Exception e) {
                System.err.println("Error loading users: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void displayUserTiles() {
        usersTileContainer.getChildren().clear();

        if (usersList.isEmpty()) {
            Label emptyLabel = new Label("No users found");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            usersTileContainer.getChildren().add(emptyLabel);
            return;
        }


        HBox currentRow = new HBox(15);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        int tilesInRow = 0;

        for (User user : usersList) {
            VBox tile = createUserTile(user, "#e74c3c");
            currentRow.getChildren().add(tile);
            tilesInRow++;

            if (tilesInRow == 4) {
                usersTileContainer.getChildren().add(currentRow);
                currentRow = new HBox(15);
                currentRow.setAlignment(Pos.CENTER_LEFT);
                tilesInRow = 0;
            }
        }

        if (tilesInRow > 0) {
            usersTileContainer.getChildren().add(currentRow);
        }
    }

    private void displayResearcherTiles() {
        researchersTileContainer.getChildren().clear();

        if (researchersList.isEmpty()) {
            Label emptyLabel = new Label("No researchers found");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            researchersTileContainer.getChildren().add(emptyLabel);
            return;
        }

        HBox currentRow = new HBox(15);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        int tilesInRow = 0;

        for (User researcher : researchersList) {
            VBox tile = createUserTile(researcher, "#9b59b6");
            currentRow.getChildren().add(tile);
            tilesInRow++;

            if (tilesInRow == 4) {
                researchersTileContainer.getChildren().add(currentRow);
                currentRow = new HBox(15);
                currentRow.setAlignment(Pos.CENTER_LEFT);
                tilesInRow = 0;
            }
        }

        if (tilesInRow > 0) {
            researchersTileContainer.getChildren().add(currentRow);
        }
    }

    private void displayAdminTiles() {
        adminsTileContainer.getChildren().clear();

        if (adminsList.isEmpty()) {
            Label emptyLabel = new Label("No admins found");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            adminsTileContainer.getChildren().add(emptyLabel);
            return;
        }

        HBox currentRow = new HBox(15);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        int tilesInRow = 0;

        for (User admin : adminsList) {
            VBox tile = createUserTile(admin, "#c0392b");
            currentRow.getChildren().add(tile);
            tilesInRow++;

            if (tilesInRow == 4) {
                adminsTileContainer.getChildren().add(currentRow);
                currentRow = new HBox(15);
                currentRow.setAlignment(Pos.CENTER_LEFT);
                tilesInRow = 0;
            }
        }

        if (tilesInRow > 0) {
            adminsTileContainer.getChildren().add(currentRow);
        }
    }

    private VBox createUserTile(User user, String accentColor) {
        VBox tile = new VBox(10);
        tile.setPadding(new Insets(15));
        tile.setPrefWidth(250);
        tile.setMaxWidth(250);
        tile.setMinHeight(150);
        tile.setAlignment(Pos.TOP_CENTER);

        tile.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                     "-fx-border-color: " + accentColor + "; -fx-border-width: 2; -fx-border-radius: 10; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");


        Label iconLabel = new Label("👤");
        iconLabel.setStyle("-fx-font-size: 48px;");


        Label idLabel = new Label("ID: " + user.getId());
        idLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: white; -fx-background-color: " + accentColor + "; " +
                        "-fx-padding: 3 8; -fx-background-radius: 10;");


        Label nameLabel = new Label(user.getUsername());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxWidth(220);


        Label locationLabel = new Label("📍 " + user.getLocation());
        locationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        locationLabel.setWrapText(true);
        locationLabel.setAlignment(Pos.CENTER);
        locationLabel.setMaxWidth(220);

        tile.getChildren().addAll(iconLabel, idLabel, nameLabel, locationLabel);


        tile.setOnMouseEntered(e -> {
            tile.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 10; " +
                         "-fx-border-color: " + accentColor + "; -fx-border-width: 3; -fx-border-radius: 10; " +
                         "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 3);");
        });

        tile.setOnMouseExited(e -> {
            tile.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                         "-fx-border-color: " + accentColor + "; -fx-border-width: 2; -fx-border-radius: 10; " +
                         "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");
        });

        return tile;
    }


    private void loadAllReports() {
        new Thread(() -> {
            try (Connection conn = DBConnector.getInstance().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM reports ORDER BY id DESC")) {

                reportsList.clear();
                int count = 0;
                while (rs.next()) {
                    reportsList.add(new Report(
                        rs.getInt("id"),
                        rs.getString("submitted_date"),
                        rs.getString("reporter_name"),
                        rs.getString("location"),
                        rs.getString("issue_type"),
                        rs.getString("severity"),
                        rs.getString("status"),
                        rs.getString("description"),
                        rs.getString("contact"),
                        rs.getString("aqi_value"),
                        rs.getString("image_path")
                    ));
                    count++;
                }

                int finalCount = count;
                Platform.runLater(() -> {
                    totalReportsLabel.setText(String.valueOf(finalCount));
                    displayReportTiles();
                });

                System.out.println("✓ Loaded " + count + " reports");

            } catch (Exception e) {
                System.err.println("Error loading reports: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void displayReportTiles() {
        reportsTileContainer.getChildren().clear();

        if (reportsList.isEmpty()) {
            Label emptyLabel = new Label("No reports found");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            reportsTileContainer.getChildren().add(emptyLabel);
            return;
        }


        HBox currentRow = new HBox(15);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        int tilesInRow = 0;

        for (Report report : reportsList) {
            VBox tile = createReportTile(report);
            currentRow.getChildren().add(tile);
            tilesInRow++;

            if (tilesInRow == 3) {
                reportsTileContainer.getChildren().add(currentRow);
                currentRow = new HBox(15);
                currentRow.setAlignment(Pos.CENTER_LEFT);
                tilesInRow = 0;
            }
        }


        if (tilesInRow > 0) {
            reportsTileContainer.getChildren().add(currentRow);
        }
    }

    private VBox createReportTile(Report report) {
        VBox tile = new VBox(10);
        tile.setPadding(new Insets(15));
        tile.setPrefWidth(350);
        tile.setMaxWidth(350);
        tile.setMinHeight(450);

        String borderColor = getSeverityBorderColor(report.getSeverity());
        String bgColor = getStatusBackgroundColor(report.getStatus());
        tile.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10; " +
                     "-fx-border-color: " + borderColor + "; -fx-border-width: 3; -fx-border-radius: 10; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 2);");


        VBox imageContainer = new VBox();
        imageContainer.setPrefHeight(200);
        imageContainer.setMaxHeight(200);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 8;");

        if (report.getImagePath() != null && !report.getImagePath().isEmpty() && !report.getImagePath().equals("null")) {
            try {
                File imageFile = new File(report.getImagePath());
                if (imageFile.exists()) {
                    Image image = new Image(imageFile.toURI().toString(), 320, 200, true, true);
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(320);
                    imageView.setFitHeight(200);
                    imageView.setPreserveRatio(true);
                    imageView.setStyle("-fx-background-radius: 8;");
                    imageContainer.getChildren().add(imageView);
                } else {
                    addNoImagePlaceholder(imageContainer);
                }
            } catch (Exception e) {
                addNoImagePlaceholder(imageContainer);
            }
        } else {
            addNoImagePlaceholder(imageContainer);
        }


        Label statusLabel = new Label(report.getStatus().toUpperCase());
        statusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 10; " +
                           "-fx-background-color: " + getStatusBadgeColor(report.getStatus()) + "; " +
                           "-fx-text-fill: white; -fx-background-radius: 5;");


        Label issueLabel = new Label("🚨 " + report.getIssueType());
        issueLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        issueLabel.setWrapText(true);

        Label severityLabel = new Label("Severity: " + report.getSeverity());
        severityLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                              getSeverityTextColor(report.getSeverity()) + ";");


        Label locationLabel = new Label("📍 " + report.getLocation());
        locationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #34495e;");
        locationLabel.setWrapText(true);

        Label reporterLabel = new Label("Reporter: " + report.getReporterName());
        reporterLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        // Description
        Label descLabel = new Label(report.getDescription());
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2c3e50;");
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(60);


        Label dateLabel = new Label("📅 " + formatDate(report.getDate()));
        dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");


        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER);

        Button viewBtn = new Button("View Details");
        viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 5 10; " +
                        "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 3;");
        viewBtn.setOnAction(e -> showReportDetailsDialog(report));

        Button resolveBtn = new Button("✓ Resolve");
        resolveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 5 10; " +
                           "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 3;");
        resolveBtn.setOnAction(e -> markReportAsResolved(report.getId()));

        if ("Resolved".equals(report.getStatus())) {
            resolveBtn.setDisable(true);
            resolveBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-padding: 5 10; " +
                               "-fx-font-size: 11px; -fx-background-radius: 3;");
        }

        buttonBox.getChildren().addAll(viewBtn, resolveBtn);

        tile.getChildren().addAll(imageContainer, statusLabel, issueLabel, severityLabel,
                                  locationLabel, reporterLabel, descLabel, dateLabel, buttonBox);

        return tile;
    }

    private void addNoImagePlaceholder(VBox container) {
        Label noImageLabel = new Label("📷\nNo Image");
        noImageLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #95a5a6; -fx-text-alignment: center;");
        noImageLabel.setAlignment(Pos.CENTER);
        container.getChildren().add(noImageLabel);
    }

    private String getSeverityBorderColor(String severity) {
        if (severity.contains("Critical")) return "#c0392b";
        if (severity.contains("High")) return "#e74c3c";
        if (severity.contains("Medium")) return "#f39c12";
        return "#27ae60";
    }

    private String getSeverityTextColor(String severity) {
        if (severity.contains("Critical")) return "#c0392b";
        if (severity.contains("High")) return "#e74c3c";
        if (severity.contains("Medium")) return "#f39c12";
        return "#27ae60";
    }

    private String getStatusBackgroundColor(String status) {
        if ("Resolved".equals(status)) return "#e8f8f5";
        if ("In Progress".equals(status)) return "#fef9e7";
        return "#ffffff";
    }

    private String getStatusBadgeColor(String status) {
        if ("Resolved".equals(status)) return "#27ae60";
        if ("In Progress".equals(status)) return "#f39c12";
        return "#e74c3c";
    }

    private String formatDate(String dateStr) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateStr);
            return dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
        } catch (Exception e) {
            return dateStr;
        }
    }

    private void showReportDetailsDialog(Report report) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Report Details - ID: " + report.getId());
        alert.setHeaderText("Full Report Information");

        String details = String.format(
            "Report ID: %d\n" +
            "Date: %s\n" +
            "Reporter: %s\n" +
            "Contact: %s\n" +
            "Location: %s\n" +
            "Issue Type: %s\n" +
            "Severity: %s\n" +
            "Status: %s\n" +
            "AQI Value: %s\n\n" +
            "Description:\n%s\n\n" +
            "Image: %s",
            report.getId(),
            formatDate(report.getDate()),
            report.getReporterName(),
            report.getContact(),
            report.getLocation(),
            report.getIssueType(),
            report.getSeverity(),
            report.getStatus(),
            report.getAqiValue(),
            report.getDescription(),
            (report.getImagePath() != null && !report.getImagePath().isEmpty() && !report.getImagePath().equals("null"))
                ? "Attached" : "No image"
        );

        alert.setContentText(details);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    private void markReportAsResolved(int reportId) {
        try {
            Connection conn = DBConnector.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE reports SET status='Resolved' WHERE id=?");
            stmt.setInt(1, reportId);
            stmt.executeUpdate();

            System.out.println("✅ Report " + reportId + " marked as resolved");
            showAlert("Success", "Report has been marked as resolved.");
            refreshReports();
        } catch (Exception e) {
            System.err.println("Error updating report status: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "Failed to update report status.");
        }
    }


    private void loadActiveAlerts() {
        new Thread(() -> {
            try (Connection conn = DBConnector.getInstance().getConnection();
                 Statement createStmt = conn.createStatement()) {


                String createTable = "CREATE TABLE IF NOT EXISTS alerts(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "alert_type TEXT, " +
                        "severity TEXT, " +
                        "location TEXT, " +
                        "latitude REAL, " +
                        "longitude REAL, " +
                        "message TEXT, " +
                        "created_date TEXT, " +
                        "status TEXT DEFAULT 'Active')";
                createStmt.execute(createTable);


                try {
                    createStmt.execute("ALTER TABLE alerts ADD COLUMN latitude REAL");
                    System.out.println("✅ Added latitude column to alerts table");
                } catch (Exception e) {

                }


                try {
                    createStmt.execute("ALTER TABLE alerts ADD COLUMN longitude REAL");
                    System.out.println("✅ Added longitude column to alerts table");
                } catch (Exception e) {

                }


                try (ResultSet rs = createStmt.executeQuery(
                    "SELECT * FROM alerts WHERE status='Active' ORDER BY id DESC"
                )) {
                    alertsList.clear();
                    while (rs.next()) {
                        alertsList.add(new Alert(
                            rs.getInt("id"),
                            rs.getString("alert_type"),
                            rs.getString("severity"),
                            rs.getString("location"),
                            rs.getString("message"),
                            rs.getString("created_date"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude")
                        ));
                    }
                }

                Platform.runLater(this::displayActiveAlerts);
                System.out.println("✓ Loaded " + alertsList.size() + " active alerts");

            } catch (Exception e) {
                System.err.println("Error loading alerts: " + e.getMessage());
            }
        }).start();
    }


    private void displayActiveAlerts() {
        activeAlertsBox.getChildren().clear();

        if (alertsList.isEmpty()) {
            Label emptyLabel = new Label("No active alerts");
            emptyLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d; -fx-padding: 10;");
            activeAlertsBox.getChildren().add(emptyLabel);
            return;
        }

        for (Alert alert : alertsList) {
            VBox alertCard = createAlertCard(alert);
            activeAlertsBox.getChildren().add(alertCard);
        }
    }


    private VBox createAlertCard(Alert alert) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));

        String bgColor = getSeverityColor(alert.getSeverity());
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8; -fx-border-color: #d35400; -fx-border-width: 2; -fx-border-radius: 8;");

        Label typeLabel = new Label("🚨 " + alert.getAlertType());
        typeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label severityLabel = new Label("Severity: " + alert.getSeverity());
        severityLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");

        Label locationLabel = new Label("📍 " + alert.getLocation());
        locationLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #34495e;");

        Label messageLabel = new Label(alert.getMessage());
        messageLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2c3e50;");
        messageLabel.setWrapText(true);

        Label dateLabel = new Label("Issued: " + alert.getDate());
        dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        Button deactivateBtn = new Button("❌ Deactivate");
        deactivateBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-padding: 6 12; -fx-cursor: hand;");
        deactivateBtn.setOnAction(e -> deactivateAlert(alert.getId()));

        card.getChildren().addAll(typeLabel, severityLabel, locationLabel, messageLabel, dateLabel, deactivateBtn);
        return card;
    }

    private String getSeverityColor(String severity) {
        switch (severity) {
            case "Critical": return "#ffebee";
            case "High": return "#fff3e0";
            case "Medium": return "#fff9c4";
            case "Low": return "#e8f5e9";
            default: return "#f5f5f5";
        }
    }


    private void calculatePolicyData() {

        goodAqiDaysLabel.setText("18");
        moderateAqiDaysLabel.setText("9");
        unhealthyAqiDaysLabel.setText("3");


        int totalAffected = usersList.size() + researchersList.size();
        affectedUsersLabel.setText(String.valueOf(totalAffected));
    }



    @FXML
    private void showUsers() {
        hideAllPanels();
        usersPanel.setVisible(true);
    }

    @FXML
    private void showReports() {
        hideAllPanels();
        reportsPanel.setVisible(true);
    }

    @FXML
    private void showAlerts() {
        hideAllPanels();
        alertsPanel.setVisible(true);
    }

    @FXML
    private void showPolicyData() {
        hideAllPanels();
        policyDataPanel.setVisible(true);
        calculatePolicyData();
    }

    private void hideAllPanels() {
        usersPanel.setVisible(false);
        reportsPanel.setVisible(false);
        alertsPanel.setVisible(false);
        policyDataPanel.setVisible(false);
    }

    @FXML
    private void refreshUsers() {
        loadAllUsers();
    }

    @FXML
    private void refreshResearchers() {
        loadAllUsers();
    }

    @FXML
    private void refreshAdmins() {
        loadAllUsers();
    }

    @FXML
    private void refreshReports() {
        loadAllReports();
    }

    @FXML
    private void filterReports() {
        reportsTileContainer.getChildren().clear();

        String filter = reportStatusFilter.getValue();
        ObservableList<Report> filteredReports;

        if ("All".equals(filter)) {
            filteredReports = reportsList;
        } else {
            filteredReports = FXCollections.observableArrayList();
            for (Report report : reportsList) {
                if (report.getStatus().equals(filter)) {
                    filteredReports.add(report);
                }
            }
        }

        if (filteredReports.isEmpty()) {
            Label emptyLabel = new Label("No reports found for filter: " + filter);
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            reportsTileContainer.getChildren().add(emptyLabel);
            return;
        }


        HBox currentRow = new HBox(15);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        int tilesInRow = 0;

        for (Report report : filteredReports) {
            VBox tile = createReportTile(report);
            currentRow.getChildren().add(tile);
            tilesInRow++;

            if (tilesInRow == 3) {
                reportsTileContainer.getChildren().add(currentRow);
                currentRow = new HBox(15);
                currentRow.setAlignment(Pos.CENTER_LEFT);
                tilesInRow = 0;
            }
        }


        if (tilesInRow > 0) {
            reportsTileContainer.getChildren().add(currentRow);
        }

        System.out.println("✓ Filtered reports: " + filteredReports.size() + " / " + reportsList.size());
    }


    @FXML
    private void issueAlert() {
        String alertType = alertTypeCombo.getValue();
        String severity = alertSeverityCombo.getValue();
        String message = alertMessageArea.getText().trim();


        if (alertType == null || severity == null || message.isEmpty()) {
            showAlert("Missing Information", "Please fill in all fields to issue an alert");
            return;
        }

        if (selectedAlertLat == 0 && selectedAlertLon == 0) {
            showAlert("Location Required", "Please click on the map to select an alert location");
            return;
        }

        try {
            Connection conn = DBConnector.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO alerts(alert_type, severity, location, latitude, longitude, message, created_date) VALUES (?, ?, ?, ?, ?, ?, ?)"
            );
            stmt.setString(1, alertType);
            stmt.setString(2, severity);
            stmt.setString(3, selectedAlertLocationName);
            stmt.setDouble(4, selectedAlertLat);
            stmt.setDouble(5, selectedAlertLon);
            stmt.setString(6, message);
            stmt.setString(7, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            stmt.executeUpdate();

            showAlert("Alert Issued", "Public health alert has been issued successfully for " + selectedAlertLocationName);


            alertTypeCombo.setValue(null);
            alertSeverityCombo.setValue(null);
            alertMessageArea.clear();
            selectedAlertLocationLabel.setText("None selected");
            selectedAlertLocationLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e67e22;");
            selectedAlertLat = 0;
            selectedAlertLon = 0;
            selectedAlertLocationName = "";

            loadActiveAlerts();

        } catch (Exception e) {
            showAlert("Error", "Failed to issue alert: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deactivateAlert(int alertId) {
        try (Connection conn = DBConnector.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE alerts SET status='Inactive' WHERE id=?")) {

            stmt.setInt(1, alertId);
            stmt.executeUpdate();

            loadActiveAlerts();
            System.out.println("✓ Alert deactivated: " + alertId);

        } catch (Exception e) {
            showAlert("Error", "Failed to deactivate alert: " + e.getMessage());
        }
    }

    @FXML
    private void generatePolicyReport() {
        int totalUsers = usersList.size() + researchersList.size() + adminsList.size();
        int totalReports = reportsList.size();

        long pendingReports = reportsList.stream()
            .filter(r -> "Pending".equals(r.getStatus()))
            .count();

        long resolvedReports = reportsList.stream()
            .filter(r -> "Resolved".equals(r.getStatus()))
            .count();

        String report = String.format(
            "📊 POLICY REPORT - Generated on %s\n\n" +
            "SYSTEM OVERVIEW:\n" +
            "• Total Registered Users: %d\n" +
            "• Regular Users: %d\n" +
            "• Researchers: %d\n" +
            "• Government Officials: %d\n\n" +
            "AIR QUALITY REPORTS:\n" +
            "• Total Reports Submitted: %d\n" +
            "• Pending Review: %d\n" +
            "• Resolved Issues: %d\n" +
            "• Resolution Rate: %.1f%%\n\n" +
            "AIR QUALITY TRENDS (Last 30 Days):\n" +
            "• Good AQI Days: %s (60%%)\n" +
            "• Moderate AQI Days: %s (30%%)\n" +
            "• Unhealthy AQI Days: %s (10%%)\n\n" +
            "POLICY RECOMMENDATIONS:\n" +
            "• Continue monitoring air quality in high-risk areas\n" +
            "• Increase public awareness campaigns\n" +
            "• Implement stricter emission controls\n" +
            "• Expand green spaces in urban areas\n\n" +
            "ESTIMATED POPULATION IMPACT:\n" +
            "• Citizens Using Platform: %d\n" +
            "• Active Alert System: ✓ Operational\n" +
            "• Public Reporting: ✓ Active",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            totalUsers,
            usersList.size(),
            researchersList.size(),
            adminsList.size(),
            totalReports,
            pendingReports,
            resolvedReports,
            totalReports > 0 ? (resolvedReports * 100.0 / totalReports) : 0,
            goodAqiDaysLabel.getText(),
            moderateAqiDaysLabel.getText(),
            unhealthyAqiDaysLabel.getText(),
            totalUsers
        );

        policyReportLabel.setText(report);
        System.out.println("✓ Policy report generated");
    }

    @FXML
    private void logout() {
        try {

            SessionManager.getInstance().clearSession();

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 400));
            System.out.println("Admin logged out");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }



    public static class User {
        private final SimpleIntegerProperty id;
        private final SimpleStringProperty username;
        private final SimpleStringProperty location;

        public User(int id, String username, String location) {
            this.id = new SimpleIntegerProperty(id);
            this.username = new SimpleStringProperty(username);
            this.location = new SimpleStringProperty(location);
        }

        public int getId() { return id.get(); }
        public String getUsername() { return username.get(); }
        public String getLocation() { return location.get(); }
    }

    public static class Report {
        private final SimpleIntegerProperty id;
        private final SimpleStringProperty date;
        private final SimpleStringProperty reporterName;
        private final SimpleStringProperty location;
        private final SimpleStringProperty issueType;
        private final SimpleStringProperty severity;
        private final SimpleStringProperty status;
        private final SimpleStringProperty description;
        private final SimpleStringProperty contact;
        private final SimpleStringProperty aqiValue;
        private final SimpleStringProperty imagePath;

        public Report(int id, String date, String reporterName, String location,
                     String issueType, String severity, String status, String description,
                     String contact, String aqiValue, String imagePath) {
            this.id = new SimpleIntegerProperty(id);
            this.date = new SimpleStringProperty(date);
            this.reporterName = new SimpleStringProperty(reporterName);
            this.location = new SimpleStringProperty(location);
            this.issueType = new SimpleStringProperty(issueType);
            this.severity = new SimpleStringProperty(severity);
            this.status = new SimpleStringProperty(status);
            this.description = new SimpleStringProperty(description);
            this.contact = new SimpleStringProperty(contact);
            this.aqiValue = new SimpleStringProperty(aqiValue);
            this.imagePath = new SimpleStringProperty(imagePath);
        }

        public int getId() { return id.get(); }
        public String getDate() { return date.get(); }
        public String getReporterName() { return reporterName.get(); }
        public String getLocation() { return location.get(); }
        public String getIssueType() { return issueType.get(); }
        public String getSeverity() { return severity.get(); }
        public String getStatus() { return status.get(); }
        public String getDescription() { return description.get(); }
        public String getContact() { return contact.get(); }
        public String getAqiValue() { return aqiValue.get(); }
        public String getImagePath() { return imagePath.get(); }
    }

    public static class Alert {
        private final int id;
        private final String alertType;
        private final String severity;
        private final String location;
        private final String message;
        private final String date;
        private final double latitude;
        private final double longitude;

        public Alert(int id, String alertType, String severity, String location,
                    String message, String date, double latitude, double longitude) {
            this.id = id;
            this.alertType = alertType;
            this.severity = severity;
            this.location = location;
            this.message = message;
            this.date = date;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public int getId() { return id; }
        public String getAlertType() { return alertType; }
        public String getSeverity() { return severity; }
        public String getLocation() { return location; }
        public String getMessage() { return message; }
        public String getDate() { return date; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}

