#!/usr/bin/env node
/**
 * LumeCard 导入 JSON 校验器
 *
 * 用法:
 *   node validate.mjs <file.json> [--strict]
 *
 * --strict: 把 WARNING 也视为失败（退出码 1）。
 *
 * 只做静态检查，不修改文件。退出码:
 *   0 = 无 ERROR（--strict 时还要求无 WARNING）
 *   1 = 有 ERROR（或 --strict 下有 WARNING）
 *   2 = 用法/读取/JSON 解析错误
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const fileArg = process.argv[2];
const strict = process.argv.includes('--strict');

if (!fileArg) {
  console.error('用法: node validate.mjs <file.json> [--strict]');
  process.exit(2);
}

const filePath = resolve(process.cwd(), fileArg);
let raw;
try {
  raw = readFileSync(filePath, 'utf8');
} catch (e) {
  console.error(`[FATAL] 无法读取文件: ${filePath}\n        ${e.message}`);
  process.exit(2);
}

// 去掉 UTF-8 BOM
raw = raw.replace(/^\uFEFF/, '');

let data;
try {
  data = JSON.parse(raw);
} catch (e) {
  console.error(`[FATAL] JSON 解析失败: ${e.message}`);
  process.exit(2);
}

const errors = [];
const warnings = [];

function err(where, message) {
  errors.push({ where, message });
}
function warn(where, message) {
  warnings.push({ where, message });
}

function isObject(v) {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}
function isNonEmptyString(v) {
  return typeof v === 'string' && v.trim().length > 0;
}
function hasField(obj, key) {
  return Object.prototype.hasOwnProperty.call(obj, key);
}
function isIsoInstant(v) {
  return typeof v === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/.test(v) &&
    !Number.isNaN(Date.parse(v));
}
function displayWidth(s) {
  let width = 0;
  for (const ch of s) {
    const code = ch.codePointAt(0);
    // CJK、全角标点、emoji 粗略按 2 个宽度计算
    width += (code >= 0x1100 && (
      code <= 0x115f ||
      (code >= 0x2e80 && code <= 0xa4cf) ||
      (code >= 0xac00 && code <= 0xd7a3) ||
      (code >= 0xf900 && code <= 0xfaff) ||
      (code >= 0xfe30 && code <= 0xfe4f) ||
      (code >= 0xff00 && code <= 0xff60) ||
      (code >= 0xffe0 && code <= 0xffe6) ||
      (code >= 0x1f300 && code <= 0x1faff)
    )) ? 2 : 1;
  }
  return width;
}
function stripHtml(s) {
  return s
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<\/li>/gi, '\n')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}
function stripClozeMarkers(s) {
  return s
    .replace(/\{\{c\d+::([^{}]+?)::[^{}]+?\}\}/g, '$1')
    .replace(/\{\{c\d+::([^{}]+?)\}\}/g, '$1');
}
function normalizeForDup(s) {
  return s
    .normalize('NFKC')
    .toLowerCase()
    .replace(/<[^>]*>/g, ' ')
    .replace(/\{\{c\d+::([^{}]+?)(?:::[^{}]+?)?\}\}/g, '$1')
    .replace(/[\s\p{P}\p{S}]+/gu, '');
}

const TOP_TYPES = new Set(['share', 'backup', 'incremental']);
const CARD_TYPES = new Set([
  'BASIC', 'REVERSED', 'CLOZE', 'MULTIPLE_CHOICE', 'MARKDOWN', 'RICH_TEXT',
]);
const CENTERABLE = new Set(['BASIC', 'REVERSED', 'RICH_TEXT']);
const FONT_SIZE_TYPES = new Set(['BASIC', 'REVERSED', 'CLOZE']);
const FONT_FAMILY_TYPES = new Set(['BASIC', 'REVERSED']);
const BUILTIN_FONTS = new Set(['default', 'serif', 'sans-serif', 'monospace', 'cursive']);
const KNOWN_METADATA = new Set(['hcenter', 'vcenter', 'fontSize', 'fontFamily']);
const DECK_COLORS = new Set([
  '#4CAF50', '#2196F3', '#FF9800', '#E91E63',
  '#9C27B0', '#00BCD4', '#FF5722', '#607D8B',
]);

function asArray(v, name, where) {
  if (!Array.isArray(v)) {
    err(where, `"${name}" 必须是数组`);
    return [];
  }
  return v;
}

// ── 顶层 ──────────────────────────────────────────────
if (!isObject(data)) {
  console.error('[FATAL] 顶层必须是 JSON 对象。');
  process.exit(2);
}

if (!TOP_TYPES.has(data.exportType)) {
  err('根', `exportType 必须是 "share"（推荐）、"backup" 或 "incremental"，当前为 ${JSON.stringify(data.exportType)}`);
} else if (data.exportType !== 'share') {
  warn('根', `建议使用 "share" 格式制卡，当前为 "${data.exportType}"`);
}
if (typeof data.version !== 'string' || !data.version) {
  warn('根', 'version 建议为字符串 "1.0.0"');
}
if (data.schemaVersion !== 1) {
  warn('根', `当前应用 schemaVersion 为 1，文件为 ${JSON.stringify(data.schemaVersion)}；若应用已升级请以 version.properties 为准`);
}
if (!isIsoInstant(data.exportDate)) {
  err('根', `exportDate 不是合法的 ISO-8601 UTC 时间: ${JSON.stringify(data.exportDate)}`);
}

if (data.reviewLogs !== undefined) warn('根', 'share 文件不应包含 reviewLogs（学习进度），导入时会被忽略或污染数据');
if (data.learningPlans !== undefined) warn('根', 'share 文件不应包含 learningPlans');

const kbs = asArray(data.knowledgeBases, 'knowledgeBases', '根');
const decks = asArray(data.decks, 'decks', '根');
const cards = asArray(data.cards, 'cards', '根');

// ── 知识库 ────────────────────────────────────────────
const kbIds = new Map();
const kbNames = new Map();
kbs.forEach((kb, i) => {
  const where = `knowledgeBases[${i}]`;
  if (!isObject(kb)) {
    err(where, '必须是对象');
    return;
  }
  for (const key of ['id', 'name', 'createdAt', 'updatedAt']) {
    if (!hasField(kb, key)) err(where, `缺少必填字段 ${key}（应用反序列化会失败）`);
  }
  if (!isNonEmptyString(kb.id)) err(where, '缺少 id');
  if (!isNonEmptyString(kb.name)) err(where, '缺少 name');
  if (isNonEmptyString(kb.id)) {
    if (kbIds.has(kb.id)) err(where, `id 重复: ${kb.id}`);
    kbIds.set(kb.id, kb);
    if (!/^[A-Za-z0-9_-]+$/.test(kb.id)) warn(where, `id 含非 ASCII/特殊字符，建议只用字母数字下划线: ${kb.id}`);
  }
  if (isNonEmptyString(kb.name)) {
    if (kbNames.has(kb.name)) warn(where, `知识库名称重复: ${kb.name}`);
    kbNames.set(kb.name, kb);
  }
  if (hasField(kb, 'icon') && typeof kb.icon !== 'string') {
    err(where, 'icon 必须是字符串（emoji），不能是 null/数字');
  }
  for (const key of ['createdAt', 'updatedAt']) {
    if (!isIsoInstant(kb[key])) err(where, `${key} 缺失或不是合法 ISO-8601 UTC 时间`);
  }
  if (kb.deletedAt) warn(where, '制卡文件不应包含 deletedAt');
});

// ── 牌组 ──────────────────────────────────────────────
const deckIds = new Map();
const deckNamesInKb = new Map();
decks.forEach((deck, i) => {
  const where = `decks[${i}]`;
  if (!isObject(deck)) {
    err(where, '必须是对象');
    return;
  }
  for (const key of ['id', 'knowledgeBaseId', 'name', 'description', 'color', 'icon', 'createdAt', 'updatedAt']) {
    if (!hasField(deck, key)) err(where, `缺少必填字段 ${key}（应用反序列化会失败）`);
  }
  if (!isNonEmptyString(deck.id)) err(where, '缺少 id');
  if (!isNonEmptyString(deck.name)) err(where, '缺少 name');
  if (!isNonEmptyString(deck.knowledgeBaseId)) {
    err(where, '缺少 knowledgeBaseId');
  } else if (!kbIds.has(deck.knowledgeBaseId)) {
    err(where, `knowledgeBaseId 指向不存在的知识库: ${deck.knowledgeBaseId}`);
  }
  if (isNonEmptyString(deck.id)) {
    if (deckIds.has(deck.id)) err(where, `id 重复: ${deck.id}`);
    deckIds.set(deck.id, deck);
    if (!/^[A-Za-z0-9_-]+$/.test(deck.id)) warn(where, `id 含非 ASCII/特殊字符: ${deck.id}`);
  }
  if (isNonEmptyString(deck.name) && isNonEmptyString(deck.knowledgeBaseId)) {
    const key = `${deck.knowledgeBaseId}\u0000${deck.name}`;
    if (deckNamesInKb.has(key)) warn(where, `同一知识库内名称重复: ${deck.name}`);
    deckNamesInKb.set(key, deck);
  }
  if (hasField(deck, 'color')) {
    if (typeof deck.color !== 'string') {
      err(where, 'color 必须是十六进制颜色字符串，不能是 null/数字');
    } else if (!DECK_COLORS.has(deck.color.toUpperCase())) {
      warn(where, `color 不在内置色板中: ${deck.color}（建议使用内置色板）`);
    }
  }
  if (hasField(deck, 'icon') && typeof deck.icon !== 'string') {
    err(where, 'icon 必须是字符串（emoji），不能是 null/数字');
  }
  if (deck.parentId !== undefined && deck.parentId !== null) {
    if (typeof deck.parentId !== 'string') {
      err(where, 'parentId 必须是字符串或 null');
    } else if (!deckIds.has(deck.parentId)) {
      // 父牌组可能在本数组更后面，第二遍再检查
    }
  }
  for (const key of ['createdAt', 'updatedAt']) {
    if (!isIsoInstant(deck[key])) err(where, `${key} 缺失或不是合法 ISO-8601 UTC 时间`);
  }
  if (deck.deletedAt) warn(where, '制卡文件不应包含 deletedAt');
});

// 父引用与循环依赖（第二遍）
decks.forEach((deck, i) => {
  if (!isObject(deck) || !isNonEmptyString(deck.parentId)) return;
  const where = `decks[${i}]`;
  if (!deckIds.has(deck.parentId)) {
    err(where, `parentId 指向不存在的牌组: ${deck.parentId}`);
    return;
  }
  const parent = deckIds.get(deck.parentId);
  if (parent && isNonEmptyString(deck.knowledgeBaseId) && parent.knowledgeBaseId !== deck.knowledgeBaseId) {
    warn(where, 'parentId 与子牌组不在同一知识库');
  }
  if (parent && decks.indexOf(parent) > i) {
    warn(where, '父牌组排在子牌组之后；建议父牌组先出现，避免导入时外键顺序问题');
  }
  if (deck.parentId === deck.id) {
    err(where, 'parentId 不能指向自己');
    return;
  }
  const seen = new Set([deck.id]);
  let cur = parent;
  while (cur) {
    if (seen.has(cur.id)) {
      err(where, `牌组层级存在循环: ${[...seen, cur.id].join(' → ')}`);
      break;
    }
    seen.add(cur.id);
    cur = isNonEmptyString(cur.parentId) ? deckIds.get(cur.parentId) : null;
  }
});

// ── 卡牌 ──────────────────────────────────────────────
const cardIds = new Map();
const normalizedFronts = new Map();
const typeCounts = {};

function metadataOf(card, where) {
  const md = {};
  if (!hasField(card, 'metadata')) return md;
  if (!isObject(card.metadata)) {
    err(where, 'metadata 必须是对象（字符串键值对），不能是 null/数组');
    return md;
  }
  for (const [k, v] of Object.entries(card.metadata)) {
    if (!KNOWN_METADATA.has(k)) {
      warn(where, `未知 metadata 键 "${k}"，应用不会识别`);
    }
    if (typeof v !== 'string') {
      err(where, `metadata.${k} 必须是字符串，当前为 ${typeof v}`);
      continue;
    }
    md[k] = v;
    if ((k === 'hcenter' || k === 'vcenter') && !/^(true|false)$/i.test(v)) {
      err(where, `metadata.${k} 只能是 "true" 或 "false"，当前为 ${JSON.stringify(v)}`);
    }
    if (k === 'fontSize') {
      const n = Number(v);
      if (!/^\d+$/.test(v) || n < 12 || n > 120) {
        err(where, `fontSize 必须是 12–120 的整数字符串，当前为 ${JSON.stringify(v)}`);
      }
    }
    if (k === 'fontFamily' && !BUILTIN_FONTS.has(v)) {
      warn(where, `fontFamily "${v}" 不是内置字体；用户字体 ID（user_*）在其他设备上会失效`);
    }
  }
  return md;
}

function checkTextLength(card, where, md) {
  if (!CENTERABLE.has(card.type)) return;
  const visible = stripHtml(card.front || '') + '\n' + stripHtml(card.back || '');
  const lines = visible.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  const maxWidth = lines.reduce((m, l) => Math.max(m, displayWidth(l)), 0);
  const hasList = /(^|\n)\s*([-*•]|\d+[.)])\s+/.test(visible) || /<li\b/i.test(card.front || '') || /<li\b/i.test(card.back || '');
  const hasCode = /```/.test(visible) || /<pre\b/i.test(card.front || '') || /<pre\b/i.test(card.back || '');

  if (md.hcenter === 'true') {
    if (hasList || hasCode) {
      warn(where, '启用了水平居中，但内容含列表/代码；建议左对齐');
    }
    if (maxWidth > 60) {
      warn(where, `启用了水平居中，但最长行显示宽度约 ${maxWidth}（建议 ≤ 60）；长段落居中会难读`);
    }
  }
  if (md.vcenter === 'true') {
    const totalChars = visible.replace(/\s+/g, '').length;
    if (totalChars > 180 || lines.length > 6 || hasList || hasCode) {
      warn(where, `启用了垂直居中，但内容约 ${totalChars} 字符 / ${lines.length} 行${hasList ? '，含列表' : ''}${hasCode ? '，含代码' : ''}；垂直居中会关闭滚动，长内容可能被截断`);
    }
  }
}

function checkBasic(card, where, md) {
  if (!isNonEmptyString(card.front)) err(where, 'front 不能为空');
  if (!isNonEmptyString(card.back)) err(where, 'back 不能为空');
  const both = `${card.front || ''}\n${card.back || ''}`;
  if (/\*\*[^*]+\*\*|^#{1,6}\s|\n#{1,6}\s|```|\|.*\|.*\||\[[^\]]+\]\([^)]+\)/m.test(both)) {
    warn(where, 'BASIC/REVERSED 是纯文本，Markdown 语法会原样显示；需要格式请改用 MARKDOWN 或 RICH_TEXT');
  }
  if (isNonEmptyString(card.front) && card.front.trim() === (card.back || '').trim()) {
    warn(where, 'front 与 back 完全相同，缺少回忆价值');
  }
  if ((card.back || '').length > 600) {
    warn(where, `back 长度 ${(card.back || '').length} 字符，可能过长；考虑拆分`);
  }
  checkTextLength(card, where, md);
}

const CLOZE_RE = /\{\{c(\d+)::([^{}]+?)(?:::[^{}]+?)?\}\}/g;

function checkCloze(card, where, md) {
  if (!isNonEmptyString(card.front)) {
    err(where, 'front 不能为空');
    return;
  }
  const matches = [...card.front.matchAll(CLOZE_RE)];
  if (matches.length === 0) {
    err(where, 'CLOZE 的 front 中没有找到 {{c1::答案}} 挖空标记');
  }
  if (matches.length > 2) {
    warn(where, `CLOZE 包含 ${matches.length} 个挖空；建议一张卡只挖 1 个空（最多 2 个）`);
  }
  const numbers = new Set();
  for (const m of matches) {
    const n = Number(m[1]);
    if (!Number.isInteger(n) || n < 1) err(where, `挖空编号非法: c${m[1]}`);
    if (numbers.has(m[1])) warn(where, `挖空编号 c${m[1]} 重复`);
    numbers.add(m[1]);
    if (!m[2].trim()) err(where, `挖空 c${m[1]} 的答案为空`);
  }
  if (/\{\{|\}\}/.test(card.front.replace(CLOZE_RE, ''))) {
    warn(where, 'CLOZE front 中存在未闭合或格式错误的 {{ }} 标记');
  }
  const stripped = stripClozeMarkers(card.front).trim();
  const back = (card.back || '').trim();
  if (back && normalizeForDup(back) !== normalizeForDup(stripped)) {
    warn(where, 'CLOZE 学习页只渲染 front，back 不参与复习显示；补充解释请放进 front 或另建 BASIC 卡');
  }
  if (md.hcenter !== undefined || md.vcenter !== undefined) {
    warn(where, 'CLOZE 不支持水平/垂直居中；这两个设置不会生效');
  }
  if (md.fontFamily !== undefined) {
    warn(where, 'CLOZE 不支持 fontFamily；该设置不会生效');
  }
}

function checkMultipleChoice(card, where, md) {
  if (!isNonEmptyString(card.front)) err(where, 'front 不能为空');
  if (!isNonEmptyString(card.back)) {
    err(where, 'back 不能为空（每行一个选项，正确项以 + 开头）');
    return;
  }
  const options = card.back.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
  if (options.length < 2) err(where, '至少需要 2 个选项');
  if (options.length < 3) warn(where, `只有 ${options.length} 个选项；建议 3–6 个`);
  if (options.length > 6) warn(where, `有 ${options.length} 个选项；建议 3–6 个`);

  const correct = options.filter((o) => o.startsWith('+'));
  if (correct.length === 0) err(where, '没有正确选项：正确项必须以 + 开头');
  if (correct.length > 1) {
    const declaresMulti = /多选|多項|multiple[\s-]?select|select\s+all/i.test(card.front || '');
    if (!declaresMulti) {
      warn(where, `有 ${correct.length} 个正确选项；若为多选，front 必须标注「（多选）」`);
    }
  }
  if (correct.some((o) => o.slice(1).trim() === '')) err(where, '存在空正确选项（只有 +）');

  const seen = new Map();
  for (const opt of options) {
    const body = opt.replace(/^\+/, '').trim();
    if (!body) {
      err(where, '存在空选项');
      continue;
    }
    if (/^[+＋]/.test(opt) && opt.startsWith('＋')) {
      warn(where, `全角加号不会被识别为正确项: ${opt}`);
    }
    if (/^[A-Za-z][).、:：]/.test(opt) || /^\d+[).、:：](?!\d)/.test(opt)) {
      warn(where, `选项包含字母/数字前缀，会被原样显示: ${opt}`);
    }
    if (/^(答案|正确答案|answer)\s*[:：]/i.test(opt)) {
      warn(where, `选项中出现「答案：」行，它会被当成一个选项: ${opt}`);
    }
    if (displayWidth(opt) > 80) {
      warn(where, `选项过长（显示宽度约 ${displayWidth(opt)}）: ${opt.slice(0, 30)}…`);
    }
    const key = body.normalize('NFKC').toLowerCase();
    if (seen.has(key)) err(where, `选项重复: ${body}`);
    seen.set(key, opt);
  }
  if (md.hcenter !== undefined || md.vcenter !== undefined || md.fontSize !== undefined || md.fontFamily !== undefined) {
    warn(where, 'MULTIPLE_CHOICE 使用固定布局，metadata 样式不会生效');
  }
}

function checkMarkdown(card, where, md) {
  if (!isNonEmptyString(card.front)) err(where, 'front 不能为空');
  if (!isNonEmptyString(card.back)) err(where, 'back 不能为空');
  const both = `${card.front || ''}\n${card.back || ''}`;
  const fences = (both.match(/```/g) || []).length;
  if (fences % 2 !== 0) err(where, '代码围栏 ``` 数量不成对');
  const mathBlocks = (both.match(/\$\$/g) || []).length;
  if (mathBlocks % 2 !== 0) warn(where, '$$ 数学公式块数量不成对');
  if (/<[a-z][^>]*>/i.test(both)) {
    warn(where, 'MARKDOWN 中混用 HTML，渲染结果可能不一致；需要富文本请改用 RICH_TEXT');
  }
  if (md.hcenter !== undefined || md.vcenter !== undefined || md.fontSize !== undefined || md.fontFamily !== undefined) {
    warn(where, 'MARKDOWN 使用固定排版，metadata 样式不会生效');
  }
}

const HTML_VOID = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);

function checkRichText(card, where, md) {
  if (!isNonEmptyString(card.front)) err(where, 'front 不能为空');
  if (!isNonEmptyString(card.back)) err(where, 'back 不能为空');
  const both = `${card.front || ''}\n${card.back || ''}`;
  if (!/<[a-z][\s\S]*>/i.test(both)) {
    warn(where, 'RICH_TEXT 没有 HTML 标签；纯文本建议改用 BASIC');
  }
  if (/\*\*[^*]+\*\*|^#{1,6}\s|\n#{1,6}\s|```|\[[^\]]+\]\([^)]+\)/m.test(both)) {
    warn(where, 'RICH_TEXT 只解析 HTML，Markdown 语法会原样显示');
  }
  if (/<(script|style|iframe|object|embed)\b/i.test(both) || /\son[a-z]+\s*=/i.test(both)) {
    warn(where, 'RICH_TEXT 中包含脚本/样式/事件属性，可能不渲染或带来风险');
  }
  if (/<table\b/i.test(both)) {
    warn(where, 'RICH_TEXT 的表格渲染不稳定，建议改用 MARKDOWN 表格');
  }
  // 简单标签配对检查
  const stack = [];
  const tagRe = /<\/?([a-zA-Z][\w-]*)(?:\s[^>]*)?\/?>/g;
  let m;
  while ((m = tagRe.exec(both)) !== null) {
    const full = m[0];
    const name = m[1].toLowerCase();
    if (full.endsWith('/>') || HTML_VOID.has(name)) continue;
    if (full.startsWith('</')) {
      if (stack.length === 0 || stack[stack.length - 1] !== name) {
        warn(where, `HTML 标签可能不配对: </${name}>`);
        // 尽量恢复：如果栈里存在，弹到它
        const idx = stack.lastIndexOf(name);
        if (idx >= 0) stack.length = idx;
      } else {
        stack.pop();
      }
    } else {
      stack.push(name);
    }
  }
  if (stack.length > 0) {
    warn(where, `HTML 标签未闭合: <${stack.join('>, <')}>`);
  }
  if (md.fontSize !== undefined) warn(where, 'RICH_TEXT 不支持 metadata.fontSize；请用 HTML 标题层级/强调');
  if (md.fontFamily !== undefined) warn(where, 'RICH_TEXT 不支持 metadata.fontFamily');
  checkTextLength(card, where, md);
}

cards.forEach((card, i) => {
  const where = `cards[${i}]`;
  if (!isObject(card)) {
    err(where, '必须是对象');
    return;
  }
  for (const key of ['id', 'deckId', 'type', 'front', 'back', 'tags', 'createdAt', 'updatedAt']) {
    if (!hasField(card, key)) err(where, `缺少必填字段 ${key}（应用反序列化会失败）`);
  }
  if (!isNonEmptyString(card.id)) err(where, '缺少 id');
  if (!isNonEmptyString(card.deckId)) {
    err(where, '缺少 deckId');
  } else if (!deckIds.has(card.deckId)) {
    err(where, `deckId 指向不存在的牌组: ${card.deckId}`);
  }
  if (isNonEmptyString(card.id)) {
    if (cardIds.has(card.id)) err(where, `id 重复: ${card.id}`);
    cardIds.set(card.id, card);
    if (!/^[A-Za-z0-9_-]+$/.test(card.id)) warn(where, `id 含非 ASCII/特殊字符: ${card.id}`);
  }
  if (card.type === 'AI_GENERATED') {
    err(where, '不要手写 AI_GENERATED（保留给应用内 AI 生成流程）');
    return;
  }
  if (!CARD_TYPES.has(card.type)) {
    err(where, `非法卡型 ${JSON.stringify(card.type)}；只允许 ${[...CARD_TYPES].join(', ')}`);
    return;
  }
  typeCounts[card.type] = (typeCounts[card.type] || 0) + 1;

  if (card.title !== undefined && typeof card.title !== 'string') {
    err(where, 'title 必须是字符串');
  } else if (!isNonEmptyString(card.title)) {
    warn(where, '缺少 title；建议填写 4–16 字的标题');
  } else if (card.title.length > 60) {
    warn(where, `title 过长（${card.title.length} 字符），建议 4–16 字`);
  }

  if (card.front !== undefined && typeof card.front !== 'string') err(where, 'front 必须是字符串');
  if (card.back !== undefined && typeof card.back !== 'string') err(where, 'back 必须是字符串');

  if (card.tags !== undefined && card.tags !== null) {
    if (!Array.isArray(card.tags)) {
      err(where, 'tags 必须是字符串数组');
    } else {
      const seenTags = new Set();
      for (const tag of card.tags) {
        if (typeof tag !== 'string' || !tag.trim()) {
          err(where, 'tags 中存在空值或非字符串');
          continue;
        }
        if (seenTags.has(tag)) warn(where, `tag 重复: ${tag}`);
        seenTags.add(tag);
        if (tag.length > 40) warn(where, `tag 过长: ${tag.slice(0, 20)}…`);
      }
      if (card.tags.length > 4) warn(where, `tags 有 ${card.tags.length} 个，建议 1–4 个`);
    }
  }

  if (hasField(card, 'media')) {
    if (!Array.isArray(card.media)) err(where, 'media 必须是数组，不能是 null/对象');
    else if (card.media.length > 0) warn(where, 'media 非空；当前学习界面不渲染媒体，建议留空');
  }
  if (card.lastReviewedAt || card.nextReviewAt) {
    warn(where, 'share 文件不应带 lastReviewedAt/nextReviewAt（学习进度）');
  }

  for (const key of ['createdAt', 'updatedAt']) {
    if (!isIsoInstant(card[key])) err(where, `${key} 缺失或不是合法 ISO-8601 UTC 时间`);
  }

  const md = metadataOf(card, where);

  if ((md.hcenter === 'true' || md.vcenter === 'true') && !CENTERABLE.has(card.type)) {
    warn(where, `${card.type} 不支持水平/垂直居中；hcenter/vcenter 不会生效`);
  }
  if (md.fontSize !== undefined && !FONT_SIZE_TYPES.has(card.type)) {
    warn(where, `${card.type} 不支持 metadata.fontSize；该设置不会生效`);
  }
  if (md.fontFamily !== undefined && !FONT_FAMILY_TYPES.has(card.type)) {
    warn(where, `${card.type} 不支持 metadata.fontFamily；该设置不会生效`);
  }

  switch (card.type) {
    case 'BASIC':
    case 'REVERSED':
      checkBasic(card, where, md);
      break;
    case 'CLOZE':
      checkCloze(card, where, md);
      break;
    case 'MULTIPLE_CHOICE':
      checkMultipleChoice(card, where, md);
      break;
    case 'MARKDOWN':
      checkMarkdown(card, where, md);
      break;
    case 'RICH_TEXT':
      checkRichText(card, where, md);
      break;
  }

  // 重复正面检测
  if (isNonEmptyString(card.front)) {
    const key = normalizeForDup(card.front);
    if (key) {
      if (!normalizedFronts.has(key)) normalizedFronts.set(key, []);
      normalizedFronts.get(key).push({ id: card.id, where });
    }
  }
});

// 重复正面
for (const [, list] of normalizedFronts) {
  if (list.length > 1) {
    warn(list[0].where, `正面内容与其他卡重复: ${list.map((x) => x.id || '(无 id)').join(', ')}`);
  }
}

// ── 结构与覆盖统计 ────────────────────────────────────
if (kbs.length === 0) warn('根', '没有知识库');
if (cards.length === 0) warn('根', '没有卡牌');
if (decks.length === 0 && kbs.length > 0) warn('根', '有知识库但没有牌组');

const cardsPerDeck = new Map();
for (const card of cards) {
  if (isObject(card) && isNonEmptyString(card.deckId)) {
    cardsPerDeck.set(card.deckId, (cardsPerDeck.get(card.deckId) || 0) + 1);
  }
}
for (const [id, deck] of deckIds) {
  const n = cardsPerDeck.get(id) || 0;
  const where = `decks[${decks.indexOf(deck)}]`;
  if (n === 0) warn(where, `牌组「${deck.name || id}」没有卡片`);
  else if (n > 150) warn(where, `牌组「${deck.name || id}」有 ${n} 张卡，建议拆分（≤ 80 更易复习）`);
  else if (n > 80) warn(where, `牌组「${deck.name || id}」有 ${n} 张卡，考虑拆分`);
}

const decksPerKb = new Map();
for (const deck of decks) {
  if (isObject(deck) && isNonEmptyString(deck.knowledgeBaseId)) {
    decksPerKb.set(deck.knowledgeBaseId, (decksPerKb.get(deck.knowledgeBaseId) || 0) + 1);
  }
}
for (const [id, kb] of kbIds) {
  if ((decksPerKb.get(id) || 0) === 0) {
    warn(`knowledgeBases[${kbs.indexOf(kb)}]`, `知识库「${kb.name || id}」没有牌组`);
  }
}

// ── 输出 ──────────────────────────────────────────────
function printIssues(list, label) {
  if (list.length === 0) return;
  console.log(`\n${label} (${list.length})`);
  for (const item of list) {
    console.log(`  - [${item.where}] ${item.message}`);
  }
}

console.log('LumeCard 导入文件校验');
console.log(`文件: ${filePath}`);
console.log(`结构: 知识库 ${kbs.length} | 牌组 ${decks.length} | 卡牌 ${cards.length}`);
const typeLine = Object.entries(typeCounts)
  .map(([k, v]) => `${k} ${v}`)
  .join(' | ');
if (typeLine) console.log(`卡型: ${typeLine}`);

printIssues(errors, 'ERROR');
printIssues(warnings, 'WARNING');

if (errors.length === 0) {
  console.log('\n结果: ✅ 通过（没有 ERROR）');
} else {
  console.log(`\n结果: ❌ 失败（${errors.length} 个 ERROR，${warnings.length} 个 WARNING）`);
}

if (strict && errors.length === 0 && warnings.length > 0) {
  console.log('--strict 模式: WARNING 视为失败。');
  process.exit(1);
}
process.exit(errors.length > 0 ? 1 : 0);
