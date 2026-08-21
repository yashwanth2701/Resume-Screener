package com.screener.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts raw text from an uploaded resume file (PDF or plain text) and then
 * derives structured fields from that text using a keyword dictionary plus a
 * small set of regex heuristics. This is intentionally rule-based (not the LLM)
 * so structured extraction is fast, free, and deterministic; the LLM is reserved
 * for the harder semantic-matching step in {@link LlmService}.
 */
@Service
public class ResumeParserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3,4}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}");

    private static final Pattern YEARS_EXPERIENCE_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:years|yrs)\\s+(?:of\\s+)?experience",
                    Pattern.CASE_INSENSITIVE);

    private static final List<String> EDUCATION_KEYWORDS = List.of(
            "bachelor", "master", "b.tech", "m.tech", "b.e", "m.e", "bsc", "msc",
            "b.sc", "m.sc", "mba", "phd", "ph.d", "university", "college",
            "institute of technology", "diploma", "b.com", "m.com"
    );

    private static final List<String> EXPERIENCE_SECTION_HEADERS = List.of(
            "experience", "work experience", "professional experience", "employment history"
    );

    private static final List<String> EDUCATION_SECTION_HEADERS = List.of(
            "education", "academic background", "qualifications"
    );

    /** Reasonably broad dictionary of common technical/professional skills to match against. */
    private static final List<String> SKILLS_DICTIONARY = List.of(
            // Languages
            "java", "python", "javascript", "typescript", "c++", "c#", "go", "golang", "rust",
            "kotlin", "swift", "php", "ruby", "scala", "r", "matlab", "sql", "html", "css",
            // Frameworks / libraries
            "spring", "spring boot", "hibernate", "react", "angular", "vue", "node.js", "nodejs",
            "express", "django", "flask", "fastapi", ".net", "asp.net", "next.js", "redux",
            // Data / ML
            "machine learning", "deep learning", "nlp", "computer vision", "tensorflow",
            "pytorch", "scikit-learn", "pandas", "numpy", "keras", "data analysis",
            "data science", "llm", "generative ai", "prompt engineering",
            // Databases
            "mongodb", "mysql", "postgresql", "oracle", "redis", "cassandra", "dynamodb",
            "elasticsearch", "sqlite", "mariadb",
            // Cloud / DevOps
            "aws", "azure", "gcp", "docker", "kubernetes", "jenkins", "terraform", "ansible",
            "ci/cd", "git", "github", "gitlab", "linux", "bash", "microservices", "rest api",
            "graphql", "kafka", "rabbitmq", "nginx",
            // Testing / other
            "junit", "selenium", "agile", "scrum", "jira", "figma", "tableau", "power bi",
            "excel", "communication", "leadership", "project management"
    );

    /** Extracts raw text from a multipart resume file (.pdf or .txt). */
    public String extractText(MultipartFile file) throws IOException {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".pdf")) {
                try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(in.readAllBytes())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            }
            // Fall back to plain text for .txt or unknown extensions
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String extractEmail(String text) {
        Matcher m = EMAIL_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    public String extractPhone(String text) {
        Matcher m = PHONE_PATTERN.matcher(text);
        while (m.find()) {
            String candidate = m.group().replaceAll("[^0-9+]", "");
            if (candidate.length() >= 7) {
                return m.group().trim();
            }
        }
        return null;
    }

    /** Heuristic: the first non-blank line that isn't an email/phone is treated as the candidate name. */
    public String extractName(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (EMAIL_PATTERN.matcher(trimmed).find()) continue;
            if (trimmed.matches(".*\\d{3,}.*") && trimmed.length() < 20) continue; // looks like a phone number
            if (trimmed.length() > 60) continue; // unlikely to be just a name
            return trimmed;
        }
        return "Unknown Candidate";
    }

    public List<String> extractSkills(String text) {
        String lower = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String skill : SKILLS_DICTIONARY) {
            if (containsSkill(lower, skill)) {
                found.add(skill);
            }
        }
        return found;
    }

    private boolean containsSkill(String lowerText, String skill) {
        // Use word-boundary matching for alphanumeric skills; plain contains for ones with symbols (c++, .net, ci/cd)
        if (skill.matches("[a-z0-9 ]+")) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b");
            return p.matcher(lowerText).find();
        }
        return lowerText.contains(skill);
    }

    public List<String> extractEducation(String text) {
        return extractSectionLines(text, EDUCATION_SECTION_HEADERS, EDUCATION_KEYWORDS);
    }

    public List<String> extractExperience(String text) {
        List<String> lines = extractSectionLines(text, EXPERIENCE_SECTION_HEADERS, List.of());
        // Also capture an explicit "N years of experience" mention if present, since it's
        // useful signal even when it falls outside a well-labeled "Experience" section.
        Matcher m = YEARS_EXPERIENCE_PATTERN.matcher(text);
        if (m.find()) {
            String summary = m.group().trim();
            if (!lines.contains(summary)) {
                lines.add(0, summary);
            }
        }
        return lines;
    }

    /**
     * Generic section extractor: finds a line matching one of the section headers,
     * then collects subsequent non-blank lines until the next likely section header
     * (a short, capitalized-looking line) or a keyword match, whichever applies.
     */
    private List<String> extractSectionLines(String text, List<String> sectionHeaders, List<String> keywordFallback) {
        String[] lines = text.split("\\r?\\n");
        List<String> results = new ArrayList<>();
        boolean inSection = false;
        int collected = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lowerLine = line.toLowerCase();

            boolean isHeader = sectionHeaders.stream().anyMatch(h -> lowerLine.equals(h) || lowerLine.startsWith(h + ":"));
            if (isHeader) {
                inSection = true;
                continue;
            }

            if (inSection) {
                boolean looksLikeNewSection = line.length() < 40 && line.equals(line.toUpperCase()) && line.length() > 2;
                if (looksLikeNewSection || collected >= 8) {
                    inSection = false;
                    continue;
                }
                results.add(line);
                collected++;
            }
        }

        // Fallback: if no explicit section was found, grab lines containing keywords (education case).
        if (results.isEmpty() && !keywordFallback.isEmpty()) {
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                String lowerLine = line.toLowerCase();
                if (keywordFallback.stream().anyMatch(lowerLine::contains)) {
                    results.add(line);
                }
            }
        }

        return results;
    }
}
