package com.attendance.attendance_application.service;

import com.attendance.attendance_application.model.AttendanceRecord;
import com.attendance.attendance_application.model.AttendanceStatus;
import com.attendance.attendance_application.repository.AttendanceRepository;
import com.attendance.attendance_application.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIService {
    private final WebClient geminiWebClient;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    @Value("${gemini.api.key}")
    private String apiKey;

    public String generateDailySummary(LocalDate date) {
        List<AttendanceRecord> records = attendanceRepository.findByDate(date);
        Map<String, Long> stats = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus().name(),
                        Collectors.counting()
                ));

        String prompt = String.format(
                "Generate a natural language summary of daily attendance for %s. " +
                        "Statistics: Present - %d, WFH - %d, Absent - %d. " +
                        "Highlight any notable patterns.",
                date,
                stats.getOrDefault(AttendanceStatus.PRESENT.name(), 0L),
                stats.getOrDefault(AttendanceStatus.WFH.name(), 0L),
                stats.getOrDefault(AttendanceStatus.ABSENT.name(), 0L)
        );

        return getAIResponse(prompt);
    }

    public String answerQuestion(String question) {
        LocalDate now = LocalDate.now();
        return switch (question.toLowerCase()) {
            case "who was absent the most this month?" -> getMostAbsentEmployee(now);
            case "how many wfh days last week?" -> getWFHDaysLastWeek(now);
            default -> handleGenericQuestion(question);
        };
    }

    private String getMostAbsentEmployee(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());

        List<Object[]> results = attendanceRepository.findMostAbsentEmployee(
                start, end, PageRequest.of(0, 1)  // Get top result
        );
        if (results.isEmpty() || (Long) results.get(0)[1] == 0) {
            return "No absences recorded this month";
        }
        Object[] result = results.get(0);
        String response = String.format(
                "%s was absent the most this month with %d absences.",
                result[0], result[1]
        );
        return getAIResponse("Convert this data to natural language: " + response);
    }

    private String getWFHDaysLastWeek(LocalDate date) {
        LocalDate start = date.minusWeeks(1);
        List<AttendanceRecord> records = attendanceRepository.findByDateBetween(start, date);
        long wfhCount = records.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.WFH)
                .count();

        String response = String.format("There were %d WFH days last week.", wfhCount);
        return getAIResponse("Convert this data to natural language: " + response);
    }

    private String handleGenericQuestion(String question) {
        return getAIResponse("Answer this question about attendance: " + question);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private String getAIResponse(String prompt) {
        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/gemini-1.5-pro-latest:generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(Map.of(
                        "contents", List.of(  // Fixed Map/List nesting
                                Map.of(
                                        "parts", List.of(
                                                Map.of("text", prompt)
                                        )
                                )
                        )
                ))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    Object candidatesObj = response.get("candidates");
                    if (!(candidatesObj instanceof List<?>)) {
                        return "Invalid response format";
                    }

                    List<?> candidates = (List<?>) candidatesObj;
                    if (candidates.isEmpty()) {
                        return "No response from AI";
                    }

                    Object firstCandidate = candidates.get(0);
                    if (!(firstCandidate instanceof Map<?, ?>)) {
                        return "Invalid candidate format";
                    }

                    Object content = ((Map<?, ?>) firstCandidate).get("content");
                    if (!(content instanceof Map<?, ?>)) {
                        return "Invalid content format";
                    }

                    Object partsObj = ((Map<?, ?>) content).get("parts");
                    if (!(partsObj instanceof List<?>)) {
                        return "Invalid parts format";
                    }

                    List<?> parts = (List<?>) partsObj;
                    if (parts.isEmpty()) {
                        return "No text in response";
                    }

                    Object firstPart = parts.get(0);
                    if (!(firstPart instanceof Map<?, ?>)) {
                        return "Invalid part format";
                    }

                    Object text = ((Map<?, ?>) firstPart).get("text");
                    return (text != null) ? text.toString() : "Empty response";
                })
                .onErrorReturn("Error communicating with AI service")
                .block();
    }
}