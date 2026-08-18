package com.ticketbooking.unit;

import com.ticketbooking.config.AppConfig;
import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.CreateBookingRequest;
import com.ticketbooking.entity.*;
import com.ticketbooking.exception.LockAcquisitionException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.locking.LockStrategyFactory;
import com.ticketbooking.locking.LockStrategyType;
import com.ticketbooking.locking.SeatLockStrategy;
import com.ticketbooking.metrics.BookingMetricsService;
import com.ticketbooking.payment.PaymentOutcome;
import com.ticketbooking.payment.PaymentService;
import com.ticketbooking.repository.*;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.BookingTransactionDelegate;
import com.ticketbooking.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private LockStrategyFactory lockStrategyFactory;
    @Mock private IdempotencyService idempotencyService;
    @Mock private PaymentService paymentService;
    @Mock private BookingMetricsService metricsService;
    @Mock private AppConfig appConfig;
    @Mock private BookingTransactionDelegate transactionDelegate;
    @Mock private SeatLockStrategy seatLockStrategy;

    @InjectMocks
    private BookingService bookingService;

    private User testUser;
    private Event testEvent;
    private Seat testSeat;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("John Doe").email("john@example.com").build();
        testEvent = Event.builder().id(10L).name("Rock Concert").totalSeats(100).availableSeats(50).build();
        testSeat = Seat.builder().id(100L).event(testEvent).seatNumber("A1").price(new BigDecimal("50.00")).status(SeatStatus.AVAILABLE).version(0L).build();
    }

    @Test
    @DisplayName("Should return cached booking DTO when idempotency key exists")
    void createBooking_IdempotencyHit() {
        String key = "idem-key-123";
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .responseBody("{\"id\":1,\"bookingReference\":\"REF-1001\",\"userId\":1,\"eventId\":10,\"status\":\"PENDING\"}")
                .statusCode(201)
                .build();

        CreateBookingRequest request = CreateBookingRequest.builder()
                .userId(1L)
                .eventId(10L)
                .seatIds(List.of(100L))
                .idempotencyKey(key)
                .build();

        when(idempotencyService.getRecord(key)).thenReturn(Optional.of(record));

        BookingDTO result = bookingService.createBooking(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("REF-1001", result.getBookingReference());
        verify(transactionDelegate, never()).executeBookingTransaction(any());
    }

    @Test
    @DisplayName("Should create booking successfully using IN_MEMORY lock strategy")
    void createBooking_InMemoryLock_Success() throws InterruptedException {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .userId(1L)
                .eventId(10L)
                .seatIds(List.of(100L))
                .strategy(LockStrategyType.IN_MEMORY)
                .idempotencyKey("new-key-456")
                .build();

        BookingDTO expectedDto = BookingDTO.builder()
                .id(99L)
                .bookingReference("REF-9999")
                .userId(1L)
                .eventId(10L)
                .status(BookingStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .build();

        when(idempotencyService.getRecord(any())).thenReturn(Optional.empty());
        when(lockStrategyFactory.getStrategy(LockStrategyType.IN_MEMORY)).thenReturn(seatLockStrategy);
        when(seatLockStrategy.tryLockAll(anyList(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(List.of("seat:100"));
        when(transactionDelegate.executeBookingTransaction(request)).thenReturn(expectedDto);
        when(appConfig.getLockWaitSeconds()).thenReturn(5L);
        when(appConfig.getLockLeaseSeconds()).thenReturn(10L);

        BookingDTO result = bookingService.createBooking(request);

        assertNotNull(result);
        assertEquals(99L, result.getId());
        verify(seatLockStrategy).unlockAll(List.of("seat:100"));
        verify(idempotencyService).saveRecord(eq("new-key-456"), any(), eq(201));
    }

    @Test
    @DisplayName("Should throw LockAcquisitionException when lock cannot be acquired")
    void createBooking_LockAcquisitionFailed() throws InterruptedException {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .userId(1L)
                .eventId(10L)
                .seatIds(List.of(100L))
                .strategy(LockStrategyType.REDIS)
                .idempotencyKey("key-fail")
                .build();

        when(idempotencyService.getRecord(any())).thenReturn(Optional.empty());
        when(lockStrategyFactory.getStrategy(LockStrategyType.REDIS)).thenReturn(seatLockStrategy);
        when(seatLockStrategy.tryLockAll(anyList(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(Collections.emptyList());
        when(appConfig.getLockWaitSeconds()).thenReturn(5L);
        when(appConfig.getLockLeaseSeconds()).thenReturn(10L);

        assertThrows(LockAcquisitionException.class, () -> bookingService.createBooking(request));
        verify(transactionDelegate, never()).executeBookingTransaction(any());
    }

    @Test
    @DisplayName("Should confirm booking when payment is successful")
    void processBookingPayment_Success() {
        Booking booking = Booking.builder()
                .id(1L)
                .bookingReference("REF-100")
                .user(testUser)
                .event(testEvent)
                .status(BookingStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .build();

        BookingSeat bookingSeat = BookingSeat.builder().booking(booking).seat(testSeat).build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentService.processPaymentWithOutcome(eq("REF-100"), any(), eq(PaymentOutcome.SUCCESS)))
                .thenReturn(PaymentOutcome.SUCCESS);
        when(bookingSeatRepository.findByBookingId(1L)).thenReturn(List.of(bookingSeat));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingDTO result = bookingService.processBookingPayment(1L, PaymentOutcome.SUCCESS);

        assertNotNull(result);
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(SeatStatus.BOOKED, testSeat.getStatus());
        verify(metricsService).recordPaymentSuccess();
    }

    @Test
    @DisplayName("Should cancel booking and release seat when payment fails")
    void processBookingPayment_Failure() {
        Booking booking = Booking.builder()
                .id(1L)
                .bookingReference("REF-100")
                .user(testUser)
                .event(testEvent)
                .status(BookingStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .build();

        BookingSeat bookingSeat = BookingSeat.builder().booking(booking).seat(testSeat).build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentService.processPaymentWithOutcome(eq("REF-100"), any(), eq(PaymentOutcome.FAILURE)))
                .thenReturn(PaymentOutcome.FAILURE);
        when(bookingSeatRepository.findByBookingId(1L)).thenReturn(List.of(bookingSeat));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingDTO result = bookingService.processBookingPayment(1L, PaymentOutcome.FAILURE);

        assertNotNull(result);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(SeatStatus.AVAILABLE, testSeat.getStatus());
        verify(eventRepository).incrementAvailableSeats(eq(10L), eq(1));
        verify(metricsService).recordPaymentFailure();
    }
}
