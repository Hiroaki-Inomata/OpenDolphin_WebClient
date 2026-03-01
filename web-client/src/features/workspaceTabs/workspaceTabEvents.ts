export const WORKSPACE_CHARTS_TAB_REQUEST_EVENT = 'workspace:charts-tab-request';
export const CHARTS_PATIENT_TABS_UPDATED_EVENT = 'charts:patient-tabs-updated';

export type WorkspaceChartsTabRequest = {
  action: 'select' | 'close';
  key: string;
};

export const dispatchWorkspaceChartsTabRequest = (detail: WorkspaceChartsTabRequest) => {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(
    new CustomEvent<WorkspaceChartsTabRequest>(WORKSPACE_CHARTS_TAB_REQUEST_EVENT, {
      detail,
    }),
  );
};

export const dispatchChartsPatientTabsUpdated = () => {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent(CHARTS_PATIENT_TABS_UPDATED_EVENT));
};
