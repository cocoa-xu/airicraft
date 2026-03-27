package ai.moeru.airicraft.agent;

import ai.moeru.airicraft.SingleplayerWorldService;
import ai.moeru.airicraft.agent.behavior.BehaviorTreeRuntime;
import ai.moeru.airicraft.agent.behavior.BehaviorTreeSnapshot;
import ai.moeru.airicraft.agent.dialogue.DialogueIntentType;
import ai.moeru.airicraft.agent.dialogue.DialogueResponse;
import ai.moeru.airicraft.agent.dialogue.DialogueSnapshot;
import ai.moeru.airicraft.agent.dialogue.DialogueRuntime;
import ai.moeru.airicraft.agent.events.SemanticEventBuffer;
import ai.moeru.airicraft.agent.events.SemanticEventQueryResult;
import ai.moeru.airicraft.agent.follow.FollowCapability;
import ai.moeru.airicraft.agent.follow.FollowState;
import ai.moeru.airicraft.agent.goals.GoalDirector;
import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.goals.GoalType;
import ai.moeru.airicraft.agent.llm.OpenAiCompatibleLlmBackend;
import ai.moeru.airicraft.agent.llm.PlannerExecutor;
import ai.moeru.airicraft.agent.llm.PlannerIntent;
import ai.moeru.airicraft.agent.llm.PlannerResponse;
import ai.moeru.airicraft.agent.session.LanHostingService;
import ai.moeru.airicraft.agent.session.SessionSnapshot;
import ai.moeru.airicraft.agent.session.SessionRuntime;
import ai.moeru.airicraft.agent.social.ChatIngestService;
import ai.moeru.airicraft.agent.social.NearbyPlayerSnapshot;
import ai.moeru.airicraft.agent.social.NearbyPlayerTracker;
import ai.moeru.airicraft.agent.social.PrimaryInteractionPlayer;
import ai.moeru.airicraft.agent.social.PrimaryInteractionResolver;
import ai.moeru.airicraft.agent.speech.SpeechService;
import ai.moeru.airicraft.agent.verification.VerificationReport;
import ai.moeru.airicraft.agent.verification.VerificationRunner;
import ai.moeru.airicraft.agent.verification.scenarios.DialogueVerification;
import ai.moeru.airicraft.agent.verification.scenarios.FollowVerification;
import ai.moeru.airicraft.agent.verification.scenarios.LlmDegradationVerification;
import ai.moeru.airicraft.agent.verification.scenarios.SessionLanVerification;
import ai.moeru.airicraft.agent.verification.scenarios.SessionVerification;
import ai.moeru.airicraft.agent.verification.scenarios.SocialChatIngestVerification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EmbodiedAgentRuntime {
	private final AgentConfig config;
	private final VerificationRunner verificationRunner = new VerificationRunner();
	private final SingleplayerWorldService singleplayerWorldService = new SingleplayerWorldService();
	private final SessionRuntime sessionRuntime = new SessionRuntime();
	private final LanHostingService lanHostingService = new LanHostingService();
	private final SemanticEventBuffer eventBuffer = new SemanticEventBuffer(512);
	private final ChatIngestService chatIngestService = new ChatIngestService();
	private final NearbyPlayerTracker nearbyPlayerTracker = new NearbyPlayerTracker();
	private final PrimaryInteractionResolver primaryInteractionResolver = new PrimaryInteractionResolver(200L);
	private final GoalDirector goalDirector = new GoalDirector();
	private final FollowCapability followCapability = new FollowCapability();
	private final BehaviorTreeRuntime behaviorTreeRuntime = new BehaviorTreeRuntime();
	private final SpeechService speechService = new SpeechService();
	private final DialogueRuntime dialogueRuntime;

	private boolean initialized;
	private long tickCount;
	private long worldLoadTick = -1L;
	private SessionSnapshot sessionSnapshot = SessionSnapshot.initial();
	private FollowState followState = FollowState.idle();

	public EmbodiedAgentRuntime(AgentConfig config) {
		this.config = Objects.requireNonNull(config, "config");
		this.dialogueRuntime = new DialogueRuntime(
			new PlannerExecutor(new OpenAiCompatibleLlmBackend(config.llm())),
			config.llm().maxRecentConversationTurns()
		);
		registerDefaultScenarios();
	}

	public static EmbodiedAgentRuntime createDefault() {
		return new EmbodiedAgentRuntime(AgentConfigLoader.load());
	}

	public AgentConfig config() {
		return config;
	}

	public VerificationRunner verificationRunner() {
		return verificationRunner;
	}

	public List<String> verificationScenarioNames() {
		return verificationRunner.scenarioNames();
	}

	public void onClientStarted(MinecraftClient client) {
		initialized = true;
		sessionRuntime.onClientStarted(client, tickCount, eventBuffer);
		sessionSnapshot = sessionRuntime.snapshot();
	}

	public void onWorldLeave() {
		sessionRuntime.onWorldLeave(tickCount, eventBuffer);
		sessionSnapshot = sessionRuntime.snapshot();
		nearbyPlayerTracker.clear(tickCount, eventBuffer);
		primaryInteractionResolver.clear();
		dialogueRuntime.clear();
		goalDirector.clear();
		followCapability.clear();
		followState = FollowState.idle();
		behaviorTreeRuntime.stop(MinecraftClient.getInstance());
		speechService.clear();
	}

	public void onClientTick(MinecraftClient client) {
		tickCount++;
		FollowState previousFollowState = followState;
		BehaviorTreeSnapshot previousTreeSnapshot = behaviorTreeRuntime.snapshot();
		boolean wasWorldLoaded = sessionSnapshot.worldLoaded();
		sessionSnapshot = sessionRuntime.poll(client, tickCount, eventBuffer);
		if (!wasWorldLoaded && sessionSnapshot.worldLoaded()) {
			worldLoadTick = tickCount;
		}

		nearbyPlayerTracker.poll(client, tickCount, eventBuffer);
		primaryInteractionResolver.current().ifPresent(current ->
			primaryInteractionResolver.clearIfNotNearby(current.uuid(), nearbyPlayerTracker.isNearby(current.uuid()))
		);
		primaryInteractionResolver.expireInactive(tickCount);

		DialogueResponse completedDialogueResponse = dialogueRuntime.poll(tickCount, eventBuffer);
		if (completedDialogueResponse != null) {
			goalDirector.onPlannerResponse(completedDialogueResponse);
		}

		followState = followCapability.tick(
			client,
			sessionSnapshot,
			goalDirector.activeGoal(),
			nearbyPlayerTracker,
			tickCount,
			eventBuffer
		);
		behaviorTreeRuntime.tick(
			client,
			sessionSnapshot,
			dialogueRuntime,
			speechService,
			goalDirector.activeGoal(),
			followState,
			tickCount
		);
		if (previousFollowState.targetNearby() && !followState.targetNearby() && previousFollowState.targetPlayer() != null) {
			goalDirector.clearFollowGoal(previousFollowState.targetPlayer());
		}

		BehaviorTreeSnapshot currentTreeSnapshot = behaviorTreeRuntime.snapshot();
		if (
			followState.goalActive()
			&& followState.targetNearby()
			&& !previousTreeSnapshot.movement().stuck()
			&& currentTreeSnapshot.movement().stuck()
		) {
			eventBuffer.append(tickCount, "follow.stuck", Map.of(
				"player", followState.targetPlayer(),
				"distanceToTarget", followState.distanceToTarget()
			));
		}

		verificationRunner.onTick();
	}

	public void shutdown() {
		initialized = false;
		tickCount = 0L;
		worldLoadTick = -1L;
		verificationRunner.reset();
		nearbyPlayerTracker.clear(tickCount, eventBuffer);
		eventBuffer.clear();
		primaryInteractionResolver.clear();
		dialogueRuntime.shutdown();
		goalDirector.clear();
		followCapability.clear();
		followState = FollowState.idle();
		behaviorTreeRuntime.stop(MinecraftClient.getInstance());
		speechService.clear();
		sessionSnapshot = SessionSnapshot.initial();
	}

	public SessionSnapshot sessionSnapshot() {
		return sessionSnapshot.withTickCount(tickCount);
	}

	public AgentRuntimeSnapshot snapshot() {
		return new AgentRuntimeSnapshot(
			initialized,
			tickCount,
			sessionSnapshot(),
			verificationRunner.report()
		);
	}

	public VerificationReport verificationReport() {
		return verificationRunner.report();
	}

	public Optional<GoalSnapshot> activeGoal() {
		return goalDirector.activeGoal();
	}

	public BehaviorTreeSnapshot behaviorTreeSnapshot() {
		return behaviorTreeRuntime.snapshot();
	}

	public Optional<DialogueResponse> lastDialogueResponse() {
		return dialogueRuntime.lastResponse();
	}

	public DialogueSnapshot dialogueSnapshot() {
		return dialogueRuntime.snapshot();
	}

	public boolean llmAvailable() {
		return dialogueRuntime.llmAvailable();
	}

	public boolean isDegraded() {
		return dialogueRuntime.isDegraded();
	}

	public long lastSpokenTick() {
		return speechService.lastSpokenTick();
	}

	public String lastSpokenText() {
		return speechService.lastSpokenText();
	}

	public boolean startVerification(String scenarioName) {
		return verificationRunner.start(scenarioName);
	}

	public void onChatReceived(String senderName, String plainTextMessage) {
		chatIngestService.ingest(
			senderName,
			plainTextMessage,
			tickCount,
			nearbyPlayerTracker,
			primaryInteractionResolver,
			eventBuffer
		);

		if (dialogueRuntime.handleResetCommand(senderName, plainTextMessage, tickCount, eventBuffer)) {
			return;
		}

		if (nearbyPlayerTracker.findByName(senderName).isPresent() && ChatIngestService.isAddressedToAgent(plainTextMessage)) {
			dialogueRuntime.onAddressedChat(
				senderName,
				plainTextMessage,
				tickCount,
				sessionSnapshot,
				primaryInteractionResolver.current().map(PrimaryInteractionPlayer::name).orElse(null),
				goalDirector.activeGoal()
			);
		}
	}

	public SemanticEventQueryResult recentEvents(Long sinceSeqNo) {
		return eventBuffer.query(sinceSeqNo);
	}

	public Optional<PrimaryInteractionPlayer> primaryInteractionPlayer() {
		return primaryInteractionResolver.current();
	}

	public List<NearbyPlayerSnapshot> nearbyPlayers() {
		return nearbyPlayerTracker.snapshot();
	}

	public Map<String, Object> openLan() {
		return lanHostingService.openLan(sessionSnapshot);
	}

	public void injectMockPlannerResponse(PlannerResponse response) {
		dialogueRuntime.injectMockResponse(response);
	}

	public void injectPlannerTimeout() {
		dialogueRuntime.injectTimeout();
	}

	private void registerDefaultScenarios() {
		verificationRunner.register(new SessionVerification(
			() -> sessionSnapshot.mode(),
			this::joinFirstWorld,
			this::leaveCurrentWorld,
			() -> eventBuffer.containsType("session.world_loaded"),
			() -> worldLoadTick >= 0L && tickCount - worldLoadTick >= 20L
		));
		verificationRunner.register(new SessionLanVerification(
			() -> sessionSnapshot.mode(),
			this::openLan,
			() -> sessionSnapshot.lanPort() > 0,
			() -> eventBuffer.containsType("session.lan_opened")
		));
		verificationRunner.register(new SocialChatIngestVerification(
			() -> sessionSnapshot.worldLoaded(),
			() -> nearbyPlayerTracker.injectPlayerNearby("Alice", new Vec3d(5.0D, 64.0D, 0.0D), tickCount, eventBuffer),
			() -> chatIngestService.injectMessage("Alice", "hello everyone", tickCount, nearbyPlayerTracker, primaryInteractionResolver, eventBuffer),
			() -> chatIngestService.injectMessage("Alice", "@agent follow me", tickCount, nearbyPlayerTracker, primaryInteractionResolver, eventBuffer),
			() -> eventBuffer.containsTypeForPlayer("social.player_joined_nearby", "Alice"),
			() -> eventBuffer.containsTypeForPlayer("social.player_spoke", "Alice"),
			() -> eventBuffer.containsTypeForPlayer("social.player_addressed_agent", "Alice"),
			() -> primaryInteractionResolver.current().map(PrimaryInteractionPlayer::name).filter("Alice"::equals).isPresent()
		));
		verificationRunner.register(new FollowVerification(
			() -> sessionSnapshot.worldLoaded(),
			() -> injectMockPlannerResponse(new PlannerResponse(
				"Following Alice.",
				new PlannerIntent("set_goal", GoalType.FOLLOW_PLAYER, "Alice")
			)),
			() -> nearbyPlayerTracker.injectPlayerNearby("Alice", playerOffset(5.0D), tickCount, eventBuffer),
			() -> onChatReceived("Alice", "@agent follow me"),
			() -> lastDialogueResponse().isPresent(),
			() -> lastDialogueResponse()
				.map(response -> response.intent().type() == DialogueIntentType.SET_GOAL)
				.orElse(false),
			() -> eventBuffer.containsTypeForPlayer("follow.target_acquired", "Alice"),
			() -> activeGoal().map(goal -> goal.type() == GoalType.FOLLOW_PLAYER).orElse(false),
			() -> nearbyPlayerTracker.injectPlayerMove("Alice", playerOffset(20.0D), tickCount, eventBuffer),
			() -> behaviorTreeSnapshot().activeNodePath().stream().anyMatch(node -> node.contains("MoveCloser")),
			() -> nearbyPlayerTracker.injectPlayerDisconnect("Alice", tickCount, eventBuffer),
			() -> eventBuffer.containsTypeForPlayer("follow.target_lost", "Alice")
		));
		verificationRunner.register(new DialogueVerification(
			() -> sessionSnapshot.worldLoaded(),
			() -> nearbyPlayerTracker.injectPlayerNearby("Alice", playerOffset(5.0D), tickCount, eventBuffer),
			() -> injectMockPlannerResponse(new PlannerResponse(
				"Sure, I'll follow you!",
				new PlannerIntent("set_goal", GoalType.FOLLOW_PLAYER, "Alice")
			)),
			() -> onChatReceived("Alice", "@agent follow me"),
			() -> lastDialogueResponse().isPresent(),
			() -> lastDialogueResponse().map(response -> response.text() != null && !response.text().isBlank()).orElse(false),
			() -> lastSpokenTick() > 0L,
			() -> activeGoal().map(goal -> goal.type() == GoalType.FOLLOW_PLAYER).orElse(false)
		));
		verificationRunner.register(new LlmDegradationVerification(
			() -> sessionSnapshot.worldLoaded(),
			() -> injectPlannerTimeout(),
			() -> injectPlannerTimeout(),
			() -> injectPlannerTimeout(),
			() -> isDegraded(),
			() -> behaviorTreeSnapshot().activeNodePath() != null && !behaviorTreeSnapshot().activeNodePath().isEmpty(),
			() -> eventBuffer.containsType("planner.degraded_entered"),
			() -> lastSpokenTick() > 0L,
			() -> onChatReceived("Alice", "@agent reset"),
			() -> !isDegraded(),
			() -> eventBuffer.containsType("planner.degraded_cleared"),
			() -> eventBuffer.containsType("planner.reset_requested"),
			() -> "Planner state reset.".equals(lastSpokenText())
		));
	}

	private void joinFirstWorld() {
		List<Map<String, Object>> worlds = singleplayerWorldService.listWorlds();
		if (worlds.isEmpty()) {
			throw new IllegalStateException("No singleplayer worlds are available for session.basic");
		}

		Object worldName = worlds.get(0).get("name");
		if (!(worldName instanceof String worldNameValue) || worldNameValue.isBlank()) {
			throw new IllegalStateException("First singleplayer world is missing a valid internal name");
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			throw new IllegalStateException("Minecraft client is not initialized");
		}

		client.createIntegratedServerLoader().start(worldNameValue, () -> {
		});
	}

	private void leaveCurrentWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			throw new IllegalStateException("Minecraft client is not initialized");
		}
		if (client.world == null && client.player == null) {
			return;
		}

		client.disconnect(Text.empty());
	}

	private Vec3d playerOffset(double xOffset) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return new Vec3d(xOffset, 64.0D, 0.0D);
		}
		return new Vec3d(
			client.player.getX() + xOffset,
			client.player.getY(),
			client.player.getZ()
		);
	}
}
