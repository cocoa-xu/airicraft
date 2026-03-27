package ai.moeru.airicraft.agent;

import ai.moeru.airicraft.SingleplayerWorldService;
import ai.moeru.airicraft.agent.events.SemanticEventBuffer;
import ai.moeru.airicraft.agent.events.SemanticEventQueryResult;
import ai.moeru.airicraft.agent.session.LanHostingService;
import ai.moeru.airicraft.agent.session.SessionSnapshot;
import ai.moeru.airicraft.agent.session.SessionRuntime;
import ai.moeru.airicraft.agent.social.ChatIngestService;
import ai.moeru.airicraft.agent.social.NearbyPlayerSnapshot;
import ai.moeru.airicraft.agent.social.NearbyPlayerTracker;
import ai.moeru.airicraft.agent.social.PrimaryInteractionPlayer;
import ai.moeru.airicraft.agent.social.PrimaryInteractionResolver;
import ai.moeru.airicraft.agent.verification.VerificationReport;
import ai.moeru.airicraft.agent.verification.VerificationRunner;
import ai.moeru.airicraft.agent.verification.scenarios.SessionVerification;
import ai.moeru.airicraft.agent.verification.scenarios.SessionLanVerification;
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

	private boolean initialized;
	private long tickCount;
	private long worldLoadTick = -1L;
	private SessionSnapshot sessionSnapshot = SessionSnapshot.initial();

	public EmbodiedAgentRuntime(AgentConfig config) {
		this.config = Objects.requireNonNull(config, "config");
		registerDefaultScenarios();
	}

	public static EmbodiedAgentRuntime createDefault() {
		return new EmbodiedAgentRuntime(AgentConfig.defaults());
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
	}

	public void onClientTick(MinecraftClient client) {
		tickCount++;
		boolean wasWorldLoaded = sessionSnapshot.worldLoaded();
		sessionSnapshot = sessionRuntime.poll(client, tickCount, eventBuffer);
		if (!wasWorldLoaded && sessionSnapshot.worldLoaded()) {
			worldLoadTick = tickCount;
		}
		nearbyPlayerTracker.poll(client, tickCount, eventBuffer);
		primaryInteractionResolver.expireInactive(tickCount);
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
			() -> openLan(),
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
}
