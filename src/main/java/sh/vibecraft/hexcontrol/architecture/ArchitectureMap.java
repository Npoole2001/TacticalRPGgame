package sh.vibecraft.hexcontrol.architecture;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * The "Constant Chart" - continuously updated architecture map per spec.
 * Provides shared mental model of modules, boundaries, dependencies, and ownership.
 */
public class ArchitectureMap {

    private final List<ModuleNode> modules;
    private final List<DependencyEdge> dependencies;
    private final Map<String, String> ownershipTags;  // module path -> agent ID
    private final Map<String, EntryPoint> entryPoints;
    private long lastUpdateTime;
    private String repoRoot;

    public ArchitectureMap() {
        this.modules = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.ownershipTags = new HashMap<>();
        this.entryPoints = new HashMap<>();
    }

    /**
     * Scan a codebase and build the architecture map.
     */
    public void scanCodebase(Path root) throws IOException {
        this.repoRoot = root.toString();
        modules.clear();
        dependencies.clear();

        // Find all Java packages
        try (var stream = Files.walk(root)) {
            Map<String, ModuleNode> moduleMap = new HashMap<>();

            stream.filter(p -> p.toString().endsWith(".java"))
                  .forEach(javaFile -> {
                      try {
                          processJavaFile(javaFile, root, moduleMap);
                      } catch (IOException e) {
                          System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                      }
                  });

            modules.addAll(moduleMap.values());
        }

        // Identify key entry points
        identifyEntryPoints();

        lastUpdateTime = System.currentTimeMillis();
    }

    private void processJavaFile(Path javaFile, Path root, Map<String, ModuleNode> moduleMap) throws IOException {
        String content = Files.readString(javaFile);
        String relativePath = root.relativize(javaFile).toString();

        // Extract package
        String packageName = extractPackage(content);
        if (packageName == null) return;

        // Get or create module node
        ModuleNode module = moduleMap.computeIfAbsent(packageName, ModuleNode::new);
        module.files.add(relativePath);

        // Extract imports and track dependencies
        List<String> imports = extractImports(content);
        for (String importLine : imports) {
            // Skip standard library imports
            if (importLine.startsWith("java.") || importLine.startsWith("javax.")) continue;

            // Extract package from import
            String importPackage = extractPackageFromImport(importLine);
            if (importPackage != null && !importPackage.equals(packageName)) {
                DependencyEdge edge = new DependencyEdge(packageName, importPackage);
                if (!dependencies.contains(edge)) {
                    dependencies.add(edge);
                }
            }
        }

        // Extract class/interface names
        extractClassNames(content).forEach(module.classes::add);
    }

    private String extractPackage(String content) {
        Pattern pattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<String> extractImports(String content) {
        List<String> imports = new ArrayList<>();
        Pattern pattern = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private String extractPackageFromImport(String importLine) {
        int lastDot = importLine.lastIndexOf('.');
        if (lastDot > 0) {
            String potentialPackage = importLine.substring(0, lastDot);
            // Check if it's a class import (ends with capital) or package import
            String lastPart = potentialPackage.substring(potentialPackage.lastIndexOf('.') + 1);
            if (Character.isUpperCase(lastPart.charAt(0))) {
                // It's a class, get parent package
                int prevDot = potentialPackage.lastIndexOf('.');
                return prevDot > 0 ? potentialPackage.substring(0, prevDot) : potentialPackage;
            }
            return potentialPackage;
        }
        return null;
    }

    private List<String> extractClassNames(String content) {
        List<String> classes = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        return classes;
    }

    private void identifyEntryPoints() {
        entryPoints.clear();

        for (ModuleNode module : modules) {
            String pkg = module.packageName;

            // Identify by naming conventions
            if (pkg.endsWith(".core")) {
                entryPoints.put("core", new EntryPoint("Core", pkg, "Main engine/orchestrator"));
            } else if (pkg.endsWith(".render") || pkg.contains(".render.")) {
                entryPoints.put("renderer", new EntryPoint("Renderer", pkg, "Graphics/rendering subsystem"));
            } else if (pkg.endsWith(".persistence")) {
                entryPoints.put("persistence", new EntryPoint("Persistence", pkg, "State storage"));
            } else if (pkg.endsWith(".security")) {
                entryPoints.put("security", new EntryPoint("Security", pkg, "Security/secrets"));
            } else if (pkg.endsWith(".orchestrator")) {
                entryPoints.put("orchestrator", new EntryPoint("Orchestrator", pkg, "Agent coordination"));
            } else if (pkg.endsWith(".git") || pkg.contains(".git.")) {
                entryPoints.put("git", new EntryPoint("Git Integration", pkg, "Version control"));
            } else if (pkg.endsWith(".ui")) {
                entryPoints.put("ui", new EntryPoint("UI", pkg, "User interface"));
            } else if (pkg.endsWith(".agent")) {
                entryPoints.put("agent", new EntryPoint("Agent Model", pkg, "Agent data model"));
            }
        }
    }

    /**
     * Set ownership of a module to an agent.
     */
    public void setOwnership(String modulePath, String agentId) {
        ownershipTags.put(modulePath, agentId);
    }

    /**
     * Clear ownership of a module.
     */
    public void clearOwnership(String modulePath) {
        ownershipTags.remove(modulePath);
    }

    /**
     * Get the owner agent of a module.
     */
    public String getOwner(String modulePath) {
        return ownershipTags.get(modulePath);
    }

    /**
     * Check if an agent can modify a module (owns it or unowned).
     */
    public boolean canModify(String agentId, String modulePath) {
        String owner = ownershipTags.get(modulePath);
        return owner == null || owner.equals(agentId);
    }

    /**
     * Export map to JSON for agent consumption.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"repoRoot\": \"").append(escapeJson(repoRoot)).append("\",\n");
        sb.append("  \"lastUpdate\": ").append(lastUpdateTime).append(",\n");

        // Modules
        sb.append("  \"modules\": [\n");
        for (int i = 0; i < modules.size(); i++) {
            ModuleNode m = modules.get(i);
            sb.append("    {\"package\": \"").append(m.packageName).append("\", ");
            sb.append("\"files\": ").append(m.files.size()).append(", ");
            sb.append("\"classes\": [");
            sb.append(m.classes.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", ")));
            sb.append("]}");
            if (i < modules.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Dependencies
        sb.append("  \"dependencies\": [\n");
        for (int i = 0; i < dependencies.size(); i++) {
            DependencyEdge d = dependencies.get(i);
            sb.append("    {\"from\": \"").append(d.from).append("\", \"to\": \"").append(d.to).append("\"}");
            if (i < dependencies.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Entry points
        sb.append("  \"entryPoints\": {\n");
        int epCount = 0;
        for (Map.Entry<String, EntryPoint> entry : entryPoints.entrySet()) {
            EntryPoint ep = entry.getValue();
            sb.append("    \"").append(entry.getKey()).append("\": {");
            sb.append("\"name\": \"").append(ep.name).append("\", ");
            sb.append("\"package\": \"").append(ep.packagePath).append("\", ");
            sb.append("\"description\": \"").append(ep.description).append("\"}");
            if (++epCount < entryPoints.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  },\n");

        // Ownership
        sb.append("  \"ownership\": {\n");
        int ownerCount = 0;
        for (Map.Entry<String, String> entry : ownershipTags.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            if (++ownerCount < ownershipTags.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n");

        sb.append("}");
        return sb.toString();
    }

    /**
     * Get a human-readable summary.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Architecture Map ===\n");
        sb.append(String.format("Modules: %d | Dependencies: %d | Entry Points: %d\n\n",
            modules.size(), dependencies.size(), entryPoints.size()));

        sb.append("Entry Points:\n");
        for (Map.Entry<String, EntryPoint> entry : entryPoints.entrySet()) {
            EntryPoint ep = entry.getValue();
            sb.append(String.format("  [%s] %s - %s\n", entry.getKey(), ep.name, ep.description));
        }

        sb.append("\nModule Ownership:\n");
        if (ownershipTags.isEmpty()) {
            sb.append("  (no ownership assigned)\n");
        } else {
            for (Map.Entry<String, String> entry : ownershipTags.entrySet()) {
                sb.append(String.format("  %s -> %s\n", entry.getKey(), entry.getValue()));
            }
        }

        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Getters
    public List<ModuleNode> getModules() { return Collections.unmodifiableList(modules); }
    public List<DependencyEdge> getDependencies() { return Collections.unmodifiableList(dependencies); }
    public Map<String, EntryPoint> getEntryPoints() { return Collections.unmodifiableMap(entryPoints); }
    public long getLastUpdateTime() { return lastUpdateTime; }

    /**
     * A module (package) in the architecture.
     */
    public static class ModuleNode {
        public final String packageName;
        public final List<String> files = new ArrayList<>();
        public final Set<String> classes = new HashSet<>();

        public ModuleNode(String packageName) {
            this.packageName = packageName;
        }
    }

    /**
     * A dependency edge between modules.
     */
    public record DependencyEdge(String from, String to) {}

    /**
     * A key entry point in the system.
     */
    public record EntryPoint(String name, String packagePath, String description) {}
}
