package com.example.aerotutorial;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnector {
    private static DBConnector instance;
    private Connection connection;
    private final String url = "jdbc:sqlite:aerosafe.db";

    private DBConnector() {
        try {
            connection = DriverManager.getConnection(url);
            System.out.println("SQLite connected.");
            System.out.println("SQLite DB at: " + new java.io.File("aerosafe.db").getAbsolutePath());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static DBConnector getInstance() {
        if (instance == null) {
            instance = new DBConnector();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
