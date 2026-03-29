package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

public final class PlannerObservabilityVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final LongSupplier latestEventSeqNo;
	private final Runnable setupReplyOnlyResponse;
	private final Runnable injectGreeting;
	private final LongPredicate responseAppliedSeenSince;
	private final BooleanSupplier lastResponseIsReplyOnly;
	private final Runnable setupGoalSetResponse;
	private final Runnable injectFollowRequest;
	private final LongPredicate goalSetSeenSince;
	private final Runnable setupGoalClearResponse;
	private final Runnable injectStopRequest;
	private final LongPredicate goalClearedSeenSince;

	private long responseBaselineSeqNo;
	private long goalSetBaselineSeqNo;
	private long goalClearedBaselineSeqNo;

	public PlannerObservabilityVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		LongSupplier latestEventSeqNo,
		Runnable setupReplyOnlyResponse,
		Runnable injectGreeting,
		LongPredicate responseAppliedSeenSince,
		BooleanSupplier lastResponseIsReplyOnly,
		Runnable setupGoalSetResponse,
		Runnable injectFollowRequest,
		LongPredicate goalSetSeenSince,
		Runnable setupGoalClearResponse,
		Runnable injectStopRequest,
		LongPredicate goalClearedSeenSince
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.latestEventSeqNo = Objects.requireNonNull(latestEventSeqNo, "latestEventSeqNo");
		this.setupReplyOnlyResponse = Objects.requireNonNull(setupReplyOnlyResponse, "setupReplyOnlyResponse");
		this.injectGreeting = Objects.requireNonNull(injectGreeting, "injectGreeting");
		this.responseAppliedSeenSince = Objects.requireNonNull(responseAppliedSeenSince, "responseAppliedSeenSince");
		this.lastResponseIsReplyOnly = Objects.requireNonNull(lastResponseIsReplyOnly, "lastResponseIsReplyOnly");
		this.setupGoalSetResponse = Objects.requireNonNull(setupGoalSetResponse, "setupGoalSetResponse");
		this.injectFollowRequest = Objects.requireNonNull(injectFollowRequest, "injectFollowRequest");
		this.goalSetSeenSince = Objects.requireNonNull(goalSetSeenSince, "goalSetSeenSince");
		this.setupGoalClearResponse = Objects.requireNonNull(setupGoalClearResponse, "setupGoalClearResponse");
		this.injectStopRequest = Objects.requireNonNull(injectStopRequest, "injectStopRequest");
		this.goalClearedSeenSince = Objects.requireNonNull(goalClearedSeenSince, "goalClearedSeenSince");
	}

	@Override
	public String name() {
		return "planner.observability";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.action("capture response baseline", () -> responseBaselineSeqNo = latestEventSeqNo.getAsLong())
			.action("setup reply-only response", setupReplyOnlyResponse)
			.action("inject greeting", injectGreeting)
			.waitUntil("planner response applied", 300, () -> responseAppliedSeenSince.test(responseBaselineSeqNo))
			.assertThat("last response is reply-only", lastResponseIsReplyOnly)
			.action("capture goal-set baseline", () -> goalSetBaselineSeqNo = latestEventSeqNo.getAsLong())
			.action("setup goal-set response", setupGoalSetResponse)
			.action("inject follow request", injectFollowRequest)
			.waitUntil("planner goal_set emitted", 300, () -> goalSetSeenSince.test(goalSetBaselineSeqNo))
			.action("capture goal-cleared baseline", () -> goalClearedBaselineSeqNo = latestEventSeqNo.getAsLong())
			.action("setup goal-clear response", setupGoalClearResponse)
			.action("inject stop request", injectStopRequest)
			.waitUntil("planner goal_cleared emitted", 300, () -> goalClearedSeenSince.test(goalClearedBaselineSeqNo));
	}
}
