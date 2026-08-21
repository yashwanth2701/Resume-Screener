package com.screener.controller;

import com.screener.dto.JobDescriptionRequest;
import com.screener.model.JobDescription;
import com.screener.repository.JobDescriptionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobDescriptionController {

    private final JobDescriptionRepository jobDescriptionRepository;

    public JobDescriptionController(JobDescriptionRepository jobDescriptionRepository) {
        this.jobDescriptionRepository = jobDescriptionRepository;
    }

    @PostMapping
    public ResponseEntity<JobDescription> create(@Valid @RequestBody JobDescriptionRequest request) {
        JobDescription job = new JobDescription();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequiredSkills(request.getRequiredSkills());
        job.setCreatedAt(Instant.now());

        JobDescription saved = jobDescriptionRepository.save(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<JobDescription> listAll() {
        return jobDescriptionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDescription> getOne(@PathVariable String id) {
        return jobDescriptionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!jobDescriptionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        jobDescriptionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
