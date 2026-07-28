package com.pointagepro.auth.dto;

public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private String username;
    private String fullName;
    private String email;
    private String role;
    private boolean twoFactorRequired;
    private String tempToken;

    public AuthResponse(String token, String username, String fullName, String email, String role) {
        this.token = token;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public AuthResponse(String token, String username, String fullName, String email, String role,
                        boolean twoFactorRequired, String tempToken) {
        this.token = token;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.twoFactorRequired = twoFactorRequired;
        this.tempToken = tempToken;
    }

    public String getToken() { return token; }
    public String getType() { return type; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isTwoFactorRequired() { return twoFactorRequired; }
    public String getTempToken() { return tempToken; }
}
