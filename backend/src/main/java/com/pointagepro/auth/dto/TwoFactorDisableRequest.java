package com.pointagepro.auth.dto;

public class TwoFactorDisableRequest {
    private String password;
    private String code;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
