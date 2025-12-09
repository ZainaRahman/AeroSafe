package com.example.aerotutorial;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DBSetup {
    public static void initialize() throws SQLException {
        Connection conn = DBConnector.getInstance().getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT, location TEXT)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS researchers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS admin(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS aq_data(id INTEGER PRIMARY KEY AUTOINCREMENT, city TEXT, date TEXT, pm25 REAL, pm10 REAL, aqi INTEGER)");
            System.out.println("Tables created or already exist.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
