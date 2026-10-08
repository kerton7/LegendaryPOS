package app.backend.model;

public record Product(int id, String name, String variant, double price, double cost) {
    @Override public String toString() { return name + " · " + variant; }
}
