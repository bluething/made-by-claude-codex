package io.github.bluething.searchindexer.report;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteFeature;
import io.github.bluething.searchindexer.indexing.FileReport;
import io.github.bluething.searchindexer.rule.RuleResult;
import io.github.bluething.searchindexer.rule.RuleValue;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes one JSON object per line (<a href="https://jsonlines.org">JSON Lines</a>), so consumers can stream it.
 * This is the machine-readable contract; field names are stable.
 *
 * <pre>{@code
 * {"file":"a.txt","status":"indexed","results":{"uppercase-word-count":{"count":3},"long-words":{"terms":["Indexing"],"truncated":false}}}
 * {"file":"b.txt","status":"failed","error":{"reason":"not_found","message":"file not found","detail":"b.txt"}}
 * }</pre>
 *
 * Uses Jackson's streaming API directly, so domain types stay free of serialisation annotations.
 */
final class JsonLinesReportWriter implements ReportWriter {

    private static final JsonFactory JSON = JsonFactory.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET) // the caller owns stdout
            .build();

    private final Writer out;

    JsonLinesReportWriter(Writer out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void write(FileReport report) throws IOException {
        try (JsonGenerator json = JSON.createGenerator(out)) {
            json.writeStartObject();
            json.writeStringField("file", report.file().toString());
            switch (report) {
                case FileReport.Indexed indexed -> writeIndexed(json, indexed);
                case FileReport.Failed failed -> writeFailed(json, failed);
            }
            json.writeEndObject();
        }
        out.write('\n'); // JSON Lines mandates \n, not the platform line separator
        out.flush();
    }

    private static void writeIndexed(JsonGenerator json, FileReport.Indexed report) throws IOException {
        json.writeStringField("status", "indexed");
        json.writeObjectFieldStart("results");
        for (RuleResult result : report.results()) {
            json.writeObjectFieldStart(result.ruleId());
            switch (result.value()) {
                case RuleValue.Count count -> json.writeNumberField("count", count.value());
                case RuleValue.Terms terms -> {
                    json.writeArrayFieldStart("terms");
                    for (String term : terms.values()) {
                        json.writeString(term);
                    }
                    json.writeEndArray();
                    json.writeBooleanField("truncated", terms.truncated());
                }
            }
            json.writeEndObject();
        }
        json.writeEndObject();
    }

    private static void writeFailed(JsonGenerator json, FileReport.Failed report) throws IOException {
        json.writeStringField("status", "failed");
        json.writeObjectFieldStart("error");
        json.writeStringField("reason", report.reason().name().toLowerCase(Locale.ROOT));
        json.writeStringField("message", report.reason().description());
        json.writeStringField("detail", report.detail());
        json.writeEndObject();
    }
}
