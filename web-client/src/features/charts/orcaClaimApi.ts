import { httpFetch } from '../../libs/http/httpClient';
import { getObservabilityMeta } from '../../libs/observability/observability';
import { checkRequiredTags, escapeXml, extractOrcaXmlMeta, isOrcaApiResultOk, parseXmlDocument, readXmlText } from '../../libs/xml/xmlUtils';

export type OrcaClaimSendResult = {
  ok: boolean;
  apiOk?: boolean;
  status: number;
  rawXml: string;
  apiResult?: string;
  apiResultMessage?: string;
  informationDate?: string;
  informationTime?: string;
  invoiceNumber?: string;
  dataId?: string;
  medicalWarnings?: Array<{
    medicalWarning?: string;
    medicalWarningMessage?: string;
    medicalWarningPosition?: number;
    medicalWarningItemPosition?: number;
    medicalWarningCode?: string;
  }>;
  missingTags?: string[];
  runId?: string;
  traceId?: string;
  error?: string;
};

export const ORCA_MEDICALMODV2_PATH = '/api21/medicalmodv2';

export type MedicalModV2Medication = {
  code: string;
  name?: string;
  number?: string;
  unit?: string;
  genericFlg?: 'yes' | 'no';
};

export type MedicalModV2Information = {
  medicalClass: string;
  medicalClassName?: string;
  medicalClassNumber?: string;
  medications: MedicalModV2Medication[];
};

export const buildMedicalModV2RequestXml = (params: {
  patientId: string;
  performDate: string;
  departmentCode: string;
  physicianCode?: string;
  requestNumber?: string;
  medicalUid?: string;
  includeInitialConsultation?: boolean;
  medicalInformation?: MedicalModV2Information[];
}) => {
  const performDate = params.performDate.length >= 10 ? params.performDate.slice(0, 10) : params.performDate;
  const performTime = params.performDate.slice(11, 19) || '00:00:00';
  const physicianCode = params.physicianCode?.trim();
  const baseMedicalInfo: MedicalModV2Information[] = params.includeInitialConsultation
    ? [
        {
          medicalClass: '11',
          medicalClassName: '基本診療料',
          medicalClassNumber: '1',
          medications: [
            {
              code: '110000010',
              name: '初診料',
              number: '1',
            },
          ],
        },
      ]
    : [];
  const medicalInformation = [...baseMedicalInfo, ...(params.medicalInformation ?? [])].reduce<
    Array<{
      medicalClass: string;
      medicalClassName?: string;
      medicalClassNumber: string;
      medications: Array<{ code: string; name?: string; number: string; genericFlg?: 'yes' | 'no' }>;
    }>
  >((acc, info) => {
    const medicalClass = info.medicalClass?.trim();
    const medications = (info.medications ?? [])
      .map((medication) => ({
        code: medication.code.trim(),
        name: medication.name?.trim() || undefined,
        number: medication.number?.trim() || '',
        genericFlg: medication.genericFlg,
      }))
      .filter((medication) => medication.code.length > 0);
    if (!medicalClass || medications.length === 0) return acc;
    acc.push({
      medicalClass,
      medicalClassName: info.medicalClassName?.trim() || undefined,
      medicalClassNumber: info.medicalClassNumber?.trim() || '1',
      medications,
    });
    return acc;
  }, []);
  return [
    '<data>',
    '  <medicalreq type="record">',
    `    <Request_Number type="string">${escapeXml(params.requestNumber ?? '01')}</Request_Number>`,
    '    <InOut type="string">O</InOut>',
    `    <Patient_ID type="string">${escapeXml(params.patientId)}</Patient_ID>`,
    `    <Perform_Date type="string">${escapeXml(performDate)}</Perform_Date>`,
    `    <Perform_Time type="string">${escapeXml(performTime)}</Perform_Time>`,
    params.medicalUid ? `    <Medical_Uid type="string">${escapeXml(params.medicalUid)}</Medical_Uid>` : undefined,
    '    <Diagnosis_Information type="record">',
    `      <Department_Code type="string">${escapeXml(params.departmentCode)}</Department_Code>`,
    physicianCode ? `      <Physician_Code type="string">${escapeXml(physicianCode)}</Physician_Code>` : undefined,
    '      <Medical_Information type="array">',
    ...medicalInformation.flatMap((info) => [
      '        <Medical_Information_child type="record">',
      `          <Medical_Class type="string">${escapeXml(info.medicalClass)}</Medical_Class>`,
      info.medicalClassName ? `          <Medical_Class_Name type="string">${escapeXml(info.medicalClassName)}</Medical_Class_Name>` : undefined,
      `          <Medical_Class_Number type="string">${escapeXml(info.medicalClassNumber)}</Medical_Class_Number>`,
      '          <Medication_info type="array">',
      ...info.medications.flatMap((medication) => [
        '            <Medication_info_child type="record">',
        `              <Medication_Code type="string">${escapeXml(medication.code)}</Medication_Code>`,
        medication.name ? `              <Medication_Name type="string">${escapeXml(medication.name)}</Medication_Name>` : undefined,
        `              <Medication_Number type="string">${escapeXml(medication.number)}</Medication_Number>`,
        medication.genericFlg
          ? `              <Medication_Generic_Flg type="string">${escapeXml(medication.genericFlg)}</Medication_Generic_Flg>`
          : undefined,
        '            </Medication_info_child>',
      ]),
      '          </Medication_info>',
      '        </Medical_Information_child>',
    ]),
    '      </Medical_Information>',
    '    </Diagnosis_Information>',
    '  </medicalreq>',
    '</data>',
  ]
    .filter((line): line is string => Boolean(line))
    .join('\n');
};

export async function postOrcaMedicalModV2Xml(
  requestXml: string,
  options: { classCode?: string; signal?: AbortSignal } = {},
): Promise<OrcaClaimSendResult> {
  const runId = getObservabilityMeta().runId;
  const url = options.classCode ? `${ORCA_MEDICALMODV2_PATH}?class=${options.classCode}` : `${ORCA_MEDICALMODV2_PATH}?class=01`;
  const response = await httpFetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/xml; charset=UTF-8',
      Accept: 'application/xml',
    },
    body: requestXml,
    signal: options.signal,
  });
  const rawXml = await response.text();
  const { doc, error } = parseXmlDocument(rawXml);
  const meta = extractOrcaXmlMeta(doc);
  const apiOk = isOrcaApiResultOk(meta.apiResult);
  const invoiceNumber = readXmlText(doc, 'Invoice_Number');
  const dataId = readXmlText(doc, 'Data_Id') ?? readXmlText(doc, 'DataID') ?? readXmlText(doc, 'Data_ID');
  const parsePosition = (value?: string) => {
    if (!value) return undefined;
    const num = Number(value);
    return Number.isFinite(num) && num > 0 ? num : undefined;
  };
  const medicalWarnings = doc
    ? Array.from(doc.querySelectorAll('Medical_Warning_Info_child'))
        .map((node) => ({
          medicalWarning: readXmlText(node, 'Medical_Warning'),
          medicalWarningMessage: readXmlText(node, 'Medical_Warning_Message'),
          medicalWarningPosition: parsePosition(readXmlText(node, 'Medical_Warning_Position')),
          medicalWarningItemPosition: parsePosition(readXmlText(node, 'Medical_Warning_Item_Position')),
          medicalWarningCode: readXmlText(node, 'Medical_Warning_Code'),
        }))
        .filter((entry) =>
          Boolean(
            entry.medicalWarning ||
              entry.medicalWarningMessage ||
              entry.medicalWarningPosition ||
              entry.medicalWarningItemPosition ||
              entry.medicalWarningCode,
          ),
        )
    : undefined;
  const requiredCheck = checkRequiredTags(doc, ['Api_Result', 'Invoice_Number', 'Data_Id']);
  return {
    ok: response.ok && !error,
    apiOk,
    status: response.status,
    rawXml,
    apiResult: meta.apiResult,
    apiResultMessage: meta.apiResultMessage,
    informationDate: meta.informationDate,
    informationTime: meta.informationTime,
    invoiceNumber,
    dataId,
    medicalWarnings,
    missingTags: requiredCheck.missingTags,
    runId: getObservabilityMeta().runId ?? runId,
    traceId: getObservabilityMeta().traceId,
    error,
  };
}
