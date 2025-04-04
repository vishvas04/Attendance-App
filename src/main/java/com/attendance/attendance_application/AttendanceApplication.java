package com.attendance.attendance_application;

import com.attendance.attendance_application.model.AttendanceRecord;
import com.attendance.attendance_application.model.AttendanceStatus;
import com.attendance.attendance_application.model.Employee;
import com.attendance.attendance_application.repository.AttendanceRepository;
import com.attendance.attendance_application.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDate;

@SpringBootApplication(scanBasePackages = "com.attendance.attendance_application")
public class AttendanceApplication {
	public static void main(String[] args) {
		SpringApplication.run(AttendanceApplication.class, args);
	}

	@Bean
	public CommandLineRunner demo(
			EmployeeRepository employeeRepo,
			AttendanceRepository attendanceRepo
	) {
		return args -> {
			// Create sample employee
			Employee emp = new Employee();
			emp.setName("Test User Dad");
			emp.setEmail("Vishonh69ask89@company.com");
			emp.setTeamId("Test-09");
			Employee saved = employeeRepo.save(emp);
			System.out.println("Saved employee with ID: " + saved.getId());
			employeeRepo.save(emp);


			AttendanceRecord record = new AttendanceRecord();
			record.setEmployee(emp);
			record.setDate(LocalDate.now());
			record.setStatus(AttendanceStatus.PRESENT);
			attendanceRepo.save(record);
		};
	}
}
