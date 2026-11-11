package dev.erick.roastmytaste.domain.enums;

import java.util.Arrays;

public enum RoastLanguage{
    ENGLISH_UK("en-GB","British English"),
    ENGLISH_US("en-US", "American English"),
    PORTUGUESE("pt-BR","Portuguese"),
    SPANISH("es","Spanish");

    private final String code;
    private final String displayName;

    RoastLanguage(String code, String displayName){
        this.code = code;
        this.displayName = displayName;
    }

    public String code(){
        return code;
    }

    public String displayName(){
        return displayName;
    }

    public static RoastLanguage fromCode(String code){
        return Arrays.stream(values())
                .filter(l -> l.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + code));
    }
}