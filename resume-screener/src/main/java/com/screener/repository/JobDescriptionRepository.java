package com.screener.repository;

import com.screener.model.JobDescription;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobDescriptionRepository extends MongoRepository<JobDescription, String> {
}
