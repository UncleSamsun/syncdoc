package io.github.unclesamsun.syncdoc.github;

import java.util.List;

/**
 * 저장소 원문을 읽는 포트. 수집 전용이며 **설치 토큰**을 쓴다. 여기서 읽은 내용은
 * 그대로 사용자에게 돌아가지 않고, 읽기 계약이 요청자의 권한을 따로 확인한 뒤에만 보인다.
 *
 * <p>모든 읽기는 revision 하나에 고정한다. 수집 도중 저장소가 바뀌어도 한 게시본에
 * 서로 다른 revision의 문서가 섞이지 않게 하기 위해서다.
 *
 * <p>저장소 해소는 {@link #open}에서 한 번만 한다. 문서마다 다시 해소하면 문서 수만큼
 * 요청이 두 배가 되고 GitHub 요청 제한을 그만큼 빨리 쓴다.
 */
public interface RepositoryContentGateway {

    /**
     * 수집 한 번 동안 쓰는 저장소 손잡이.
     *
     * @param fullName 숫자 ID로 해소한 `소유자/이름`. 수집 도중에는 이 값을 고정해 쓴다
     */
    record RepositoryRef(String githubInstallationId, String githubRepositoryId, String fullName) {
    }

    /**
     * @param blobSha 내용의 GitHub 해시. 내용이 같으면 값이 같으므로 재수집 여부 판단에 쓴다
     * @param size    바이트 수. 상한을 넘는 파일은 읽기 전에 걸러낸다
     */
    record SourceFile(String path, String blobSha, int size) {
    }

    /** 숫자 ID로 저장소를 해소한다. 이름이 바뀌어도 같은 프로젝트를 계속 따라간다. */
    RepositoryRef open(String githubInstallationId, String githubRepositoryId);

    /** 브랜치가 지금 가리키는 commit. 이후 수집은 이 값에 고정한다. */
    String headRevision(RepositoryRef repository, String branch);

    /**
     * 문서 경로 아래의 Markdown 파일을 모은다.
     *
     * @throws DocsRootMissingException   연결 설정의 문서 경로가 그 revision에 없을 때
     * @throws TooManyDocumentsException  상한을 넘을 때. 일부만 모아 완료로 보이게 하지 않는다
     */
    List<SourceFile> listDocuments(RepositoryRef repository, String revision, String docsRoot,
                                   int maxDocuments);

    /**
     * @throws DocumentTooLargeException 상한을 넘는 문서일 때
     */
    String readText(RepositoryRef repository, String blobSha, int maxBytes);

    /**
     * 문서 경로 아래의 첨부 후보를 모은다. 어떤 형식을 받아들일지는 부르는 쪽이 정한다.
     *
     * @throws TooManyDocumentsException 상한을 넘을 때
     */
    List<SourceFile> listAssets(RepositoryRef repository, String revision, String docsRoot, int maxAssets);

    /**
     * @throws DocumentTooLargeException 상한을 넘는 첨부일 때
     */
    byte[] readBytes(RepositoryRef repository, String blobSha, int maxBytes);

    /**
     * 저장소의 Issue를 읽는다. 상태·담당자·라벨은 GitHub가 정본이며 여기서는 관찰만 한다.
     *
     * @param max 읽을 최대 건수. 상한에 걸리면 불완전으로 알린다
     */
    IssuePage listIssues(RepositoryRef repository, int max);

    /**
     * 저장소의 PR을 읽는다. 작업 완료 수를 세는 데 쓰지 않고 작업마다 어떤 PR이 있는지만 보여준다.
     */
    List<PullRequestSummary> listPullRequests(RepositoryRef repository, int max);

    /**
     * @param complete 상한에 걸리지 않고 전부 읽었으면 true. 집계가 확정인지 판단하는 근거다
     */
    record IssuePage(List<IssueSummary> items, boolean complete) {
    }

    /**
     * @param nodeId      GitHub가 준 전역 ID. 번호와 달리 저장소를 옮겨도 바뀌지 않는다
     * @param stateReason `completed`·`not_planned`. 취소를 완료와 구분하는 유일한 값이다
     */
    record IssueSummary(String nodeId, int number, String title, String state, String stateReason,
                        List<String> assignees, List<String> labels) {
    }

    record PullRequestSummary(int number, String title, String state, boolean merged) {
    }

    /** 연결 설정이 가리키는 문서 경로가 사라졌다. 재시도로 풀리지 않고 설정이나 저장소가 바뀌어야 한다. */
    class DocsRootMissingException extends RuntimeException {

        public DocsRootMissingException(String docsRoot) {
            super("문서 경로를 찾을 수 없다: " + docsRoot);
        }
    }

    class TooManyDocumentsException extends RuntimeException {

        public TooManyDocumentsException(int limit) {
            super("문서 수가 상한 " + limit + "을 넘는다");
        }
    }

    class DocumentTooLargeException extends RuntimeException {

        public DocumentTooLargeException(String path, int limit) {
            super("문서 " + path + "가 상한 " + limit + "바이트를 넘는다");
        }
    }
}
