package dev.guessbingo.client;

import com.google.gson.Gson;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.net.HudState;

/** 客户端保存的最新一份面板状态。 */
public final class ClientState {
	private static final Gson GSON = new Gson();
	private static HudState state;
	private static long receivedAt;

	private ClientState() {
	}

	public static void update(String json) {
		try {
			state = GSON.fromJson(json, HudState.class);
			receivedAt = System.currentTimeMillis();
		} catch (RuntimeException e) {
			GuessBingo.LOGGER.warn("无法解析服务端发来的面板状态", e);
			state = null;
		}
	}

	public static void clear() {
		state = null;
	}

	public static HudState get() {
		return state;
	}

	public static boolean active() {
		HudState s = state;
		return s != null && s.active;
	}

	/** 投票剩余秒数：在两次同步之间由本地时钟补足。 */
	public static int voteSecondsLeft() {
		HudState s = state;
		if (s == null || s.vote == null) return 0;
		long elapsed = (System.currentTimeMillis() - receivedAt) / 1000;
		return (int) Math.max(0, s.vote.secondsLeft - elapsed);
	}
}
