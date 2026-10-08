package app.backend.model;

/** Aggregated sales figures for one requested date range. */
public record SalesSummary(double revenue, double profit, double quantity, long transactionCount) { }
