#!/usr/bin/env node
/**
 * 把 lumecard-deck-builder Skill 从规范源同步到各 agent 的 skills 目录。
 *
 * 用法：
 *   node scripts/sync-skill.mjs           # 写入/覆盖所有副本
 *   node scripts/sync-skill.mjs --check   # 只检查副本是否与源一致，不一致时退出码 1
 *
 * 规范源：.opencode/skills/lumecard-deck-builder
 * 副本：  .claude/skills/、.agents/skills/、.codebuddy/skills/
 */
import { cp, mkdir, readdir, readFile, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SKILL_NAME = 'lumecard-deck-builder';
const SOURCE = path.join(ROOT, '.opencode', 'skills', SKILL_NAME);
const TARGETS = [
  path.join(ROOT, '.claude', 'skills', SKILL_NAME),
  path.join(ROOT, '.agents', 'skills', SKILL_NAME),
  path.join(ROOT, '.codebuddy', 'skills', SKILL_NAME),
];

const toPosix = (p) => p.split(path.sep).join('/');

async function exists(p) {
  try {
    await stat(p);
    return true;
  } catch {
    return false;
  }
}

/** 返回目录下所有文件的相对路径（按字典序）。 */
async function listFiles(dir, base = dir, out = []) {
  const entries = await readdir(dir, { withFileTypes: true });
  entries.sort((a, b) => a.name.localeCompare(b.name));
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      await listFiles(full, base, out);
    } else if (entry.isFile()) {
      out.push(path.relative(base, full));
    }
  }
  return out;
}

async function checkTarget(target) {
  if (!(await exists(target))) return [`缺少目录：${toPosix(path.relative(ROOT, target))}`];

  const sourceFiles = await listFiles(SOURCE);
  const targetFiles = await listFiles(target);
  const problems = [];

  const targetSet = new Set(targetFiles);
  const sourceSet = new Set(sourceFiles);

  for (const rel of sourceFiles) {
    if (!targetSet.has(rel)) {
      problems.push(`缺少文件：${toPosix(path.join(path.relative(ROOT, target), rel))}`);
      continue;
    }
    const [a, b] = await Promise.all([
      readFile(path.join(SOURCE, rel)),
      readFile(path.join(target, rel)),
    ]);
    if (!a.equals(b)) {
      problems.push(`内容不一致：${toPosix(path.join(path.relative(ROOT, target), rel))}`);
    }
  }
  for (const rel of targetFiles) {
    if (!sourceSet.has(rel)) {
      problems.push(`多余文件：${toPosix(path.join(path.relative(ROOT, target), rel))}`);
    }
  }
  return problems;
}

async function main() {
  const checkOnly = process.argv.includes('--check');

  if (!(await exists(path.join(SOURCE, 'SKILL.md')))) {
    console.error(`找不到规范源：${toPosix(path.relative(ROOT, SOURCE))}/SKILL.md`);
    process.exit(1);
  }

  let failed = false;

  for (const target of TARGETS) {
    const rel = toPosix(path.relative(ROOT, target));
    if (checkOnly) {
      const problems = await checkTarget(target);
      if (problems.length === 0) {
        console.log(`OK   ${rel}`);
      } else {
        failed = true;
        console.error(`DRIFT ${rel}`);
        for (const problem of problems) console.error(`      - ${problem}`);
      }
      continue;
    }

    await rm(target, { recursive: true, force: true });
    await mkdir(path.dirname(target), { recursive: true });
    await cp(SOURCE, target, { recursive: true });
    console.log(`SYNC ${rel}`);
  }

  if (checkOnly) {
    console.log(
      failed
        ? '\n副本与规范源不一致，请运行：node scripts/sync-skill.mjs'
        : '\n所有副本均与规范源一致。',
    );
    process.exit(failed ? 1 : 0);
  }

  console.log(`\n完成：已从 ${toPosix(path.relative(ROOT, SOURCE))} 同步 ${TARGETS.length} 份副本。`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
