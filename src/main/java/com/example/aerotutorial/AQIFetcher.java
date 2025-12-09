package com.example.aerotutorial;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AQIFetcher {
    private static final String API_KEY = "38cf0daba15b57dd2cb158526e0c8ef4";

    /**
     * Fetch AQI from OpenWeatherMap for given coordinates.
     * @param lat Latitude
     * @param lon Longitude
     * @return AQI as integer (1-500), -1 if failed
     */
    public static int fetchAQI(double lat, double lon) {
        try {
            String urlStr = String.format(
                    "http://api.openweathermap.org/data/2.5/air_pollution?lat=%f&lon=%f&appid=%s",
                    lat, lon, API_KEY
            );

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                System.out.println("API Error: " + conn.getResponseCode());
                return -1;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            // OpenWeatherMap returns AQI in "list[0].main.aqi" (1-5 scale)
            int aqiIndex = json.getJSONArray("list")
                    .getJSONObject(0)
                    .getJSONObject("main")
                    .getInt("aqi");

            // Convert 1-5 scale to approximate 0-500 scale
            switch(aqiIndex) {
                case 1: return 50;   // Good
                case 2: return 100;  // Fair
                case 3: return 150;  // Moderate
                case 4: return 200;  // Poor
                case 5: return 300;  // Very Poor
                default: return -1;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
