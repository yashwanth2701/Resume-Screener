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
@Document(collection = "resumes")
public class Resume {

    @Id
    private String id;

    private String fileName;

    /** Full raw text extracted from the uploaded PDF/text file. */
    private String rawText;

    // ---- Structured fields extracted from rawText ----
    private String candidateName;
    private String email;
    private String phone;
    private List<String> skills;
    private List<String> experience;   // short bullet-style lines describing roles/years
    private List<String> education;    // degrees / institutions found in the resume

    @CreatedDate
    private Instant uploadedAt;
}
