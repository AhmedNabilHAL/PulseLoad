package com.pulseload.workshop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pulseload.TestcontainersConfiguration;
import com.pulseload.feature.tests.service.TestService;
import com.pulseload.observability.ServiceLoggingAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for the aspect-oriented logging topic. The service-logging aspect must centralize
 * logging for control-plane service operations (removing per-method boilerplate) while staying
 * narrowly scoped — a single service call should produce a single aspect log line, not one for
 * every repository/mapper collaborator it fans out to. Fails if the pointcut is too broad.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("acceptance")
class AspectLoggingAcceptanceTest {

    @Autowired
    private TestService testService;

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        aspectLogger = (Logger) LoggerFactory.getLogger(ServiceLoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.setLevel(Level.INFO);
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        aspectLogger.detachAppender(appender);
    }

    @Test
    void aspectLogsTheServiceOperationButNotItsInternalCollaborators() {
        appender.list.clear();

        testService.getAllTests();

        long aspectEvents = appender.list.stream()
                .filter(event -> event.getLoggerName().equals(ServiceLoggingAspect.class.getName()))
                .count();

        assertThat(aspectEvents)
                .as("a narrowly scoped aspect logs the service call once, not each collaborator")
                .isEqualTo(1);
    }
}
