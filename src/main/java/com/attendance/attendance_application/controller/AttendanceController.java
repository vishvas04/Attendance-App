package com.attendance.attendance_application.controller;

import com.attendance.attendance_application.dto.AttendanceRequestDTO;
import com.attendance.attendance_application.dto.AttendanceResponseDTO;
import com.attendance.attendance_application.model.AttendanceRecord;
import com.attendance.attendance_application.service.AIService;
import com.attendance.attendance_application.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// AttendanceController.java
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;
    private final AIService aiService;

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<AttendanceRecord>> getEmployeeAttendance(@PathVariable Long employeeId) {
        return ResponseEntity.ok(attendanceService.getAttendanceByEmployee(employeeId));
    }

    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        return ResponseEntity.ok(attendanceService.getAttendanceTrends(start, end));
    }

    @GetMapping("/summary/daily")
    public ResponseEntity<String> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(aiService.generateDailySummary(date));
    }

    @PostMapping("/ask")
    public ResponseEntity<String> askQuestion(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        return ResponseEntity.ok(aiService.answerQuestion(question));
    }

    @PostMapping
    public ResponseEntity<AttendanceResponseDTO> addEntry(@RequestBody AttendanceRequestDTO request) {
        return ResponseEntity.ok(attendanceService.addAttendance(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttendanceResponseDTO> updateEntry(
            @PathVariable Long id,
            @RequestBody AttendanceRequestDTO request) {
        return ResponseEntity.ok(attendanceService.updateAttendance(id, request));
    }
}
