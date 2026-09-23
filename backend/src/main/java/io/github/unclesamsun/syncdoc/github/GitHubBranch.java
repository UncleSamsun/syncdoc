package io.github.unclesamsun.syncdoc.github;

/** 저장소의 브랜치 하나. 문서 기준 브랜치를 목록에서 고르게 하려고 쓴다. */
public record GitHubBranch(String name, boolean isDefault) {
}
