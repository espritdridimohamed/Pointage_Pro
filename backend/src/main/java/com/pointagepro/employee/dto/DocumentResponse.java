package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DocumentResponse {

    private Long id;

    private Long documentTypeId;

    private String documentType;

    private String filePath;

    private String documentNumber;

    private LocalDate issueDate;

    private LocalDate expiryDate;

    private String notes;

    private LocalDateTime createdAt;
}
