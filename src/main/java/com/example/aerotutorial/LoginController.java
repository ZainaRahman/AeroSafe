package com.example.aerotutorial;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if(username.isEmpty() || password.isEmpty()){
            messageLabel.setText("Please enter username and password.");
            return;
        }

        try(Connection conn = DBConnector.getInstance().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM users WHERE username=? AND password=?"
            );
            stmt.setString(1, usernameField.getText().trim());
            stmt.setString(2, passwordField.getText().trim());
            ResultSet rs = stmt.executeQuery();

            if(rs.next()){
                System.out.println("Login success: " + rs.getString("username"));
                messageLabel.setText("Login successful!");
                Stage stage = (Stage) usernameField.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard.fxml"));
                Scene dashboardScene = new Scene(loader.load(), 1200, 600);
                stage.setScene(dashboardScene);
                stage.centerOnScreen();


            } else {
                System.out.println("Login failed for: " + usernameField.getText());
                messageLabel.setText("Invalid username or password.");
            }
        } catch(Exception e){
            e.printStackTrace();
        }

    }

    @FXML
    private void switchToSignup() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("signup.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 300));
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
