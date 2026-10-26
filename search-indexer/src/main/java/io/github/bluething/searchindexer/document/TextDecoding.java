package io.github.bluething.searchindexer.document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * Strict decoding helpers.
 *
 * <p>Java's convenience APIs ({@code new String(bytes, cs)}, {@code InputStreamReader(in, cs)}) silently replace
 * invalid bytes with U+FFFD. For an indexer that is data corruption, so every decoder here reports errors instead.
 */
final class TextDecoding {

    private static final char BYTE_ORDER_MARK = '﻿';

    private TextDecoding() {
    }

    static Reader strictReader(InputStream in, Charset charset) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, strictDecoder(charset)));
        skipByteOrderMark(reader);
        return reader;
    }

    static String strictDecode(byte[] bytes, Charset charset) throws CharacterCodingException {
        String text = strictDecoder(charset).decode(ByteBuffer.wrap(bytes)).toString();
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }

    private static CharsetDecoder strictDecoder(Charset charset) {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private static void skipByteOrderMark(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
    }
}
