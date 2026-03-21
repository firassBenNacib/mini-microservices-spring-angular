function normalizeBaseUrl(baseUrl: string): string {
  let normalized = baseUrl.trim();
  while (normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1);
  }
  return normalized;
}

function joinBaseAndPath(baseUrl: string, path: string): string {
  const normalizedBaseUrl = normalizeBaseUrl(baseUrl);
  return normalizedBaseUrl ? `${normalizedBaseUrl}${path}` : path;
}

export function buildEnvironment(baseUrl = '') {
  return {
    authUrl: joinBaseAndPath(baseUrl, '/auth'),
    apiUrl: joinBaseAndPath(baseUrl, '/api'),
    auditUrl: joinBaseAndPath(baseUrl, '/audit'),
    gatewayUrl: joinBaseAndPath(baseUrl, '/gateway')
  };
}
