const fs = require('node:fs');

const [summaryPath, statementsFloor, branchesFloor, functionsFloor, linesFloor] = process.argv.slice(2);

if (!summaryPath) {
  throw new Error('Usage: node scripts/check-coverage.cjs <summary-path> <statements> <branches> <functions> <lines>');
}

const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
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
