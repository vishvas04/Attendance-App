// AttendanceResponseDTO.java
package com.attendance.attendance_application.dto;

import com.attendance.attendance_application.model.AttendanceStatus;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AttendanceResponseDTO {
    private Long id;
    private Long employeeId;
    private LocalDate date;
    private AttendanceStatus status;
}