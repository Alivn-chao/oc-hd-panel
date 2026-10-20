package dev.bec.hdpanel.mcp;

public final class McpLimits {
    public static final int PACKET = 98304, QUEUE = 8, OUTPUT = 16384;
    private McpLimits() {}
    public static String path(String value) {
        if (value == null || value.length() > 512 || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("invalid OC path");
        for (String part : value.split("/")) if (part.equals("..")) throw new IllegalArgumentException("parent traversal forbidden");
        return value.startsWith("/") ? value.substring(1) : value;
    }
    public static String bounded(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("value too long");
        return value;
    }
}
