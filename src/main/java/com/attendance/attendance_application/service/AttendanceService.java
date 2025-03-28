package com.attendance.attendance_application.service;

import com.attendance.attendance_application.dto.AttendanceRequestDTO;
import com.attendance.attendance_application.dto.AttendanceResponseDTO;
import com.attendance.attendance_application.model.AttendanceRecord;
import com.attendance.attendance_application.model.AttendanceStatus;
import com.attendance.attendance_application.model.Employee;
import com.attendance.attendance_application.repository.AttendanceRepository;
import com.attendance.attendance_application.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    @Retryable(
            retryFor = DataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000))
    public AttendanceRecord addAttendance(AttendanceRecord record) {
        return attendanceRepository.save(record);
    }

    @Transactional
    public List<AttendanceRecord> getAttendanceByEmployee(Long employeeId) {
        return attendanceRepository.findByEmployeeId(employeeId);
    }

    @Transactional
    public Map<String, Object> getAttendanceTrends(LocalDate start, LocalDate end) {
        List<AttendanceRecord> records = attendanceRepository.findByDateBetween(start, end);

        return Map.of(
                "total_entries", records.size(),
                "present_count", countStatus(records, AttendanceStatus.PRESENT),
                "wfh_trend", calculateWFHTrend(records)
        );
    }

    @Transactional
    private long countStatus(List<AttendanceRecord> records, AttendanceStatus status) {
        return records.stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    @Transactional
    private Map<String, Long> calculateWFHTrend(List<AttendanceRecord> records) {
        return records.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.WFH)
                .collect(Collectors.groupingBy(
                        r -> r.getEmployee().getTeamId(),
                        Collectors.counting()
                ));
    }

    @Transactional
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public AttendanceResponseDTO updateAttendance(Long id, AttendanceRequestDTO dto) {
        AttendanceRecord existingRecord = attendanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Record not found"));
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        existingRecord.setEmployee(employee);
        existingRecord.setDate(dto.getDate());
        existingRecord.setStatus(dto.getStatus());

        AttendanceRecord updatedRecord = attendanceRepository.save(existingRecord);
        return convertToDTO(updatedRecord);
    }

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);
    private final EntityManager entityManager;

    @Transactional
    public AttendanceResponseDTO addAttendance(AttendanceRequestDTO dto) {
        try {
            Employee employee = employeeRepository.findById(dto.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            AttendanceRecord record = new AttendanceRecord();
            record.setEmployee(employee);
            record.setDate(dto.getDate());
            record.setStatus(dto.getStatus());

            logger.info("Saving attendance record: {}", record);
            AttendanceRecord savedRecord = attendanceRepository.save(record);
            logger.info("Saved record ID: {}", savedRecord.getId());

            entityManager.flush(); // Force flush to test
            logger.info("Flushed record ID: {}", savedRecord.getId());
            return convertToDTO(savedRecord);
        } catch (Exception e) {
            logger.error("Failed to save attendance record", e);
            throw e;
        }
    }

    @Transactional
    private AttendanceResponseDTO convertToDTO(AttendanceRecord record) {
        AttendanceResponseDTO dto = new AttendanceResponseDTO();
        dto.setId(record.getId());
        dto.setEmployeeId(record.getEmployee().getId());
        dto.setDate(record.getDate());
        dto.setStatus(record.getStatus());
        return dto;
    }
}