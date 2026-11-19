package dev.guessbingo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.guessbingo.GuessBingo;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 客户端面板设置，保存在 config/guessbingo-client.json，改完重进游戏生效。 */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance;

	/** 是否显示左上角面板 */
	public boolean enabled = true;
	/** 面板缩放 */
	public float scale = 0.6f;
	/** 面板最多占屏幕宽度的比例；内容太宽时会自动再缩小 */
	public float maxWidthFraction = 0.45f;
	/** 面板左上角的位置（界面像素） */
	public int x = 4, y = 4;
	/** 对局进行时是否隐藏 bingo 模组自己的棋盘，让出左上角 */
	public boolean hideBingoCard = true;
	/** 标签、子标签等列的最大宽度（像素，超出部分以省略号截断） */
	public int maxColumnWidth = 96;

	public static ClientConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static ClientConfig load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(GuessBingo.MOD_ID + "-client.json");
		ClientConfig config = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, ClientConfig.class);
			} catch (Exception e) {
				GuessBingo.LOGGER.warn("读取 {} 失败，使用默认面板设置", file, e);
			}
		}
		if (config == null) config = new ClientConfig();
		config.scale = Math.max(0.3f, Math.min(2.0f, config.scale));
		config.maxWidthFraction = Math.max(0.15f, Math.min(1.0f, config.maxWidthFraction));
		config.maxColumnWidth = Math.max(30, Math.min(400, config.maxColumnWidth));
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (Exception e) {
			GuessBingo.LOGGER.warn("保存 {} 失败", file, e);
		}
		return config;
	}
}
