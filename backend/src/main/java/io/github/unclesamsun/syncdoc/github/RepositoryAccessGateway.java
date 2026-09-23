package io.github.unclesamsun.syncdoc.github;

import java.util.List;
import java.util.Optional;

/**
 * 저장소·브랜치·경로 조회 포트. 모든 메서드가 **사용자 토큰**을 받는다.
 * 설치 토큰으로 읽은 결과를 사용자에게 그대로 돌려주지 않기 위해서다.
 */
public interface RepositoryAccessGateway {

    /**
     * @param items    앱과 사용자가 모두 접근할 수 있는 저장소
     * @param complete 상한에 걸려 일부만 읽었으면 false. 불완전한 목록을 완전한 것처럼 보이지 않게 한다
     */
    record AccessibleRepositories(List<GitHubRepository> items, boolean complete) {
    }

    AccessibleRepositories listAccessibleRepositories(String userAccessToken);

    Optional<GitHubRepository> findRepository(String userAccessToken, String githubRepositoryId);

    List<GitHubBranch> listBranches(String userAccessToken, String githubRepositoryId);

    boolean pathExists(String userAccessToken, String githubRepositoryId, String branch, String path);
}
