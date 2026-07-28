package com.pointagepro.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyResetCodeRequest {
    @NotBlank(message = "Email requis")
    @Email(message = "Email invalide")
    private String email;

    @NotBlank(message = "Code requis")
    @Size(min = 6, max = 6, message = "Le code doit contenir 6 chiffres")
    private String code;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
