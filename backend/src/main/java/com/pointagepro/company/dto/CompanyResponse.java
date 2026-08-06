package com.pointagepro.company.dto;

import com.pointagepro.company.entity.Company;
import com.pointagepro.company.entity.CompanySettings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompanyResponse {

    private Long id;
    private String code;
    private String name;
    private String legalName;
    private String taxId;
    private String cnssNumber;
    private String address;
    private String city;
    private String phone;
    private String email;
    private String website;
    private String currency;
    private String logoPath;
    private String statusCode;
    private CompanySettingsResponse settings;

    public static CompanyResponse from(Company c, CompanySettings settings) {
        CompanyResponse dto = new CompanyResponse();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setName(c.getName());
        dto.setLegalName(c.getLegalName());
        dto.setTaxId(c.getTaxId());
        dto.setCnssNumber(c.getCnssNumber());
        dto.setAddress(c.getAddress());
        dto.setCity(c.getCity());
        dto.setPhone(c.getPhone());
        dto.setEmail(c.getEmail());
        dto.setWebsite(c.getWebsite());
        dto.setCurrency(c.getCurrency());
        dto.setLogoPath(c.getLogoPath());
        dto.setStatusCode(c.getStatus() != null ? c.getStatus().getCode() : null);
        dto.setSettings(settings != null ? CompanySettingsResponse.from(settings) : null);
        return dto;
    }
}
