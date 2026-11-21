package dev.guessbingo.client;

import dev.guessbingo.net.SyncAdvancementsPayload;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 客户端持有的服务端推送进度缓存：与玩家的解锁进度无关，包含全部进度。 */
public final class ClientAdvancementsStore {
	private static final Map<Identifier, DisplayInfo> DISPLAYS = new HashMap<>();

	private ClientAdvancementsStore() {
	}

	public static void update(@NonNull SyncAdvancementsPayload payload) {
		synchronized (DISPLAYS) {
			DISPLAYS.clear();
			DISPLAYS.putAll(payload.advancements());
		}
	}

	public static void clear() {
		synchronized (DISPLAYS) {
			DISPLAYS.clear();
		}
	}

	public static Optional<DisplayInfo> display(Identifier id) {
		synchronized (DISPLAYS) {
			return Optional.ofNullable(DISPLAYS.get(id));
		}
	}

	public static List<DisplayInfo> all() {
		synchronized (DISPLAYS) {
			return List.copyOf(DISPLAYS.values());
		}
	}
}