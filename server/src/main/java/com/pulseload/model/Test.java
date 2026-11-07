package com.pulseload.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tests")
@Getter
@Setter
@NoArgsConstructor
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "test_name", length = 150, nullable = false)
    private String testName;

    @Column(length = 2048, nullable = false)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private HttpMethod httpMethod;

    @Column(nullable = false)
    private Integer durationInSeconds;

    @Column(nullable = false)
    private Integer requestsPerSec;
}
