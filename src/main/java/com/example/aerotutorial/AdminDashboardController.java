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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

public class AdminDashboardController implements Initializable {

    // FXML Components
    @FXML private Label welcomeLabel, totalUsersLabel, totalResearchersLabel, totalAdminsLabel;
    @FXML private Label totalReportsLabel, goodAqiDaysLabel, moderateAqiDaysLabel, unhealthyAqiDaysLabel;
    @FXML private Label affectedUsersLabel, policyReportLabel;
    @FXML private StackPane contentPane;

    // Panels
    @FXML private ScrollPane usersPanel, reportsPanel, alertsPanel, policyDataPanel;

    // Users Tables
    @FXML private TableView<User> usersTable, researchersTable, adminsTable;
    @FXML private TableColumn<User, Integer> userIdCol, researcherIdCol, adminIdCol;
    @FXML private TableColumn<User, String> userNameCol, userLocationCol;
    @FXML private TableColumn<User, String> researcherNameCol, researcherLocationCol;
    @FXML private TableColumn<User, String> adminNameCol, adminLocationCol;

    // Reports Table
    @FXML private TableView<Report> reportsTable;
    @FXML private TableColumn<Report, Integer> reportIdCol;
    @FXML private TableColumn<Report, String> reportDateCol, reporterNameCol, reportLocationCol;
    @FXML private TableColumn<Report, String> reportIssueCol, reportSeverityCol, reportStatusCol;
    @FXML private ComboBox<String> reportStatusFilter;

    // Alerts
    @FXML private ComboBox<String> alertTypeCombo, alertSeverityCombo;
    @FXML private TextField alertLocationField;
    @FXML private TextArea alertMessageArea;
    @FXML private VBox activeAlertsBox;

    // Data
    private ObservableList<User> usersList = FXCollections.observableArrayList();
    private ObservableList<User> researchersList = FXCollections.observableArrayList();
    private ObservableList<User> adminsList = FXCollections.observableArrayList();
    private ObservableList<Report> reportsList = FXCollections.observableArrayList();
    private ObservableList<Alert> alertsList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUsersTables();
        setupReportsTable();
        setupAlertControls();

        // Load data
        loadAllUsers();
        loadAllReports();
        loadActiveAlerts();
        calculatePolicyData();

        // Show users panel by default
        showUsers();
    }

    /** Setup users tables */
    private void setupUsersTables() {
        // Users table
        userIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        userNameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        userLocationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        usersTable.setItems(usersList);

        // Researchers table
        researcherIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        researcherNameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        researcherLocationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        researchersTable.setItems(researchersList);

        // Admins table
        adminIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        adminNameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        adminLocationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        adminsTable.setItems(adminsList);
    }

    /** Setup reports table */
    private void setupReportsTable() {
        reportIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        reportDateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        reporterNameCol.setCellValueFactory(new PropertyValueFactory<>("reporterName"));
        reportLocationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        reportIssueCol.setCellValueFactory(new PropertyValueFactory<>("issueType"));
        reportSeverityCol.setCellValueFactory(new PropertyValueFactory<>("severity"));
        reportStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        reportsTable.setItems(reportsList);

        // Status filter
        reportStatusFilter.getItems().addAll("All", "Pending", "Resolved", "In Progress");
        reportStatusFilter.setValue("All");
    }

    /** Setup alert controls */
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

    /** Load all users from database */
    private void loadAllUsers() {
        new Thread(() -> {
            try {
                Connection conn = DBConnector.getInstance().getConnection();

                // Load regular users
                Statement stmt = conn.createStatement();
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
                int finalUserCount = userCount;
                Platform.runLater(() -> totalUsersLabel.setText(String.valueOf(finalUserCount)));

                // Load researchers
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
                int finalResearcherCount = researcherCount;
                Platform.runLater(() -> totalResearchersLabel.setText(String.valueOf(finalResearcherCount)));

                // Load admins
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
                int finalAdminCount = adminCount;
                Platform.runLater(() -> totalAdminsLabel.setText(String.valueOf(finalAdminCount)));

                System.out.println("✓ Loaded users: " + finalUserCount + ", researchers: " + finalResearcherCount + ", admins: " + finalAdminCount);

            } catch (Exception e) {
                System.err.println("Error loading users: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /** Load all reports from database */
    private void loadAllReports() {
        new Thread(() -> {
            try {
                Connection conn = DBConnector.getInstance().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM reports ORDER BY id DESC");

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
                        rs.getString("status")
                    ));
                    count++;
                }

                int finalCount = count;
                Platform.runLater(() -> totalReportsLabel.setText(String.valueOf(finalCount)));

                System.out.println("✓ Loaded " + count + " reports");

            } catch (Exception e) {
                System.err.println("Error loading reports: " + e.getMessage());
            }
        }).start();
    }

    /** Load active alerts */
    private void loadActiveAlerts() {
        new Thread(() -> {
            try {
                Connection conn = DBConnector.getInstance().getConnection();

                // Create alerts table if not exists
                String createTable = "CREATE TABLE IF NOT EXISTS alerts(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "alert_type TEXT, " +
                        "severity TEXT, " +
                        "location TEXT, " +
                        "message TEXT, " +
                        "created_date TEXT, " +
                        "status TEXT DEFAULT 'Active')";
                conn.createStatement().execute(createTable);

                // Load active alerts
                ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT * FROM alerts WHERE status='Active' ORDER BY id DESC"
                );

                alertsList.clear();
                while (rs.next()) {
                    alertsList.add(new Alert(
                        rs.getInt("id"),
                        rs.getString("alert_type"),
                        rs.getString("severity"),
                        rs.getString("location"),
                        rs.getString("message"),
                        rs.getString("created_date")
                    ));
                }

                Platform.runLater(this::displayActiveAlerts);
                System.out.println("✓ Loaded " + alertsList.size() + " active alerts");

            } catch (Exception e) {
                System.err.println("Error loading alerts: " + e.getMessage());
            }
        }).start();
    }

    /** Display active alerts */
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

    /** Create alert card */
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

    /** Get severity color */
    private String getSeverityColor(String severity) {
        switch (severity) {
            case "Critical": return "#ffebee";
            case "High": return "#fff3e0";
            case "Medium": return "#fff9c4";
            case "Low": return "#e8f5e9";
            default: return "#f5f5f5";
        }
    }

    /** Calculate policy data */
    private void calculatePolicyData() {
        // Generate sample policy statistics
        goodAqiDaysLabel.setText("18");
        moderateAqiDaysLabel.setText("9");
        unhealthyAqiDaysLabel.setText("3");

        // Calculate affected users (total users + researchers)
        int totalAffected = usersList.size() + researchersList.size();
        affectedUsersLabel.setText(String.valueOf(totalAffected));
    }

    // Action Methods

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
        String filter = reportStatusFilter.getValue();
        if ("All".equals(filter)) {
            reportsTable.setItems(reportsList);
        } else {
            ObservableList<Report> filtered = FXCollections.observableArrayList();
            for (Report report : reportsList) {
                if (report.getStatus().equals(filter)) {
                    filtered.add(report);
                }
            }
            reportsTable.setItems(filtered);
        }
    }

    @FXML
    private void viewReportDetails() {
        Report selected = reportsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a report to view details");
            return;
        }

        try {
            Connection conn = DBConnector.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM reports WHERE id=?"
            );
            stmt.setInt(1, selected.getId());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String details = String.format(
                    "Report ID: %d\n" +
                    "Date: %s\n" +
                    "Reporter: %s\n" +
                    "Location: %s\n" +
                    "Issue Type: %s\n" +
                    "Severity: %s\n" +
                    "Status: %s\n" +
                    "AQI Value: %s\n" +
                    "Contact: %s\n\n" +
                    "Description:\n%s",
                    rs.getInt("id"),
                    rs.getString("submitted_date"),
                    rs.getString("reporter_name"),
                    rs.getString("location"),
                    rs.getString("issue_type"),
                    rs.getString("severity"),
                    rs.getString("status"),
                    rs.getString("aqi_value"),
                    rs.getString("contact"),
                    rs.getString("description")
                );

                showAlert("Report Details", details);
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to load report details: " + e.getMessage());
        }
    }

    @FXML
    private void markReportResolved() {
        Report selected = reportsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a report to mark as resolved");
            return;
        }

        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Resolution");
        confirm.setHeaderText("Mark report as resolved?");
        confirm.setContentText("Report ID: " + selected.getId() + " - " + selected.getIssueType());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DBConnector.getInstance().getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE reports SET status='Resolved' WHERE id=?"
                );
                stmt.setInt(1, selected.getId());
                stmt.executeUpdate();

                showAlert("Success", "Report marked as resolved");
                refreshReports();

            } catch (Exception e) {
                showAlert("Error", "Failed to update report: " + e.getMessage());
            }
        }
    }

    @FXML
    private void issueAlert() {
        String alertType = alertTypeCombo.getValue();
        String severity = alertSeverityCombo.getValue();
        String location = alertLocationField.getText().trim();
        String message = alertMessageArea.getText().trim();

        if (alertType == null || severity == null || location.isEmpty() || message.isEmpty()) {
            showAlert("Missing Information", "Please fill in all fields to issue an alert");
            return;
        }

        try {
            Connection conn = DBConnector.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO alerts(alert_type, severity, location, message, created_date) VALUES (?, ?, ?, ?, ?)"
            );
            stmt.setString(1, alertType);
            stmt.setString(2, severity);
            stmt.setString(3, location);
            stmt.setString(4, message);
            stmt.setString(5, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            stmt.executeUpdate();

            showAlert("Alert Issued", "Public health alert has been issued successfully!");

            // Clear fields
            alertTypeCombo.setValue(null);
            alertSeverityCombo.setValue(null);
            alertLocationField.clear();
            alertMessageArea.clear();

            // Reload alerts
            loadActiveAlerts();

        } catch (Exception e) {
            showAlert("Error", "Failed to issue alert: " + e.getMessage());
        }
    }

    private void deactivateAlert(int alertId) {
        try {
            Connection conn = DBConnector.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE alerts SET status='Inactive' WHERE id=?"
            );
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

    // Model Classes

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

        public Report(int id, String date, String reporterName, String location,
                     String issueType, String severity, String status) {
            this.id = new SimpleIntegerProperty(id);
            this.date = new SimpleStringProperty(date);
            this.reporterName = new SimpleStringProperty(reporterName);
            this.location = new SimpleStringProperty(location);
            this.issueType = new SimpleStringProperty(issueType);
            this.severity = new SimpleStringProperty(severity);
            this.status = new SimpleStringProperty(status);
        }

        public int getId() { return id.get(); }
        public String getDate() { return date.get(); }
        public String getReporterName() { return reporterName.get(); }
        public String getLocation() { return location.get(); }
        public String getIssueType() { return issueType.get(); }
        public String getSeverity() { return severity.get(); }
        public String getStatus() { return status.get(); }
    }

    public static class Alert {
        private final int id;
        private final String alertType;
        private final String severity;
        private final String location;
        private final String message;
        private final String date;

        public Alert(int id, String alertType, String severity, String location,
                    String message, String date) {
            this.id = id;
            this.alertType = alertType;
            this.severity = severity;
            this.location = location;
            this.message = message;
            this.date = date;
        }

        public int getId() { return id; }
        public String getAlertType() { return alertType; }
        public String getSeverity() { return severity; }
        public String getLocation() { return location; }
        public String getMessage() { return message; }
        public String getDate() { return date; }
    }
}

