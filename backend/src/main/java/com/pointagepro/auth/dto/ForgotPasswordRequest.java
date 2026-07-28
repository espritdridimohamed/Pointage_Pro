package com.pointagepro.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {
    @NotBlank(message = "Email requis")
    @Email(message = "Email invalide")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
