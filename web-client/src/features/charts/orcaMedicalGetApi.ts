import { httpFetch } from '../../libs/http/httpClient';
import { getObservabilityMeta } from '../../libs/observability/observability';
import { checkRequiredTags, escapeXml, extractOrcaXmlMeta, parseXmlDocument } from '../../libs/xml/xmlUtils';

export type OrcaXmlResponse = {
  ok: boolean;
  status: number;
  rawXml: string;
  apiResult?: string;
  apiResultMessage?: string;
  informationDate?: string;
  informationTime?: string;
  missingTags?: string[];
  runId?: string;
  traceId?: string;
  error?: string;
};

export const ORCA_MEDICALGETV2_PATH = '/orca/medicalgetv2?class=01';
export const ORCA_TMEDICALGETV2_PATH = '/orca/tmedicalgetv2';

const escapeXmlValue = (value?: string | null) => escapeXml(value ?? '');

export const buildMedicalGetRequestXml = (params: {
  patientId: string;
  inOut?: string;
  performDate?: string;
  forMonths?: string;
  insuranceCombinationNumber?: string;
  departmentCode?: string;
  sequentialNumber?: string;
}) => {
  return [
    '<data>',
    '  <medicalgetreq type="record">',
    `    <InOut type="string">${escapeXmlValue(params.inOut)}</InOut>`,
    `    <Patient_ID type="string">${escapeXmlValue(params.patientId)}</Patient_ID>`,
    `    <Perform_Date type="string">${escapeXmlValue(params.performDate)}</Perform_Date>`,
    `    <For_Months type="string">${escapeXmlValue(params.forMonths)}</For_Months>`,
    '    <Medical_Information type="record">',
    `      <Insurance_Combination_Number type="string">${escapeXmlValue(params.insuranceCombinationNumber)}</Insurance_Combination_Number>`,
    `      <Department_Code type="string">${escapeXmlValue(params.departmentCode)}</Department_Code>`,
    `      <Sequential_Number type="string">${escapeXmlValue(params.sequentialNumber)}</Sequential_Number>`,
    '    </Medical_Information>',
    '  </medicalgetreq>',
    '</data>',
  ].join('\n');
};

export const buildTMedicalGetRequestXml = (params: {
  patientId: string;
  performDate?: string;
  inOut?: string;
  departmentCode?: string;
}) => {
  return [
    '<data>',
    '  <tmedicalgetreq type="record">',
    `    <Perform_Date type="string">${escapeXmlValue(params.performDate)}</Perform_Date>`,
    `    <InOut type="string">${escapeXmlValue(params.inOut)}</InOut>`,
    `    <Department_Code type="string">${escapeXmlValue(params.departmentCode)}</Department_Code>`,
    `    <Patient_ID type="string">${escapeXmlValue(params.patientId)}</Patient_ID>`,
    '  </tmedicalgetreq>',
    '</data>',
  ].join('\n');
};

export async function fetchOrcaMedicalGetXml(requestXml: string): Promise<OrcaXmlResponse> {
  const runId = getObservabilityMeta().runId;
  const response = await httpFetch(ORCA_MEDICALGETV2_PATH, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/xml; charset=UTF-8',
      Accept: 'application/xml',
    },
    body: requestXml,
  });
  const rawXml = await response.text();
  const { doc, error } = parseXmlDocument(rawXml);
  const meta = extractOrcaXmlMeta(doc);
  const requiredCheck = checkRequiredTags(doc, ['Api_Result']);
  return {
    ok: response.ok && !error,
    status: response.status,
    rawXml,
    apiResult: meta.apiResult,
    apiResultMessage: meta.apiResultMessage,
    informationDate: meta.informationDate,
    informationTime: meta.informationTime,
    missingTags: requiredCheck.missingTags,
    runId: getObservabilityMeta().runId ?? runId,
    traceId: getObservabilityMeta().traceId,
    error,
  };
}

export async function fetchOrcaTMedicalGetXml(requestXml: string): Promise<OrcaXmlResponse> {
  const runId = getObservabilityMeta().runId;
  const response = await httpFetch(ORCA_TMEDICALGETV2_PATH, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/xml; charset=UTF-8',
      Accept: 'application/xml',
    },
    body: requestXml,
  });
  const rawXml = await response.text();
  const { doc, error } = parseXmlDocument(rawXml);
  const meta = extractOrcaXmlMeta(doc);
  const requiredCheck = checkRequiredTags(doc, ['Api_Result']);
  return {
    ok: response.ok && !error,
    status: response.status,
    rawXml,
    apiResult: meta.apiResult,
    apiResultMessage: meta.apiResultMessage,
    informationDate: meta.informationDate,
    informationTime: meta.informationTime,
    missingTags: requiredCheck.missingTags,
    runId: getObservabilityMeta().runId ?? runId,
    traceId: getObservabilityMeta().traceId,
    error,
  };
}
