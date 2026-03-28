const fs = require('node:fs');
const path = require('node:path');

const [summaryPath, statementsFloor, branchesFloor, functionsFloor, linesFloor] = process.argv.slice(2);

if (!summaryPath) {
  throw new Error('Usage: node scripts/check-coverage.cjs <summary-path> <statements> <branches> <functions> <lines>');
}

function resolveWorkspacePath(inputPath) {
  const workspaceRoot = process.cwd();
  const resolvedPath = path.resolve(inputPath);
  const relativePath = path.relative(workspaceRoot, resolvedPath);
  if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) {
    throw new Error('summary-path must stay within the current workspace');
  }
  return resolvedPath;
}

const summary = JSON.parse(fs.readFileSync(resolveWorkspacePath(summaryPath), 'utf8'));
const total = summary.total;
const floors = {
  statements: Number(statementsFloor),
  branches: Number(branchesFloor),
  functions: Number(functionsFloor),
  lines: Number(linesFloor),
};

for (const [key, minimum] of Object.entries(floors)) {
  const actual = Number(total[key]?.pct ?? 0);
  if (actual < minimum) {
    console.error(`Coverage check failed for ${key}: ${actual}% < ${minimum}%`);
    process.exit(1);
  }
}
