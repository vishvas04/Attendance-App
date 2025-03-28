package com.attendance.attendance_application.repository;

import com.attendance.attendance_application.model.AttendanceRecord;
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

    @Query("SELECT e.name, COUNT(a) as absences " +
            "FROM AttendanceRecord a " +
            "JOIN a.employee e " +
            "WHERE a.status = 'ABSENT' " +
            "AND a.date BETWEEN :start AND :end " +
            "GROUP BY e.id, e.name " +  // Include all non-aggregated columns
            "ORDER BY absences DESC")
    List<Object[]> findMostAbsentEmployee(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            PageRequest pageable
    );
}
