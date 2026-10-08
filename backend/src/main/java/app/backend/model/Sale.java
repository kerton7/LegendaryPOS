package app.backend.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Sale(long id, LocalDate date, LocalTime time, String productName, String variant, double quantity,
                   double revenue, double profit, String paymentMethod, String note) { }
