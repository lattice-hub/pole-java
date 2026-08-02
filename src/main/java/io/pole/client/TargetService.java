package io.pole.client;

import java.util.Objects;

public final class TargetService {
    private final String namespace;
    private final String service;

    private TargetService(Builder builder) {
        this.namespace = normalizeRequired("namespace", builder.namespace);
        this.service = normalizeRequired("service", builder.service);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getNamespace() {
        return namespace;
    }

    public String getService() {
        return service;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TargetService that)) {
            return false;
        }
        return namespace.equals(that.namespace) && service.equals(that.service);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, service);
    }

    @Override
    public String toString() {
        return "TargetService{"
                + "namespace='" + namespace + '\''
                + ", service='" + service + '\''
                + '}';
    }

    private static String normalizeRequired(String fieldName, String value) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        validateUnicodeScalars(fieldName, value);
        value.codePoints().forEach(codePoint -> {
            if (Character.getType(codePoint) == Character.CONTROL) {
                throw new IllegalArgumentException(fieldName + " must not contain Unicode Cc characters");
            }
        });

        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        String normalized = value.substring(start, end);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static void validateUnicodeScalars(String fieldName, String value) {
        for (int index = 0; index < value.length(); index++) {
            char codeUnit = value.charAt(index);
            if (Character.isHighSurrogate(codeUnit)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            fieldName + " must contain only Unicode scalar values");
                }
                index++;
            } else if (Character.isLowSurrogate(codeUnit)) {
                throw new IllegalArgumentException(
                        fieldName + " must contain only Unicode scalar values");
            }
        }
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
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

    public static final class Builder {
        private String namespace;
        private String service;

        private Builder() {
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public TargetService build() {
            return new TargetService(this);
        }
    }
}
