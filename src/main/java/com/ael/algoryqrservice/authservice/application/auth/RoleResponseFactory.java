package com.ael.algoryqrservice.authservice.application.auth;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class RoleResponseFactory {

    private final Map<String, ProfileBuilder> builders;

    public RoleResponseFactory(List<ProfileBuilder> profileBuilders) {
        this.builders = profileBuilders.stream()
                .collect(Collectors.toMap(ProfileBuilder::roleCode, Function.identity()));
    }

    public Map<String, Object> profile(AuthSubject subject) {
        ProfileBuilder builder = builders.get(subject.roleCode());
        if (builder == null) {
            return Map.of(
                    "subjectId", subject.subjectId(),
                    "role", subject.roleCode(),
                    "displayName", subject.displayName()
            );
        }
        return builder.build(subject);
    }

    public interface ProfileBuilder {
        String roleCode();

        Map<String, Object> build(AuthSubject subject);
    }
}
