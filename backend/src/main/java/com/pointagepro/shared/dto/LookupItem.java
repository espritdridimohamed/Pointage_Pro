package com.pointagepro.shared.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LookupItem {

    private Long id;
    private String code;
    private String label;

    public LookupItem(Long id, String code, String label) {
        this.id = id;
        this.code = code;
        this.label = label;
    }
}
