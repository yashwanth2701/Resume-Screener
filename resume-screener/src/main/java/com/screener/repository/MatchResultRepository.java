package com.screener.repository;

import com.screener.model.MatchResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchResultRepository extends MongoRepository<MatchResult, String> {

    List<MatchResult> findByJobDescriptionIdOrderByScoreDesc(String jobDescriptionId);
}
