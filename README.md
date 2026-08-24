# Screenline — Smart Resume Screener

Parses resumes (PDF/text), extracts structured candidate data, and uses an LLM to
score each candidate's fit against a job description with a written justification.

**Stack:** Spring Boot (Java) · MongoDB · plain HTML/CSS/JS dashboard · Anthropic Claude API

---

## 1. Architecture

```
frontend/ (HTML/CSS/JS)          Backend (Spring Boot)                 Data / AI
┌───────────────────┐            ┌───────────────────────────┐        ┌────────────┐
│ index.html         │  fetch()  │ ResumeController            │        │            │
│ style.css           │ ───────► │ JobDescriptionController    │──────► │  MongoDB   │
│ script.js           │ ◄─────── │ MatchController              │        │            │
└───────────────────┘            │                              │        └────────────┘
                                   │  ResumeParserService (PDFBox)│
                                   │  MatchingService              │──────► ┌────────────┐
                                   │  LlmService                    │       │ Anthropic  │
                                   └───────────────────────────┘       │ Claude API │
                                                                        └────────────┘
```

**Flow:**
1. User uploads a resume → `ResumeParserService` extracts raw text (PDFBox for PDF, plain read for `.txt`), then derives `candidateName`, `email`, `phone`, `skills`, `education`, `experience` using a keyword dictionary + regex heuristics. The `Resume` document is saved to MongoDB.
2. User saves a job description (title, description, optional required skills) → stored as a `JobDescription` document.
3. User clicks **Run matching** → `MatchingService` loads the job and the selected (or all) resumes, and for each pair calls `LlmService`, which sends the resume text + extracted fields + job description to Claude with a strict-JSON system prompt, asking for a 1–10 score, a justification, matched skills, and missing skills.
4. Each response is parsed and saved as a `MatchResult` document, and the shortlist is returned to the dashboard sorted by score, descending.

### Why rule-based extraction *and* an LLM?
Structured field extraction (name/email/skills/education) is deterministic and cheap, so it's done in Java with regex + a skills dictionary — no need to spend LLM tokens on it. The **semantic fit scoring** (does this candidate's actual experience match what the job needs, beyond just keyword overlap) is the part that genuinely benefits from an LLM's judgment, so that's the only step that calls out to Claude.

---

## 2. Project structure

```
resume-screener/
├── pom.xml
├── src/main/java/com/screener/
│   ├── ResumeScreenerApplication.java
│   ├── config/CorsConfig.java
│   ├── controller/
│   │   ├── ResumeController.java
│   │   ├── JobDescriptionController.java
│   │   └── MatchController.java
│   ├── dto/
│   │   ├── JobDescriptionRequest.java
│   │   ├── MatchRequest.java
│   │   └── LlmMatchResponse.java
│   ├── model/
│   │   ├── Resume.java
│   │   ├── JobDescription.java
│   │   └── MatchResult.java
│   ├── repository/
│   │   ├── ResumeRepository.java
│   │   ├── JobDescriptionRepository.java
│   │   └── MatchResultRepository.java
│   └── service/
│       ├── ResumeParserService.java   (PDFBox + regex/keyword extraction)
│       ├── LlmService.java            (Anthropic API integration)
│       └── MatchingService.java       (orchestration)
├── src/main/resources/application.properties
└── frontend/
    ├── index.html
    ├── style.css
    └── script.js
```

---

## 3. Setup

### Prerequisites
- Java 17+
- Maven 3.9+
- MongoDB running locally (or a connection string to a hosted instance, e.g. Atlas)
- An Anthropic API key (get one at https://console.anthropic.com)

### Run MongoDB (local, via Docker)
```bash
docker run -d -p 27017:27017 --name resume-screener-mongo mongo:7
```

### Configure environment variables
```bash
export MONGODB_URI="mongodb://localhost:27017/resume_screener"
export ANTHROPIC_API_KEY="sk-ant-xxxxx"
```

### Run the backend
```bash
cd resume-screener
mvn spring-boot:run
```
The API starts on `http://localhost:8080`.

### Run the frontend
The `frontend/` folder is static — no build step. Simplest option:
```bash
cd frontend
python3 -m http.server 5500
```
Then open `http://localhost:5500`. (CORS is already enabled on the backend for `/api/**`.)

---

## 4. API reference

| Method | Endpoint                          | Purpose                                      |
|--------|------------------------------------|-----------------------------------------------|
| POST   | `/api/resumes/upload`             | Upload + parse a resume (`multipart/form-data`, field `file`) |
| GET    | `/api/resumes`                    | List all parsed resumes                      |
| GET    | `/api/resumes/{id}`               | Get one resume                               |
| DELETE | `/api/resumes/{id}`               | Delete a resume                              |
| POST   | `/api/jobs`                       | Create a job description                     |
| GET    | `/api/jobs`                       | List job descriptions                        |
| POST   | `/api/match`                      | Score resume(s) against a job (see below)    |
| GET    | `/api/match/job/{jobDescriptionId}` | Get the stored shortlist for a job        |

**`POST /api/match` body:**
```json
{
  "jobDescriptionId": "66f...",
  "resumeIds": ["66a...", "66b..."],   // optional — omit/empty to match ALL stored resumes
  "minScore": 5                          // optional — filter out low-scoring matches
}
```

---

## 5. LLM prompt design

**System prompt** (fixed, instructs strict JSON output):
```
You are an expert technical recruiter. You compare a candidate resume against a job
description and rate how well the candidate fits the role on a scale of 1 to 10.
Respond with ONLY a single valid JSON object and nothing else (no markdown fences,
no commentary). The JSON object must have exactly these fields:
{
  "score": <integer 1-10>,
  "justification": "<2-4 sentence explanation of the score>",
  "matchedSkills": ["<skills from the resume that satisfy the job requirements>"],
  "missingSkills": ["<important skills the job needs that the resume lacks>"]
}
```

**User prompt template** (per the brief's example — *"Compare the following resume with
this job description and rate fit on 1–10 with justification"*), filled in per candidate:
```
Compare the following resume with this job description and rate fit on 1-10 with justification.

JOB TITLE: {job.title}

JOB DESCRIPTION:
{job.description}

REQUIRED SKILLS (if listed): {job.requiredSkills}

CANDIDATE RESUME (extracted text):
{resume.rawText}

CANDIDATE'S DETECTED SKILLS: {resume.skills}
CANDIDATE'S DETECTED EDUCATION: {resume.education}
CANDIDATE'S DETECTED EXPERIENCE NOTES: {resume.experience}
```

Passing both the raw resume text *and* the pre-extracted fields gives the model full
context while nudging it to pay attention to the structured signals a recruiter would
actually check first.

The response is parsed defensively in `LlmService.parseLlmJson()`: it strips markdown
code fences if present and trims to the outermost `{ ... }` before calling
`Jackson.readTree`, so minor formatting drift from the model doesn't break parsing.

---

## 6. Extraction heuristics (`ResumeParserService`)

- **Email** — standard email regex.
- **Phone** — regex tolerant of `+country`, dashes, spaces, parentheses.
- **Name** — first non-blank line that isn't an email/phone and isn't implausibly long (most resumes lead with the candidate's name).
- **Skills** — matched against a ~80-term dictionary spanning languages, frameworks, databases, cloud/DevOps, and soft skills, using word-boundary matching (case-insensitive).
- **Education / Experience** — looks for a labeled section header (`Education`, `Experience`, `Work Experience`, etc.) and collects the following lines until the next all-caps/short line or a line cap is hit; falls back to keyword scanning (degree names, universities) if no explicit section is found. An explicit "`N years of experience`" mention is also captured if present.

These are intentionally transparent, tunable rules rather than a black box — the
`SKILLS_DICTIONARY` and section headers can be extended directly in the service.

---












