package ai.moeru.airicraft;

public record AiricraftConfig(
	int socialChatMaxDistanceBlocks,
	boolean readSystemChatMessages,
	boolean enableProactiveSocialMode
) {
	public static AiricraftConfig defaults() {
		return new AiricraftConfig(-1, true, false);
	}

	public boolean socialChatDistanceUnlimited() {
		return socialChatMaxDistanceBlocks < 0;
	}
}
