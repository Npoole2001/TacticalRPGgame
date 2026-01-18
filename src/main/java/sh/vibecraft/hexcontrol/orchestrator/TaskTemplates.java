package sh.vibecraft.hexcontrol.orchestrator;

import sh.vibecraft.hexcontrol.agent.AgentRole;

import java.util.*;

/**
 * Task templates for common workflows.
 * Per spec: feature/refactor/tests/docs templates.
 */
public class TaskTemplates {

    private final Map<String, TaskTemplate> templates;
    private final List<TaskTemplate> templateList;

    public TaskTemplates() {
        this.templates = new LinkedHashMap<>();
        this.templateList = new ArrayList<>();
        initializeDefaultTemplates();
    }

    private void initializeDefaultTemplates() {
        // Feature implementation template
        addTemplate(new TaskTemplate(
            "feature",
            "New Feature",
            "Implement a new feature end-to-end",
            """
                ## Feature: {title}

                ### Objective
                {description}

                ### Acceptance Criteria
                - [ ] Feature works as specified
                - [ ] Tests pass
                - [ ] No regressions introduced
                - [ ] Code follows project conventions

                ### Implementation Notes
                {notes}
                """,
            List.of(AgentRole.BACKEND_CODER, AgentRole.FRONTEND_CODER),
            Map.of(
                "title", "Feature title",
                "description", "What the feature should do",
                "notes", "Additional implementation notes"
            ),
            TemplateCategory.DEVELOPMENT
        ));

        // Bug fix template
        addTemplate(new TaskTemplate(
            "bugfix",
            "Bug Fix",
            "Fix an identified bug or issue",
            """
                ## Bug Fix: {title}

                ### Problem
                {problem}

                ### Expected Behavior
                {expected}

                ### Steps to Reproduce
                {steps}

                ### Fix Strategy
                - Identify root cause
                - Implement minimal fix
                - Add regression test
                - Verify fix doesn't break other functionality
                """,
            List.of(AgentRole.BACKEND_CODER, AgentRole.FRONTEND_CODER, AgentRole.QA_ENGINEER),
            Map.of(
                "title", "Bug title",
                "problem", "What's broken",
                "expected", "What should happen",
                "steps", "How to reproduce"
            ),
            TemplateCategory.DEVELOPMENT
        ));

        // Refactor template
        addTemplate(new TaskTemplate(
            "refactor",
            "Code Refactor",
            "Improve code structure without changing behavior",
            """
                ## Refactor: {title}

                ### Current State
                {current}

                ### Target State
                {target}

                ### Constraints
                - No behavior changes
                - All tests must pass
                - Incremental changes preferred

                ### Refactoring Steps
                1. Ensure test coverage exists
                2. Make incremental changes
                3. Run tests after each change
                4. Document architectural decisions
                """,
            List.of(AgentRole.TECH_LEAD, AgentRole.BACKEND_CODER),
            Map.of(
                "title", "Refactor title",
                "current", "Current code structure/issues",
                "target", "Desired structure"
            ),
            TemplateCategory.DEVELOPMENT
        ));

        // Test writing template
        addTemplate(new TaskTemplate(
            "tests",
            "Write Tests",
            "Add test coverage for a module or feature",
            """
                ## Tests: {title}

                ### Target Module
                {module}

                ### Test Types Needed
                - [ ] Unit tests
                - [ ] Integration tests
                - [ ] Edge case coverage

                ### Test Cases
                {cases}

                ### Coverage Goal
                Target: {coverage}% coverage for the module
                """,
            List.of(AgentRole.QA_ENGINEER, AgentRole.TESTER),
            Map.of(
                "title", "Test task title",
                "module", "Module/file to test",
                "cases", "Specific test cases",
                "coverage", "80"
            ),
            TemplateCategory.TESTING
        ));

        // Documentation template
        addTemplate(new TaskTemplate(
            "docs",
            "Documentation",
            "Write or update documentation",
            """
                ## Documentation: {title}

                ### Scope
                {scope}

                ### Audience
                {audience}

                ### Sections to Cover
                {sections}

                ### Guidelines
                - Clear, concise language
                - Include code examples where helpful
                - Keep consistent with existing docs
                - Add diagrams if they clarify concepts
                """,
            List.of(AgentRole.DOCS_ENGINEER),
            Map.of(
                "title", "Documentation title",
                "scope", "What to document",
                "audience", "Who will read this",
                "sections", "Key sections to include"
            ),
            TemplateCategory.DOCUMENTATION
        ));

        // Code review template
        addTemplate(new TaskTemplate(
            "review",
            "Code Review",
            "Review a PR or code changes",
            """
                ## Code Review: {pr_title}

                ### PR/Branch
                {pr_ref}

                ### Review Checklist
                - [ ] Code correctness
                - [ ] Test coverage
                - [ ] Performance implications
                - [ ] Security considerations
                - [ ] Code style/conventions
                - [ ] Documentation

                ### Focus Areas
                {focus}
                """,
            List.of(AgentRole.REVIEWER, AgentRole.TECH_LEAD),
            Map.of(
                "pr_title", "PR title",
                "pr_ref", "PR number or branch",
                "focus", "Specific areas to focus on"
            ),
            TemplateCategory.REVIEW
        ));

        // Performance optimization template
        addTemplate(new TaskTemplate(
            "performance",
            "Performance Optimization",
            "Improve performance of a specific area",
            """
                ## Performance: {title}

                ### Current Performance
                {current_metrics}

                ### Target Performance
                {target_metrics}

                ### Optimization Strategy
                1. Profile to identify bottlenecks
                2. Implement targeted optimizations
                3. Benchmark after each change
                4. Document trade-offs

                ### Constraints
                - Don't sacrifice readability unnecessarily
                - Maintain test coverage
                """,
            List.of(AgentRole.BACKEND_CODER, AgentRole.GRAPHICS_CODER),
            Map.of(
                "title", "Optimization title",
                "current_metrics", "Current performance metrics",
                "target_metrics", "Target performance goals"
            ),
            TemplateCategory.DEVELOPMENT
        ));

        // Security audit template
        addTemplate(new TaskTemplate(
            "security",
            "Security Audit",
            "Review code for security vulnerabilities",
            """
                ## Security Audit: {scope}

                ### Audit Scope
                {files}

                ### Check Categories
                - [ ] Input validation
                - [ ] Authentication/Authorization
                - [ ] Data handling
                - [ ] Secrets management
                - [ ] Dependencies
                - [ ] Logging (no sensitive data)

                ### Findings Template
                For each issue:
                - Severity: [Critical/High/Medium/Low]
                - Location: [file:line]
                - Description
                - Remediation
                """,
            List.of(AgentRole.TECH_LEAD, AgentRole.REVIEWER),
            Map.of(
                "scope", "What to audit",
                "files", "Files/modules to review"
            ),
            TemplateCategory.REVIEW
        ));

        // CI/CD setup template
        addTemplate(new TaskTemplate(
            "ci-setup",
            "CI/CD Configuration",
            "Set up or modify CI/CD pipeline",
            """
                ## CI/CD: {title}

                ### Pipeline Requirements
                {requirements}

                ### Stages
                - [ ] Build
                - [ ] Test
                - [ ] Lint/Static analysis
                - [ ] Deploy (if applicable)

                ### Configuration
                Target platform: {platform}

                ### Success Criteria
                - Pipeline runs on every PR
                - Build failures block merge
                - Test results visible
                """,
            List.of(AgentRole.OPS),
            Map.of(
                "title", "CI/CD task title",
                "requirements", "What the pipeline should do",
                "platform", "GitHub Actions / GitLab CI / etc."
            ),
            TemplateCategory.OPERATIONS
        ));

        // Research/investigation template
        addTemplate(new TaskTemplate(
            "research",
            "Research & Investigation",
            "Investigate a technical question or approach",
            """
                ## Research: {question}

                ### Background
                {context}

                ### Research Goals
                {goals}

                ### Deliverables
                - Summary of findings
                - Recommendations
                - Trade-off analysis
                - Proof of concept (if applicable)

                ### Time Box
                Max time: {hours} hours
                """,
            List.of(AgentRole.RESEARCHER, AgentRole.TECH_LEAD),
            Map.of(
                "question", "Research question",
                "context", "Why we need this",
                "goals", "What we want to learn",
                "hours", "4"
            ),
            TemplateCategory.RESEARCH
        ));
    }

    public void addTemplate(TaskTemplate template) {
        templates.put(template.id, template);
        templateList.add(template);
    }

    public TaskTemplate getTemplate(String id) {
        return templates.get(id);
    }

    public List<TaskTemplate> getAllTemplates() {
        return new ArrayList<>(templateList);
    }

    public List<TaskTemplate> getTemplatesByCategory(TemplateCategory category) {
        return templateList.stream()
            .filter(t -> t.category == category)
            .toList();
    }

    public List<TaskTemplate> getTemplatesForRole(AgentRole role) {
        return templateList.stream()
            .filter(t -> t.suggestedRoles.contains(role))
            .toList();
    }

    /**
     * Create a task from a template with filled parameters.
     */
    public TaskManager.Task createFromTemplate(String templateId, Map<String, String> parameters,
                                                String creatorAgentId, TaskManager taskManager) {
        TaskTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template: " + templateId);
        }

        // Fill in template variables
        String title = parameters.getOrDefault("title", template.name);
        String description = template.promptTemplate;

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            description = description.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Replace any unfilled variables with their defaults
        for (Map.Entry<String, String> entry : template.variables.entrySet()) {
            description = description.replace("{" + entry.getKey() + "}", "[" + entry.getValue() + "]");
        }

        return taskManager.createTask(title, description, creatorAgentId);
    }

    /**
     * Template category for organization.
     */
    public enum TemplateCategory {
        DEVELOPMENT("Development"),
        TESTING("Testing"),
        DOCUMENTATION("Documentation"),
        REVIEW("Review"),
        OPERATIONS("Operations"),
        RESEARCH("Research");

        private final String displayName;

        TemplateCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Task template definition.
     */
    public static class TaskTemplate {
        public final String id;
        public final String name;
        public final String description;
        public final String promptTemplate;
        public final List<AgentRole> suggestedRoles;
        public final Map<String, String> variables;  // variable name -> description
        public final TemplateCategory category;

        public TaskTemplate(String id, String name, String description, String promptTemplate,
                            List<AgentRole> suggestedRoles, Map<String, String> variables,
                            TemplateCategory category) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.promptTemplate = promptTemplate;
            this.suggestedRoles = suggestedRoles;
            this.variables = variables;
            this.category = category;
        }

        public List<String> getRequiredVariables() {
            return new ArrayList<>(variables.keySet());
        }
    }
}
