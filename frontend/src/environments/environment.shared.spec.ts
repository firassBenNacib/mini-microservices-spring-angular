import { buildEnvironment } from './environment.shared';

describe('buildEnvironment', () => {
  it('keeps relative paths when no base URL is configured', () => {
    expect(buildEnvironment()).toEqual({
      authUrl: '/auth',
      apiUrl: '/api',
      auditUrl: '/audit',
      gatewayUrl: '/gateway'
    });
  });

  it('normalizes trailing slashes in absolute base URLs', () => {
    expect(buildEnvironment('https://api.example.com///')).toEqual({
      authUrl: 'https://api.example.com/auth',
      apiUrl: 'https://api.example.com/api',
      auditUrl: 'https://api.example.com/audit',
      gatewayUrl: 'https://api.example.com/gateway'
    });
  });
});
