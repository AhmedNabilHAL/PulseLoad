package com.pulseload.feature.tests.data;

import com.pulseload.TestcontainersConfiguration;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import com.pulseload.model.Stage;
import com.pulseload.model.WorkloadProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TestRepositoryIT {

    @Autowired
    private TestRepository testRepository;

    @Test
    void persistsAndReloadsWorkloadProfileAsJsonb() {
        WorkloadProfile workloadProfile = new WorkloadProfile();
        workloadProfile.setLoadModel(LoadModel.OPEN);
        workloadProfile.setStartTarget(10);
        workloadProfile.setMaxConcurrency(200);
        workloadProfile.setStages(List.of(
                new Stage(60, 100),
                new Stage(60, 500),
                new Stage(60, 1000)
        ));

        com.pulseload.model.Test test = new com.pulseload.model.Test();
        test.setTestName("Checkout API ramp");
        test.setTargetUrl("https://example.com/checkout");
        test.setHttpMethod(HttpMethod.POST);
        test.setWorkloadProfile(workloadProfile);

        com.pulseload.model.Test saved = testRepository.saveAndFlush(test);

        com.pulseload.model.Test reloaded = testRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getWorkloadProfile().getLoadModel()).isEqualTo(LoadModel.OPEN);
        assertThat(reloaded.getWorkloadProfile().getStartTarget()).isEqualTo(10);
        assertThat(reloaded.getWorkloadProfile().getMaxConcurrency()).isEqualTo(200);
        assertThat(reloaded.getWorkloadProfile().getStages()).containsExactly(
                new Stage(60, 100),
                new Stage(60, 500),
                new Stage(60, 1000)
        );
    }
}
