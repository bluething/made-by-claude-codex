package io.github.bluething.searchindexer.document;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorsTest {

    private final ContentExtractors extractors = ContentExtractors.defaults();

    @Test
    void htmlFilesUseTheHtmlExtractor() {
        assertThat(extractors.forFile(Path.of("page.HTML"))).isInstanceOf(HtmlExtractor.class);
    }

    @Test
    void everythingElseFallsBackToPlainText() {
        assertThat(extractors.forFile(Path.of("notes.txt"))).isInstanceOf(PlainTextExtractor.class);
        assertThat(extractors.forFile(Path.of("README"))).isInstanceOf(PlainTextExtractor.class);
        assertThat(extractors.forFile(Path.of("/"))).isInstanceOf(PlainTextExtractor.class);
    }
}
