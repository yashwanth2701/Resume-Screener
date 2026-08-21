package com.screener.service;

import com.screener.dto.LlmMatchResponse;
import com.screener.model.JobDescription;
import com.screener.model.MatchResult;
import com.screener.model.Resume;
import com.screener.repository.JobDescriptionRepository;
import com.screener.repository.MatchResultRepository;
import com.screener.repository.ResumeRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class MatchingService {

    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final MatchResultRepository matchResultRepository;
    private final LlmService llmService;

    public MatchingService(ResumeRepository resumeRepository,
                            JobDescriptionRepository jobDescriptionRepository,
                            MatchResultRepository matchResultRepository,
                            LlmService llmService) {
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.matchResultRepository = matchResultRepository;
        this.llmService = llmService;
    }

    /**
     * Scores every requested resume (or all stored resumes if none specified) against
     * the given job description, persists each result, and returns the shortlist
     * sorted by score descending, filtered by an optional minimum score.
     */
    public List<MatchResult> runMatching(String jobDescriptionId, List<String> resumeIds, Integer minScore) {
        JobDescription job = jobDescriptionRepository.findById(jobDescriptionId)
                .orElseThrow(() -> new NoSuchElementException("Job description not found: " + jobDescriptionId));

        List<Resume> resumes = (resumeIds == null || resumeIds.isEmpty())
                ? resumeRepository.findAll()
                : resumeRepository.findAllById(resumeIds);

        List<MatchResult> results = new ArrayList<>();
        int threshold = minScore == null ? 0 : minScore;

        for (Resume resume : resumes) {
            LlmMatchResponse llmResponse = llmService.scoreMatch(resume, job);

            MatchResult match = new MatchResult();
            match.setResumeId(resume.getId());
            match.setJobDescriptionId(job.getId());
            match.setCandidateName(resume.getCandidateName());
            match.setJobTitle(job.getTitle());
            match.setScore(llmResponse.getScore());
            match.setJustification(llmResponse.getJustification());
            match.setMatchedSkills(llmResponse.getMatchedSkills());
            match.setMissingSkills(llmResponse.getMissingSkills());
            match.setCreatedAt(Instant.now());

            MatchResult saved = matchResultRepository.save(match);
            if (saved.getScore() >= threshold) {
                results.add(saved);
            }
        }

        results.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return results;
    }

    public List<MatchResult> getShortlistForJob(String jobDescriptionId) {
        return matchResultRepository.findByJobDescriptionIdOrderByScoreDesc(jobDescriptionId);
    }
}
