package com.screener.repository;

import com.screener.model.Resume;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResumeRepository extends MongoRepository<Resume, String> {
}
