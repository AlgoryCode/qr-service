package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.chef-avatars")
public class ChefAvatarProperties {

    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        private String key;
        private String label;
        private String objectKey;
        private String classpathResource;
    }
}
