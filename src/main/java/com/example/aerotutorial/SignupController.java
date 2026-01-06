package com.example.aerotutorial;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.ResourceBundle;

public class SignupController implements Initializable {
    @FXML
    private ComboBox<String> roleComboBox;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordTextField;
    @FXML
    private Button togglePasswordBtn;
    @FXML
    private TextField locationField;
    @FXML
    private Label messageLabel;

    private boolean isPasswordVisible = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        roleComboBox.getItems().addAll("User", "Researcher", "Government Official");
        roleComboBox.setValue("User");


        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isPasswordVisible) {
                passwordTextField.setText(newVal);
            }
        });

        passwordTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isPasswordVisible) {
                passwordField.setText(newVal);
            }
        });
    }

    @FXML
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            passwordTextField.setText(passwordField.getText());
            passwordTextField.setVisible(true);
            passwordTextField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            togglePasswordBtn.setText("🙈");
        } else {

            passwordField.setText(passwordTextField.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordTextField.setVisible(false);
            passwordTextField.setManaged(false);
            togglePasswordBtn.setText("👁");
        }
    }

    @FXML
    private void handleSignup() {
        String role = roleComboBox.getValue();
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = isPasswordVisible ? passwordTextField.getText().trim() : passwordField.getText().trim();
        String location = locationField.getText().trim();


        if (role == null || username.isEmpty() || email.isEmpty() || password.isEmpty() || location.isEmpty()) {
            showMessage("All fields are required.", "red");
            return;
        }


        if (!isValidEmail(email)) {
            showMessage("Please enter a valid email address.", "red");
            return;
        }

        if (password.length() < 6) {
            showMessage("Password must be at least 6 characters long.", "red");
            return;
        }


        showMessage("Sending verification email...", "#3498db");

        new Thread(() -> {
            boolean emailSent = EmailService.getInstance().sendVerificationEmail(email, username);

            Platform.runLater(() -> {
                if (emailSent) {

                    showEmailVerificationDialog(role, username, email, password, location);
                } else {
                    showMessage("❌ Failed to send verification email. Please check your email configuration.", "red");
                }
            });
        }).start();
    }

    private void showEmailVerificationDialog(String role, String username, String email, String password, String location) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Email Verification");


        String headerText = "📧 Verification email sent to:\n" + email +
                           "\n\n📬 Check your email inbox for the verification code!";
        dialog.setHeaderText(headerText);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label instruction = new Label("Please check your email and enter the 6-digit verification code:");
        instruction.setWrapText(true);
        instruction.setStyle("-fx-font-size: 13px;");

        TextField codeField = new TextField();
        codeField.setPromptText("Enter 6-digit code");
        codeField.setMaxWidth(200);
        codeField.setStyle("-fx-font-size: 16px; -fx-alignment: center; -fx-font-weight: bold;");


        codeField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal.length() > 6) {
                codeField.setText(old);
            }
            if (!newVal.matches("\\d*")) {
                codeField.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });

        Label timerLabel = new Label("⏱️ Code expires in 10 minutes");
        timerLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");

        content.getChildren().addAll(instruction, codeField, timerLabel);
        dialog.getDialogPane().setContent(content);

        ButtonType verifyButton = new ButtonType("Verify", ButtonBar.ButtonData.OK_DONE);
        ButtonType resendButton = new ButtonType("Resend Code", ButtonBar.ButtonData.LEFT);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(verifyButton, resendButton, cancelButton);


        Button verifyBtn = (Button) dialog.getDialogPane().lookupButton(verifyButton);
        verifyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

        Button resendBtn = (Button) dialog.getDialogPane().lookupButton(resendButton);
        resendBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");


        resendBtn.setOnAction(e -> {
            new Thread(() -> {
                EmailService.getInstance().sendVerificationEmail(email, username);
                Platform.runLater(() -> {
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Email Sent");
                    info.setHeaderText(null);
                    info.setContentText("✅ Verification code has been resent to " + email);
                    info.showAndWait();
                });
            }).start();
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == verifyButton) {
                return codeField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(code -> {
            if (EmailService.getInstance().verifyCode(email, code)) {

                saveUserToDatabase(role, username, email, password, location);
            } else {
                showMessage("❌ Invalid or expired verification code.", "red");


                Alert retry = new Alert(Alert.AlertType.CONFIRMATION);
                retry.setTitle("Verification Failed");
                retry.setHeaderText("Invalid or expired code");
                retry.setContentText("Would you like to try again?");
                retry.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

                Optional<ButtonType> retryResult = retry.showAndWait();
                if (retryResult.isPresent() && retryResult.get() == ButtonType.YES) {
                    showEmailVerificationDialog(role, username, email, password, location);
                }
            }
        });
    }

    private void saveUserToDatabase(String role, String username, String email, String password, String location) {
        String tableName = getTableNameByRole(role);

        try (Connection conn = DBConnector.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tableName + "(username, email, password, location, email_verified) VALUES (?, ?, ?, ?, 1)"
             )) {

            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setString(4, location);
            int rows = stmt.executeUpdate();

            System.out.println("✅ User registered: " + username + " (" + role + ") - Email: " + email);

            showMessage("✅ Registration successful! Email verified. You can now login.", "green");

            usernameField.clear();
            emailField.clear();
            passwordField.clear();
            passwordTextField.clear();
            locationField.clear();
            roleComboBox.setValue("User");

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(this::switchToLogin);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            showMessage("❌ Registration failed: " + e.getMessage(), "red");

            if (e.getMessage().contains("UNIQUE constraint failed")) {
                if (e.getMessage().contains("username")) {
                    showMessage("❌ Username already exists!", "red");
                } else if (e.getMessage().contains("email")) {
                    showMessage("❌ Email already registered!", "red");
                }
            }
        }
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    private void showMessage(String message, String color) {
        messageLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        messageLabel.setText(message);
    }

    @FXML
    private void switchToLogin() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 500));
            stage.centerOnScreen();
        } catch(Exception e){
            e.printStackTrace();
        }
    }


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
