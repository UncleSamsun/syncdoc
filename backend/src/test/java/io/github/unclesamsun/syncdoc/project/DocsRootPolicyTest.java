package io.github.unclesamsun.syncdoc.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocsRootPolicyTest {

    @Test
    void an_empty_value_falls_back_to_the_default() {
        assertThat(DocsRootPolicy.normalize(null)).isEqualTo("docs");
        assertThat(DocsRootPolicy.normalize("  ")).isEqualTo("docs");
    }

    @Test
    void a_trailing_slash_is_removed_so_the_same_path_is_stored_once() {
        assertThat(DocsRootPolicy.normalize("docs/")).isEqualTo("docs");
        assertThat(DocsRootPolicy.normalize("spec/docs")).isEqualTo("spec/docs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/docs",
            "../docs",
            "docs/../..",
            "docs/./secret",
            "C:\\docs",
            "https://evil.test/docs",
            "docs//nested",
    })
    void a_path_that_could_escape_the_repository_is_rejected(String candidate) {
        assertThatThrownBy(() -> DocsRootPolicy.normalize(candidate))
                .isInstanceOf(DocsRootPolicy.InvalidDocsRootException.class);
    }

    @Test
    void a_control_character_is_rejected() {
        assertThatThrownBy(() -> DocsRootPolicy.normalize("docs\nmore"))
                .isInstanceOf(DocsRootPolicy.InvalidDocsRootException.class);
    }
}
