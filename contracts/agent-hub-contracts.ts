/**
 * SARKAR AGENT HUB — Shared Contracts (TypeScript Reference Specification)
 * Shared between Web Dashboard, Server Gateway, and SARKAR Assistant clients.
 */

export type CapabilityStatus = 'IMPLEMENTED' | 'PLACEHOLDER' | 'NOT_CONFIGURED' | 'DISABLED';

export type PermissionLevel = 'PUBLIC' | 'SAFE' | 'CONFIRMATION_REQUIRED' | 'RESTRICTED';

export type SideEffectType = 'NONE' | 'READ' | 'WRITE' | 'EXTERNAL';

export type TaskStatus =
  | 'PENDING'
  | 'PLANNING'
  | 'WAITING_FOR_PERMISSION'
  | 'EXECUTING'
  | 'VERIFYING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'PAUSED'
  | 'EXPIRED';

export type OriginSource = 'web' | 'api' | 'sarkar';

export interface BudgetConfig {
  maxSteps: number;
  maxToolCalls: number;
  maxRetriesPerStep: number;
  maxReplans: number;
  maxTokens: number;
  maxWallClockMs: number;
  perToolTimeoutMs: number;
}

export interface BudgetUsage {
  stepsUsed: number;
  toolCallsUsed: number;
  retriesUsed: number;
  replansUsed: number;
  tokensUsed: number;
  wallClockMsUsed: number;
}

export interface PlanStep {
  id: string;
  description: string;
  expectedTool: string;
  dependencies?: string[];
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
  toolInput?: string;
  toolOutput?: string;
  failureReason?: string;
}

export interface Plan {
  planId: string;
  explanation: string;
  steps: PlanStep[];
}

export interface FinalResponse {
  spokenSummary: string; // Plain phonetically formatted text without Markdown for TTS
  fullText: string;      // Comprehensive Markdown report
  language: string;      // en, hi-IN, etc.
}

export interface TaskEvent {
  eventId: string;
  taskId: string;
  type: string;
  timestamp: number;
  payloadJson: string;
  prevHash: string;
  eventHash: string; // SHA-256(eventId|taskId|type|timestamp|payloadJson|prevHash)
}

export interface DelegatedToolRequest {
  delegationId: string;
  taskId: string;
  deviceToolName: string;
  parameters: Record<string, unknown>;
}

export interface SarkarTaskInput {
  voicePrompt: string;
  userId: string;
  deviceContext?: Record<string, string>;
  language?: string;
}
