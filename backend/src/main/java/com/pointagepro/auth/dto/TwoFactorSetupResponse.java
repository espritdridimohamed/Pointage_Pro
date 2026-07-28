package com.pointagepro.auth.dto;

public class TwoFactorSetupResponse {
    private String secret;
    private String otpauthUri;

    public TwoFactorSetupResponse(String secret, String otpauthUri) {
        this.secret = secret;
        this.otpauthUri = otpauthUri;
    }

    public String getSecret() { return secret; }
    public String getOtpauthUri() { return otpauthUri; }
}
