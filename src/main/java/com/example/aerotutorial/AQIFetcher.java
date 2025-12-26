package com.example.aerotutorial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class AQIFetcher {
    // Load API key securely from config.properties file
    // The API key is NOT hardcoded in the source code!
    private static String getApiKey() {
        return ConfigLoader.getApiKey();
    }

    /**
     * Fetch AQI from OpenWeatherMap for given coordinates.
     * @param lat Latitude
     * @param lon Longitude
     * @return AQI as integer (1-500), -1 if failed
     */
    public static int fetchAQI(double lat, double lon) {
        try {
            String apiKey = getApiKey();
            System.out.println("API Key loaded: " + (apiKey != null && !apiKey.isEmpty() ? "Yes (length: " + apiKey.length() + ")" : "No"));

            String urlStr = String.format(
                    "http://api.openweathermap.org/data/2.5/air_pollution?lat=%f&lon=%f&appid=%s",
                    lat, lon, apiKey
            );

            // Log URL with masked API key for debugging
            String maskedUrl = urlStr.replaceAll("appid=[^&]+", "appid=****");
            System.out.println("Calling API: " + maskedUrl);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("❌ API Error: " + responseCode);
                System.out.println("API URL: " + urlStr);

                // Read error response
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    System.out.println("Error response: " + errorResponse);
                } catch (Exception e) {
                    System.out.println("Could not read error response");
                }
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
            JSONObject listItem = json.getJSONArray("list").getJSONObject(0);
            JSONObject components = listItem.getJSONObject("components");

            // Get all pollutant concentrations from the API
            double pm25 = components.optDouble("pm2_5", 0);
            double pm10 = components.optDouble("pm10", 0);
            double no2 = components.optDouble("no2", 0);
            double o3 = components.optDouble("o3", 0);
            double so2 = components.optDouble("so2", 0);
            double co = components.optDouble("co", 0);

            System.out.println("=== Real API Data ===");
            System.out.println("PM2.5: " + pm25 + " μg/m³, PM10: " + pm10 + " μg/m³");
            System.out.println("NO2: " + no2 + " μg/m³, O3: " + o3 + " μg/m³");
            System.out.println("SO2: " + so2 + " μg/m³, CO: " + co + " μg/m³");

            // Calculate AQI for each pollutant and take the maximum (EPA standard)
            int aqiPM25 = calculateAqiFromPM25(pm25);
            int aqiPM10 = calculateAqiFromPM10(pm10);
            int aqiNO2 = calculateAqiFromNO2(no2);
            int aqiO3 = calculateAqiFromO3(o3);
            int aqiSO2 = calculateAqiFromSO2(so2);
            int aqiCO = calculateAqiFromCO(co);

            System.out.println("=== Calculated Individual AQIs ===");
            System.out.println("PM2.5 AQI: " + aqiPM25 + ", PM10 AQI: " + aqiPM10);
            System.out.println("NO2 AQI: " + aqiNO2 + ", O3 AQI: " + aqiO3);
            System.out.println("SO2 AQI: " + aqiSO2 + ", CO AQI: " + aqiCO);

            // The overall AQI is the maximum of all pollutant AQIs (EPA standard)
            int finalAqi = Math.max(aqiPM25, Math.max(aqiPM10, Math.max(aqiNO2,
                          Math.max(aqiO3, Math.max(aqiSO2, aqiCO)))));

            System.out.println("=== Final AQI: " + finalAqi + " ===");

            return finalAqi > 0 ? finalAqi : -1;

        } catch (Exception e) {
            System.out.println("Error fetching AQI: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Fetch historical AQI data for the last X days
     * @param lat Latitude
     * @param lon Longitude
     * @param days Number of days to fetch (e.g., 7 for last week)
     * @return Map of LocalDate to AQI values, ordered from oldest to newest
     */
    public static Map<LocalDate, Integer> fetchHistoricalAQI(double lat, double lon, int days) {
        Map<LocalDate, Integer> history = new LinkedHashMap<>();

        try {
            // Calculate Unix timestamps for date range
            long endTime = System.currentTimeMillis() / 1000; // Now (in seconds)
            long startTime = endTime - ((long)days * 24 * 60 * 60); // X days ago

            String apiKey = getApiKey();
            System.out.println("Historical API - API Key loaded: " + (apiKey != null && !apiKey.isEmpty() ? "Yes" : "No"));

            String urlStr = String.format(
                "http://api.openweathermap.org/data/2.5/air_pollution/history?lat=%f&lon=%f&start=%d&end=%d&appid=%s",
                lat, lon, startTime, endTime, apiKey
            );

            String maskedUrl = urlStr.replaceAll("appid=[^&]+", "appid=****");
            System.out.println("Fetching historical data from: " + maskedUrl);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("❌ Historical API Error: " + responseCode);

                // Read error response
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    System.out.println("Error response: " + errorResponse);
                } catch (Exception e) {
                    System.out.println("Could not read error response");
                }
                return history;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            JSONArray list = json.getJSONArray("list");

            System.out.println("Received " + list.length() + " data points from historical API");

            // Group by date and collect AQI values per day
            Map<LocalDate, List<Integer>> dailyData = new HashMap<>();

            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                long timestamp = item.getLong("dt");
                LocalDate date = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

                JSONObject components = item.getJSONObject("components");
                int aqi = calculateOverallAQI(components);

                dailyData.computeIfAbsent(date, k -> new ArrayList<>()).add(aqi);
            }

            // Average AQI per day and sort by date
            List<LocalDate> sortedDates = new ArrayList<>(dailyData.keySet());
            Collections.sort(sortedDates);

            for (LocalDate date : sortedDates) {
                List<Integer> aqiValues = dailyData.get(date);
                int avgAqi = (int) aqiValues.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);
                history.put(date, avgAqi);
                System.out.println("  " + date + ": AQI " + avgAqi + " (avg of " + aqiValues.size() + " readings)");
            }

            System.out.println("✅ Fetched " + history.size() + " days of real historical data");

        } catch (Exception e) {
            System.out.println("Error fetching historical AQI: " + e.getMessage());
            e.printStackTrace();
        }

        return history;
    }

    /**
     * Calculate overall AQI from all pollutant components
     * @param components JSONObject containing all pollutant concentrations
     * @return Overall AQI (maximum of all individual pollutant AQIs)
     */
    private static int calculateOverallAQI(JSONObject components) {
        double pm25 = components.optDouble("pm2_5", 0);
        double pm10 = components.optDouble("pm10", 0);
        double no2 = components.optDouble("no2", 0);
        double o3 = components.optDouble("o3", 0);
        double so2 = components.optDouble("so2", 0);
        double co = components.optDouble("co", 0);

        int aqiPM25 = calculateAqiFromPM25(pm25);
        int aqiPM10 = calculateAqiFromPM10(pm10);
        int aqiNO2 = calculateAqiFromNO2(no2);
        int aqiO3 = calculateAqiFromO3(o3);
        int aqiSO2 = calculateAqiFromSO2(so2);
        int aqiCO = calculateAqiFromCO(co);

        return Math.max(aqiPM25,
               Math.max(aqiPM10,
               Math.max(aqiNO2,
               Math.max(aqiO3,
               Math.max(aqiSO2, aqiCO)))));
    }

    /**
     * Calculate AQI from PM2.5 concentration using EPA formula
     * @param pm25 PM2.5 concentration in μg/m³
     * @return AQI value
     */
    public static int calculateAqiFromPM25(double pm25) {
        if (pm25 < 0) return 0;

        // EPA AQI breakpoints for PM2.5 (24-hour average)
        if (pm25 <= 12.0) {
            return (int) linearScale(pm25, 0, 12.0, 0, 50);
        } else if (pm25 <= 35.4) {

            return (int) linearScale(pm25, 12.1, 35.4, 51, 100);
        } else if (pm25 <= 55.4) {
            return (int) linearScale(pm25, 35.5, 55.4, 101, 150);
        } else if (pm25 <= 150.4) {
            return (int) linearScale(pm25, 55.5, 150.4, 151, 200);
        } else if (pm25 <= 250.4) {
            return (int) linearScale(pm25, 150.5, 250.4, 201, 300);
        } else if (pm25 <= 350.4) {
            return (int) linearScale(pm25, 250.5, 350.4, 301, 400);
        } else if (pm25 <= 500.4) {
            return (int) linearScale(pm25, 350.5, 500.4, 401, 500);
        } else {
            return 500; // Max AQI
        }
    }

    /**
     * Calculate AQI from PM10 concentration using EPA formula
     * @param pm10 PM10 concentration in μg/m³
     * @return AQI value
     */
    public static int calculateAqiFromPM10(double pm10) {
        if (pm10 < 0) return 0;

        // EPA AQI breakpoints for PM10 (24-hour average)
        if (pm10 <= 54) {
            return (int) linearScale(pm10, 0, 54, 0, 50);
        } else if (pm10 <= 154) {
            return (int) linearScale(pm10, 55, 154, 51, 100);
        } else if (pm10 <= 254) {
            return (int) linearScale(pm10, 155, 254, 101, 150);
        } else if (pm10 <= 354) {
            return (int) linearScale(pm10, 255, 354, 151, 200);
        } else if (pm10 <= 424) {
            return (int) linearScale(pm10, 355, 424, 201, 300);
        } else if (pm10 <= 504) {
            return (int) linearScale(pm10, 425, 504, 301, 400);
        } else if (pm10 <= 604) {
            return (int) linearScale(pm10, 505, 604, 401, 500);
        } else {
            return 500;
        }
    }

    /**
     * Calculate AQI from NO2 concentration
     * @param no2 NO2 concentration in μg/m³
     * @return AQI value
     */
    public static int calculateAqiFromNO2(double no2) {
        if (no2 < 0) return 0;

        // Convert μg/m³ to ppb (at 25°C and 1 atm): ppb = μg/m³ × 0.5319
        double no2_ppb = no2 * 0.5319;

        // EPA AQI breakpoints for NO2 (1-hour average in ppb)
        if (no2_ppb <= 53) {
            return (int) linearScale(no2_ppb, 0, 53, 0, 50);
        } else if (no2_ppb <= 100) {
            return (int) linearScale(no2_ppb, 54, 100, 51, 100);
        } else if (no2_ppb <= 360) {
            return (int) linearScale(no2_ppb, 101, 360, 101, 150);
        } else if (no2_ppb <= 649) {
            return (int) linearScale(no2_ppb, 361, 649, 151, 200);
        } else if (no2_ppb <= 1249) {
            return (int) linearScale(no2_ppb, 650, 1249, 201, 300);
        } else if (no2_ppb <= 1649) {
            return (int) linearScale(no2_ppb, 1250, 1649, 301, 400);
        } else if (no2_ppb <= 2049) {
            return (int) linearScale(no2_ppb, 1650, 2049, 401, 500);
        } else {
            return 500;
        }
    }

    /**
     * Calculate AQI from O3 (Ozone) concentration
     * @param o3 O3 concentration in μg/m³
     * @return AQI value
     */
    public static int calculateAqiFromO3(double o3) {
        if (o3 < 0) return 0;

        // Convert μg/m³ to ppb: ppb = μg/m³ × 0.5087
        double o3_ppb = o3 * 0.5087;

        // EPA AQI breakpoints for O3 (8-hour average in ppb)
        if (o3_ppb <= 54) {
            return (int) linearScale(o3_ppb, 0, 54, 0, 50);
        } else if (o3_ppb <= 70) {
            return (int) linearScale(o3_ppb, 55, 70, 51, 100);
        } else if (o3_ppb <= 85) {
            return (int) linearScale(o3_ppb, 71, 85, 101, 150);
        } else if (o3_ppb <= 105) {
            return (int) linearScale(o3_ppb, 86, 105, 151, 200);
        } else if (o3_ppb <= 200) {
            return (int) linearScale(o3_ppb, 106, 200, 201, 300);
        } else {
            return 300; // Max for 8-hour O3
        }
    }

    /**
     * Calculate AQI from SO2 concentration
     * @param so2 SO2 concentration in μg/m³
     * @return AQI value
     */
    public static int calculateAqiFromSO2(double so2) {
        if (so2 < 0) return 0;

        // Convert μg/m³ to ppb: ppb = μg/m³ × 0.3817
        double so2_ppb = so2 * 0.3817;

        // EPA AQI breakpoints for SO2 (1-hour average in ppb)
        if (so2_ppb <= 35) {
            return (int) linearScale(so2_ppb, 0, 35, 0, 50);
        } else if (so2_ppb <= 75) {
            return (int) linearScale(so2_ppb, 36, 75, 51, 100);
        } else if (so2_ppb <= 185) {
            return (int) linearScale(so2_ppb, 76, 185, 101, 150);
        } else if (so2_ppb <= 304) {
            return (int) linearScale(so2_ppb, 186, 304, 151, 200);
        } else if (so2_ppb <= 604) {
            return (int) linearScale(so2_ppb, 305, 604, 201, 300);
        } else if (so2_ppb <= 804) {
            return (int) linearScale(so2_ppb, 605, 804, 301, 400);
        } else if (so2_ppb <= 1004) {
            return (int) linearScale(so2_ppb, 805, 1004, 401, 500);
        } else {
            return 500;
        }
    }

    /**
     * Calculate AQI from CO concentration
     * @param co CO concentration in μg/m³
     * @return AQI value
     */
    public static int calculateAqiFromCO(double co) {
        if (co < 0) return 0;

        // Convert μg/m³ to ppm: ppm = μg/m³ × 0.000873
        double co_ppm = co * 0.000873;

        // EPA AQI breakpoints for CO (8-hour average in ppm)
        if (co_ppm <= 4.4) {
            return (int) linearScale(co_ppm, 0, 4.4, 0, 50);
        } else if (co_ppm <= 9.4) {
            return (int) linearScale(co_ppm, 4.5, 9.4, 51, 100);
        } else if (co_ppm <= 12.4) {
            return (int) linearScale(co_ppm, 9.5, 12.4, 101, 150);
        } else if (co_ppm <= 15.4) {
            return (int) linearScale(co_ppm, 12.5, 15.4, 151, 200);
        } else if (co_ppm <= 30.4) {
            return (int) linearScale(co_ppm, 15.5, 30.4, 201, 300);
        } else if (co_ppm <= 40.4) {
            return (int) linearScale(co_ppm, 30.5, 40.4, 301, 400);
        } else if (co_ppm <= 50.4) {
            return (int) linearScale(co_ppm, 40.5, 50.4, 401, 500);
        } else {
            return 500;
        }
    }

    /**
     * Linear interpolation helper
     */
    private static double linearScale(double value, double inLow, double inHigh, double outLow, double outHigh) {
        return ((value - inLow) / (inHigh - inLow)) * (outHigh - outLow) + outLow;
    }
}
