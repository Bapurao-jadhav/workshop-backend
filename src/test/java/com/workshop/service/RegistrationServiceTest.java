package com.workshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workshop.dto.RegistrationResponse;
import com.workshop.entity.Registration;
import com.workshop.entity.RegistrationStatus;
import com.workshop.entity.Role;
import com.workshop.entity.User;
import com.workshop.entity.Workshop;
import com.workshop.entity.WorkshopStatus;
import com.workshop.exception.BusinessRuleException;
import com.workshop.exception.ConflictException;
import com.workshop.exception.ResourceNotFoundException;
import com.workshop.repository.RegistrationRepository;
import com.workshop.repository.UserRepository;
import com.workshop.repository.WorkshopRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RegistrationServiceTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-12-01T10:00:00Z"), ZoneOffset.UTC);

    private RegistrationRepository registrations;
    private WorkshopRepository workshops;
    private UserRepository users;
    private ApplicationEventPublisher publisher;
    private RegistrationService service;

    private User user;
    private Workshop workshop;

    @BeforeEach
    void setUp() {
        registrations = mock(RegistrationRepository.class);
        workshops = mock(WorkshopRepository.class);
        users = mock(UserRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new RegistrationService(registrations, workshops, users, publisher, NOW);

        user = new User();
        user.setId(5L);
        user.setName("Asha Patil");
        user.setEmail("asha@example.com");
        user.setRole(Role.USER);

        workshop = new Workshop();
        workshop.setId(1L);
        workshop.setTitle("Java Bootcamp");
        workshop.setCapacity(2);
        workshop.setStatus(WorkshopStatus.PUBLISHED);
        workshop.setWorkshopDate(LocalDate.of(2026, 12, 15));
        workshop.setStartTime(LocalTime.of(10, 0));
        workshop.setEndTime(LocalTime.of(16, 0));
        workshop.setRegistrationDeadline(LocalDateTime.of(2026, 12, 10, 18, 0));
        workshop.setVenue("Hall A");

        when(workshops.findByIdForUpdate(1L)).thenReturn(Optional.of(workshop));
        when(users.findById(5L)).thenReturn(Optional.of(user));
        when(registrations.findByUserIdAndWorkshopId(5L, 1L)).thenReturn(Optional.empty());
        when(registrations.countByWorkshopIdAndStatus(1L, RegistrationStatus.CONFIRMED)).thenReturn(0L);
        when(registrations.saveAndFlush(any(Registration.class))).thenAnswer(inv -> {
            Registration r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(99L);
            }
            return r;
        });
    }

    @Test
    void registersAndPublishesConfirmationEvent() {
        RegistrationResponse response = service.register(5L, 1L);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.cancellable()).isTrue();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        RegistrationConfirmedEvent event = (RegistrationConfirmedEvent) captor.getValue();
        assertThat(event.registrationId()).isEqualTo(99L);
        assertThat(event.userEmail()).isEqualTo("asha@example.com");
        assertThat(event.workshopTitle()).isEqualTo("Java Bootcamp");
    }

    @Test
    void rejectsDuplicateRegistration() {
        Registration existing = new Registration();
        existing.setId(7L);
        existing.setStatus(RegistrationStatus.CONFIRMED);
        when(registrations.findByUserIdAndWorkshopId(5L, 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(5L, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
        verify(registrations, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsWhenWorkshopIsFull() {
        when(registrations.countByWorkshopIdAndStatus(1L, RegistrationStatus.CONFIRMED)).thenReturn(2L);

        assertThatThrownBy(() -> service.register(5L, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("full");
        verify(registrations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCancelledWorkshop() {
        workshop.setStatus(WorkshopStatus.CANCELLED);

        assertThatThrownBy(() -> service.register(5L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void rejectsUnpublishedWorkshop() {
        workshop.setStatus(WorkshopStatus.UNPUBLISHED);

        assertThatThrownBy(() -> service.register(5L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not open");
    }

    @Test
    void rejectsAfterRegistrationDeadline() {
        Clock late = Clock.fixed(Instant.parse("2026-12-11T00:00:00Z"), ZoneOffset.UTC);
        RegistrationService lateService = new RegistrationService(registrations, workshops, users, publisher, late);

        assertThatThrownBy(() -> lateService.register(5L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    void rejectsUnknownWorkshop() {
        when(workshops.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(5L, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivatesPreviouslyCancelledRegistration() {
        Registration cancelled = new Registration();
        cancelled.setId(7L);
        cancelled.setStatus(RegistrationStatus.CANCELLED);
        cancelled.setCancelledAt(Instant.parse("2026-11-20T00:00:00Z"));
        when(registrations.findByUserIdAndWorkshopId(5L, 1L)).thenReturn(Optional.of(cancelled));

        RegistrationResponse response = service.register(5L, 1L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void cancelsOwnRegistrationBeforeWorkshopStarts() {
        Registration reg = confirmedRegistration();
        when(registrations.findByIdAndUserId(10L, 5L)).thenReturn(Optional.of(reg));

        RegistrationResponse response = service.cancelMine(5L, 10L);

        assertThat(response.status()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
        verify(registrations).saveAndFlush(reg);
    }

    @Test
    void cannotCancelAfterWorkshopStarted() {
        Registration reg = confirmedRegistration();
        when(registrations.findByIdAndUserId(10L, 5L)).thenReturn(Optional.of(reg));
        Clock afterStart = Clock.fixed(Instant.parse("2026-12-15T10:30:00Z"), ZoneOffset.UTC);
        RegistrationService lateService =
                new RegistrationService(registrations, workshops, users, publisher, afterStart);

        assertThatThrownBy(() -> lateService.cancelMine(5L, 10L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotCancelAnotherUsersRegistration() {
        when(registrations.findByIdAndUserId(10L, 6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelMine(6L, 10L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Registration confirmedRegistration() {
        Registration reg = new Registration();
        reg.setId(10L);
        reg.setUser(user);
        reg.setWorkshop(workshop);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setRegisteredAt(Instant.parse("2026-11-30T00:00:00Z"));
        return reg;
    }
}
