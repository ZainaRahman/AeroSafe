package com.example.aerotutorial;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ResourceBundle;

public class SignupController implements Initializable {
    @FXML
    private ComboBox<String> roleComboBox;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField locationField;
    @FXML
    private Label messageLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Populate role ComboBox
        roleComboBox.getItems().addAll("User", "Researcher", "Government Official");
        roleComboBox.setValue("User"); // Default selection
    }

    @FXML
    private void handleSignup() {
        String role = roleComboBox.getValue();
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String location = locationField.getText().trim();

        if (role == null || username.isEmpty() || password.isEmpty() || location.isEmpty()) {
            messageLabel.setText("All fields are required.");
            return;
        }

        // Determine which table to insert into based on role
        String tableName = getTableNameByRole(role);

        try (Connection conn = DBConnector.getInstance().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO " + tableName + "(username, password, location) VALUES (?, ?, ?)"
            );
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, location);
            int rows = stmt.executeUpdate();

            System.out.println("Rows inserted: " + rows + " in table: " + tableName);
            System.out.println("Signup successful for " + role + ": " + username);

            messageLabel.setStyle("-fx-text-fill: green; -fx-font-size: 13px; -fx-font-weight: bold;");
            messageLabel.setText("Signup successful! Please login as " + role + ".");

            // Clear fields after successful signup
            usernameField.clear();
            passwordField.clear();
            locationField.clear();
            roleComboBox.setValue("User");
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 13px; -fx-font-weight: bold;");

            // Check for duplicate username error
            if (e.getMessage().contains("Duplicate entry")) {
                messageLabel.setText("Username already exists for " + role + "!");
            } else {
                messageLabel.setText("Error: " + e.getMessage());
            }
        }
    }


    @FXML
    private void switchToLogin() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 400));
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Returns the database table name based on selected role
     */
    private String getTableNameByRole(String role) {
        switch (role) {
            case "Researcher":
                return "researchers";
            case "Government Official":
                return "admin";
            case "User":
            default:
                return "users";
        }
    }
}
