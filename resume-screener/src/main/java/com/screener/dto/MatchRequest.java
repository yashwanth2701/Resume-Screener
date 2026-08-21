package com.screener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class MatchRequest {

    @NotBlank(message = "jobDescriptionId is required")
    private String jobDescriptionId;

    /** Optional: match only these resume ids. If null/empty, all stored resumes are scored. */
    private List<String> resumeIds;

    /** Optional: only return matches with score >= this threshold. Defaults to 0 (all). */
    private Integer minScore;
}
