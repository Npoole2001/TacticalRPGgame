package sh.vibecraft.hexcontrol.orchestrator;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages tasks, assignments, and the staged integration pipeline per spec.
 * Pipeline: Plan -> Propose diff -> Apply diff -> Run checks -> Review -> Merge/PR
 */
public class TaskManager {

    private final Map<String, Task> tasks;
    private final Map<String, List<String>> agentTasks;  // agent ID -> task IDs
    private final BlockingQueue<Task> pendingQueue;
    private final List<TaskListener> listeners;

    public TaskManager() {
        this.tasks = new ConcurrentHashMap<>();
        this.agentTasks = new ConcurrentHashMap<>();
        this.pendingQueue = new LinkedBlockingQueue<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Create a new task.
     */
    public Task createTask(String title, String description, String creatorAgentId) {
        Task task = new Task(title, description, creatorAgentId);
        tasks.put(task.id, task);
        notifyListeners(TaskEvent.CREATED, task);
        return task;
    }

    /**
     * Assign a task to an agent.
     */
    public void assignTask(String taskId, String agentId) {
        Task task = tasks.get(taskId);
        if (task == null) return;

        task.assignedAgentId = agentId;
        task.state = TaskState.ASSIGNED;

        agentTasks.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(taskId);
        notifyListeners(TaskEvent.ASSIGNED, task);
    }

    /**
     * Move task to next pipeline stage.
     */
    public void advanceTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) return;

        TaskState nextState = switch (task.state) {
            case CREATED, ASSIGNED -> TaskState.PLANNING;
            case PLANNING -> TaskState.PROPOSING;
            case PROPOSING -> TaskState.PROPOSED;
            case PROPOSED -> TaskState.APPLYING;
            case APPLYING -> TaskState.APPLIED;
            case APPLIED -> TaskState.CHECKING;
            case CHECKING -> TaskState.REVIEWING;
            case REVIEWING -> TaskState.APPROVED;
            case APPROVED -> TaskState.MERGING;
            case MERGING -> TaskState.COMPLETED;
            default -> task.state;
        };

        task.state = nextState;
        task.stateHistory.add(new StateTransition(task.state, System.currentTimeMillis()));
        notifyListeners(TaskEvent.STATE_CHANGED, task);
    }

    /**
     * Mark task as needing user input.
     */
    public void requestInput(String taskId, String question) {
        Task task = tasks.get(taskId);
        if (task == null) return;

        task.state = TaskState.NEEDS_INPUT;
        task.pendingQuestion = question;
        notifyListeners(TaskEvent.NEEDS_INPUT, task);
    }

    /**
     * Provide input to a blocked task.
     */
    public void provideInput(String taskId, String answer) {
        Task task = tasks.get(taskId);
        if (task == null || task.state != TaskState.NEEDS_INPUT) return;

        task.inputHistory.add(new InputExchange(task.pendingQuestion, answer));
        task.pendingQuestion = null;
        task.state = TaskState.PLANNING;  // Resume from planning
        notifyListeners(TaskEvent.INPUT_PROVIDED, task);
    }

    /**
     * Fail a task with an error.
     */
    public void failTask(String taskId, String reason) {
        Task task = tasks.get(taskId);
        if (task == null) return;

        task.state = TaskState.FAILED;
        task.failureReason = reason;
        notifyListeners(TaskEvent.FAILED, task);
    }

    /**
     * Add a subtask to a parent task.
     */
    public Task addSubtask(String parentTaskId, String title, String description) {
        Task parent = tasks.get(parentTaskId);
        if (parent == null) return null;

        Task subtask = new Task(title, description, parent.creatorAgentId);
        subtask.parentTaskId = parentTaskId;
        tasks.put(subtask.id, subtask);
        parent.subtaskIds.add(subtask.id);

        notifyListeners(TaskEvent.SUBTASK_ADDED, subtask);
        return subtask;
    }

    /**
     * Get all tasks for an agent.
     */
    public List<Task> getAgentTasks(String agentId) {
        List<String> taskIds = agentTasks.get(agentId);
        if (taskIds == null) return Collections.emptyList();

        return taskIds.stream()
            .map(tasks::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Get tasks by state.
     */
    public List<Task> getTasksByState(TaskState state) {
        return tasks.values().stream()
            .filter(t -> t.state == state)
            .toList();
    }

    /**
     * Get pending tasks in queue.
     */
    public List<Task> getPendingTasks() {
        return new ArrayList<>(pendingQueue);
    }

    /**
     * Queue a task for execution.
     */
    public void queueTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.state = TaskState.QUEUED;
            pendingQueue.offer(task);
            notifyListeners(TaskEvent.QUEUED, task);
        }
    }

    /**
     * Take next task from queue.
     */
    public Task takeNextTask() {
        return pendingQueue.poll();
    }

    /**
     * Get task by ID.
     */
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    public void addListener(TaskListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TaskListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(TaskEvent event, Task task) {
        for (TaskListener listener : listeners) {
            listener.onTaskEvent(event, task);
        }
    }

    /**
     * Task states following the staged integration pipeline.
     */
    public enum TaskState {
        CREATED,
        ASSIGNED,
        QUEUED,
        PLANNING,
        PROPOSING,    // Generating diff (dry-run)
        PROPOSED,     // Diff ready for review
        APPLYING,     // Applying changes
        APPLIED,      // Changes applied
        CHECKING,     // Running tests/checks
        REVIEWING,    // Awaiting review
        APPROVED,     // Ready to merge
        MERGING,      // Creating PR / merging
        COMPLETED,
        FAILED,
        NEEDS_INPUT,
        CANCELLED
    }

    /**
     * Task event types.
     */
    public enum TaskEvent {
        CREATED, ASSIGNED, QUEUED, STATE_CHANGED,
        NEEDS_INPUT, INPUT_PROVIDED, SUBTASK_ADDED,
        FAILED, COMPLETED
    }

    /**
     * A task in the system.
     */
    public static class Task {
        public final String id;
        public final String title;
        public final String description;
        public final String creatorAgentId;
        public final long createdAt;
        public final List<String> subtaskIds;
        public final List<StateTransition> stateHistory;
        public final List<InputExchange> inputHistory;
        public final Set<String> touchedFiles;
        public final Map<String, String> metadata;

        public String assignedAgentId;
        public String parentTaskId;
        public TaskState state;
        public String pendingQuestion;
        public String failureReason;
        public String proposedDiff;
        public int linesChanged;
        public int filesChanged;

        public Task(String title, String description, String creatorAgentId) {
            this.id = UUID.randomUUID().toString();
            this.title = title;
            this.description = description;
            this.creatorAgentId = creatorAgentId;
            this.createdAt = System.currentTimeMillis();
            this.state = TaskState.CREATED;
            this.subtaskIds = new ArrayList<>();
            this.stateHistory = new ArrayList<>();
            this.inputHistory = new ArrayList<>();
            this.touchedFiles = new HashSet<>();
            this.metadata = new HashMap<>();

            this.stateHistory.add(new StateTransition(TaskState.CREATED, createdAt));
        }

        public boolean isTerminal() {
            return state == TaskState.COMPLETED ||
                   state == TaskState.FAILED ||
                   state == TaskState.CANCELLED;
        }

        public boolean isBlocked() {
            return state == TaskState.NEEDS_INPUT;
        }
    }

    /**
     * Record of a state transition.
     */
    public record StateTransition(TaskState state, long timestamp) {}

    /**
     * Record of a question/answer exchange.
     */
    public record InputExchange(String question, String answer) {}

    /**
     * Listener for task events.
     */
    public interface TaskListener {
        void onTaskEvent(TaskEvent event, Task task);
    }
}
