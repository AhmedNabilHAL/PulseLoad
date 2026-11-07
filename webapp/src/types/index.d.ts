
export const VALID_HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'] as const;

export type HttpMethod = typeof VALID_HTTP_METHODS[number];

export function isHttpMethod(method: string): method is HttpMethod {
  return VALID_HTTP_METHODS.includes(String(method).toUpperCase() as HttpMethod);
}

export interface Test {
  id?: string;
  testName: string;
  targetUrl: string;
  httpMethod: HttpMethod;
  durationInSeconds: number;
  requestsPerSec: number;
}
