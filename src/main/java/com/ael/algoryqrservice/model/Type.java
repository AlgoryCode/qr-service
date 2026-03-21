package com.ael.algoryqrservice.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Type {
    MAIL(1,"mail"),
    LINK(2,"link"),
    LOCATION(3,"location"),
    CONTACT(4,"contact"),
    WIFI(5,"wifi"),
    TEXT(6,"text");

    private final Integer typeId;
    private final String value;

    public static Type from(String raw){
        for (Type t : values()) {
            if (t.value.equalsIgnoreCase(raw)) return t;
        }
        throw new IllegalArgumentException("Unsupported QR type: " + raw);
    }
}
