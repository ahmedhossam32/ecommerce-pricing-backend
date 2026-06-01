package com.ecommerce.enums;

public enum Condition {
    NEW, USED, REFURBISHED, UNKNOWN;

    public static Condition from(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        try {
            return Condition.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
