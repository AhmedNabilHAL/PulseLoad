package com.pulseload.service.tests.controller;

import com.pulseload.service.tests.dto.CreateTestDto;
import com.pulseload.service.tests.dto.TestDto;
import com.pulseload.service.tests.service.TestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @GetMapping()
    public ResponseEntity<List<TestDto>> getTests() {
        return ResponseEntity.ok(testService.getAllTests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestDto> getTestById(@PathVariable UUID id) {
        return ResponseEntity.ok(testService.getTestById(id));
    }

    @PostMapping()
    public ResponseEntity<TestDto> createTest(@Valid @RequestBody CreateTestDto createTestDto) {
        TestDto created = testService.createTest(createTestDto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTest(@PathVariable UUID id, @Valid @RequestBody CreateTestDto createTestDto) {
        testService.updateTest(id, createTestDto);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTest(@PathVariable UUID id) {
        testService.deleteTest(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
