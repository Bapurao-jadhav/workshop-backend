package com.workshop;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.entity.RegistrationStatus;
import com.workshop.entity.Role;
import com.workshop.entity.User;
import com.workshop.entity.Workshop;
import com.workshop.entity.WorkshopStatus;
import com.workshop.exception.ConflictException;
import com.workshop.repository.RegistrationRepository;
import com.workshop.repository.UserRepository;
import com.workshop.repository.WorkshopRepository;
import com.workshop.service.RegistrationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Fires many simultaneous registrations at a small workshop and checks that capacity is never exceeded. */
@SpringBootTest
@ActiveProfiles("test")
class RegistrationConcurrencyTest {

    private static final int CAPACITY = 3;
    private static final int CONTENDERS = 12;

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private WorkshopRepository workshopRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RegistrationRepository registrationRepository;

    @Test
    void neverExceedsCapacityUnderConcurrentRegistrations() throws Exception {
        Workshop workshop = new Workshop();
        workshop.setTitle("Concurrency Workshop");
        workshop.setDescription("d");
        workshop.setCategory("c");
        workshop.setInstructorName("i");
        workshop.setTargetAudience("a");
        workshop.setWorkshopDate(LocalDate.now().plusDays(30));
        workshop.setStartTime(LocalTime.of(10, 0));
        workshop.setEndTime(LocalTime.of(12, 0));
        workshop.setRegistrationDeadline(workshop.getWorkshopDate().minusDays(1).atTime(12, 0));
        workshop.setVenue("Room 1");
        workshop.setCapacity(CAPACITY);
        workshop.setStatus(WorkshopStatus.PUBLISHED);
        workshop = workshopRepository.saveAndFlush(workshop);
        final Long workshopId = workshop.getId();

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            User u = new User();
            u.setName("Racer " + i);
            u.setEmail("racer-" + UUID.randomUUID() + "@example.com");
            u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghi");
            u.setRole(Role.USER);
            userIds.add(userRepository.saveAndFlush(u).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejectedFull = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Long userId : userIds) {
            Callable<Void> task = () -> {
                startGun.await();
                try {
                    registrationService.register(userId, workshopId);
                    succeeded.incrementAndGet();
                } catch (ConflictException full) {
                    rejectedFull.incrementAndGet();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        startGun.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(CAPACITY);
        assertThat(rejectedFull.get()).isEqualTo(CONTENDERS - CAPACITY);
        assertThat(registrationRepository.countByWorkshopIdAndStatus(workshopId, RegistrationStatus.CONFIRMED))
                .isEqualTo(CAPACITY);
    }
}
