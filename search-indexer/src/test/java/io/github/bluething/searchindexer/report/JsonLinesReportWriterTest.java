package io.github.bluething.searchindexer.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bluething.searchindexer.indexing.FailureReason;
import io.github.bluething.searchindexer.indexing.FileReport;
import io.github.bluething.searchindexer.rule.RuleResult;
import io.github.bluething.searchindexer.rule.RuleValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLinesReportWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringWriter out = new StringWriter();
    private final ReportWriter writer = ReportFormat.JSONL.newWriter(new PrintWriter(out), new PrintWriter(new StringWriter()));

    @Test
    void writesIndexedReportAsOneLine() throws IOException {
        writer.write(new FileReport.Indexed(Path.of("a.txt"), List.of(
                new RuleResult("uppercase-word-count", "Upper", new RuleValue.Count(3)),
                new RuleResult("long-words", "Long", new RuleValue.Terms(List.of("Indexing"), false)))));

        assertThat(out.toString()).isEqualTo("""
                {"file":"a.txt","status":"indexed","results":{"uppercase-word-count":{"count":3},\
                "long-words":{"terms":["Indexing"],"truncated":false}}}
                """);
    }

    @Test
    void writesFailuresToStdoutSoPipelinesSeeThem() throws IOException {
        writer.write(new FileReport.Failed(Path.of("b.txt"), FailureReason.NOT_FOUND, "b.txt"));

        JsonNode json = MAPPER.readTree(out.toString());
        assertThat(json.path("status").asText()).isEqualTo("failed");
        assertThat(json.path("error").path("reason").asText()).isEqualTo("not_found");
        assertThat(json.path("error").path("message").asText()).isEqualTo("file not found");
    }

    @Test
    void escapingKeepsOneRecordPerLine() throws IOException {
        writer.write(new FileReport.Indexed(Path.of("we\"ird\nname.txt"), List.of(
                new RuleResult("long-words", "Long", new RuleValue.Terms(List.of("tab\there"), false)))));
        writer.write(new FileReport.Failed(Path.of("x"), FailureReason.IO_ERROR, "multi\nline"));

        List<String> lines = out.toString().lines().toList();
        assertThat(lines).hasSize(2);
        assertThat(MAPPER.readTree(lines.getFirst()).path("file").asText()).isEqualTo("we\"ird\nname.txt");
        assertThat(MAPPER.readTree(lines.get(1)).path("error").path("detail").asText()).isEqualTo("multi\nline");
    }
}
