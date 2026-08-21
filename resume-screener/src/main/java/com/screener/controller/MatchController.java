package com.screener.controller;

import com.screener.dto.MatchRequest;
import com.screener.model.MatchResult;
import com.screener.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final MatchingService matchingService;

    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /** Runs LLM-based scoring for a job against a set of resumes (or all resumes) and returns the shortlist. */
    @PostMapping
    public ResponseEntity<List<MatchResult>> runMatching(@Valid @RequestBody MatchRequest request) {
        List<MatchResult> shortlist = matchingService.runMatching(
                request.getJobDescriptionId(), request.getResumeIds(), request.getMinScore());
        return ResponseEntity.ok(shortlist);
    }

    /** Retrieves the previously computed shortlist for a job, sorted by score descending. */
    @GetMapping("/job/{jobDescriptionId}")
    public ResponseEntity<List<MatchResult>> getShortlist(@PathVariable String jobDescriptionId) {
        return ResponseEntity.ok(matchingService.getShortlistForJob(jobDescriptionId));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleLlmError(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
