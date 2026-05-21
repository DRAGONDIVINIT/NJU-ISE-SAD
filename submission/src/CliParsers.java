import java.util.ArrayList;
import java.util.List;

final class CliParsers {

    private CliParsers() {}

    static String normalizePath(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            return null;
        }
        if (path.length() == 1) {
            return "/";
        }
        List<String> stack = new ArrayList<>();
        int i = 0;
        int n = path.length();
        while (i < n) {
            while (i < n && path.charAt(i) == '/') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int j = i;
            while (j < n && path.charAt(j) != '/') {
                j++;
            }
            String seg = path.substring(i, j);
            i = j;
            if (".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                continue;
            }
            stack.add(seg);
        }
        if (stack.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : stack) {
            sb.append('/').append(part);
        }
        return sb.toString();
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
            int value = Integer.parseInt(token);
            if (value < 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
