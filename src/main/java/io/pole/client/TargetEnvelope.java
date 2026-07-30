package io.pole.client;

import java.util.Objects;

public final class TargetEnvelope {
    public static final String VERSION = "1";

    private final String namespace;
    private final String service;
    private final String protocol;
    private final String group;
    private final String serviceVersion;
    private final String method;
    private final String originalEndpoint;

    private TargetEnvelope(Builder builder) {
        this.namespace = normalizeRequired("namespace", builder.namespace);
        this.service = normalizeRequired("service", builder.service);
        this.protocol = normalizeOptional("protocol", builder.protocol);
        this.group = normalizeOptional("group", builder.group);
        this.serviceVersion = normalizeOptional("serviceVersion", builder.serviceVersion);
        this.method = normalizeOptional("method", builder.method);
        this.originalEndpoint = normalizeOptional("originalEndpoint", builder.originalEndpoint);
        if (originalEndpoint != null) {
            EndpointValidator.validate(originalEndpoint);
        }
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

    public String getProtocol() {
        return protocol;
    }

    public String getGroup() {
        return group;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public String getMethod() {
        return method;
    }

    public String getOriginalEndpoint() {
        return originalEndpoint;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TargetEnvelope that)) {
            return false;
        }
        return namespace.equals(that.namespace)
                && service.equals(that.service)
                && Objects.equals(protocol, that.protocol)
                && Objects.equals(group, that.group)
                && Objects.equals(serviceVersion, that.serviceVersion)
                && Objects.equals(method, that.method)
                && Objects.equals(originalEndpoint, that.originalEndpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                namespace,
                service,
                protocol,
                group,
                serviceVersion,
                method,
                originalEndpoint);
    }

    @Override
    public String toString() {
        return "TargetEnvelope{"
                + "namespace='" + namespace + '\''
                + ", service='" + service + '\''
                + ", protocol='" + protocol + '\''
                + ", group='" + group + '\''
                + ", serviceVersion='" + serviceVersion + '\''
                + ", method='" + method + '\''
                + ", originalEndpoint='" + originalEndpoint + '\''
                + '}';
    }

    private static String normalizeRequired(String fieldName, String value) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = normalize(fieldName, value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(fieldName, value);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalize(String fieldName, String value) {
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
        return value.substring(start, end);
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
        private String protocol;
        private String group;
        private String serviceVersion;
        private String method;
        private String originalEndpoint;

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

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder originalEndpoint(String originalEndpoint) {
            this.originalEndpoint = originalEndpoint;
            return this;
        }

        public TargetEnvelope build() {
            return new TargetEnvelope(this);
        }
    }

    private static final class EndpointValidator {
        private EndpointValidator() {
        }

        private static void validate(String endpoint) {
            String port;
            if (endpoint.startsWith("[")) {
                int closingBracket = endpoint.indexOf(']');
                if (closingBracket <= 1
                        || closingBracket + 1 >= endpoint.length()
                        || endpoint.charAt(closingBracket + 1) != ':') {
                    invalid(endpoint);
                }
                String host = endpoint.substring(1, closingBracket);
                if (!isValidIpv6(host)) {
                    invalid(endpoint);
                }
                port = endpoint.substring(closingBracket + 2);
            } else {
                int separator = endpoint.lastIndexOf(':');
                if (separator <= 0 || separator == endpoint.length() - 1) {
                    invalid(endpoint);
                }
                String host = endpoint.substring(0, separator);
                if (host.indexOf(':') >= 0 || !isValidHost(host)) {
                    invalid(endpoint);
                }
                port = endpoint.substring(separator + 1);
            }
            validatePort(endpoint, port);
        }

        private static boolean isValidHost(String host) {
            return host.codePoints().noneMatch(codePoint ->
                    isUnicodeWhitespace(codePoint)
                            || codePoint == '/'
                            || codePoint == '\\'
                            || codePoint == '['
                            || codePoint == ']'
                            || codePoint == '@'
                            || codePoint == '?'
                            || codePoint == '#'
                            || codePoint == '%');
        }

        private static boolean isValidIpv6(String host) {
            int compression = host.indexOf("::");
            if (compression != host.lastIndexOf("::")) {
                return false;
            }
            if (compression < 0) {
                return countIpv6Units(host, true) == 8;
            }

            String left = host.substring(0, compression);
            String right = host.substring(compression + 2);
            int leftUnits = countIpv6Units(left, false);
            int rightUnits = countIpv6Units(right, true);
            return leftUnits >= 0 && rightUnits >= 0 && leftUnits + rightUnits < 8;
        }

        private static int countIpv6Units(String value, boolean allowIpv4Tail) {
            if (value.isEmpty()) {
                return 0;
            }
            String[] groups = value.split(":", -1);
            int units = 0;
            for (int index = 0; index < groups.length; index++) {
                String group = groups[index];
                if (group.isEmpty()) {
                    return -1;
                }
                boolean last = index == groups.length - 1;
                if (last && allowIpv4Tail && group.indexOf('.') >= 0) {
                    if (!isValidIpv4(group)) {
                        return -1;
                    }
                    units += 2;
                } else {
                    if (group.length() > 4 || !isHex(group)) {
                        return -1;
                    }
                    units++;
                }
            }
            return units;
        }

        private static boolean isHex(String value) {
            return value.codePoints().allMatch(codePoint ->
                    codePoint >= '0' && codePoint <= '9'
                            || codePoint >= 'a' && codePoint <= 'f'
                            || codePoint >= 'A' && codePoint <= 'F');
        }

        private static boolean isValidIpv4(String value) {
            String[] octets = value.split("\\.", -1);
            if (octets.length != 4) {
                return false;
            }
            for (String octet : octets) {
                if (octet.isEmpty() || !octet.codePoints().allMatch(codePoint ->
                        codePoint >= '0' && codePoint <= '9')) {
                    return false;
                }
                if (octet.length() > 1 && octet.charAt(0) == '0') {
                    return false;
                }
                try {
                    if (Integer.parseInt(octet) > 255) {
                        return false;
                    }
                } catch (NumberFormatException exception) {
                    return false;
                }
            }
            return true;
        }

        private static void validatePort(String endpoint, String port) {
            if (port.isEmpty() || !port.codePoints().allMatch(codePoint ->
                    codePoint >= '0' && codePoint <= '9')) {
                invalid(endpoint);
            }
            if (port.length() > 1 && port.charAt(0) == '0') {
                invalid(endpoint);
            }
            try {
                int parsedPort = Integer.parseInt(port);
                if (parsedPort < 1 || parsedPort > 65_535) {
                    invalid(endpoint);
                }
            } catch (NumberFormatException exception) {
                invalid(endpoint);
            }
        }

        private static void invalid(String endpoint) {
            throw new IllegalArgumentException(
                    "originalEndpoint must be host:port or [ipv6]:port with port in range 1..65535: "
                            + endpoint);
        }
    }
}
