package com.example.aerotutorial;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        DBSetup.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        primaryStage.setScene(new Scene(loader.load(), 400, 300));
        primaryStage.setTitle("AeroSafe Desktop");
        primaryStage.show();
    }


}
