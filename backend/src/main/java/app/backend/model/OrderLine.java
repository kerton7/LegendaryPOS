package app.backend.model;

/** A product and portion count queued in one order. */
public record OrderLine(Product product, double quantity) { }
