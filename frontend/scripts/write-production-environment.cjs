const fs = require('fs');
const path = require('path');

const frontendPublicApiBaseUrl = (process.env.FRONTEND_PUBLIC_API_BASE_URL || '').trim().replace(/\/+$/, '');
const targetFile = path.join(__dirname, '..', 'src', 'environments', 'environment.prod.ts');

const fileContents = `import { buildEnvironment } from './environment.shared';

export const environment = buildEnvironment(${JSON.stringify(frontendPublicApiBaseUrl)});
`;

if (!fs.existsSync(targetFile) || fs.readFileSync(targetFile, 'utf8') !== fileContents) {
  fs.writeFileSync(targetFile, fileContents);
}
