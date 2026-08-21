package com.screener.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Shape of the JSON we instruct the LLM to reply with when scoring a resume
 * against a job description. Kept intentionally small and flat so parsing
 * is reliable.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmMatchResponse {
    private int score;
    private String justification;
    private List<String> matchedSkills;
    private List<String> missingSkills;
}
