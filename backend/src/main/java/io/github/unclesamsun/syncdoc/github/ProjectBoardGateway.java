package io.github.unclesamsun.syncdoc.github;

import java.util.Map;
import java.util.Optional;

/**
 * GitHub Project(v2)의 상태별 건수를 읽는 포트.
 *
 * <p>**사용자 토큰**으로 조회한다. 한 사용자의 Project 조회 결과를 다른 사용자에게 보여주지 않는다 —
 * 저장소를 볼 수 있다고 Project를 볼 수 있는 것은 아니며, 둘을 같은 권한으로 취급하면 권한 경계가
 * 무너진다.
 */
public interface ProjectBoardGateway {

    /**
     * @param statusCounts Project의 Status 필드 값별 건수. 값 이름은 그 Project가 정한 그대로다
     */
    record BoardCounts(Map<String, Integer> statusCounts, int total) {
    }

    /**
     * @param githubProjectNodeId 연결 설정에 저장한 Project 노드 ID
     * @return 조회할 수 없으면 비어 있다. 비어 있다고 0으로 대체하지 않는다
     */
    Optional<BoardCounts> countByStatus(String userAccessToken, String githubProjectNodeId);
}
