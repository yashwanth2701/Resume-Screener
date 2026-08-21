package com.screener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.screener.dto.LlmMatchResponse;
import com.screener.model.JobDescription;
import com.screener.model.Resume;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Calls the Google Gemini API to compute a semantic fit score
 * between a candidate resume and a job description.
 */
@Service
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.url}")
    private String apiUrl;

    @Value("${llm.api.model}")
    private String model;

    @Value("${llm.api.max-tokens}")
    private int maxTokens;

    private static final String SYSTEM_PROMPT = """
            You are an expert technical recruiter. You compare a candidate resume against a job
            description and rate how well the candidate fits the role on a scale of 1 to 10.

            Return ONLY a valid JSON object with exactly these fields:
            {
              "score": <integer 1-10>,
              "justification": "<2-4 sentence explanation of the score>",
              "matchedSkills": ["<skills from the resume that satisfy the job requirements>"],
              "missingSkills": ["<important skills the job needs that the resume lacks>"]
            }

            Do not include markdown fences.
            Do not include any text before or after the JSON object.
            """;

    /**
     * Builds the prompt for a single resume/job pair.
     */
    public String buildUserPrompt(Resume resume, JobDescription job) {
        return """
                Compare the following resume with this job description and rate fit on 1-10 with justification.

                JOB TITLE: %s

                JOB DESCRIPTION:
                %s

                REQUIRED SKILLS (if listed): %s

                CANDIDATE RESUME (extracted text):
                %s

                CANDIDATE'S DETECTED SKILLS: %s
                CANDIDATE'S DETECTED EDUCATION: %s
                CANDIDATE'S DETECTED EXPERIENCE NOTES: %s
                """.formatted(
                job.getTitle(),
                job.getDescription(),
                job.getRequiredSkills() == null
                        ? "(none explicitly listed)"
                        : String.join(", ", job.getRequiredSkills()),
                truncate(resume.getRawText(), 6000),
                String.join(", ", resume.getSkills()),
                String.join("; ", resume.getEducation()),
                String.join("; ", resume.getExperience())
        );
    }

    public LlmMatchResponse scoreMatch(Resume resume, JobDescription job) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Add it to your environment variables before starting the app."
            );
        }

        String userPrompt = buildUserPrompt(resume, job);

        /*
         * Gemini request format:
         *
         * {
         *   "system_instruction": {
         *      "parts": [
         *          {"text": "..."}
         *      ]
         *   },
         *   "contents": [
         *      {
         *          "parts": [
         *              {"text": "..."}
         *          ]
         *      }
         *   ],
         *   "generationConfig": {
         *      "responseMimeType": "application/json"
         *   }
         * }
         */

        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(
                        Map.of("text", SYSTEM_PROMPT)
                )
        );

        Map<String, Object> userContent = Map.of(
                "parts", List.of(
                        Map.of("text", userPrompt)
                )
        );

        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "maxOutputTokens", maxTokens
        );

        Map<String, Object> requestBody = Map.of(
                "system_instruction", systemInstruction,
                "contents", List.of(userContent),
                "generationConfig", generationConfig
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(requestBody, headers);

        try {

            Map<?, ?> response =
                    restTemplate.postForObject(
                            apiUrl,
                            entity,
                            Map.class
                    );

            String rawJsonReply = extractTextFromResponse(response);

            return parseLlmJson(rawJsonReply);

        } catch (RestClientException e) {

            throw new IllegalStateException(
                    "Failed to call Gemini API: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Extracts generated text from Gemini response.
     *
     * Gemini response structure:
     *
     * candidates
     *   -> content
     *       -> parts
     *           -> text
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<?, ?> response) {

        if (response == null) {
            throw new IllegalStateException(
                    "Gemini returned an empty response."
            );
        }

        Object candidatesObject = response.get("candidates");

        if (!(candidatesObject instanceof List<?> candidates)
                || candidates.isEmpty()) {

            throw new IllegalStateException(
                    "Gemini response did not contain any candidates: "
                            + response
            );
        }

        Object firstCandidate = candidates.get(0);

        if (!(firstCandidate instanceof Map<?, ?> candidateMap)) {
            throw new IllegalStateException(
                    "Unexpected Gemini candidate format: "
                            + firstCandidate
            );
        }

        Object contentObject = candidateMap.get("content");

        if (!(contentObject instanceof Map<?, ?> contentMap)) {
            throw new IllegalStateException(
                    "Gemini response did not contain content: "
                            + candidateMap
            );
        }

        Object partsObject = contentMap.get("parts");

        if (!(partsObject instanceof List<?> parts)) {
            throw new IllegalStateException(
                    "Gemini response did not contain parts: "
                            + contentMap
            );
        }

        StringBuilder result = new StringBuilder();

        for (Object partObject : parts) {

            if (partObject instanceof Map<?, ?> partMap) {

                Object textObject = partMap.get("text");

                if (textObject != null) {
                    result.append(textObject);
                }
            }
        }

        String text = result.toString().trim();

        if (text.isEmpty()) {
            throw new IllegalStateException(
                    "Gemini returned an empty text response."
            );
        }

        return text;
    }

    /**
     * Converts Gemini JSON text into LlmMatchResponse.
     */
    private LlmMatchResponse parseLlmJson(String rawJsonReply) {

        try {

            String cleaned = rawJsonReply.trim();

            // Remove markdown fences if Gemini ever returns them.
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }

            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(
                        0,
                        cleaned.length() - 3
                );
            }

            cleaned = cleaned.trim();

            // Find the JSON object if there is any accidental text.
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');

            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            JsonNode node = objectMapper.readTree(cleaned);

            return objectMapper.treeToValue(
                    node,
                    LlmMatchResponse.class
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Could not parse Gemini response as JSON: "
                            + rawJsonReply,
                    e
            );
        }
    }

    private String truncate(String text, int maxChars) {

        if (text == null) {
            return "";
        }

        return text.length() <= maxChars
                ? text
                : text.substring(0, maxChars)
                  + "\n[...truncated...]";
    }
}