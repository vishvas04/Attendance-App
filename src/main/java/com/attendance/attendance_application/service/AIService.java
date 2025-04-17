package com.attendance.attendance_application.service;

import com.attendance.attendance_application.model.AttendanceRecord;
import com.attendance.attendance_application.model.AttendanceStatus;
import com.attendance.attendance_application.model.Employee;
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
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
        try {
            // First check for month-specific queries
            String month = extractMonthFromQuestion(question.toLowerCase());
            if (month != null) {
                return getAbsentEmployeesForMonth(month);
            }

            // Handle predefined questions
            return switch (question.toLowerCase()) {
                case "who was absent the most this month?" -> getMostAbsentEmployee(LocalDate.now());
                case "how many wfh days last week?" -> getWFHDaysLastWeek(LocalDate.now());
                default -> handleGenericQuestion(question);
            };
        } catch (Exception e) {
            return "Error processing your request. Please try again with a different phrasing.";
        }
    }

    private String getMostAbsentEmployee(LocalDate date) {
        ZoneId zone = ZoneId.of("America/New_York");
        YearMonth yearMonth = YearMonth.now(); // Replace YearMonth.of(2024, 3)
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        System.out.println("[DEBUG] Querying absences between: " + start + " and " + end);

        List<Object[]> results = attendanceRepository.findMostAbsentEmployee(
                AttendanceStatus.ABSENT, // Pass enum here
                start,
                end,
                PageRequest.of(0, 1)
        );
        System.out.println("[DEBUG] Query results count: " + results.size());

        if (results.isEmpty() || (Long) results.get(0)[1] == 0) {
            return "No absences recorded this month";
        }

        Object[] result = results.get(0);
        return String.format(
                "%s was absent the most this month with %d absences.",
                result[0], result[1]
        );
    }

    private String getAbsentEmployeesForMonth(String monthName) {
        try {
            int year = LocalDate.now().getYear();
            Month month = Month.valueOf(monthName.toUpperCase());
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());

            List<AttendanceRecord> absences = attendanceRepository.findByStatusAndDateBetween(
                    AttendanceStatus.ABSENT, start, end
            );

            if (absences.isEmpty()) {
                return "No absences recorded in " + monthName + ".";
            }

            Map<Employee, Long> absentEmployees = absences.stream()
                    .collect(Collectors.groupingBy(
                            AttendanceRecord::getEmployee,
                            Collectors.counting()
                    ));

            StringBuilder response = new StringBuilder();
            response.append("Absences in ").append(monthName).append(":\n");
            absentEmployees.forEach((employee, count) -> {
                response.append("- ").append(employee.getName())
                        .append(": ").append(count).append(" days\n");
            });

            return response.toString();
        } catch (Exception e) {
            return "Could not process request for " + monthName + ". Please check the month name.";
        }
    }

    private String extractMonthFromQuestion(String question) {
        List<String> months = List.of(
                "january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"
        );
        for (String month : months) {
            if (question.contains(month)) {
                return month;
            }
        }
        return null;
    }

    private String getWFHDaysLastWeek(LocalDate date) {
        LocalDate start = date.minusWeeks(1);
        List<AttendanceRecord> records = attendanceRepository.findByDateBetween(start, date);
        long wfhCount = records.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.WFH)
                .count();

        return String.format("There were %d WFH days last week.", wfhCount);
    }

    private String handleGenericQuestion(String question) {
        return getAIResponse("Answer this question about attendance: " + question);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private String getAIResponse(String prompt) {
        try {
            return geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/gemini-1.5-pro-latest:generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .bodyValue(Map.of(
                            "contents", List.of(
                                    Map.of(
                                            "parts", List.of(
                                                    Map.of("text", prompt)
                                            )
                                    )
                            )
                    ))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(response -> parseAIResponse(response))
                    .onErrorReturn("Error communicating with AI service")
                    .block();
        } catch (Exception e) {
            return "AI service unavailable. Please try again later.";
        }
    }

    private String parseAIResponse(Map<String, Object> response) {
        try {
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
        } catch (Exception e) {
            return "Could not parse AI response";
        }
    }
}