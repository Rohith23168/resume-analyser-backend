package com.ai.Resume.analyser.service;


import com.ai.Resume.analyser.model.*;
import com.ai.Resume.analyser.repository.prevTable;
import com.ai.Resume.analyser.repository.usersTableRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class appService {

    @Value("${genKey}")
    private String genKey ;

    // ===== Adzuna credentials (replaces RapidAPI) =====
    @Value("${adzuna.app-id}")
    private String adzunaAppId;

    @Value("${adzuna.app-key}")
    private String adzunaAppKey;

    @Value("${adzuna.country}")
    private String adzunaCountry;

    @Autowired
    private prevTable previousTableRepo;

    @Autowired
    private usersTableRepo usersTableRepository;

    // Bound the retry loop instead of looping forever on persistent failures.
    private static final int MAX_GEMINI_ATTEMPTS = 3;

    private static final String ANALYSIS_PROMPT_TEMPLATE =
            "You are a senior technical recruiter and ATS specialist. Evaluate the resume below for the target role(s): %s.\n" +
                    "\n" +
                    "IMPORTANT: Treat the target role string as case-insensitive and ignore extra whitespace, punctuation, or minor typos. " +
                    "Treat common abbreviations and close synonyms as equivalent to the full role name " +
                    "(e.g. \"java dev\", \"Java Developer\", \"JAVA DEVELOPER\", and \"java backend developer\" all refer to the same role; " +
                    "\"swe\", \"sde\", and \"software engineer\" all refer to the same role). " +
                    "Do not penalize the resume for how the role text was capitalized, spaced, or abbreviated.\n" +
                    "\n" +
                    "Before analyzing, confirm the resume content is genuine resume content (not random/gibberish text) and that it is at least reasonably or " +
                    "adjacently related to the target role(s) (e.g. a Backend Developer resume IS relevant to a \"Java Developer\" role search; a Full Stack " +
                    "Developer resume IS relevant to a \"Frontend Developer\" search). Only return all numeric fields as 0 and all array fields as empty arrays " +
                    "if the resume is COMPLETELY unrelated to the role(s) (e.g. a chef's resume submitted for a Software Engineer role) or is not a real resume at all.\n" +
                    "\n" +
                    "Scoring philosophy:\n" +
                    "- Be strict, not lenient, but use GRADED scoring — do not zero out entire sections just because they are not a perfect literal match to the role text.\n" +
                    "- Score 90-100 only for near-perfect, fully role-aligned resumes.\n" +
                    "- If a section's content is only partially relevant to the target role(s), deduct points proportionally — do not assign zero unless that section is truly unrelated.\n" +
                    "- 50-89: partially relevant, missing keywords/formatting/role alignment.\n" +
                    "- Below 50: significant relevance or ATS issues, but the resume is still a real, identifiable attempt at the role.\n" +
                    "\n" +
                    "Score atsoptimizationscore separately based on ATS parsing readiness, keyword usage, readability, section clarity, absence of graphics/tables, and role alignment.\n" +
                    "\n" +
                    "Return ONLY raw JSON (alphanumeric content only, no markdown fences, no commentary) matching EXACTLY this schema:\n" +
                    "{\n" +
                    "  \"score\": number,\n" +
                    "  \"atsoptimizationscore\": number,\n" +
                    "  \"summary\": string,\n" +
                    "  \"experienceLevel\": string,\n" +
                    "  \"skills\": [string],\n" +
                    "  \"missingSkills\": [string],\n" +
                    "  \"strengths\": [string],\n" +
                    "  \"weaknesses\": [string],\n" +
                    "  \"interviewTips\": [string],\n" +
                    "  \"pros\": [string],\n" +
                    "  \"cons\": [string],\n" +
                    "  \"suggestions\": [string]\n" +
                    "}\n" +
                    "\n" +
                    "Field rules:\n" +
                    "- summary: 2-3 neutral sentences describing the candidate's background relative to the role(s).\n" +
                    "- experienceLevel: exactly one of \"Entry\", \"Mid\", \"Senior\", or \"Lead/Principal\".\n" +
                    "- skills: only skills actually present in the resume that are relevant to the target role(s).\n" +
                    "- missingSkills: role-critical skills the resume does NOT demonstrate.\n" +
                    "- strengths, weaknesses, suggestions, interviewTips, pros, cons: each array item must be under 275 characters, concise and actionable.\n" +
                    "- Do not include any irrelevant keywords or content unrelated to the target role(s).\n" +
                    "\n" +
                    "Resume content:\n" +
                    "%s\n";

    public ResponseEntity<?> extract(String roles, MultipartFile file) throws TikaException, IOException, InterruptedException {

        // Normalize the role text: trim, collapse internal whitespace.
        // We intentionally do NOT lowercase it for storage/display purposes (job search query, previous report),
        // but the prompt itself explicitly instructs case-insensitive matching so typed casing never affects scoring.
        String normalizedRoles = normalizeRoles(roles);
        if (normalizedRoles.isEmpty()) {
            return new ResponseEntity<>("Role must not be empty", HttpStatus.BAD_REQUEST);
        }

        Tika tika = new Tika();
        ByteArrayInputStream inpfile = new ByteArrayInputStream(file.getBytes());
        String extracted = tika.parseToString(inpfile);

        String promptText = String.format(ANALYSIS_PROMPT_TEMPLATE, normalizedRoles, extracted);

        String results = null;
        Client client = Client.builder().apiKey(genKey).build();
        Content content = Content.builder().parts(Part.fromText(promptText)).build();

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_GEMINI_ATTEMPTS; attempt++) {
            try {
                GenerateContentResponse response = client.models.generateContent(
                        "gemini-2.5-flash", content, GenerateContentConfig.builder().temperature(0.0f).build());
                results = response.text();
                lastException = null;
                break;
            } catch (Exception e) {
                lastException = e;
                System.out.println("Gemini call failed (attempt " + attempt + "/" + MAX_GEMINI_ATTEMPTS + "): " + e.getMessage());
                if (attempt < MAX_GEMINI_ATTEMPTS) {
                    Thread.sleep(1500);
                }
            }
        }

        if (lastException != null || results == null) {
            return new ResponseEntity<>("Resume analysis service is temporarily unavailable. Please try again.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        int firstBrace = results.indexOf("{");
        int lastBrace = results.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            results = results.substring(firstBrace, lastBrace + 1);
        }

        JsonNode node;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            node = objectMapper.readTree(results);
        } catch (Exception e) {
            System.out.println("Failed to parse Gemini response as JSON: " + e.getMessage());
            return new ResponseEntity<>("Invalid document", HttpStatus.NOT_ACCEPTABLE);
        }

        int score = safeInt(node, "score");
        int atsScore = safeInt(node, "atsoptimizationscore");
        String summary = safeText(node, "summary");
        String experienceLevel = safeText(node, "experienceLevel");
        List<String> skills = safeStringList(node, "skills");
        List<String> missingSkills = safeStringList(node, "missingSkills");
        List<String> strengths = safeStringList(node, "strengths");
        List<String> weaknesses = safeStringList(node, "weaknesses");
        List<String> interviewTips = safeStringList(node, "interviewTips");
        List<String> pros = safeStringList(node, "pros");
        List<String> cons = safeStringList(node, "cons");
        List<String> suggestions = safeStringList(node, "suggestions");

        if (score != 0) {
            String uname = SecurityContextHolder.getContext().getAuthentication().getName();

            previousTable processedData = new previousTable(
                    uname,
                    score,
                    atsScore,
                    normalizedRoles,
                    summary,
                    experienceLevel,
                    skills,
                    missingSkills,
                    strengths,
                    weaknesses,
                    interviewTips,
                    pros,
                    cons,
                    suggestions
            );
            previousTableRepo.save(processedData);

            usersTable usermod = usersTableRepository.findById(uname).orElse(null);
            if (usermod != null) {
                usermod.setPreviousResults(true);
                usersTableRepository.save(usermod);
            }
            return new ResponseEntity<>("Analysed successfully", HttpStatus.OK);
        }

        return new ResponseEntity<>("Invalid document", HttpStatus.NOT_ACCEPTABLE);
    }

    public ResponseEntity<?> lastReport() {
        previousTable previousTable = previousTableRepo.findById(SecurityContextHolder.getContext().getAuthentication().getName()).orElse(null);
        if (previousTable != null) {
            RestTemplate restTemplate = new RestTemplate();
            List<Job> jobs;
            String encodedRole = UriUtils.encodeQueryParam(previousTable.getRoles(), StandardCharsets.UTF_8);

            // ===== Adzuna job search (replaces RapidAPI/JSearch) =====
            // Adzuna endpoint pattern: https://api.adzuna.com/v1/api/jobs/{country}/search/{page}
            String url = "https://api.adzuna.com/v1/api/jobs/" + adzunaCountry + "/search/1"
                    + "?app_id=" + adzunaAppId
                    + "&app_key=" + adzunaAppKey
                    + "&what=" + encodedRole
                    + "&results_per_page=10"
                    + "&content-type=application/json";

            try {
                JobSearchResponse response = restTemplate.getForObject(url, JobSearchResponse.class);
                jobs = response != null && response.getResults() != null ? response.getResults() : new ArrayList<>();
            } catch (Exception e) {
                System.out.println("Adzuna job fetch failed: " + e.getMessage());
                return new ResponseEntity<>("Job Fetch Failed", HttpStatus.NOT_FOUND);
            }

            resultsDto resultsDto = new resultsDto(
                    previousTable.getScore(),
                    previousTable.getAtsoptimizationscore(),
                    nullToEmpty(previousTable.getSummary()),
                    nullToEmpty(previousTable.getExperienceLevel()),
                    nullToEmptyList(previousTable.getSkills()),
                    nullToEmptyList(previousTable.getMissingSkills()),
                    nullToEmptyList(previousTable.getStrengths()),
                    nullToEmptyList(previousTable.getWeaknesses()),
                    nullToEmptyList(previousTable.getInterviewTips()),
                    nullToEmptyList(previousTable.getPros()),
                    nullToEmptyList(previousTable.getCons()),
                    nullToEmptyList(previousTable.getSuggestions()),
                    jobs
            );
            return new ResponseEntity<>(resultsDto, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("No previous Analysis", HttpStatus.NOT_FOUND);
        }
    }

    public ResponseEntity<?> logout() {
        HttpHeaders headers = new HttpHeaders();
        ResponseCookie cookie = ResponseCookie.from("entrypasstoken", "").httpOnly(true).secure(false).sameSite("Strict").maxAge(0).path("/").build();
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        return new ResponseEntity<>("Successfully loggedOut", headers, HttpStatus.OK);
    }

    public ResponseEntity<?> deleteAccount() {

        try {
            String uname = SecurityContextHolder.getContext().getAuthentication().getName();
            usersTableRepository.deleteById(uname);
            previousTableRepo.deleteById(uname);
            HttpHeaders headers = new HttpHeaders();
            ResponseCookie cookie = ResponseCookie.from("entrypasstoken", "").httpOnly(true).secure(false).sameSite("Strict").maxAge(0).path("/").build();
            headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
            return new ResponseEntity<>("Account deleted successfully", headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Failed to delete", HttpStatus.NOT_FOUND);
        }
    }

    public ResponseEntity<?> tokenValidation() {
        try {
            String name = SecurityContextHolder.getContext().getAuthentication().getName();
            usersTable user = usersTableRepository.findById(name).orElse(null);
            if (user == null) {
                return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
            }
            loginResponse loginRes = new loginResponse(user.getUsername(), user.getPreviousResults());
            return new ResponseEntity<>(loginRes, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
    }

    // ===================== Helpers =====================

    /**
     * Trims and collapses internal whitespace in the user-typed role string.
     * Casing is preserved (not lowercased) since it's used for display and job search,
     * but the LLM prompt explicitly instructs case-insensitive interpretation.
     */
    private String normalizeRoles(String roles) {
        if (roles == null) return "";
        return roles.trim().replaceAll("\\s+", " ");
    }

    private int safeInt(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return 0;
        JsonNode value = node.get(field);
        return value.isNumber() ? value.asInt() : 0;
    }

    private String safeText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return "";
        JsonNode value = node.get(field);
        return value.isTextual() ? value.asText() : "";
    }

    private List<String> safeStringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.hasNonNull(field)) return result;
        JsonNode arr = node.get(field);
        if (!arr.isArray()) return result;
        for (JsonNode item : arr) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<String> nullToEmptyList(List<String> value) {
        return value == null ? new ArrayList<>() : value;
    }

}