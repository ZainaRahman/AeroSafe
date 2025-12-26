package com.example.aerotutorial;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DBSetup {
    public static void initialize() throws SQLException {
        Connection conn = DBConnector.getInstance().getConnection();
        try (Statement stmt = conn.createStatement()) {

            // Check if tables need migration (have old schema with 'name' and 'email' columns)
            boolean needsMigration = checkIfMigrationNeeded(conn);

            if (needsMigration) {
                System.out.println("⚠️ Old database schema detected. Running one-time migration...");
                DatabaseMigration.migrateDatabase();
            }

            // Create users table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT)");

            // Create researchers table with same structure
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS researchers(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT)");

            // Create admin table with same structure
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS admin(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT)");

            // Create air quality data table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS aq_data(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "city TEXT, " +
                    "date TEXT, " +
                    "pm25 REAL, " +
                    "pm10 REAL, " +
                    "aqi INTEGER)");

            // Create reports table for user-submitted environmental issues
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS reports(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "reporter_name TEXT NOT NULL, " +
                    "location TEXT NOT NULL, " +
                    "issue_type TEXT NOT NULL, " +
                    "severity TEXT NOT NULL, " +
                    "aqi_value TEXT, " +
                    "description TEXT NOT NULL, " +
                    "contact TEXT, " +
                    "status TEXT DEFAULT 'Pending', " +
                    "submitted_date TEXT NOT NULL)");

            // Create research data table for researcher dashboard
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS research_data(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp TEXT, " +
                    "location TEXT, " +
                    "pm25 REAL, " +
                    "pm10 REAL, " +
                    "no2 REAL, " +
                    "o3 REAL, " +
                    "so2 REAL, " +
                    "co REAL)");

            System.out.println("✓ All database tables ready!");
            System.out.println("  - users table");
            System.out.println("  - researchers table");
            System.out.println("  - admin table");
            System.out.println("  - aq_data table");
            System.out.println("  - reports table");
            System.out.println("  - research_data table");
        } catch (Exception e) {
            System.err.println("❌ Error creating database tables:");
            e.printStackTrace();
        }
    }

    /**
     * Check if database needs migration by detecting old schema
     * Returns true if researchers/admin tables exist with old columns (name, email)
     */
    private static boolean checkIfMigrationNeeded(Connection conn) {
        try {
            // Try to select from researchers table with old column 'name'
            var stmt = conn.createStatement();
            stmt.executeQuery("SELECT name FROM researchers LIMIT 1");
            // If this succeeds, table has old schema
            System.out.println("  ⚠️ Old 'researchers' table schema detected");
            return true;
        } catch (SQLException e) {
            // If error contains "no such table" or "no such column", migration not needed
            if (e.getMessage().contains("no such table") ||
                e.getMessage().contains("no such column") ||
                e.getMessage().contains("has no column named name")) {
                // Table either doesn't exist or has correct schema
                return false;
            }
            // Some other error
            return false;
        }
    }
}
