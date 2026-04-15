package ai.moeru.airicraft;

import java.nio.file.Path;
import java.util.Objects;

public final class ConfigLoadException extends Exception {
	private final Path path;

	public ConfigLoadException(Path path, String message, Throwable cause) {
		super(message, cause);
		this.path = Objects.requireNonNull(path, "path");
	}

	public Path path() {
		return path;
	}
}
