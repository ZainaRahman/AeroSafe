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

public class SignupController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField locationField;
    @FXML
    private Label messageLabel;

    @FXML
    private void handleSignup() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String location = locationField.getText().trim();

        if(username.isEmpty() || password.isEmpty() || location.isEmpty()){
            messageLabel.setText("All fields are required.");
            return;
        }

        try(Connection conn = DBConnector.getInstance().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users(username, password, location) VALUES (?, ?, ?)"
            );
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, location);
            int rows = stmt.executeUpdate();
            System.out.println("Rows inserted: " + rows);

            messageLabel.setText("Signup successful! Please login.");
        } catch(Exception e){
            e.printStackTrace();
            messageLabel.setText("Error: " + e.getMessage());
        }
    }


    @FXML
    private void switchToLogin() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 300));
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
