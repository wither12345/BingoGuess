// 把「猜词」数据包（noun_guess）里以 mcfunction 形式写入 storage 的词库导出为 JSON。
// 用法：node tools/export_datapack.mjs <数据包的 data/mom/function/data 目录> <输出 json>
import fs from 'node:fs';
import path from 'node:path';

const [, , dataDir, outFile] = process.argv;
if (!dataDir || !outFile) {
	console.error('usage: node tools/export_datapack.mjs <datapack data dir> <out.json>');
	process.exit(1);
}

/** 极简 SNBT 解析器：只覆盖数据包里用到的写法。 */
class Snbt {
	constructor(text) {
		this.s = text;
		this.i = 0;
	}

	ws() {
		while (this.i < this.s.length && /\s/.test(this.s[this.i])) this.i++;
	}

	value() {
		this.ws();
		const c = this.s[this.i];
		if (c === '{') return this.compound();
		if (c === '[') return this.list();
		if (c === '"' || c === "'") return this.quoted();
		return this.bare();
	}

	compound() {
		const out = {};
		this.i++; // {
		this.ws();
		if (this.s[this.i] === '}') {
			this.i++;
			return out;
		}
		for (;;) {
			this.ws();
			const key = this.s[this.i] === '"' || this.s[this.i] === "'" ? this.quoted() : this.bareKey();
			this.ws();
			if (this.s[this.i] !== ':') throw new Error(`expected ':' at ${this.i}: ${this.s.slice(this.i - 20, this.i + 20)}`);
			this.i++;
			out[key] = this.value();
			this.ws();
			if (this.s[this.i] === ',') {
				this.i++;
				continue;
			}
			if (this.s[this.i] === '}') {
				this.i++;
				return out;
			}
			throw new Error(`expected ',' or '}' at ${this.i}: ${this.s.slice(this.i - 20, this.i + 20)}`);
		}
	}

	list() {
		const out = [];
		this.i++; // [
		this.ws();
		if (this.s[this.i] === ']') {
			this.i++;
			return out;
		}
		for (;;) {
			out.push(this.value());
			this.ws();
			if (this.s[this.i] === ',') {
				this.i++;
				continue;
			}
			if (this.s[this.i] === ']') {
				this.i++;
				return out;
			}
			throw new Error(`expected ',' or ']' at ${this.i}: ${this.s.slice(this.i - 20, this.i + 20)}`);
		}
	}

	quoted() {
		const q = this.s[this.i++];
		let out = '';
		while (this.i < this.s.length) {
			const c = this.s[this.i++];
			if (c === '\\') {
				const n = this.s[this.i++];
				if (n === 'n') out += '\n';
				else if (n === 't') out += '\t';
				else if (n === 'r') out += '\r';
				else out += n;
			} else if (c === q) {
				return out;
			} else {
				out += c;
			}
		}
		throw new Error('unterminated string');
	}

	bareKey() {
		const start = this.i;
		while (this.i < this.s.length && !/[:,{}\[\]\s]/.test(this.s[this.i])) this.i++;
		return this.s.slice(start, this.i);
	}

	bare() {
		const start = this.i;
		while (this.i < this.s.length && !/[,{}\[\]\s]/.test(this.s[this.i])) this.i++;
		const tok = this.s.slice(start, this.i);
		if (tok === 'true') return true;
		if (tok === 'false') return false;
		const m = /^(-?\d+(?:\.\d+)?)([bslfdBSLFD])?$/.exec(tok);
		if (m) return Number(m[1]);
		return tok;
	}
}

/** 解析 storage 路径：a.b."c d".e -> [a, b, 'c d', e] */
function parsePath(p) {
	const out = [];
	let i = 0;
	while (i < p.length) {
		if (p[i] === '"') {
			let j = i + 1;
			let seg = '';
			while (p[j] !== '"') {
				if (p[j] === '\\') j++;
				seg += p[j++];
			}
			out.push(seg);
			i = j + 1;
		} else {
			let j = i;
			while (j < p.length && p[j] !== '.') j++;
			out.push(p.slice(i, j));
			i = j;
		}
		if (p[i] === '.') i++;
	}
	return out;
}

const root = {};
const files = fs.readdirSync(dataDir).filter((f) => f.endsWith('.mcfunction')).sort();
const lineRe = /^data modify storage mom:lex (.+?) (set|append) value (.*)$/;
let count = 0;
for (const f of files) {
	const text = fs.readFileSync(path.join(dataDir, f), 'utf8');
	for (const raw of text.split(/\r?\n/)) {
		const line = raw.trim();
		if (!line || line.startsWith('#')) continue;
		// 路径里可能含空格（带引号的名称），所以从右侧找 " set value " / " append value "
		let idx = line.indexOf(' set value ');
		let op = 'set';
		const idxAppend = line.indexOf(' append value ');
		if (idx < 0 || (idxAppend >= 0 && idxAppend < idx)) {
			idx = idxAppend;
			op = 'append';
		}
		if (!line.startsWith('data modify storage mom:lex ') || idx < 0) continue;
		const pathText = line.slice('data modify storage mom:lex '.length, idx);
		const valueText = line.slice(idx + (op === 'set' ? ' set value '.length : ' append value '.length));
		const segs = parsePath(pathText);
		const top = segs[0];
		// 查词索引体积大且模组内自行实现搜索，不导出
		if (['index', 'selection_index', 'removed_index', 'removed_selection_index', 'random_pools', 'byname'].includes(top)) continue;
		let value;
		try {
			value = new Snbt(valueText).value();
		} catch (e) {
			console.error(`parse error in ${f}: ${line.slice(0, 120)} -> ${e.message}`);
			process.exit(2);
		}
		let node = root;
		for (let k = 0; k < segs.length - 1; k++) {
			node[segs[k]] ??= {};
			node = node[segs[k]];
		}
		const last = segs[segs.length - 1];
		if (op === 'append') {
			node[last] ??= [];
			node[last].push(value);
		} else {
			node[last] = value;
		}
		count++;
	}
}

fs.mkdirSync(path.dirname(outFile), { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(root));
console.log(`parsed ${count} statements; top-level keys:`, Object.keys(root).map((k) => `${k}(${Array.isArray(root[k]) ? root[k].length : Object.keys(root[k]).length})`).join(', '));
