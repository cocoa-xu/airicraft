package ai.moeru.airicraft.agent.llm;

public final class RelativeTimeFormatter {
	private RelativeTimeFormatter() {
	}

	public static String format(long eventTimeMs, long anchorTimeMs) {
		long deltaMillis = Math.max(0L, anchorTimeMs - eventTimeMs);
		long seconds = deltaMillis / 1000L;
		if (seconds < 10L) {
			return "just now";
		}
		if (seconds < 60L) {
			return seconds + " seconds ago";
		}

		long minutes = Math.max(1L, seconds / 60L);
		if (minutes < 90L) {
			return minutes + " minutes ago";
		}

		long hours = Math.max(1L, minutes / 60L);
		if (hours < 24L) {
			return hours + " hours ago";
		}

		long days = Math.max(1L, hours / 24L);
		return days + " days ago";
	}
}
