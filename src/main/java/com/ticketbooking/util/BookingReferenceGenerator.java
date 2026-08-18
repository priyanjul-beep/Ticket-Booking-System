package com.ticketbooking.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class BookingReferenceGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String generateBookingReference() {
        String dateStr = ZonedDateTime.now().format(FORMATTER);
        int randomNum = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "BK-" + dateStr + "-" + randomNum;
    }

    public static String generateFlashPurchaseReference() {
        String dateStr = ZonedDateTime.now().format(FORMATTER);
        int randomNum = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "FS-" + dateStr + "-" + randomNum;
    }
}
