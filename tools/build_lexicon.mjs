// 生成模组内置词库 src/main/resources/guessbingo/lexicon.json 以及 docs/词库报告.md。
//
// 用法：
//   node tools/build_lexicon.mjs <lexicon_raw.json> <vanilla 目录> <bingo 数据目录> <输出 lexicon.json> <输出 报告.md>
//
//   lexicon_raw.json  由 tools/export_datapack.mjs 从「猜词」数据包导出
//   vanilla 目录      需包含 zh_cn.json 以及 jar/（从 26.3 客户端、服务端 jar 解出的 assets 与 data）
//   bingo 数据目录    bingo 模组 jar 内的 data/yet-another-minecraft-bingo/yet-another-minecraft-bingo
import fs from 'node:fs';
import path from 'node:path';

const [, , rawFile, vanillaDir, bingoDir, outJson, outReport] = process.argv;
if (!outReport) {
	console.error('usage: node tools/build_lexicon.mjs <raw.json> <vanilla dir> <bingo data dir> <out lexicon.json> <out report.md>');
	process.exit(1);
}

const readJson = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));
const raw = readJson(rawFile);
const zh = readJson(path.join(vanillaDir, 'zh_cn.json'));
const jarDir = path.join(vanillaDir, 'jar');
const listIds = (dir) => (fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)) : []);

// ---------- 原版注册表（由游戏文件推得） ----------
const ITEMS = new Set(listIds(path.join(jarDir, 'assets/minecraft/items')));
const BLOCKS = new Set(listIds(path.join(jarDir, 'assets/minecraft/blockstates')));
const ENCHANTS = new Set(listIds(path.join(jarDir, 'data/minecraft/enchantment')));
const BIOMES = new Set(listIds(path.join(jarDir, 'data/minecraft/worldgen/biome')));
const STRUCTURES = new Set(listIds(path.join(jarDir, 'data/minecraft/worldgen/structure')));
const MOB_LOOT = new Set(listIds(path.join(jarDir, 'data/minecraft/loot_table/entities')));
for (const [name, set] of Object.entries({ ITEMS, BLOCKS, ENCHANTS, BIOMES, STRUCTURES, MOB_LOOT })) {
	if (set.size === 0) throw new Error(`原版数据缺失：${name}`);
}

// 进度：标题翻译键 -> 进度 ID
const ADV_BY_TITLE_KEY = {};
(function walk(dir, prefix) {
	for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
		if (f.isDirectory()) {
			if (f.name !== 'recipes') walk(path.join(dir, f.name), `${prefix}${f.name}/`);
		} else if (f.name.endsWith('.json')) {
			const j = readJson(path.join(dir, f.name));
			const key = j.display?.title?.translate;
			if (key) (ADV_BY_TITLE_KEY[key] ??= []).push(`minecraft:${prefix}${f.name.slice(0, -5)}`);
		}
	}
})(path.join(jarDir, 'data/minecraft/advancement'), '');

// 物品标签展开（用于 bingo 分级表里的 #minecraft:xxx）
function expandItemTag(tag, seen = new Set()) {
	if (seen.has(tag)) return [];
	seen.add(tag);
	const [ns, p] = tag.split(':');
	if (ns !== 'minecraft') return [];
	const f = path.join(jarDir, 'data/minecraft/tags/item', `${p}.json`);
	if (!fs.existsSync(f)) return [];
	const out = [];
	for (const v of readJson(f).values) {
		const id = typeof v === 'string' ? v : v.id;
		if (id.startsWith('#')) out.push(...expandItemTag(id.slice(1), seen));
		else out.push(id);
	}
	return out;
}

// ---------- bingo 数据 ----------
const TIER_SCORE = { D: 1, C: 2, B: 3, A: 4, S: 5 };
function loadTiers(file) {
	const t = readJson(path.join(bingoDir, 'tierlists', file));
	const map = {};
	for (const [tier, score] of Object.entries(TIER_SCORE)) {
		for (const v of t[tier] ?? []) {
			const ids = v.startsWith('#') ? expandItemTag(v.slice(1)) : [v];
			for (const id of ids) map[id] = Math.min(map[id] ?? 9, score);
		}
	}
	return map;
}
const ITEM_TIER = loadTiers('items.tierlist.json');
const ADV_TIER = loadTiers('advancements.tierlist.json');
const UNOBTAINABLE = new Set(readJson(path.join(bingoDir, 'tags/unobtainable.json')).values);
// 药水类物品在 bingo 里因“需要指定变种”被列为不可获得，这里按具体药水单独处理
for (const id of ['minecraft:potion', 'minecraft:splash_potion', 'minecraft:lingering_potion', 'minecraft:tipped_arrow']) UNOBTAINABLE.delete(id);
// 额外补充：生存模式拿不到物品形式的东西
for (const id of ['suspicious_sand', 'suspicious_gravel', 'bundle_of_nothing']) UNOBTAINABLE.add(`minecraft:${id}`);

// ---------- 中文名 -> 翻译键 ----------
const REV = {};
for (const [k, v] of Object.entries(zh)) (REV[v] ??= []).push(k);
const stripSuffix = (n) => n.replace(/（[^（）]*）$/, '');
function keysFor(name, prefixes) {
	const keys = new Set([...(REV[name] ?? []), ...(REV[stripSuffix(name)] ?? [])]);
	return [...keys].filter((k) => prefixes.some((p) => k.startsWith(p)));
}
const mc = (id) => `minecraft:${id}`;

// ---------- 手工表 ----------
/** 方块没有同名物品时，对应的“物品形式” */
const BLOCK_ITEM = {
	wheat: 'wheat_seeds', beetroots: 'beetroot_seeds', carrots: 'carrot', potatoes: 'potato', cocoa: 'cocoa_beans',
	sweet_berry_bush: 'sweet_berries', redstone_wire: 'redstone', tripwire: 'string', melon_stem: 'melon_seeds',
	attached_melon_stem: 'melon_seeds', pumpkin_stem: 'pumpkin_seeds', attached_pumpkin_stem: 'pumpkin_seeds',
	water: 'water_bucket', lava: 'lava_bucket', powder_snow: 'powder_snow_bucket', cave_vines: 'glow_berries',
	cave_vines_plant: 'glow_berries', kelp_plant: 'kelp', weeping_vines_plant: 'weeping_vines',
	twisting_vines_plant: 'twisting_vines', big_dripleaf_stem: 'big_dripleaf', pitcher_crop: 'pitcher_pod',
	torchflower_crop: 'torchflower_seeds', bamboo_sapling: 'bamboo', wall_torch: 'torch',
	soul_wall_torch: 'soul_torch', redstone_wall_torch: 'redstone_torch', copper_wall_torch: 'copper_torch',
};
function blockToItem(block) {
	if (BLOCK_ITEM[block] && ITEMS.has(BLOCK_ITEM[block])) return BLOCK_ITEM[block];
	if (ITEMS.has(block)) return block;
	// 墙上变种与立式变种共用一个物品
	const candidates = [
		block.replace(/_wall_hanging_sign$/, '_hanging_sign'), block.replace(/_wall_sign$/, '_sign'),
		block.replace(/_wall_banner$/, '_banner'), block.replace(/_wall_head$/, '_head'),
		block.replace(/_wall_skull$/, '_skull'), block.replace(/_wall_fan$/, '_fan'),
	];
	for (const c of candidates) if (c !== block && ITEMS.has(c)) return c;
	return null;
}

/** 铜制品：氧化程度与是否涂蜡视为等价，归一化到同一形状 */
function copperKey(id) {
	if (!/copper/.test(id)) return null;
	let s = id.replace(/^waxed_/, '').replace(/^(exposed|weathered|oxidized)_/, '');
	if (s === 'copper') s = 'copper_block';
	return s;
}
const COPPER_GROUPS = {};
for (const id of ITEMS) {
	const k = copperKey(id);
	if (!k) continue;
	(COPPER_GROUPS[k] ??= []).push(id);
}
function copperFamily(id) {
	const k = copperKey(id);
	const g = k ? COPPER_GROUPS[k] : null;
	// 只有真的存在氧化/涂蜡变种时才归组（铜锭、铜镐之类保持原样）
	return g && g.length > 1 && g.some((x) => /^(waxed_|exposed_|weathered_|oxidized_)/.test(x)) ? [...g].sort() : [id];
}

const SHERDS = [...ITEMS].filter((i) => i.endsWith('_pottery_sherd')).sort();
const TRIMS = [...ITEMS].filter((i) => i.endsWith('_armor_trim_smithing_template')).sort();
const BANNER_PATTERNS = [...ITEMS].filter((i) => i.endsWith('_banner_pattern')).sort();
const DISCS = [...ITEMS].filter((i) => i.startsWith('music_disc_')).sort();
const BOATS = [...ITEMS].filter((i) => (i.endsWith('_boat') && !i.endsWith('_chest_boat')) || i === 'bamboo_raft').sort();
const CHEST_BOATS = [...ITEMS].filter((i) => i.endsWith('_chest_boat') || i === 'bamboo_chest_raft').sort();
const CUSHIONS = [...ITEMS].filter((i) => i.endsWith('_cushion')).sort();

/** 非生物实体 -> 物品形式（没有列出的非生物实体一律不可作为谜底） */
const ENTITY_ITEM = {
	fishing_bobber: ['fishing_rod'], ender_pearl: ['ender_pearl'], small_fireball: ['fire_charge'],
	experience_bottle: ['experience_bottle'], egg: ['egg', 'blue_egg', 'brown_egg'], tnt: ['tnt'],
	leash_knot: ['lead'], end_crystal: ['end_crystal'], item_frame: ['item_frame'], glow_item_frame: ['glow_item_frame'],
	painting: ['painting'], armor_stand: ['armor_stand'], minecart: ['minecart'], chest_minecart: ['chest_minecart'],
	hopper_minecart: ['hopper_minecart'], furnace_minecart: ['furnace_minecart'], tnt_minecart: ['tnt_minecart'],
	cushion: CUSHIONS,
};

/** 不能作为“击杀”谜底的生物 */
const MOB_EXCLUDE = {
	player: '击杀玩家不计入', mannequin: '仅指令生成', giant: '仅指令生成', illusioner: '仅指令生成',
	creaking: '无法被玩家直接击杀', creaking_transient: '无法被玩家直接击杀',
};
const MOB_DIFFICULTY = {
	1: ['pig', 'cow', 'sheep', 'chicken', 'zombie', 'skeleton', 'spider', 'creeper', 'cod', 'salmon', 'squid', 'drowned', 'horse', 'villager'],
	3: ['cave_spider', 'silverfish', 'guardian', 'polar_bear', 'panda', 'parrot', 'ocelot', 'axolotl', 'tadpole', 'mule', 'allay', 'vindicator', 'breeze', 'endermite', 'zombie_horse', 'camel_husk', 'zombie_nautilus', 'sulfur_cube', 'goat', 'blaze', 'ghast', 'magma_cube', 'piglin', 'zombified_piglin', 'hoglin', 'strider', 'wither_skeleton'],
	4: ['elder_guardian', 'evoker', 'ravager', 'vex', 'piglin_brute', 'zoglin', 'sniffer', 'happy_ghast', 'mooshroom', 'skeleton_horse'],
	5: ['warden', 'wither', 'ender_dragon', 'shulker'],
};
const BIOME_DIFFICULTY = {
	1: ['plains', 'forest', 'river', 'beach', 'ocean', 'deep_ocean', 'taiga', 'birch_forest'],
	3: ['jungle', 'sparse_jungle', 'bamboo_jungle', 'badlands', 'wooded_badlands', 'cherry_grove', 'pale_garden', 'mangrove_swamp', 'old_growth_birch_forest', 'old_growth_pine_taiga', 'old_growth_spruce_taiga', 'windswept_savanna', 'windswept_gravelly_hills', 'frozen_peaks', 'jagged_peaks', 'stony_peaks', 'snowy_slopes', 'grove', 'frozen_ocean', 'deep_frozen_ocean', 'warm_ocean', 'lush_caves', 'dripstone_caves', 'flower_forest', 'snowy_beach', 'frozen_river', 'nether_wastes', 'crimson_forest', 'warped_forest', 'soul_sand_valley', 'basalt_deltas'],
	4: ['deep_dark', 'mushroom_fields', 'ice_spikes', 'eroded_badlands'],
	5: ['the_end', 'end_highlands', 'end_midlands', 'end_barrens', 'small_end_islands'],
};
const BIOME_EXCLUDE = { the_void: '生存模式无法到达' };
/** 结构：中文名 -> [结构 ID 前缀或完整 ID, 难度] */
const STRUCTURE_MAP = {
	'下界化石': [['nether_fossil'], 3], '下界要塞': [['fortress'], 3], '丛林神庙': [['jungle_pyramid'], 3],
	'古迹废墟': [['trail_ruins'], 3], '埋藏的宝藏': [['buried_treasure'], 3], '堡垒遗迹': [['bastion_remnant'], 3],
	'废弃传送门': [['ruined_portal*'], 1], '废弃矿井': [['mineshaft*'], 2], '废弃营地': [['abandoned_camp*'], 2],
	'掠夺者前哨站': [['pillager_outpost'], 2], '末地城': [['end_city'], 5], '村庄': [['village_*'], 1],
	'林地府邸': [['mansion'], 5], '沉船': [['shipwreck*'], 1], '沙漠神殿': [['desert_pyramid'], 2],
	'沼泽小屋': [['swamp_hut'], 3], '海底废墟': [['ocean_ruin_*'], 2], '海底神殿': [['monument'], 3],
	'要塞': [['stronghold'], 4], '试炼密室': [['trial_chambers'], 3], '远古城市': [['ancient_city'], 4], '雪屋': [['igloo'], 3],
};
const ENCHANT_DIFFICULTY = {
	4: ['mending', 'frost_walker', 'binding_curse', 'vanishing_curse', 'soul_speed', 'loyalty', 'riptide', 'channeling', 'impaling', 'density', 'breach'],
	5: ['swift_sneak', 'wind_burst'],
};
const EFFECT_EXCLUDE = { luck: '生存模式无来源', unluck: '生存模式无来源', health_boost: '生存模式无来源' };
const EFFECT_INSTANT = new Set(['instant_health', 'instant_damage', 'saturation']);
const EFFECT_DIFFICULTY = {
	1: ['hunger', 'poison'],
	2: ['regeneration', 'absorption', 'nausea', 'saturation', 'night_vision', 'blindness', 'weakness', 'jump_boost', 'slowness', 'speed', 'bad_omen', 'dolphins_grace', 'fire_resistance', 'instant_damage'],
	4: ['levitation', 'hero_of_the_village', 'conduit_power', 'slow_falling', 'invisibility', 'darkness', 'resistance', 'wither', 'raid_omen', 'trial_omen', 'wind_charged', 'weaving', 'oozing', 'infested', 'water_breathing', 'instant_health', 'strength'],
	5: ['haste'],
};
/** 药水：不能作为谜底的基础类型 */
const POTION_EXCLUDE = { empty: '不可合成的药水在生存模式无法获得', luck: '幸运药水在生存模式无法获得' };
const POTION_EASY = new Set(['water']);
/** bingo 分级表没有收录的个别条目，手工给出难度 */
const ITEM_DIFFICULTY = { 'minecraft:egg': 1 };
const ADVANCEMENT_DIFFICULTY = {
	'minecraft:story/root': 1, 'minecraft:adventure/root': 1, 'minecraft:husbandry/root': 1,
	'minecraft:nether/root': 3, 'minecraft:end/root': 5,
};

function tableLookup(table, id, fallback) {
	for (const [score, ids] of Object.entries(table)) if (ids.includes(id)) return Number(score);
	return fallback;
}
function matchIds(patterns, set) {
	const out = [];
	for (const p of patterns) {
		if (p.endsWith('*')) out.push(...[...set].filter((s) => s.startsWith(p.slice(0, -1))));
		else if (set.has(p)) out.push(p);
	}
	return [...new Set(out)].sort();
}

// ---------- 逐条处理 ----------
const DROPPED_TYPES = new Set(['属性', '指令', '更新']);
const MAX_VI = raw.entries.find((e) => e.v === '26.3')?.vi ?? 109;
const dimChat = (n) => raw.dimensions[n]?.chat ?? '无';
const dimDefault = (n, over = 2, nether = 3, end = 5) => {
	const d = dimChat(n);
	if (d.includes('主世界') || d.includes('全部')) return over;
	if (d.includes('下界')) return nether;
	if (d.includes('末地')) return end;
	return over;
};

function itemChallenge(e, ids) {
	// 等价变种展开
	let all = [];
	for (const id of ids) all.push(...copperFamily(id));
	all = [...new Set(all)].sort();
	const blocked = all.filter((id) => UNOBTAINABLE.has(mc(id)));
	if (blocked.length === all.length) return { ok: false, why: '生存模式无法获得该物品' };
	all = all.filter((id) => !UNOBTAINABLE.has(mc(id)));
	const tiers = all.map((id) => ITEM_DIFFICULTY[mc(id)] ?? ITEM_TIER[mc(id)]).filter((t) => t != null);
	const d = tiers.length ? Math.min(...tiers) : dimDefault(e.n);
	return { ok: true, ch: { t: 'item', ids: all.map(mc) }, d };
}

function resolve(e) {
	if (e.vi > MAX_VI) return { ok: false, why: `${e.v} 才加入，26.3 中不存在` };
	const sub = raw.classifications[e.n]?.subtags?.chat ?? '';
	const tags = raw.classifications[e.n]?.tags?.chat ?? '';
	switch (e.ty) {
		case '方块': {
			const blocks = keysFor(e.n, ['block.minecraft.']).filter((k) => k.split('.').length === 3).map((k) => k.split('.')[2]).filter((b) => BLOCKS.has(b));
			if (!blocks.length) return { ok: false, why: '未找到对应的方块 ID' };
			const items = [...new Set(blocks.map(blockToItem).filter(Boolean))];
			if (!items.length) return { ok: false, why: '该方块没有物品形式' };
			return itemChallenge(e, items);
		}
		case '物品': {
			// 药水与药箭
			for (const [kind, containers] of [['potion', ['potion', 'splash_potion', 'lingering_potion']], ['splash_potion', ['potion', 'splash_potion', 'lingering_potion']], ['lingering_potion', ['potion', 'splash_potion', 'lingering_potion']], ['tipped_arrow', ['tipped_arrow']]]) {
				const k = keysFor(e.n, [`item.minecraft.${kind}.effect.`])[0];
				if (!k) continue;
				const base = k.split('.').pop();
				if (POTION_EXCLUDE[base]) return { ok: false, why: POTION_EXCLUDE[base] };
				const potions = [base, `long_${base}`, `strong_${base}`].map(mc);
				return { ok: true, ch: { t: 'potion', items: containers.map(mc), potions }, d: POTION_EASY.has(base) && kind === 'potion' ? 1 : 4 };
			}
			let ids = keysFor(e.n, ['item.minecraft.', 'block.minecraft.'])
				.map((k) => k.split('.'))
				.filter((p) => p.length === 3 || (p.length === 4 && p[3] === 'new'))
				.map((p) => p[2])
				.filter((i) => ITEMS.has(i));
			ids = [...new Set(ids)];
			if (!ids.length) return { ok: false, why: '未找到对应的物品 ID' };
			// 纹饰、图案类变种互相等价
			if (ids.some((i) => SHERDS.includes(i))) ids = SHERDS;
			else if (ids.some((i) => TRIMS.includes(i))) ids = TRIMS;
			else if (ids.some((i) => BANNER_PATTERNS.includes(i))) ids = BANNER_PATTERNS;
			else if (ids.some((i) => DISCS.includes(i)) && ids.length > 1) ids = DISCS;
			return itemChallenge(e, ids);
		}
		case '实体': {
			let ids = keysFor(e.n, ['entity.minecraft.']).filter((k) => k.split('.').length === 3).map((k) => k.split('.')[2]);
			// 彩色坐垫：实体只有一种，物品按颜色区分
			const cushion = keysFor(e.n, ['item.minecraft.']).map((k) => k.split('.')[2]).filter((i) => CUSHIONS.includes(i));
			if (cushion.length) return itemChallenge(e, cushion);
			if (!ids.length) return { ok: false, why: '未找到对应的实体 ID' };
			if (sub.includes('指令生成')) return { ok: false, why: '仅指令生成' };
			if (tags.includes('非生物')) {
				const id = ids[0];
				if (BOATS.includes(id)) return itemChallenge(e, BOATS);
				if (CHEST_BOATS.includes(id)) return itemChallenge(e, CHEST_BOATS);
				const items = ENTITY_ITEM[id]?.filter((i) => ITEMS.has(i));
				if (!items?.length) return { ok: false, why: '该实体没有物品形式' };
				return itemChallenge(e, items);
			}
			for (const id of ids) if (MOB_EXCLUDE[id]) return { ok: false, why: MOB_EXCLUDE[id] };
			ids = ids.filter((id) => MOB_LOOT.has(id));
			if (!ids.length) return { ok: false, why: '26.3 中不存在该生物' };
			return { ok: true, ch: { t: 'kill', ids: ids.map(mc) }, d: tableLookup(MOB_DIFFICULTY, ids[0], dimDefault(e.n)) };
		}
		case '进度': {
			const keys = keysFor(e.n, ['advancements.']).filter((k) => k.endsWith('.title'));
			const ids = [...new Set(keys.flatMap((k) => ADV_BY_TITLE_KEY[k] ?? []))];
			if (!ids.length) return { ok: false, why: '未找到对应的进度 ID' };
			const d = Math.min(...ids.map((id) => ADVANCEMENT_DIFFICULTY[id] ?? ADV_TIER[id] ?? 3));
			return { ok: true, ch: { t: 'advancement', ids }, d };
		}
		case '生物群系': {
			const ids = keysFor(e.n, ['biome.minecraft.']).map((k) => k.split('.')[2]).filter((b) => BIOMES.has(b));
			if (!ids.length) return { ok: false, why: '26.3 中不存在该生物群系' };
			if (BIOME_EXCLUDE[ids[0]]) return { ok: false, why: BIOME_EXCLUDE[ids[0]] };
			return { ok: true, ch: { t: 'biome', ids: ids.map(mc) }, d: tableLookup(BIOME_DIFFICULTY, ids[0], dimDefault(e.n)) };
		}
		case '附魔': {
			const ids = keysFor(e.n, ['enchantment.minecraft.']).map((k) => k.split('.')[2]).filter((b) => ENCHANTS.has(b));
			if (!ids.length) return { ok: false, why: '未找到对应的附魔 ID' };
			return { ok: true, ch: { t: 'enchant', ids: ids.map(mc) }, d: tableLookup(ENCHANT_DIFFICULTY, ids[0], 3) };
		}
		case '状态效果': {
			const ids = keysFor(e.n, ['effect.minecraft.']).filter((k) => k.split('.').length === 3).map((k) => k.split('.')[2]);
			if (!ids.length) return { ok: false, why: '未找到对应的状态效果 ID' };
			if (EFFECT_EXCLUDE[ids[0]]) return { ok: false, why: EFFECT_EXCLUDE[ids[0]] };
			const ch = { t: 'effect', ids: ids.map(mc) };
			if (EFFECT_INSTANT.has(ids[0])) ch.instant = true;
			return { ok: true, ch, d: tableLookup(EFFECT_DIFFICULTY, ids[0], 3) };
		}
		case '结构': {
			const m = STRUCTURE_MAP[e.n];
			if (!m) return { ok: false, why: '未配置对应的结构 ID' };
			const ids = matchIds(m[0], STRUCTURES);
			if (!ids.length) return { ok: false, why: '26.3 中不存在该结构' };
			return { ok: true, ch: { t: 'structure', ids: ids.map(mc) }, d: m[1] };
		}
		default:
			return { ok: false, why: '该类型不参与' };
	}
}

const entries = [];
const stats = { dropped: 0, removed: 0 };
const excluded = [];
for (const e of raw.entries) {
	if (DROPPED_TYPES.has(e.ty)) {
		stats.dropped++;
		continue;
	}
	if (e.removed) {
		stats.removed++;
		continue;
	}
	const cls = raw.classifications[e.n] ?? {};
	const dim = raw.dimensions[e.n] ?? { mask: 0, chat: '无' };
	const nm = raw.names[e.n] ?? {};
	const eq = raw.equivalences[e.n] ?? {};
	const r = resolve(e);
	const out = {
		n: e.n, ty: e.ty, v: e.v, vi: e.vi, vg: e.vg, vp: e.vp,
		tg: cls.tags?.chat ?? '无', st: cls.subtags?.chat ?? '无',
		dm: dim.chat ?? '无', mk: dim.mask ?? 0,
	};
	if (nm.base) out.nb = nm.base;
	if (nm.variant) out.nv = nm.variant;
	if (nm.fields?.length) out.nf = nm.fields;
	if (nm.blue?.length) out.b = nm.blue;
	if (nm.yellow?.length) out.y = nm.yellow;
	if (nm.orange?.length) out.o = nm.orange;
	if (eq.color) out.ec = eq.color;
	if (eq.family) out.ef = eq.family;
	if (eq.copper) out.eu = eq.copper;
	if (r.ok) {
		out.ch = r.ch;
		out.d = r.d;
	} else {
		out.why = r.why;
		excluded.push(out);
	}
	entries.push(out);
}

const lexicon = { format: 1, minecraft: '26.3', entries, classColors: raw.classification_colors };
fs.mkdirSync(path.dirname(outJson), { recursive: true });
fs.writeFileSync(outJson, JSON.stringify(lexicon));

// ---------- 报告 ----------
const TYPES = ['方块', '物品', '实体', '进度', '生物群系', '附魔', '状态效果', '结构'];
const answerable = entries.filter((e) => e.ch);
const lines = [];
lines.push('# 词库报告', '');
lines.push(`由 \`tools/build_lexicon.mjs\` 生成。词库来自「猜词」数据包，共 ${raw.entries.length} 条。`, '');
lines.push(`- 去除「属性 / 指令 / 更新」三类：${stats.dropped} 条`);
lines.push(`- 去除「被移除的内容」：${stats.removed} 条`);
lines.push(`- 保留可猜词条：${entries.length} 条，其中可作为谜底：${answerable.length} 条，只能猜不能出题：${excluded.length} 条`, '');
lines.push('## 可作为谜底的数量（按类型与难度）', '');
lines.push('| 类型 | 难度1 | 难度2 | 难度3 | 难度4 | 难度5 | 合计 | 只能猜 |', '|---|---|---|---|---|---|---|---|');
for (const t of TYPES) {
	const list = answerable.filter((e) => e.ty === t);
	const row = [1, 2, 3, 4, 5].map((d) => list.filter((e) => e.d === d).length);
	lines.push(`| ${t} | ${row.join(' | ')} | ${list.length} | ${excluded.filter((e) => e.ty === t).length} |`);
}
const total = [1, 2, 3, 4, 5].map((d) => answerable.filter((e) => e.d === d).length);
lines.push(`| **合计** | ${total.join(' | ')} | ${answerable.length} | ${excluded.length} |`, '');
lines.push('## 只能猜、不会被选为谜底的词条', '');
const byWhy = {};
for (const e of excluded) (byWhy[`${e.ty}：${e.why}`] ??= []).push(e.n);
for (const [why, names] of Object.entries(byWhy).sort()) lines.push(`### ${why}（${names.length}）`, '', names.join('、'), '');
lines.push('## 难度明细', '');
lines.push('难度即分值。方块、物品、进度优先采用 bingo 模组自带分级表（D=1、C=2、B=3、A=4、S=5），其余类型为本模组的默认判断，均可在配置文件中逐条覆盖。', '');
for (const t of TYPES) {
	lines.push(`### ${t}`, '');
	for (const d of [1, 2, 3, 4, 5]) {
		const names = answerable.filter((e) => e.ty === t && e.d === d).map((e) => e.n);
		if (names.length) lines.push(`- **难度 ${d}**（${names.length}）：${names.join('、')}`);
	}
	lines.push('');
}
fs.mkdirSync(path.dirname(outReport), { recursive: true });
fs.writeFileSync(outReport, lines.join('\n'));

console.log(`entries=${entries.length} answerable=${answerable.length} excluded=${excluded.length} dropped=${stats.dropped} removed=${stats.removed}`);
console.log('per difficulty:', total.join(' / '));
for (const [why, names] of Object.entries(byWhy).sort()) console.log(` - ${why}: ${names.length}  e.g. ${names.slice(0, 8).join('、')}`);
console.log(`size=${(fs.statSync(outJson).size / 1024).toFixed(0)}KB`);
