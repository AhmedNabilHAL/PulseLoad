package com.pulseload.service.tests.data;

import com.pulseload.model.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestRepository extends JpaRepository<Test, UUID> {
}
