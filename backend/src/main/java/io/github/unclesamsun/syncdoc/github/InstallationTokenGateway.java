package io.github.unclesamsun.syncdoc.github;

/**
 * 설치 토큰 발급 포트.
 *
 * <p>사용자가 접속하지 않는 동안에도 수집해야 하므로 수집은 설치 토큰을 쓴다. 사용자 토큰은
 * 8시간이면 만료되고 갱신하려면 그 사용자가 있어야 한다.
 *
 * <p>설치 토큰으로 읽었다는 사실은 어떤 사용자의 열람 권한도 뜻하지 않는다. 읽기 계약은
 * 여전히 요청자의 사용자 토큰으로 권한을 확인한다.
 */
public interface InstallationTokenGateway {

    /**
     * @param githubInstallationId GitHub가 매긴 설치 ID
     * @return 저장소를 읽을 수 있는 토큰. 만료 전에 알아서 새로 받는다
     */
    String accessToken(String githubInstallationId);
}
