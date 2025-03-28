package com.attendance.attendance_application.model;

import com.attendance.attendance_application.repository.EmployeeRepository;
import jakarta.persistence.*;
import org.hibernate.annotations.NaturalId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_seq")
    @SequenceGenerator(
            name = "employee_seq",
            sequenceName = "employees_id_seq",
            allocationSize = 1
    )
    private Long id;

    @Column(nullable = false)
    private String name;

    @NaturalId
    @Column(nullable = false)
    private String email;

    @Column(nullable = false, name = "team_id")
    private String teamId;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
}