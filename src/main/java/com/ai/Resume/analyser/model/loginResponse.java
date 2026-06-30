package com.ai.Resume.analyser.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class loginResponse {
    private String username;
    private Boolean isPrevious;
    private String token;

    public loginResponse(String username, Boolean isPrevious) {
        this.username = username;
        this.isPrevious = isPrevious;
    }
}