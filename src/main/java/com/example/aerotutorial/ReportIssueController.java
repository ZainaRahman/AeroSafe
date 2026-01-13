package com.example.aerotutorial;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
    @FXML
    private Button uploadImageButton;
    @FXML
    private Label imageStatusLabel;

    private File selectedImageFile = null;

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
    private void uploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Evidence Photo");


        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );


        Stage stage = (Stage) uploadImageButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            // Validate file size (limit to 10MB)
            long fileSizeInBytes = file.length();
            double fileSizeInMB = fileSizeInBytes / (1024.0 * 1024.0);
            if (fileSizeInMB > 10) {
                imageStatusLabel.setStyle("-fx-text-fill: red;");
                imageStatusLabel.setText("❌ File too large. Maximum size: 10MB");
                return;
            }

            selectedImageFile = file;
            imageStatusLabel.setStyle("-fx-text-fill: #27ae60;");
            imageStatusLabel.setText("✅ " + file.getName() + " (" + String.format("%.2f", fileSizeInMB) + " MB)");
            System.out.println("📷 Image selected: " + file.getName());
        }
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
                    "reporter_name TEXT NOT NULL, " +
                    "location TEXT NOT NULL, " +
                    "issue_type TEXT NOT NULL, " +
                    "severity TEXT NOT NULL, " +
                    "aqi_value TEXT, " +
                    "description TEXT NOT NULL, " +
                    "contact TEXT, " +
                    "status TEXT DEFAULT 'Pending', " +
                    "submitted_date TEXT NOT NULL)";
            conn.createStatement().execute(createTableSQL);


            try {
                conn.createStatement().execute("ALTER TABLE reports ADD COLUMN user_id INTEGER");
                System.out.println("✅ Added user_id column to reports table");
            } catch (Exception e) {

            }


            try {
                conn.createStatement().execute("ALTER TABLE reports ADD COLUMN image_path TEXT");
                System.out.println("✅ Added image_path column to reports table");
            } catch (Exception e) {

            }


            String imagePath = null;
            if (selectedImageFile != null) {
                try {
                    // Create reports_images directory if it doesn't exist
                    Path imagesDir = Paths.get("reports_images");
                    if (!Files.exists(imagesDir)) {
                        Files.createDirectories(imagesDir);
                    }

                    String timestamp = LocalDateTime.now().toString().replace(":", "-").replace(".", "-");
                    String extension = selectedImageFile.getName().substring(selectedImageFile.getName().lastIndexOf("."));
                    String newFileName = "report_" + timestamp + extension;
                    Path destinationPath = imagesDir.resolve(newFileName);


                    Files.copy(selectedImageFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                    imagePath = destinationPath.toString();
                    System.out.println("✅ Image saved to: " + imagePath);
                } catch (IOException e) {
                    System.err.println("⚠️ Failed to save image: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO reports(user_id, reporter_name, location, issue_type, severity, aqi_value, description, contact, image_path, submitted_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
            stmt.setInt(1, userId);
            stmt.setString(2, reporterName);
            stmt.setString(3, location);
            stmt.setString(4, issueType);
            stmt.setString(5, severity);
            stmt.setString(6, aqiValue.isEmpty() ? "Not specified" : aqiValue);
            stmt.setString(7, description);
            stmt.setString(8, contact.isEmpty() ? "Not provided" : contact);
            stmt.setString(9, imagePath);
            stmt.setString(10, LocalDateTime.now().toString());

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

