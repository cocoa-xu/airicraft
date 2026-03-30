package ai.moeru.airicraft.agent.llm;

public record PlannerResponse(
	String replyText,
	PlannerIntent intent,
	PlannerToolRequest toolRequest
) {
	public PlannerResponse(String replyText, PlannerIntent intent) {
		this(replyText, intent, null);
	}
}
