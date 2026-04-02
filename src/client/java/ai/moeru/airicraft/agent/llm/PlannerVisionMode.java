package ai.moeru.airicraft.agent.llm;

public enum PlannerVisionMode {
	EXTERNAL_SUMMARY("external_summary"),
	NATIVE_TOOL_IMAGE("native_tool_image");

	private final String wireValue;

	PlannerVisionMode(String wireValue) {
		this.wireValue = wireValue;
	}

	public String wireValue() {
		return wireValue;
	}
}
