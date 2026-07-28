package com.pointagepro.auth.dto;

public class TwoFactorVerifyRequest {
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
