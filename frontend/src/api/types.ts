// Mirrors the backend DTOs. Money and hours arrive as JSON numbers produced from BigDecimal
// with fixed scale; they are only ever displayed, never used in arithmetic here.

export type Role = 'PLANT_ADMIN' | 'MAINTENANCE_MANAGER' | 'TECHNICIAN' | 'VIEWER';
export type Trade = 'ELECTRICAL' | 'MECHANICAL' | 'INSTRUMENTATION';
export type Priority = 'EMERGENCY' | 'HIGH' | 'MEDIUM' | 'LOW';
export type WorkOrderType = 'BREAKDOWN' | 'PREVENTIVE';
export type WorkOrderStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CLOSED' | 'CANCELLED';
export type WorkOrderAction = 'START' | 'HOLD' | 'RETURN_TO_SERVICE' | 'CLOSE' | 'REWORK' | 'CANCEL';
export type AssetLevel = 'PLANT' | 'AREA' | 'LINE' | 'MACHINE' | 'COMPONENT';
export type Criticality = 'A' | 'B' | 'C';

export interface SessionUser {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  plantCode: string;
  plantName: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: SessionUser;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AssetSummary {
  id: string;
  tag: string;
  name: string;
  criticality: Criticality;
}

export interface PersonRef {
  id: string;
  name: string;
  trade: Trade | null;
}

export interface WorkOrderListItem {
  id: string;
  number: string;
  type: WorkOrderType;
  priority: Priority;
  status: WorkOrderStatus;
  title: string;
  asset: AssetSummary;
  assignee: PersonRef | null;
  raisedAt: string;
  dueAt: string | null;
  overdue: boolean;
}

export interface FailureCodeView {
  code: string;
  description: string;
  countsAsFailure: boolean;
}

export interface WorkOrderDetail {
  id: string;
  number: string;
  type: WorkOrderType;
  priority: Priority;
  status: WorkOrderStatus;
  title: string;
  description: string | null;
  failureCode: FailureCodeView | null;
  asset: AssetSummary;
  assignee: PersonRef | null;
  raisedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  closedAt: string | null;
  downtimeStart: string | null;
  downtimeEnd: string | null;
  downtimeMinutes: number | null;
  holdReason: string | null;
  closureNote: string | null;
  pmScheduleId: string | null;
  dueAt: string | null;
  dueRunningHours: number | null;
  overdue: boolean;
  labour: { id: string; technician: PersonRef | null; minutes: number; workDate: string; note: string | null }[];
  labourMinutesTotal: number;
  parts: {
    id: string;
    sparePartId: string;
    partNumber: string | null;
    description: string | null;
    unit: string | null;
    quantity: number;
    unitCost: number;
    lineCost: number;
    consumedAt: string;
  }[];
  partsCostTotal: number;
  availableActions: WorkOrderAction[];
}

export interface AssetRef {
  id: string;
  tag: string;
  name: string;
  level: AssetLevel;
}

export interface AssetDetail {
  id: string;
  parentId: string | null;
  tag: string;
  code: string;
  name: string;
  level: AssetLevel;
  make: string | null;
  model: string | null;
  rating: string | null;
  serialNumber: string | null;
  criticality: Criticality;
  runningHours: number;
  runningUpdatedAt: string | null;
  commissionedOn: string | null;
  inService: boolean;
  path: AssetRef[];
  children: AssetRef[];
}

export interface TreeNode {
  id: string;
  tag: string;
  code: string;
  name: string;
  level: AssetLevel;
  criticality: Criticality;
  inService: boolean;
  children: TreeNode[];
}

export interface ReliabilityResult {
  failures: number;
  restoredFailures: number;
  observedMinutes: number;
  downtimeMinutes: number;
  mtbfHours: number | null;
  mttrHours: number | null;
  availabilityPercent: number | null;
}

export type Trend = 'DEGRADING' | 'STABLE' | 'IMPROVING' | 'INSUFFICIENT_DATA';

export interface HealthStrip {
  assetId: string;
  tag: string;
  name: string;
  from: string;
  to: string;
  failures: {
    workOrderId: string;
    number: string;
    at: string;
    priority: Priority;
    failureCode: string | null;
    failureDescription: string | null;
    downtimeMinutes: number | null;
  }[];
  trend: {
    trend: Trend;
    olderMeanGapHours: number | null;
    recentMeanGapHours: number | null;
    points: { at: string; rollingMtbfHours: number }[];
  };
  summary: ReliabilityResult;
}

export interface DegradingAsset {
  assetId: string;
  tag: string;
  name: string;
  criticality: Criticality;
  failures: number;
  olderMeanGapHours: number;
  recentMeanGapHours: number;
}

export interface Summary {
  openByPriority: Record<Priority, number>;
  openBreakdowns: number;
  overdue: number;
  breakdownsLast30Days: number;
  lowStockParts: number;
}

export type PmState = 'INACTIVE' | 'OVERDUE' | 'SCHEDULED' | 'DUE' | 'APPROACHING' | 'OK';

export interface PmSchedule {
  id: string;
  assetId: string;
  assetTag: string;
  assetName: string;
  title: string;
  trade: Trade;
  priority: Priority;
  intervalDays: number | null;
  intervalRunningHours: number | null;
  lastDoneAt: string;
  lastDoneRunningHours: number;
  nextDueAt: string | null;
  nextDueRunningHours: number | null;
  runningHoursRemaining: number | null;
  state: PmState;
  openOrder: { id: string; number: string; overdue: boolean } | null;
  active: boolean;
}

export interface SparePart {
  id: string;
  partNumber: string;
  description: string;
  unit: string;
  stockQty: number;
  reorderPoint: number;
  unitCost: number;
  binLocation: string | null;
  belowReorderPoint: boolean;
}

export interface UserView {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  trade: Trade | null;
  shift: string | null;
  active: boolean;
}
