package com.example.aerotutorial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Random;

public class DataFetcher {

    /**
     * Generates simulated AQI data for the last 7 days for a given city
     * and stores it in the aq_data table.
     *
     * @param city Name of the city
     */
    public static void fetchAndStoreAQI(String city) {
        try (Connection conn = DBConnector.getInstance().getConnection()) {
            Random rand = new Random();
            LocalDate today = LocalDate.now();

            for (int i = 7; i >= 1; i--) { // last 7 days
                LocalDate date = today.minusDays(i);

                // Simulated PM2.5 and PM10 values
                int pm25 = 20 + rand.nextInt(80);   // 20–99
                int pm10 = 30 + rand.nextInt(100);  // 30–129

                // Simple AQI calculation: higher of the two
                int aqi = Math.max(pm25, pm10);

                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO aq_data(city, date, pm25, pm10, aqi) VALUES (?, ?, ?, ?, ?)"
                );
                stmt.setString(1, city);
                stmt.setString(2, date.toString());
                stmt.setInt(3, pm25);
                stmt.setInt(4, pm10);
                stmt.setInt(5, aqi);

                stmt.executeUpdate();
            }

            System.out.println("Simulated AQI data stored for city: " + city);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
