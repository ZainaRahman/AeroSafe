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
import java.sql.ResultSet;
import java.util.Optional;
import java.util.ResourceBundle;

public class LoginController implements Initializable {
    @FXML
    private ComboBox<String> roleComboBox;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordTextField;
    @FXML
    private Button togglePasswordBtn;
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
    private void handleLogin() {
        String role = roleComboBox.getValue();
        String usernameOrEmail = usernameField.getText().trim();
        String password = isPasswordVisible ? passwordTextField.getText().trim() : passwordField.getText().trim();

        if (role == null || usernameOrEmail.isEmpty() || password.isEmpty()) {
            messageLabel.setText("All fields are required.");
            return;
        }


        String tableName = getTableNameByRole(role);

        try (Connection conn = DBConnector.getInstance().getConnection()) {

            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM " + tableName + " WHERE (username=? OR email=?) AND password=?"
            );
            stmt.setString(1, usernameOrEmail);
            stmt.setString(2, usernameOrEmail);
            stmt.setString(3, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                System.out.println("Login successful for " + role + ": " + usernameOrEmail);
                messageLabel.setText("Login successful!");


                int userId = rs.getInt("id");
                String username = rs.getString("username");
                String email = rs.getString("email");
                String location = rs.getString("location");
                SessionManager.getInstance().setUserSession(userId, username, role, location);


                Stage stage = (Stage) usernameField.getScene().getWindow();
                String dashboardFxml = getDashboardByRole(role);
                FXMLLoader loader = new FXMLLoader(getClass().getResource(dashboardFxml));
                Scene dashboardScene = new Scene(loader.load(), 1200, 600);
                stage.setScene(dashboardScene);
                stage.centerOnScreen();
            } else {
                System.out.println("Login failed for " + role + ": " + usernameOrEmail);
                messageLabel.setText("Invalid credentials for " + role + ".");
            }
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleForgotPassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Forgot Password");
        dialog.setHeaderText("🔐 Reset Your Password");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label instruction = new Label("Enter your email address to receive a password reset code:");
        instruction.setWrapText(true);
        instruction.setStyle("-fx-font-size: 13px;");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("User", "Researcher", "Government Official");
        roleCombo.setValue("User");
        roleCombo.setPromptText("Select Role");

        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");
        emailField.setPrefWidth(250);

        content.getChildren().addAll(new Label("Select your role:"), roleCombo, instruction, emailField);
        dialog.getDialogPane().setContent(content);

        ButtonType sendCodeButton = new ButtonType("Send Code", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(sendCodeButton, cancelButton);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == sendCodeButton) {
            String email = emailField.getText().trim();
            String selectedRole = roleCombo.getValue();

            if (email.isEmpty()) {
                showAlert("Error", "Please enter your email address.");
                return;
            }


            String tableName = getTableNameByRole(selectedRole);
            try (Connection conn = DBConnector.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT username FROM " + tableName + " WHERE email=?"
                 )) {

                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String username = rs.getString("username");


                    new Thread(() -> {
                        boolean emailSent = EmailService.getInstance().sendPasswordResetEmail(email, username);

                        Platform.runLater(() -> {
                            if (emailSent) {
                                showPasswordResetDialog(email, selectedRole);
                            } else {
                                showAlert("Error", "Failed to send reset email. Please check email configuration.");
                            }
                        });
                    }).start();
                } else {
                    showAlert("Error", "No account found with this email for " + selectedRole + ".");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Database error: " + e.getMessage());
            }
        }
    }

    private void showPasswordResetDialog(String email, String role) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Password Reset");
        dialog.setHeaderText("📧 Enter verification code and new password");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label codeLabel = new Label("Verification code sent to: " + email);
        codeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60;");

        TextField codeField = new TextField();
        codeField.setPromptText("Enter 6-digit code");
        codeField.setStyle("-fx-font-size: 14px;");


        codeField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal.length() > 6) {
                codeField.setText(old);
            }
            if (!newVal.matches("\\d*")) {
                codeField.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");

        content.getChildren().addAll(
            codeLabel,
            new Label("Verification Code:"),
            codeField,
            new Label("New Password:"),
            newPasswordField,
            new Label("Confirm Password:"),
            confirmPasswordField
        );
        dialog.getDialogPane().setContent(content);

        ButtonType resetButton = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButton, cancelButton);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == resetButton) {
            String code = codeField.getText().trim();
            String newPassword = newPasswordField.getText().trim();
            String confirmPassword = confirmPasswordField.getText().trim();


            if (code.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                showAlert("Error", "All fields are required.");
                showPasswordResetDialog(email, role);
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                showAlert("Error", "Passwords do not match.");
                showPasswordResetDialog(email, role);
                return;
            }

            if (newPassword.length() < 6) {
                showAlert("Error", "Password must be at least 6 characters long.");
                showPasswordResetDialog(email, role);
                return;
            }


            if (EmailService.getInstance().verifyCode(email, code)) {

                String tableName = getTableNameByRole(role);
                try (Connection conn = DBConnector.getInstance().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "UPDATE " + tableName + " SET password=? WHERE email=?"
                     )) {

                    stmt.setString(1, newPassword);
                    stmt.setString(2, email);
                    int rows = stmt.executeUpdate();

                    if (rows > 0) {
                        showAlert("Success", "✅ Password reset successful! You can now login with your new password.");
                    } else {
                        showAlert("Error", "Failed to update password.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showAlert("Error", "Database error: " + e.getMessage());
                }
            } else {
                showAlert("Error", "Invalid or expired verification code.");
                showPasswordResetDialog(email, role);
            }
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void switchToSignup() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("signup.fxml"));
            stage.setScene(new Scene(loader.load(), 400, 550));
            stage.centerOnScreen();
        } catch (Exception e) {
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
