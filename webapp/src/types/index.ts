export const VALID_HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'] as const;

export type HttpMethod = (typeof VALID_HTTP_METHODS)[number];

export function isHttpMethod(method: string): method is HttpMethod {
  return (VALID_HTTP_METHODS as readonly string[]).includes(String(method).toUpperCase());
}

export type LoadModel = 'CLOSED';

export type TestRunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export const TERMINAL_STATUSES: readonly TestRunStatus[] = ['COMPLETED', 'FAILED', 'CANCELLED'];

export type ExecutionOutcome =
  | 'SUCCESS_2XX'
  | 'REDIRECT_3XX'
  | 'CLIENT_ERROR_4XX'
  | 'SERVER_ERROR_5XX'
  | 'TIMEOUT'
  | 'CONNECT_ERROR';

export type VirtualUserActivity = 'IDLE' | 'REQUESTING' | 'COMPLETED' | 'ERROR';

export interface Stage {
  durationSeconds: number;
  target: number;
}

export interface WorkloadProfile {
  loadModel: LoadModel;
  startTarget: number;
  maxConcurrency: number;
  stages: Stage[];
}

export interface Test {
  id: string;
  testName: string;
  targetUrl: string;
  httpMethod: HttpMethod;
  workloadProfile: WorkloadProfile;
}

export type CreateTestInput = Omit<Test, 'id'>;

export interface LatencyStats {
  minNanos: number;
  meanNanos: number;
  maxNanos: number;
  p50Nanos: number;
  p90Nanos: number;
  p95Nanos: number;
  p99Nanos: number;
}

export interface RunSummary {
  totalDispatched: number;
  totalCompleted: number;
  totalDropped: number;
  outcomeCounts: Partial<Record<ExecutionOutcome, number>> | null;
  latencyStats: LatencyStats | null;
  requestedRps: number | null;
  actualRps: number | null;
}

export interface TestRun {
  id: string;
  testId: string;
  status: TestRunStatus;
  configSnapshot: WorkloadProfile;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  summary: RunSummary | null;
}

export interface TestRunMetric {
  elapsedSeconds: number;
  requestedTarget: number;
  completedRps: number;
  completedCount: number;
  droppedCount: number;
  activeRequests: number;
  outcomeCounts: Partial<Record<ExecutionOutcome, number>>;
}

export interface VirtualUser {
  id: number;
  state: VirtualUserActivity;
  requestCount: number;
  lastOutcome: ExecutionOutcome | null;
  lastActivityAt: string;
}

export interface AuthUser {
  username: string;
  roles: string[];
}

export function hasRole(user: AuthUser | null, ...roles: string[]): boolean {
  if (!user) return false;
  return roles.some((role) => user.roles.includes(`ROLE_${role}`) || user.roles.includes(role));
}

export function isTerminal(status: TestRunStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}
