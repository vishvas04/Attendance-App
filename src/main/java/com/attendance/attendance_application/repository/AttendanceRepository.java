package com.attendance.attendance_application.repository;

import com.attendance.attendance_application.model.AttendanceRecord;
import com.attendance.attendance_application.model.AttendanceStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByEmployeeId(Long employeeId);

    List<AttendanceRecord> findByDateBetween(LocalDate start, LocalDate end);

    List<AttendanceRecord> findByDate(LocalDate date);

    List<AttendanceRecord> findByStatusAndDateBetween(
            AttendanceStatus status,
            LocalDate start,
            LocalDate end
    );

    @Query("SELECT e.name, COUNT(a) as absences " +
            "FROM AttendanceRecord a " +
            "JOIN a.employee e " +
            "WHERE a.status = :status " + // Use parameterized enum
            "AND a.date BETWEEN :start AND :end " +
            "GROUP BY e.id, e.name " +
            "ORDER BY absences DESC")
    List<Object[]> findMostAbsentEmployee(
            @Param("status") AttendanceStatus status, // Add parameter
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            PageRequest pageable
    );
}
