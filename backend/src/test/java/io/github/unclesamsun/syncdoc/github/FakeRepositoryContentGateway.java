package io.github.unclesamsun.syncdoc.github;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 저장소 원문을 테스트가 직접 심는 fake.
 *
 * <p>revision마다 파일 묶음을 따로 등록할 수 있다. 수집 도중 저장소가 바뀌는 상황을 그대로
 * 표현하려면 읽는 중에 head를 바꾸면 된다.
 */
public class FakeRepositoryContentGateway implements RepositoryContentGateway {

    private final Map<String, Map<String, String>> filesByRevision = new LinkedHashMap<>();
    private String head = "rev-1";
    private RuntimeException failure;
    private RuntimeException readFailure;
    private String docsRoot = "docs";
    private final AtomicInteger reads = new AtomicInteger();
    private Consumer<Integer> onRead = count -> {
    };

    public void reset() {
        filesByRevision.clear();
        head = "rev-1";
        failure = null;
        readFailure = null;
        docsRoot = "docs";
        reads.set(0);
        onRead = count -> {
        };
    }

    /** @param path 저장소 기준 전체 경로 */
    public void putFile(String revision, String path, String text) {
        filesByRevision.computeIfAbsent(revision, key -> new LinkedHashMap<>()).put(path, text);
    }

    public void head(String revision) {
        this.head = revision;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /** 문서 목록까지는 성공하고 본문을 읽다가 실패하는 상황이다. */
    public void failReadWith(RuntimeException failure) {
        this.readFailure = failure;
    }

    public void docsRoot(String docsRoot) {
        this.docsRoot = docsRoot;
    }

    /** 본문을 한 건 읽을 때마다 부른다. 읽는 도중 다른 일이 벌어지는 상황을 만든다. */
    public void onRead(Consumer<Integer> onRead) {
        this.onRead = onRead;
    }

    public int readCount() {
        return reads.get();
    }

    @Override
    public RepositoryRef open(String githubInstallationId, String githubRepositoryId) {
        raise();
        return new RepositoryRef(githubInstallationId, githubRepositoryId, "owner/" + githubRepositoryId);
    }

    @Override
    public String headRevision(RepositoryRef repository, String branch) {
        raise();
        return head;
    }

    @Override
    public List<SourceFile> listDocuments(RepositoryRef repository, String revision, String requestedRoot,
                                          int maxDocuments) {
        raise();
        if (!requestedRoot.equals(docsRoot)) {
            throw new DocsRootMissingException(requestedRoot);
        }
        List<SourceFile> files = new ArrayList<>();
        for (Map.Entry<String, String> entry : filesByRevision.getOrDefault(revision, Map.of()).entrySet()) {
            if (!entry.getKey().startsWith(requestedRoot + "/")) {
                continue;
            }
            if (files.size() >= maxDocuments) {
                throw new TooManyDocumentsException(maxDocuments);
            }
            files.add(new SourceFile(entry.getKey(), blobSha(revision, entry.getKey()),
                    entry.getValue().length()));
        }
        return List.copyOf(files);
    }

    @Override
    public String readText(RepositoryRef repository, String blobSha, int maxBytes) {
        if (readFailure != null) {
            throw readFailure;
        }
        onRead.accept(reads.incrementAndGet());
        for (Map.Entry<String, Map<String, String>> revision : filesByRevision.entrySet()) {
            for (Map.Entry<String, String> file : revision.getValue().entrySet()) {
                if (blobSha(revision.getKey(), file.getKey()).equals(blobSha)) {
                    return file.getValue();
                }
            }
        }
        throw new GitHubLookupFailedException("없는 blob이다");
    }

    private void raise() {
        if (failure != null) {
            throw failure;
        }
    }

    /** 내용이 아니라 경로+revision으로 만든다. 테스트가 blob을 다시 찾을 수 있게 하는 용도다. */
    private static String blobSha(String revision, String path) {
        return revision + ":" + path;
    }
}
