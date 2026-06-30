package com.ai.Resume.analyser.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity

public class previousTable {

    @Id
    private String email;

    private int score;
    private int atsoptimizationscore;
    private String roles;

    @Column(length = 1000)
    private String summary;

    private String experienceLevel;

    @ElementCollection
    @Column(length = 450)
    private List<String> skills;

    @ElementCollection
    @Column(length = 450)
    private List<String> missingSkills;

    @ElementCollection
    @Column(length = 450)
    private List<String> strengths;

    @ElementCollection
    @Column(length = 450)
    private List<String> weaknesses;

    @ElementCollection
    @Column(length = 450)
    private List<String> interviewTips;

    @ElementCollection
    @Column(length = 450)
    private List<String> pros;

    @ElementCollection
    @Column(length = 450)
    private List<String> cons;

    @ElementCollection
    @Column(length = 450)
    private List<String> suggestions;

}