package io.github.unclesamsun.syncdoc.auth;

/**
 * 저장한 GitHub 토큰을 더 이상 쓸 수 없고 갱신도 실패했다.
 *
 * <p>세션은 살아 있는데 GitHub 자격증명만 죽은 상태다. 기다린다고 풀리지 않으므로 503이 아니고,
 * 서버가 잘못한 것도 아니므로 500도 아니다. 사용자가 다시 로그인하면 새 토큰을 받는다.
 */
public class ReauthRequiredException extends RuntimeException {
}
