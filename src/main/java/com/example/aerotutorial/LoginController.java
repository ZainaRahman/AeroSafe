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
import java.sql.ResultSet;
import java.util.ResourceBundle;

public class LoginController implements Initializable {
    @FXML
    private ComboBox<String> roleComboBox;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Populate role ComboBox
        roleComboBox.getItems().addAll("User", "Researcher", "Government Official");
        roleComboBox.setValue("User"); // Default selection
    }

    @FXML
    private void handleLogin() {
        String role = roleComboBox.getValue();
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (role == null || username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("All fields are required.");
            return;
        }

        // Determine which table to query based on role
        String tableName = getTableNameByRole(role);

        try (Connection conn = DBConnector.getInstance().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM " + tableName + " WHERE username=? AND password=?"
            );
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                System.out.println("Login successful for " + role + ": " + username);
                messageLabel.setText("Login successful!");

                // Navigate to role-specific dashboard
                Stage stage = (Stage) usernameField.getScene().getWindow();
                String dashboardFxml = getDashboardByRole(role);
                FXMLLoader loader = new FXMLLoader(getClass().getResource(dashboardFxml));
                Scene dashboardScene = new Scene(loader.load(), 1200, 600);
                stage.setScene(dashboardScene);
                stage.centerOnScreen();
            } else {
                System.out.println("Login failed for " + role + ": " + username);
                messageLabel.setText("Invalid credentials for " + role + ".");
            }
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void switchToSignup() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("signup.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 450));
        } catch (Exception e) {
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

    /**
     * Returns the appropriate dashboard FXML file based on user role
     */
    private String getDashboardByRole(String role) {
        switch (role) {
            case "Researcher":
                return "researcher_dashboard.fxml";
            case "Government Official":
                return "admin_dashboard.fxml";
            case "User":
            default:
                return "dashboard.fxml";
        }
    }
}
