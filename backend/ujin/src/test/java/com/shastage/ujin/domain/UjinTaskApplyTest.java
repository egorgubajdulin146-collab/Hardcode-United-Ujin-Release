package com.shastage.ujin.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.shastage.ujin.datasource.client.PlatformTask;
import com.shastage.ujin.datasource.entity.BindingSignal;
import com.shastage.ujin.datasource.entity.PlatformBindingEntity;
import com.shastage.ujin.datasource.repository.jpa.PlatformBindingJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.*;
import com.shastage.ujin.domain.service.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest
class UjinTaskApplyTest {

    @Autowired CatalogService catalog;
    @Autowired UjinCommandService commandService;
    @Autowired PlatformBindingJpaRepository bindings;
    @Autowired CommandProperties commandsConfig;

    private UUID deviceFor(String title, String owner) {
        var device =
                catalog.create(
                        new DeviceWrite(
                                title,
                                "light",
                                List.of(
                                        new Signal(
                                                "lamp",
                                                "Lamp",
                                                "capability",
                                                "rw",
                                                "NUMBER",
                                                "switch",
                                                null,
                                                0.0,
                                                1.0,
                                                null)),
                                List.of(),
                                null),
                        owner);
        var binding = new PlatformBindingEntity();
        binding.setDeviceId(device.id());
        binding.setOwner(owner);
        binding.setSerialNumber("sn-apply");
        binding.getSignals().add(new BindingSignal("lamp", "lamp"));
        bindings.saveAndFlush(binding);
        return device.id();
    }

    @Test
    void duplicateTaskKeyAppliesOnce() {
        UUID device = deviceFor("Apply A", "ownerA");
        var first =
                commandService.applyTask(
                        device, "ownerA", new PlatformTask("lamp", "1", "t-1", "0"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.APPLIED, first);
        var second =
                commandService.applyTask(
                        device, "ownerA", new PlatformTask("lamp", "1", "t-1", "0"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.SKIPPED_DUPLICATE, second);
    }

    @Test
    void failedApplyDoesNotRecordAck() {
        UUID device = deviceFor("Apply B", "ownerB");
        var bad =
                commandService.applyTask(
                        device, "ownerB", new PlatformTask("lamp", "5", "t-2", "0"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.FAILED, bad);
        var text =
                commandService.applyTask(
                        device, "ownerB", new PlatformTask("lamp", "abc", "t-2", "0"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.FAILED, text);
        // После неуспешного применения тот же task_dt не помечен: задача повторится.
        var retry =
                commandService.applyTask(
                        device, "ownerB", new PlatformTask("lamp", "1", "t-2", "0"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.APPLIED, retry);
    }

    @Test
    void unmappedAndNotPendingAreSkipped() {
        UUID device = deviceFor("Apply C", "ownerC");
        var unmapped =
                commandService.applyTask(
                        device, "ownerC", new PlatformTask("ghost", "1", "t-3", "0"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.SKIPPED_UNMAPPED, unmapped);
        var sameValue =
                commandService.applyTask(
                        device, "ownerC", new PlatformTask("lamp", "1", "t-3", "1"), "token");
        assertEquals(UjinCommandService.ApplyOutcome.SKIPPED_NOT_PENDING, sameValue);
    }

    @Test
    void prefixMatchingUsesOnlyConfirmedPrefix() {
        UUID device = deviceFor("Apply D", "ownerD");
        String previous = commandsConfig.getTaskSignalPrefix();
        commandsConfig.setTaskSignalPrefix("integrations_devices_");
        try {
            var full =
                    commandService.applyTask(
                            device,
                            "ownerD",
                            new PlatformTask("integrations_devices_lamp", "1", "t-4", "0"),
                            "token");
            assertEquals(UjinCommandService.ApplyOutcome.APPLIED, full);
            var foreignPrefix =
                    commandService.applyTask(
                            device,
                            "ownerD",
                            new PlatformTask("other_lamp", "1", "t-5", "0"),
                            "token");
            assertEquals(UjinCommandService.ApplyOutcome.SKIPPED_UNMAPPED, foreignPrefix);
        } finally {
            commandsConfig.setTaskSignalPrefix(previous);
        }
    }

    @Test
    void concurrentApplyOfSameTaskHappensOnce() throws Exception {
        UUID device = deviceFor("Apply E", "ownerE");
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<UjinCommandService.ApplyOutcome> job =
                    () ->
                            commandService.applyTask(
                                    device,
                                    "ownerE",
                                    new PlatformTask("lamp", "1", "t-6", "0"),
                                    "token");
            List<Future<UjinCommandService.ApplyOutcome>> results =
                    List.of(pool.submit(job), pool.submit(job));
            var outcomes =
                    List.of(results.get(0).get(30, TimeUnit.SECONDS),
                            results.get(1).get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(o -> o == UjinCommandService.ApplyOutcome.APPLIED).count(),
                    outcomes.toString());
            assertEquals(
                    1,
                    outcomes.stream()
                            .filter(o -> o == UjinCommandService.ApplyOutcome.SKIPPED_DUPLICATE)
                            .count(),
                    outcomes.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void foreignOwnerCannotApplyTasks() {
        UUID device = deviceFor("Apply F", "ownerF");
        assertThrows(
                CatalogException.class,
                () ->
                        commandService.applyTask(
                                device, "ownerG", new PlatformTask("lamp", "1", "t-7", "0"), "token"));
    }
    @Test
    void olderTaskIsNotAppliedAgainAfterAnotherTask() {
        UUID device = deviceFor("History review", "review-owner");
        assertEquals(UjinCommandService.ApplyOutcome.APPLIED,
                commandService.applyTask(device, "review-owner", new PlatformTask("lamp", "1", "review-a", "0"), "token"));
        assertEquals(UjinCommandService.ApplyOutcome.APPLIED,
                commandService.applyTask(device, "review-owner", new PlatformTask("lamp", "0", "review-b", "1"), "token"));
        assertEquals(UjinCommandService.ApplyOutcome.SKIPPED_DUPLICATE,
                commandService.applyTask(device, "review-owner", new PlatformTask("lamp", "1", "review-a", "0"), "token"));
    }

}
