import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { setTimeout } from 'node:timers/promises';

const clientRoot = fileURLToPath(new URL('../', import.meta.url));
const projectRoot = resolve(clientRoot, '../..');
const cli = fileURLToPath(import.meta.resolve('@playwright/test/cli'));
const sharedTarget = process.env.E2E_BASE_URL;
const projectName = process.env.E2E_PROJECT_NAME ?? 'english-learning-e2e';
if (!/^english-learning-e2e(?:-[a-z0-9-]+)?$/.test(projectName)) throw new Error('E2E_PROJECT_NAME must use the isolated english-learning-e2e prefix.');
const env = {
  ...process.env,
  APP_PORT: process.env.E2E_APP_PORT ?? '5174',
  API_PORT: process.env.E2E_API_PORT ?? '8081',
  BOOTSTRAP_ADMIN_ENABLED: 'true',
  DEMO_ENABLED: 'true',
};
env.WS_ALLOWED_ORIGINS = `http://localhost:${env.APP_PORT},http://127.0.0.1:${env.APP_PORT}`;
env.E2E_BASE_URL = sharedTarget ?? `http://127.0.0.1:${env.APP_PORT}`;
const compose = ['compose', '--project-name', projectName, '--file', 'compose.yml'];
function docker(args, quiet = false) {
  const result = spawnSync('docker', [...compose, ...args], { cwd: projectRoot, env, encoding: 'utf8', stdio: quiet ? 'pipe' : 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Docker E2E command failed (exit ${result.status}).`);
  return result.stdout ?? '';
}
let exitCode = 1;
try {
  if (!sharedTarget) {
    console.log(`Starting isolated E2E app at ${env.E2E_BASE_URL}; Docker project/volumes: ${projectName}.`);
    docker(['up', '-d', '--build', '--wait']);
    // Compose HTTP health may pass before ApplicationRunner finishes seeding.
    let ready = false;
    for (let attempt = 0; attempt < 90; attempt++) {
      if (docker(['logs', '--no-color', 'server'], true).includes('M02 curriculum seed completed:')) { ready = true; break; }
      await setTimeout(2000);
    }
    if (!ready) throw new Error('Isolated E2E curriculum did not finish seeding in time.');
  } else console.log(`Using explicitly configured E2E_BASE_URL: ${sharedTarget}`);
  const result = spawnSync(process.execPath, [cli, 'test', ...process.argv.slice(2)], { cwd: clientRoot, env, stdio: 'inherit' });
  if (result.error) throw result.error;
  exitCode = result.status ?? 1;
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
} finally {
  if (!sharedTarget) {
    try { docker(['stop']); } catch (error) { console.error(String(error)); exitCode = 1; }
  }
}
process.exitCode = exitCode;
