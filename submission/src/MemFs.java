import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class MemFs {

    private abstract static class Node {
        abstract boolean isFile();

        abstract boolean isLink();

        abstract boolean isDirectory();
    }

    private static final class FileNode extends Node {
        int size;

        @Override
        boolean isFile() {
            return true;
        }

        @Override
        boolean isLink() {
            return false;
        }

        @Override
        boolean isDirectory() {
            return false;
        }
    }

    private static final class DirNode extends Node {
        final TreeMap<String, Node> children = new TreeMap<>();

        @Override
        boolean isFile() {
            return false;
        }

        @Override
        boolean isLink() {
            return false;
        }

        @Override
        boolean isDirectory() {
            return true;
        }
    }

    private static final class LinkNode extends Node {
        final Node target;

        LinkNode(Node target) {
            this.target = target;
        }

        @Override
        boolean isFile() {
            return target.isFile();
        }

        @Override
        boolean isLink() {
            return true;
        }

        @Override
        boolean isDirectory() {
            return target.isDirectory();
        }
    }

    private final DirNode root = new DirNode();

    void mkdir(String path) {
        String normalized = CliParsers.normalizePath(path);
        if (normalized == null || "/".equals(normalized)) {
            return;
        }
        List<String> parts = CliParsers.splitPath(normalized);
        DirNode parent = resolveParentDir(parts);
        if (parent == null) {
            return;
        }

        String name = parts.get(parts.size() - 1);
        Node existing = parent.children.get(name);
        if (existing != null && existing.isDirectory() && !existing.isLink()) {
            return;
        }
        parent.children.put(name, new DirNode());
    }

    void touch(String path, int size) {
        String normalized = CliParsers.normalizePath(path);
        if (normalized == null || "/".equals(normalized)) {
            return;
        }
        List<String> parts = CliParsers.splitPath(normalized);
        DirNode parent = resolveParentDir(parts);
        if (parent == null) {
            return;
        }

        String name = parts.get(parts.size() - 1);
        Node existing = parent.children.get(name);
        if (existing != null && existing.isFile() && !existing.isLink()) {
            ((FileNode) existing).size = size;
            return;
        }
        FileNode file = new FileNode();
        file.size = size;
        parent.children.put(name, file);
    }

    List<String> ls(String path) {
        String normalized = CliParsers.normalizePath(path);
        if (normalized == null) {
            return Collections.emptyList();
        }
        ResolvedEntry entry = resolveEntry(normalized);
        if (entry == null) {
            return Collections.emptyList();
        }

        if (entry.node.isLink()) {
            LinkNode link = (LinkNode) entry.node;
            if (link.target.isFile()) {
                return Collections.singletonList(entry.name);
            }
            DirNode dir = (DirNode) link.target;
            return new ArrayList<>(dir.children.keySet());
        }
        if (entry.node.isFile()) {
            return Collections.singletonList(entry.name);
        }
        DirNode dir = (DirNode) entry.node;
        return new ArrayList<>(dir.children.keySet());
    }

    Integer info(String path) {
        String normalized = CliParsers.normalizePath(path);
        if (normalized == null) {
            return null;
        }
        ResolvedEntry entry = resolveEntry(normalized);
        if (entry == null) {
            return null;
        }
        return nodeSizeUnique(followLinks(entry.node), new HashSet<>());
    }

    List<String> find(String path, String name) {
        String normalized = CliParsers.normalizePath(path);
        if (normalized == null) {
            return Collections.emptyList();
        }
        ResolvedEntry entry = resolveEntry(normalized);
        if (entry == null) {
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();
        Set<DirNode> expandedDirs = new HashSet<>();
        Node node = entry.node;

        if (node.isLink()) {
            LinkNode link = (LinkNode) node;
            if (entry.name.equals(name)) {
                results.add(normalized);
            }
            if (link.target.isFile()) {
                Collections.sort(results);
                return results;
            }
            DirNode dir = (DirNode) link.target;
            searchDirectory(dir, canonicalPath(dir), name, expandedDirs, results);
        } else if (node.isFile()) {
            if (entry.name.equals(name)) {
                results.add(normalized);
            }
        } else {
            DirNode dir = (DirNode) node;
            if (entry.name.equals(name)) {
                results.add(normalized);
            }
            searchDirectory(dir, normalized, name, expandedDirs, results);
        }

        Collections.sort(results);
        return results;
    }

    void rm(String path) {
        String normalized = CliParsers.normalizePath(path);
        if (normalized == null || "/".equals(normalized)) {
            return;
        }
        List<String> parts = CliParsers.splitPath(normalized);
        if (parts.isEmpty()) {
            return;
        }
        DirNode parent = resolveParentDir(parts);
        if (parent == null) {
            return;
        }
        String name = parts.get(parts.size() - 1);
        Node node = parent.children.get(name);
        if (node == null) {
            return;
        }
        if (node.isDirectory() && !node.isLink()) {
            DirNode dir = (DirNode) node;
            if (!dir.children.isEmpty()) {
                return;
            }
        }
        parent.children.remove(name);
    }

    void link(String srcPath, String dstPath) {
        String src = CliParsers.normalizePath(srcPath);
        String dst = CliParsers.normalizePath(dstPath);
        if (src == null || dst == null || "/".equals(dst)) {
            return;
        }
        ResolvedEntry srcEntry = resolveEntry(src);
        if (srcEntry == null) {
            return;
        }
        List<String> dstParts = CliParsers.splitPath(dst);
        DirNode parent = resolveParentDir(dstParts);
        if (parent == null) {
            return;
        }
        Node target = followLinks(srcEntry.node);
        String name = dstParts.get(dstParts.size() - 1);
        parent.children.put(name, new LinkNode(target));
    }

    private void searchDirectory(
            DirNode dir,
            String dirPath,
            String name,
            Set<DirNode> expandedDirs,
            List<String> results) {
        if (!expandedDirs.add(dir)) {
            return;
        }
        for (Map.Entry<String, Node> childEntry : dir.children.entrySet()) {
            String childName = childEntry.getKey();
            Node child = childEntry.getValue();
            String childPath = joinPath(dirPath, childName);

            if (child.isLink()) {
                LinkNode link = (LinkNode) child;
                if (childName.equals(name)) {
                    results.add(childPath);
                }
                if (link.target.isFile()) {
                    continue;
                }
                DirNode targetDir = (DirNode) link.target;
                searchDirectory(targetDir, canonicalPath(targetDir), name, expandedDirs, results);
            } else if (child.isFile()) {
                if (childName.equals(name)) {
                    results.add(childPath);
                }
            } else {
                if (childName.equals(name)) {
                    results.add(childPath);
                }
                DirNode childDir = (DirNode) child;
                searchDirectory(childDir, childPath, name, expandedDirs, results);
            }
        }
    }

    private String canonicalPath(DirNode dir) {
        if (dir == root) {
            return "/";
        }
        Map<DirNode, String> cache = new IdentityHashMap<>();
        cache.put(root, "/");
        buildCanonicalPaths(root, "/", cache);
        return cache.get(dir);
    }

    private void buildCanonicalPaths(DirNode dir, String path, Map<DirNode, String> cache) {
        for (Map.Entry<String, Node> entry : dir.children.entrySet()) {
            String childPath = joinPath(path, entry.getKey());
            Node child = entry.getValue();
            if (child.isLink()) {
                continue;
            }
            if (child.isDirectory()) {
                DirNode childDir = (DirNode) child;
                cache.putIfAbsent(childDir, childPath);
                buildCanonicalPaths(childDir, childPath, cache);
            }
        }
    }

    private static String joinPath(String parent, String name) {
        if ("/".equals(parent)) {
            return "/" + name;
        }
        return parent + "/" + name;
    }

    private ResolvedEntry resolveEntry(String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return new ResolvedEntry(root, "");
        }
        List<String> parts = CliParsers.splitPath(normalizedPath);
        DirNode current = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            Node child = current.children.get(parts.get(i));
            if (child == null) {
                return null;
            }
            Node resolved = followLinks(child);
            if (!resolved.isDirectory()) {
                return null;
            }
            current = (DirNode) resolved;
        }
        String name = parts.get(parts.size() - 1);
        Node node = current.children.get(name);
        if (node == null) {
            return null;
        }
        return new ResolvedEntry(node, name);
    }

    private DirNode resolveParentDir(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        DirNode current = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            Node child = current.children.get(parts.get(i));
            if (child == null) {
                return null;
            }
            Node resolved = followLinks(child);
            if (!resolved.isDirectory()) {
                return null;
            }
            current = (DirNode) resolved;
        }
        return current;
    }

    private static Node followLinks(Node node) {
        while (node.isLink()) {
            node = ((LinkNode) node).target;
        }
        return node;
    }

    private static int nodeSizeUnique(Node node, Set<Node> visited) {
        node = followLinks(node);
        if (visited.contains(node)) {
            return 0;
        }
        visited.add(node);
        if (node.isFile()) {
            return ((FileNode) node).size;
        }
        int total = 0;
        DirNode dir = (DirNode) node;
        for (Node child : dir.children.values()) {
            total += nodeSizeUnique(child, visited);
        }
        return total;
    }

    private static final class ResolvedEntry {
        final Node node;
        final String name;

        ResolvedEntry(Node node, String name) {
            this.node = node;
            this.name = name;
        }
    }
}
