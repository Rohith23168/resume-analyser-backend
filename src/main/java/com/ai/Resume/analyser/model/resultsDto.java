package com.ai.Resume.analyser.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class resultsDto {

    private int score;
    private int atsoptimizationscore;
    private String summary;
    private String experienceLevel;
    private List<String> skills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> interviewTips;
    private List<String> pros;
    private List<String> cons;
    private List<String> suggestions;
    private List<Job> jobs;

}