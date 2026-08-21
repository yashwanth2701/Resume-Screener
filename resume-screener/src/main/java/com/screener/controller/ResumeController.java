package com.screener.controller;

import com.screener.model.Resume;
import com.screener.repository.ResumeRepository;
import com.screener.service.ResumeParserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeParserService parserService;
    private final ResumeRepository resumeRepository;

    public ResumeController(ResumeParserService parserService, ResumeRepository resumeRepository) {
        this.parserService = parserService;
        this.resumeRepository = resumeRepository;
    }

    /** Uploads a single resume (PDF or .txt), parses it, extracts structured fields, and stores it. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Resume> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String text = parserService.extractText(file);

        Resume resume = new Resume();
        resume.setFileName(file.getOriginalFilename());
        resume.setRawText(text);
        resume.setCandidateName(parserService.extractName(text));
        resume.setEmail(parserService.extractEmail(text));
        resume.setPhone(parserService.extractPhone(text));
        resume.setSkills(parserService.extractSkills(text));
        resume.setEducation(parserService.extractEducation(text));
        resume.setExperience(parserService.extractExperience(text));
        resume.setUploadedAt(Instant.now());

        Resume saved = resumeRepository.save(resume);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<Resume> listAll() {
        return resumeRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> getOne(@PathVariable String id) {
        return resumeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!resumeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        resumeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
