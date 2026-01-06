package com.example.aerotutorial;


public class SessionManager {
    private static SessionManager instance;

    private int userId;
    private String username;
    private String userRole;
    private String userLocation;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }


    public void setUserSession(int userId, String username, String role, String location) {
        this.userId = userId;
        this.username = username;
        this.userRole = role;
        this.userLocation = location;
        System.out.println("✓ Session created for: " + username + " (ID: " + userId + ", Role: " + role + ")");
    }


    public void clearSession() {
        System.out.println("✓ Session cleared for: " + username);
        this.userId = 0;
        this.username = null;
        this.userRole = null;
        this.userLocation = null;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getUserRole() {
        return userRole;
    }

    public String getUserLocation() {
        return userLocation;
    }

    public boolean isLoggedIn() {
        return userId > 0 && username != null;
    }
}

