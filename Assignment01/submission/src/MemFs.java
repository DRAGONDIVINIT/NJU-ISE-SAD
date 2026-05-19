import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

final class MemFs {

    private abstract static class Node {
        abstract boolean isFile();
    }

    private static final class FileNode extends Node {
        int size;

        @Override
        boolean isFile() {
            return true;
        }
    }

    private static final class DirNode extends Node {
        final TreeMap<String, Node> children = new TreeMap<>();

        @Override
        boolean isFile() {
            return false;
        }
    }

    private final DirNode root = new DirNode();

    void mkdir(String path) {
        if (!CliParsers.isValidPath(path)) {
            return;
        }
        List<String> parts = CliParsers.splitPath(path);
        if (parts.isEmpty()) {
            return;
        }

        DirNode parent = root;
        for (int i = 0; i + 1 < parts.size(); i++) {
            Node child = parent.children.get(parts.get(i));
            if (child == null || child.isFile()) {
                return;
            }
            parent = (DirNode) child;
        }

        String name = parts.get(parts.size() - 1);
        Node existing = parent.children.get(name);
        if (existing != null && !existing.isFile()) {
            return;
        }
        parent.children.put(name, new DirNode());
    }

    void touch(String path, int size) {
        if (!CliParsers.isValidPath(path)) {
            return;
        }
        List<String> parts = CliParsers.splitPath(path);
        if (parts.isEmpty()) {
            return;
        }

        DirNode parent = root;
        for (int i = 0; i + 1 < parts.size(); i++) {
            Node child = parent.children.get(parts.get(i));
            if (child == null || child.isFile()) {
                return;
            }
            parent = (DirNode) child;
        }

        FileNode file = new FileNode();
        file.size = size;
        parent.children.put(parts.get(parts.size() - 1), file);
    }

    List<String> ls(String path) {
        if (!CliParsers.isValidPath(path)) {
            return Collections.emptyList();
        }
        Node node = resolveNode(path);
        if (node == null) {
            return Collections.emptyList();
        }

        List<String> parts = CliParsers.splitPath(path);
        if (node.isFile()) {
            return Collections.singletonList(parts.get(parts.size() - 1));
        }

        DirNode dir = (DirNode) node;
        return new ArrayList<>(dir.children.keySet());
    }

    Integer info(String path) {
        if (!CliParsers.isValidPath(path)) {
            return null;
        }
        Node node = resolveNode(path);
        if (node == null) {
            return null;
        }
        return nodeSize(node);
    }

    private Node resolveNode(String path) {
        if ("/".equals(path)) {
            return root;
        }
        List<String> parts = CliParsers.splitPath(path);
        Node cur = root;
        for (String part : parts) {
            DirNode dir = (DirNode) cur;
            Node next = dir.children.get(part);
            if (next == null) {
                return null;
            }
            cur = next;
        }
        return cur;
    }

    private static int nodeSize(Node node) {
        if (node.isFile()) {
            return ((FileNode) node).size;
        }
        int total = 0;
        DirNode dir = (DirNode) node;
        for (Node child : dir.children.values()) {
            total += nodeSize(child);
        }
        return total;
    }
}
