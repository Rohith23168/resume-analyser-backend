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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class appService {

    @Value("${genKey}")
    private String genKey ;

    @Value("${rapidapi.key}")
    private String rapidApiKey;

    @Value("${rapidapi.host}")
    private String rapidApiHost;

    @Autowired
    private prevTable previousTableRepo;

    @Autowired
    private usersTableRepo usersTableRepository;

    private static final String ANALYSIS_PROMPT_TEMPLATE =
            "You are a senior technical recruiter and ATS specialist. Evaluate the resume below STRICTLY for the target role(s): %s.\n" +
                    "\n" +
                    "Before analyzing, confirm the resume content is genuine resume content (not random text) and that it is reasonably related to the target role(s). " +
                    "If it is unrelated or not a real resume, return all numeric fields as 0 and all array fields as empty arrays, with summary and experienceLevel as empty strings.\n" +
                    "\n" +
                    "Scoring philosophy:\n" +
                    "- Be strict, not lenient. Score 90-100 only for near-perfect, fully role-aligned resumes.\n" +
                    "- If a section's content is irrelevant to the target role(s), assign zero points for that section.\n" +
                    "- 50-89: partially relevant, missing keywords/formatting/role alignment.\n" +
                    "- Below 50: significant relevance or ATS issues.\n" +
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

        Tika tika = new Tika();
        ByteArrayInputStream inpfile = new ByteArrayInputStream(file.getBytes());
        String extracted = tika.parseToString(inpfile);

        String promptText = String.format(ANALYSIS_PROMPT_TEMPLATE, roles, extracted);

        String results = null;
        Client client = Client.builder().apiKey(genKey).build();
        Content content = Content.builder().parts(Part.fromText(promptText)).build();

        while (true) {
            try {
                GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", content, GenerateContentConfig.builder().temperature(0.0f).build());
                results = response.text();
                break;
            } catch (Exception e) {
                Thread.sleep(1500);
                System.out.println(e);
            }
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
                    roles,
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
            String url = "https://jsearch.p.rapidapi.com/search?query="
                    + previousTable.getRoles()
                    + "&location=india&page=1";
            try {
                JobSearchResponse response = restTemplate.getForObject(url, JobSearchResponse.class);
                jobs = response != null && response.getResults() != null ? response.getResults() : new ArrayList<>();
            } catch (Exception e) {
                System.out.println(e.getMessage());
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

    // ===================== Safe JSON helpers =====================

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