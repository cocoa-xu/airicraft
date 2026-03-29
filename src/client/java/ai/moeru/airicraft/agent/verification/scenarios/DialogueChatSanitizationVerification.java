package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.chat.ChatService;
import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class DialogueChatSanitizationVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final LongSupplier lastChatTick;
	private final Supplier<String> lastChatText;
	private final Runnable setupUnsafeMockResponse;
	private final Runnable injectPrompt;

	private long baselineChatTick;

	public DialogueChatSanitizationVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		LongSupplier lastChatTick,
		Supplier<String> lastChatText,
		Runnable setupUnsafeMockResponse,
		Runnable injectPrompt
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.lastChatTick = Objects.requireNonNull(lastChatTick, "lastChatTick");
		this.lastChatText = Objects.requireNonNull(lastChatText, "lastChatText");
		this.setupUnsafeMockResponse = Objects.requireNonNull(setupUnsafeMockResponse, "setupUnsafeMockResponse");
		this.injectPrompt = Objects.requireNonNull(injectPrompt, "injectPrompt");
	}

	@Override
	public String name() {
		return "dialogue.chat_sanitization";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.action("capture chat baseline", () -> baselineChatTick = lastChatTick.getAsLong())
			.action("setup unsafe mock response", setupUnsafeMockResponse)
			.action("inject prompt", injectPrompt)
			.waitUntil("agent chat emitted", 100, () -> lastChatTick.getAsLong() > baselineChatTick)
			.assertThat("chat text exists", () -> lastChatText.get() != null && !lastChatText.get().isBlank())
			.assertThat("chat text is single line", () -> !lastChatText.get().contains("\n") && !lastChatText.get().contains("\r"))
			.assertThat("chat text is not command-like", () -> !lastChatText.get().startsWith("/"))
			.assertThat("chat text strips formatting codes", () -> !lastChatText.get().contains("§"))
			.assertThat("chat text is bounded", () -> lastChatText.get().length() <= ChatService.MAX_CHAT_MESSAGE_LENGTH);
	}
}
