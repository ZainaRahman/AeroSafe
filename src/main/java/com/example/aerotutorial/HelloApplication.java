package com.example.aerotutorial;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize database (creates any missing tables without dropping existing ones)
        DBSetup.initialize();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        primaryStage.setScene(new Scene(loader.load(), 400, 400));
        primaryStage.setTitle("AeroSafe Desktop");
        primaryStage.show();
    }


}
