package com.example.demo.entity;

public enum Category {
    RESERCH_WORK("НИРС"),
    SPORT("Спорт"),
    SOCIAL_ACTIVITY("Общественная деятельность"),
    IDEOLOGICAL_EDUCATION("Идеологическое воспитание"),
    OTHER("Прочее");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Category fromDisplayName(String displayName) {
        for (Category cat : values()) {
            if (cat.displayName.equals(displayName)) {
                return cat;
            }
        }
        return OTHER;
    }
}