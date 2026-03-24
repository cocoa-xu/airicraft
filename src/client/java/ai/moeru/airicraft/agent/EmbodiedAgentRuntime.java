package ai.moeru.airicraft.agent;

import ai.moeru.airicraft.SingleplayerWorldService;
import ai.moeru.airicraft.agent.session.SessionMode;
import ai.moeru.airicraft.agent.session.SessionSnapshot;
import ai.moeru.airicraft.agent.verification.VerificationReport;
import ai.moeru.airicraft.agent.verification.VerificationRunner;
import ai.moeru.airicraft.agent.verification.scenarios.SessionVerification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EmbodiedAgentRuntime {
	private final AgentConfig config;
	private final VerificationRunner verificationRunner = new VerificationRunner();
	private final SingleplayerWorldService singleplayerWorldService = new SingleplayerWorldService();

	private boolean initialized;
	private long tickCount;
	private long worldLoadEventCount;
	private long verificationWorldLoadBaseline;
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
		sessionSnapshot = sessionSnapshot.withClientBooted(true);
		refreshSessionSnapshot(client);
	}

	public void onWorldLeave() {
		sessionSnapshot = sessionSnapshot
			.withWorldLoaded(false)
			.withMode(SessionMode.OUT_OF_WORLD)
			.withDimensionId(null);
	}

	public void onClientTick(MinecraftClient client) {
		tickCount++;
		boolean wasWorldLoaded = sessionSnapshot.worldLoaded();
		refreshSessionSnapshot(client);
		if (!wasWorldLoaded && sessionSnapshot.worldLoaded()) {
			worldLoadEventCount++;
		}
		verificationRunner.onTick();
	}

	public void shutdown() {
		initialized = false;
		tickCount = 0L;
		worldLoadEventCount = 0L;
		verificationWorldLoadBaseline = 0L;
		verificationRunner.reset();
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
		verificationWorldLoadBaseline = worldLoadEventCount;
		return verificationRunner.start(scenarioName);
	}

	private void refreshSessionSnapshot(MinecraftClient client) {
		if (client == null) {
			return;
		}

		boolean worldLoaded = client.world != null;
		String dimensionId = worldLoaded ? String.valueOf(client.world.getRegistryKey().getValue()) : null;
		SessionMode mode = sessionMode(client, worldLoaded);

		sessionSnapshot = sessionSnapshot
			.withClientBooted(true)
			.withWorldLoaded(worldLoaded)
			.withMode(mode)
			.withDimensionId(dimensionId)
			.withTickCount(tickCount);
	}

	private void registerDefaultScenarios() {
		verificationRunner.register(new SessionVerification(
			() -> sessionSnapshot.mode(),
			this::joinFirstWorld,
			this::leaveCurrentWorld,
			() -> worldLoadEventCount > verificationWorldLoadBaseline
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

		client.disconnect(new TitleScreen(), false);
	}

	private static SessionMode sessionMode(MinecraftClient client, boolean worldLoaded) {
		if (!worldLoaded) {
			return SessionMode.OUT_OF_WORLD;
		}
		if (!client.isInSingleplayer() || client.getCurrentServerEntry() != null) {
			return SessionMode.REMOTE_MULTIPLAYER;
		}
		return SessionMode.SINGLEPLAYER_LOCAL;
	}
}
