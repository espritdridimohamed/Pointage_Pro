package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class BankAccountResponse {

    private Long id;

    private Long bankId;

    private String bankCode;

    private String bankName;

    private String accountNumber;

    private String iban;

    private String accountHolder;

    private Boolean isDefault;

    private LocalDate validFrom;

    private LocalDate validTo;
}
