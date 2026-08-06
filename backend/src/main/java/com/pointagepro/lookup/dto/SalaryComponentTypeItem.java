package com.pointagepro.lookup.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SalaryComponentTypeItem {

    private String code;
    private String label;
    private String category;

    public SalaryComponentTypeItem(String code, String label, String category) {
        this.code = code;
        this.label = label;
        this.category = category;
    }
}
