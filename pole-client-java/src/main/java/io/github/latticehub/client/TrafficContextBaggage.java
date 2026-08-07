package io.github.latticehub.client;

import static io.github.latticehub.client.TrafficContextDiagnostic.BAGGAGE_TOO_LARGE;
import static io.github.latticehub.client.TrafficContextDiagnostic.CONTROL_CHARACTER;
import static io.github.latticehub.client.TrafficContextDiagnostic.DUPLICATE_BUCKET;
import static io.github.latticehub.client.TrafficContextDiagnostic.DUPLICATE_CAMPAIGN;
import static io.github.latticehub.client.TrafficContextDiagnostic.DUPLICATE_LANE;
import static io.github.latticehub.client.TrafficContextDiagnostic.DUPLICATE_VERSION;
import static io.github.latticehub.client.TrafficContextDiagnostic.EMPTY_LABEL;
import static io.github.latticehub.client.TrafficContextDiagnostic.INVALID_BAGGAGE;
import static io.github.latticehub.client.TrafficContextDiagnostic.INVALID_BUCKET;
import static io.github.latticehub.client.TrafficContextDiagnostic.INVALID_UTF8;
import static io.github.latticehub.client.TrafficContextDiagnostic.LABEL_TOO_LARGE;
import static io.github.latticehub.client.TrafficContextDiagnostic.MISSING_VERSION;
import static io.github.latticehub.client.TrafficContextDiagnostic.NON_CANONICAL_VALUE;
import static io.github.latticehub.client.TrafficContextDiagnostic.RESERVED_MEMBER_PROPERTIES;
import static io.github.latticehub.client.TrafficContextDiagnostic.SURROUNDING_WHITESPACE;
import static io.github.latticehub.client.TrafficContextDiagnostic.TOO_MANY_MEMBERS;
import static io.github.latticehub.client.TrafficContextDiagnostic.UNKNOWN_RESERVED_MEMBER;
import static io.github.latticehub.client.TrafficContextDiagnostic.UNSUPPORTED_VERSION;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TrafficContextBaggage {
    public static final String HEADER = "baggage";
    public static final String VERSION = "latticehub.traffic.version";
    public static final String CAMPAIGN = "latticehub.traffic.campaign";
    public static final String LANE = "latticehub.traffic.lane";
    public static final String BUCKET = "latticehub.traffic.bucket";

    private static final String PREFIX = "latticehub.traffic.";
    private static final int MAX_MEMBERS = 180;
    private static final int MAX_BYTES = 8192;

    private TrafficContextBaggage() {
    }

    public static Optional<TrafficContext> extract(Iterable<String> baggageHeaders) {
        Objects.requireNonNull(baggageHeaders, "baggageHeaders must not be null");
        Map<String, String> reserved = new LinkedHashMap<>();
        for (Member member : members(baggageHeaders)) {
            if (!isReservedPrefix(member.name())) {
                continue;
            }
            if (!isKnownReserved(member.name())) {
                throw error(UNKNOWN_RESERVED_MEMBER, "unknown TrafficContext baggage member: " + member.name());
            }
            if (member.hasProperties()) {
                throw error(
                        RESERVED_MEMBER_PROPERTIES,
                        "TrafficContext baggage members must not contain properties");
            }
            if (reserved.putIfAbsent(member.name(), member.value()) != null) {
                throw error(
                        duplicateDiagnostic(member.name()),
                        "duplicate TrafficContext baggage member: " + member.name());
            }
        }
        if (reserved.isEmpty()) {
            return Optional.empty();
        }
        String version = required(reserved, VERSION);
        if (!"1".equals(version)) {
            throw error(UNSUPPORTED_VERSION, "unsupported TrafficContext version");
        }
        String campaign = decodeLabel(reserved.get(CAMPAIGN));
        String lane = decodeLabel(reserved.get(LANE));
        Integer bucket = decodeBucket(reserved.get(BUCKET));
        return Optional.of(TrafficContext.builder().campaign(campaign).lane(lane).bucket(bucket).build());
    }

    public static Optional<String> inject(Iterable<String> baggageHeaders, TrafficContext explicitContext) {
        Objects.requireNonNull(baggageHeaders, "baggageHeaders must not be null");
        List<String> output = new ArrayList<>();
        for (Member member : members(baggageHeaders)) {
            if (!isReservedPrefix(member.name())) {
                output.add(member.raw());
            }
        }
        TrafficContext context = explicitContext != null ? explicitContext : TrafficContext.current().orElse(null);
        if (context != null && context.hasLabels()) {
            output.add(VERSION + "=1");
            context.getCampaign().ifPresent(value -> output.add(CAMPAIGN + "=" + encodeLabel(value)));
            context.getLane().ifPresent(value -> output.add(LANE + "=" + encodeLabel(value)));
            context.getBucket().ifPresent(value -> output.add(BUCKET + "=" + value));
        }
        if (output.size() > MAX_MEMBERS) {
            throw error(TOO_MANY_MEMBERS, "baggage must not contain more than 180 members");
        }
        String result = String.join(",", output);
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw error(BAGGAGE_TOO_LARGE, "baggage must not exceed 8192 bytes");
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    public static Map<String, String> inject(Map<String, String> carrier) {
        return inject(carrier, null);
    }

    public static Map<String, String> inject(Map<String, String> carrier, TrafficContext explicitContext) {
        Objects.requireNonNull(carrier, "carrier must not be null");
        List<String> baggageHeaders = new ArrayList<>();
        LinkedHashMap<String, String> output = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : carrier.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "carrier key must not be null");
            String value = Objects.requireNonNull(entry.getValue(), "carrier value must not be null");
            if (HEADER.equalsIgnoreCase(name)) {
                baggageHeaders.add(value);
            } else {
                output.put(name, value);
            }
        }
        TrafficContext context = explicitContext != null ? explicitContext : TrafficContext.current().orElse(null);
        inject(baggageHeaders, context).ifPresent(value -> output.put(HEADER, value));
        return Collections.unmodifiableMap(output);
    }

    static String validateLabel(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            throw error(EMPTY_LABEL, fieldName + " must not be empty");
        }
        validateUnicodeScalars(fieldName, value);
        if (isContractWhitespace(value.codePointAt(0))
                || isContractWhitespace(value.codePointBefore(value.length()))) {
            throw error(
                    SURROUNDING_WHITESPACE,
                    fieldName + " must not have leading or trailing whitespace");
        }
        value.codePoints().forEach(codePoint -> {
            if (Character.getType(codePoint) == Character.CONTROL) {
                throw error(CONTROL_CHARACTER, fieldName + " must not contain control characters");
            }
        });
        if (value.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw error(LABEL_TOO_LARGE, fieldName + " must not exceed 128 UTF-8 bytes");
        }
        return value;
    }

    private static void validateUnicodeScalars(String fieldName, String value) {
        for (int index = 0; index < value.length(); index++) {
            char codeUnit = value.charAt(index);
            if (Character.isHighSurrogate(codeUnit)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw error(INVALID_UTF8, fieldName + " must contain only Unicode scalar values");
                }
                index++;
            } else if (Character.isLowSurrogate(codeUnit)) {
                throw error(INVALID_UTF8, fieldName + " must contain only Unicode scalar values");
            }
        }
    }

    private static boolean isContractWhitespace(int codePoint) {
        return codePoint >= 0x0009 && codePoint <= 0x000D
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || codePoint >= 0x2000 && codePoint <= 0x200A
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }

    private static List<Member> members(Iterable<String> baggageHeaders) {
        List<String> headers = new ArrayList<>();
        for (String header : baggageHeaders) {
            headers.add(Objects.requireNonNull(header, "baggage header must not be null"));
        }
        if (headers.isEmpty()) {
            return List.of();
        }
        String combined = String.join(",", headers);
        if (combined.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw error(BAGGAGE_TOO_LARGE, "baggage must not exceed 8192 bytes");
        }
        List<Member> result = new ArrayList<>();
        int index = 0;
        while (index < combined.length()) {
            index = skipOws(combined, index);
            int memberStart = index;
            String name = token(combined, index, "baggage key");
            index += name.length();
            index = skipOws(combined, index);
            if (index >= combined.length() || combined.charAt(index++) != '=') {
                throw error(INVALID_BAGGAGE, "invalid baggage member");
            }
            index = skipOws(combined, index);
            int valueStart = index;
            index = consumeBaggageOctets(combined, index);
            String value = combined.substring(valueStart, index);
            int rawEnd = index;
            index = skipOws(combined, index);
            boolean hasProperties = false;
            while (index < combined.length() && combined.charAt(index) == ';') {
                hasProperties = true;
                index = skipOws(combined, index + 1);
                String propertyName = token(combined, index, "baggage property key");
                index += propertyName.length();
                index = skipOws(combined, index);
                if (index < combined.length() && combined.charAt(index) == '=') {
                    index = skipOws(combined, index + 1);
                    index = consumeBaggageOctets(combined, index);
                }
                rawEnd = index;
                index = skipOws(combined, index);
            }
            if (index < combined.length() && combined.charAt(index) != ',') {
                throw error(INVALID_BAGGAGE, "invalid baggage member");
            }
            result.add(new Member(name, value, hasProperties, combined.substring(memberStart, rawEnd)));
            if (result.size() > MAX_MEMBERS) {
                throw error(TOO_MANY_MEMBERS, "baggage must not contain more than 180 members");
            }
            if (index < combined.length()) {
                index++;
                if (index == combined.length()) {
                    throw error(INVALID_BAGGAGE, "baggage member must not be empty");
                }
            }
        }
        return result;
    }

    private static int skipOws(String input, int index) {
        while (index < input.length() && (input.charAt(index) == ' ' || input.charAt(index) == '\t')) {
            index++;
        }
        return index;
    }

    private static String token(String input, int index, String fieldName) {
        int start = index;
        while (index < input.length() && isToken(input.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw error(INVALID_BAGGAGE, fieldName + " must be an RFC token");
        }
        return input.substring(start, index);
    }

    private static int consumeBaggageOctets(String input, int index) {
        while (index < input.length() && isBaggageOctet(input.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isToken(char character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || "!#$%&'*+-.^_`|~".indexOf(character) >= 0;
    }

    private static boolean isBaggageOctet(char character) {
        return character == '!' || character >= '#' && character <= '+'
                || character >= '-' && character <= ':'
                || character >= '<' && character <= '['
                || character >= ']' && character <= '~';
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null) {
            throw error(MISSING_VERSION, "TrafficContext version is required when labels are present");
        }
        return value;
    }

    private static Integer decodeBucket(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("0|[1-9][0-9]*")) {
            throw error(INVALID_BUCKET, "bucket must be canonical decimal");
        }
        try {
            int bucket = Integer.parseInt(value);
            if (bucket > 9999) {
                throw error(INVALID_BUCKET, "bucket must be between 0 and 9999");
            }
            return bucket;
        } catch (NumberFormatException exception) {
            throw error(INVALID_BUCKET, "bucket must be between 0 and 9999", exception);
        }
    }

    private static String decodeLabel(String value) {
        if (value == null) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7F) {
                throw error(NON_CANONICAL_VALUE, "baggage value must be ASCII encoded");
            }
            if (character == '%') {
                if (index + 2 >= value.length()) {
                    throw error(NON_CANONICAL_VALUE, "invalid percent encoding");
                }
                int high = Character.digit(value.charAt(++index), 16);
                int low = Character.digit(value.charAt(++index), 16);
                if (high < 0 || low < 0) {
                    throw error(NON_CANONICAL_VALUE, "invalid percent encoding");
                }
                bytes.write((high << 4) | low);
            } else {
                bytes.write(character);
            }
        }
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw error(INVALID_UTF8, "baggage value must be valid UTF-8", exception);
        }
        validateLabel("label", decoded);
        if (!encodeLabel(decoded).equals(value)) {
            throw error(NON_CANONICAL_VALUE, "baggage value must use canonical encoding");
        }
        return decoded;
    }

    private static String encodeLabel(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = current & 0xFF;
            if (unsigned >= 'A' && unsigned <= 'Z'
                    || unsigned >= 'a' && unsigned <= 'z'
                    || unsigned >= '0' && unsigned <= '9'
                    || unsigned == '-' || unsigned == '.' || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0x0F, 16)));
            }
        }
        return encoded.toString();
    }

    private static boolean isReservedPrefix(String name) {
        return name.startsWith(PREFIX);
    }

    private static boolean isKnownReserved(String name) {
        return VERSION.equals(name) || CAMPAIGN.equals(name) || LANE.equals(name) || BUCKET.equals(name);
    }

    private static TrafficContextDiagnostic duplicateDiagnostic(String name) {
        if (VERSION.equals(name)) {
            return DUPLICATE_VERSION;
        }
        if (CAMPAIGN.equals(name)) {
            return DUPLICATE_CAMPAIGN;
        }
        if (LANE.equals(name)) {
            return DUPLICATE_LANE;
        }
        if (BUCKET.equals(name)) {
            return DUPLICATE_BUCKET;
        }
        return INVALID_BAGGAGE;
    }

    private static TrafficContextException error(TrafficContextDiagnostic diagnostic, String message) {
        return new TrafficContextException(diagnostic, message);
    }

    private static TrafficContextException error(
            TrafficContextDiagnostic diagnostic,
            String message,
            Throwable cause) {
        return new TrafficContextException(diagnostic, message, cause);
    }

    private record Member(String name, String value, boolean hasProperties, String raw) {
    }
}
