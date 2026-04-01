package ai.moeru.airicraft.agent.llm;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record CompactionCheckpoint(
	String timeAnchor,
	String sessionState,
	String activeGoal,
	List<String> activeCommitments,
	List<String> durableFacts,
	List<String> relevantPeople,
	List<String> openLoops,
	List<String> recentTimeline,
	List<String> forgettableNoise
) {
	public CompactionCheckpoint {
		timeAnchor = normalize(timeAnchor);
		sessionState = normalize(sessionState);
		activeGoal = normalize(activeGoal);
		activeCommitments = sanitize(activeCommitments);
		durableFacts = sanitize(durableFacts);
		relevantPeople = sanitize(relevantPeople);
		openLoops = sanitize(openLoops);
		recentTimeline = sanitize(recentTimeline);
		forgettableNoise = sanitize(forgettableNoise);
	}

	public String renderMessage() {
		StringBuilder builder = new StringBuilder("Context checkpoint:\n");
		appendLine(builder, "Time anchor", timeAnchor);
		appendLine(builder, "Session state", sessionState);
		appendLine(builder, "Active goal", activeGoal);
		appendList(builder, "Active commitments", activeCommitments);
		appendList(builder, "Durable facts", durableFacts);
		appendList(builder, "Relevant people", relevantPeople);
		appendList(builder, "Open loops", openLoops);
		appendList(builder, "Recent timeline", recentTimeline);
		appendList(builder, "Forgettable noise", forgettableNoise);
		return builder.toString().trim();
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "none";
		}
		return value.trim();
	}

	private static List<String> sanitize(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		return values.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.collect(Collectors.toList());
	}

	private static void appendLine(StringBuilder builder, String label, String value) {
		builder.append(label).append(": ").append(value).append('\n');
	}

	private static void appendList(StringBuilder builder, String label, List<String> values) {
		builder.append(label).append(": ");
		if (values == null || values.isEmpty()) {
			builder.append("none\n");
			return;
		}
		builder.append(String.join(" | ", values)).append('\n');
	}
}
