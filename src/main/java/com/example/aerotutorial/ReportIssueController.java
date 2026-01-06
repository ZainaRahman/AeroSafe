package com.example.aerotutorial;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

public class ReportIssueController implements Initializable {

    @FXML
    private TextField reporterNameField;
    @FXML
    private TextField locationField;
    @FXML
    private ComboBox<String> issueTypeComboBox;
    @FXML
    private ComboBox<String> severityComboBox;
    @FXML
    private TextField aqiField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextField contactField;
    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        issueTypeComboBox.getItems().addAll(
            "High AQI / Poor Air Quality",
            "Industrial Pollution",
            "Vehicle Emissions",
            "Construction Dust",
            "Burning / Smoke",
            "Chemical Odor",
            "Other Environmental Concern"
        );


        severityComboBox.getItems().addAll(
            "Low - Minor concern",
            "Medium - Noticeable impact",
            "High - Significant health risk",
            "Critical - Immediate action required"
        );


        issueTypeComboBox.setValue("High AQI / Poor Air Quality");
        severityComboBox.setValue("Medium - Noticeable impact");
    }

    @FXML
    private void submitReport() {
        String reporterName = reporterNameField.getText().trim();
        String location = locationField.getText().trim();
        String issueType = issueTypeComboBox.getValue();
        String severity = severityComboBox.getValue();
        String aqiValue = aqiField.getText().trim();
        String description = descriptionArea.getText().trim();
        String contact = contactField.getText().trim();


        if (reporterName.isEmpty() || location.isEmpty() || description.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("❌ Please fill in all required fields (Name, Location, Description)");
            return;
        }

        if (issueType == null || severity == null) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("❌ Please select issue type and severity level");
            return;
        }

        try {
            Connection conn = DBConnector.getInstance().getConnection();


            SessionManager session = SessionManager.getInstance();
            int userId = session.getUserId();
            System.out.println("📝 Submitting report for user ID: " + userId);


            String createTableSQL = "CREATE TABLE IF NOT EXISTS reports(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "reporter_name TEXT NOT NULL, " +
                    "location TEXT NOT NULL, " +
                    "issue_type TEXT NOT NULL, " +
                    "severity TEXT NOT NULL, " +
                    "aqi_value TEXT, " +
                    "description TEXT NOT NULL, " +
                    "contact TEXT, " +
                    "status TEXT DEFAULT 'Pending', " +
                    "submitted_date TEXT NOT NULL, " +
                    "FOREIGN KEY(user_id) REFERENCES users(id))";
            conn.createStatement().execute(createTableSQL);


            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO reports(user_id, reporter_name, location, issue_type, severity, aqi_value, description, contact, submitted_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
            stmt.setInt(1, userId);
            stmt.setString(2, reporterName);
            stmt.setString(3, location);
            stmt.setString(4, issueType);
            stmt.setString(5, severity);
            stmt.setString(6, aqiValue.isEmpty() ? "Not specified" : aqiValue);
            stmt.setString(7, description);
            stmt.setString(8, contact.isEmpty() ? "Not provided" : contact);
            stmt.setString(9, LocalDateTime.now().toString());

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ Report submitted successfully");
                statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 14px;");
                statusLabel.setText("✅ Report submitted successfully! Government officials will review your report.");


                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        javafx.application.Platform.runLater(this::cancel);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("❌ Error submitting report: " + e.getMessage());
        }
    }

    @FXML
    private void cancel() {
        try {
            Stage stage = (Stage) reporterNameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard.fxml"));
            stage.setScene(new Scene(loader.load(), 1200, 600));
            System.out.println("Returned to dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

