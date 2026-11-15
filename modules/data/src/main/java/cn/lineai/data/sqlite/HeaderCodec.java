package cn.lineai.data.sqlite;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.HttpHeader;

/**
 * Newline/tab text encoding shared by the MCP, provider and model stores.
 *
 * <p>One header per line, the name and the value separated by a tab. Tab, newline and carriage
 * return characters inside either part are flattened to spaces so a value cannot break the record
 * boundaries; blank header names are dropped because they cannot be sent on the wire.</p>
 */
final class HeaderCodec {

    private HeaderCodec() {
    }

    /**
     * Decodes the stored column into header pairs, skipping malformed lines.
     *
     * @param raw stored column value; null decodes as an empty list
     * @return decoded headers in storage order, never null
     */
    static List<HttpHeader> decode(String raw) {
        List<HttpHeader> result = new ArrayList<>();
        if (raw == null) return result;
        for (String line : raw.split("\n")) {
            int split = line.indexOf('\t');
            if (split <= 0) continue;
            String name = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            if (!name.isEmpty()) result.add(new HttpHeader(name, value));
        }
        return result;
    }

    /**
     * Encodes header pairs into the newline/tab-separated storage format.
     *
     * @param headers headers to encode; blank names are dropped
     * @return the storage column value, empty when no header is encodable
     */
    static String encode(List<HttpHeader> headers) {
        StringBuilder out = new StringBuilder();
        for (HttpHeader header : headers) {
            if (header.name.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(clean(header.name)).append('\t').append(clean(header.value));
        }
        return out.toString();
    }

    /**
     * Flattens record separators so one value cannot span two encoded records.
     *
     * @param value raw value; null becomes empty
     * @return the value without tab, newline or carriage return characters
     */
    static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
