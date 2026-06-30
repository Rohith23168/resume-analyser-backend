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
    @CollectionTable(name = "previous_table_skills", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "skills", length = 450)
    private List<String> skills;

    @ElementCollection
    @CollectionTable(name = "previous_table_missing_skills", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "missing_skills", length = 450)
    private List<String> missingSkills;

    @ElementCollection
    @CollectionTable(name = "previous_table_strengths", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "strengths", length = 450)
    private List<String> strengths;

    @ElementCollection
    @CollectionTable(name = "previous_table_weaknesses", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "weaknesses", length = 450)
    private List<String> weaknesses;

    @ElementCollection
    @CollectionTable(name = "previous_table_interview_tips", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "interview_tips", length = 450)
    private List<String> interviewTips;

    @ElementCollection
    @CollectionTable(name = "previous_table_pros", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "pros", length = 450)
    private List<String> pros;

    @ElementCollection
    @CollectionTable(name = "previous_table_cons", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "cons", length = 450)
    private List<String> cons;

    @ElementCollection
    @CollectionTable(name = "previous_table_suggestions", joinColumns = @JoinColumn(name = "previous_table_email"))
    @Column(name = "suggestions", length = 450)
    private List<String> suggestions;

}