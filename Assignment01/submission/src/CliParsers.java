import java.util.ArrayList;
import java.util.List;

final class CliParsers {

    private CliParsers() {}

    static boolean isValidPath(String path) {
        if (path.contains("//")) {
            return false;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return false;
        }
        if ("/".equals(path)) {
            return true;
        }
        int start = 1;
        while (start < path.length()) {
            int end = path.indexOf('/', start);
            if (end == -1) {
                end = path.length();
            }
            String segment = path.substring(start, end);
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
            start = end + 1;
        }
        return true;
    }

    static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        if ("/".equals(path)) {
            return parts;
        }
        int start = 1;
        while (start < path.length()) {
            int end = path.indexOf('/', start);
            if (end == -1) {
                end = path.length();
            }
            parts.add(path.substring(start, end));
            start = end + 1;
        }
        return parts;
    }

    static Integer parseSize(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
