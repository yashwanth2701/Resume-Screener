package com.screener.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "match_results")
public class MatchResult {

    @Id
    private String id;

    private String resumeId;
    private String jobDescriptionId;

    private String candidateName;
    private String jobTitle;

    /** 1-10 fit score returned by the LLM. */
    private int score;

    /** Short natural-language justification returned by the LLM. */
    private String justification;

    /** Skills the LLM flagged as matching the job requirements. */
    private List<String> matchedSkills;

    /** Skills the LLM flagged as missing/gaps. */
    private List<String> missingSkills;

    @CreatedDate
    private Instant createdAt;
}
