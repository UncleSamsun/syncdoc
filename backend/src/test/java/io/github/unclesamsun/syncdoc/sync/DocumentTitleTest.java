package io.github.unclesamsun.syncdoc.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 문서 제목 뽑기. 목록에 보이는 이름이라 frontmatter나 제목 없는 문서에서도 무언가는 나와야 한다. */
class DocumentTitleTest {

    @Test
    void the_first_heading_is_the_title() {
        assertThat(SyncWorker.titleOf("docs/a.md", "# 안내서\n\n본문")).isEqualTo("안내서");
    }

    @Test
    void frontmatter_is_skipped() {
        assertThat(SyncWorker.titleOf("docs/a.md", "---\nid: DOC-1\ntype: guide\n---\n\n# 안내서"))
                .isEqualTo("안내서");
    }

    @Test
    void a_document_without_a_heading_falls_back_to_its_file_name() {
        assertThat(SyncWorker.titleOf("docs/nested/plan.md", "본문만 있다")).isEqualTo("plan");
    }

    @Test
    void a_deeper_heading_is_not_mistaken_for_the_title() {
        assertThat(SyncWorker.titleOf("docs/a.md", "## 절\n\n# 제목")).isEqualTo("제목");
    }

    @Test
    void the_same_text_always_hashes_the_same_and_different_text_does_not() {
        assertThat(SyncWorker.sha256("본문")).isEqualTo(SyncWorker.sha256("본문"));
        assertThat(SyncWorker.sha256("본문")).isNotEqualTo(SyncWorker.sha256("본문 "));
    }
}
