package com.example.aerotutorial;

import java.sql.Connection;
import java.sql.Statement;


public class DatabaseMigration {

    public static void migrateDatabase() {
        System.out.println("🔄 Starting database migration...");

        try (Connection conn = DBConnector.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {


            System.out.println("  → Dropping old researchers table...");
            stmt.executeUpdate("DROP TABLE IF EXISTS researchers");

            System.out.println("  → Dropping old admin table...");
            stmt.executeUpdate("DROP TABLE IF EXISTS admin");


            System.out.println("  → Creating researchers table with correct schema...");
            stmt.executeUpdate("CREATE TABLE researchers(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT)");

            System.out.println("  → Creating admin table with correct schema...");
            stmt.executeUpdate("CREATE TABLE admin(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "location TEXT)");

            System.out.println("✅ Database migration completed successfully!");
            System.out.println("  ✓ researchers table: username, password, location");
            System.out.println("  ✓ admin table: username, password, location");

        } catch (Exception e) {
            System.err.println("❌ Error during database migration:");
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  DATABASE MIGRATION UTILITY");
        System.out.println("========================================");
        migrateDatabase();
        System.out.println("========================================");
        System.out.println("Migration complete. You can now run the app.");
        System.out.println("========================================");
    }
}

