package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class LlmDegradationVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable triggerTimeoutOne;
	private final Runnable triggerTimeoutTwo;
	private final Runnable triggerTimeoutThree;
	private final BooleanSupplier degraded;
	private final Supplier<Boolean> behaviorTreeActive;
	private final BooleanSupplier degradedEventSeen;
	private final BooleanSupplier degradedChatSeen;
	private final Runnable requestReset;
	private final BooleanSupplier recovered;
	private final BooleanSupplier degradedClearedSeen;
	private final BooleanSupplier resetRequestedSeen;
	private final Supplier<Boolean> resetAckChatSeen;

	public LlmDegradationVerification(
		BooleanSupplier worldLoaded,
		Runnable triggerTimeoutOne,
		Runnable triggerTimeoutTwo,
		Runnable triggerTimeoutThree,
		BooleanSupplier degraded,
		Supplier<Boolean> behaviorTreeActive,
		BooleanSupplier degradedEventSeen,
		BooleanSupplier degradedChatSeen,
		Runnable requestReset,
		BooleanSupplier recovered,
		BooleanSupplier degradedClearedSeen,
		BooleanSupplier resetRequestedSeen,
		Supplier<Boolean> resetAckChatSeen
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.triggerTimeoutOne = Objects.requireNonNull(triggerTimeoutOne, "triggerTimeoutOne");
		this.triggerTimeoutTwo = Objects.requireNonNull(triggerTimeoutTwo, "triggerTimeoutTwo");
		this.triggerTimeoutThree = Objects.requireNonNull(triggerTimeoutThree, "triggerTimeoutThree");
		this.degraded = Objects.requireNonNull(degraded, "degraded");
		this.behaviorTreeActive = Objects.requireNonNull(behaviorTreeActive, "behaviorTreeActive");
		this.degradedEventSeen = Objects.requireNonNull(degradedEventSeen, "degradedEventSeen");
		this.degradedChatSeen = Objects.requireNonNull(degradedChatSeen, "degradedChatSeen");
		this.requestReset = Objects.requireNonNull(requestReset, "requestReset");
		this.recovered = Objects.requireNonNull(recovered, "recovered");
		this.degradedClearedSeen = Objects.requireNonNull(degradedClearedSeen, "degradedClearedSeen");
		this.resetRequestedSeen = Objects.requireNonNull(resetRequestedSeen, "resetRequestedSeen");
		this.resetAckChatSeen = Objects.requireNonNull(resetAckChatSeen, "resetAckChatSeen");
	}

	@Override
	public String name() {
		return "llm.degradation";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("trigger timeout 1", triggerTimeoutOne)
			.action("trigger timeout 2", triggerTimeoutTwo)
			.action("trigger timeout 3", triggerTimeoutThree)
			.waitUntil("runtime enters degraded", 100, degraded)
			.assertThat("behavior tree still active", behaviorTreeActive::get)
			.assertThat("degraded event emitted", degradedEventSeen)
			.assertThat("degraded chat emitted", degradedChatSeen)
			.action("request planner reset", requestReset)
			.waitUntil("runtime recovers", 20, recovered)
			.assertThat("degraded cleared event emitted", degradedClearedSeen)
			.assertThat("reset requested event emitted", resetRequestedSeen)
			.assertThat("reset acknowledgement sent", resetAckChatSeen::get);
	}
}
