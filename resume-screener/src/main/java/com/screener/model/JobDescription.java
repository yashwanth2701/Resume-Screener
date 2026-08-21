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
@Document(collection = "job_descriptions")
public class JobDescription {

    @Id
    private String id;

    private String title;

    private String description;

    /** Optional explicit list of required skills; if empty, keywords are derived from description. */
    private List<String> requiredSkills;

    @CreatedDate
    private Instant createdAt;
}
