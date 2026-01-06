package com.example.aerotutorial;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DBSetup {
    public static void initialize() throws SQLException {
        Connection conn = DBConnector.getInstance().getConnection();
        try (Statement stmt = conn.createStatement()) {


            boolean needsMigration = checkIfMigrationNeeded(conn);

            if (needsMigration) {
                System.out.println("⚠️ Old database schema detected. Running one-time migration...");
                DatabaseMigration.migrateDatabase();
            }


            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT, " +
                    "email_verified INTEGER DEFAULT 1)");


            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS researchers(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT, " +
                    "email_verified INTEGER DEFAULT 1)");


            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS admin(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT, " +
                    "email_verified INTEGER DEFAULT 1)");


            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS aq_data(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "city TEXT, " +
                    "date TEXT, " +
                    "pm25 REAL, " +
                    "pm10 REAL, " +
                    "aqi INTEGER)");


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


            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS search_history(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "location_name TEXT NOT NULL, " +
                    "latitude REAL NOT NULL, " +
                    "longitude REAL NOT NULL, " +
                    "search_date TEXT NOT NULL, " +
                    "FOREIGN KEY(user_id) REFERENCES users(id))");

            System.out.println("✓ All database tables ready!");
            System.out.println("  - users table");
            System.out.println("  - researchers table");
            System.out.println("  - admin table");
            System.out.println("  - aq_data table");
            System.out.println("  - reports table (with user_id)");
            System.out.println("  - research_data table");
            System.out.println("  - search_history table (user-specific)");
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
