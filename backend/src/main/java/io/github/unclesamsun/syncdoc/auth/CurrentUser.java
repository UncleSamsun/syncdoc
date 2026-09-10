package io.github.unclesamsun.syncdoc.auth;

import java.util.UUID;

/** 이번 요청의 사용자. 필터가 request attribute로 싣는다. */
public record CurrentUser(UUID id, String githubUserId, String login, boolean serviceAdmin) {

    public static final String ATTRIBUTE = CurrentUser.class.getName();
}
