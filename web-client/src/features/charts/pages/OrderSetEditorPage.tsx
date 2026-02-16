import { useEffect, useMemo, useState } from 'react';
import { ReturnToBar } from '../../shared/ReturnToBar';

import { useSession } from '../../../AppRouter';
import { buildFacilityPath } from '../../../routes/facilityRoutes';
import { useNavigationGuard } from '../../../routes/NavigationGuardProvider';
import { useAppNavigation } from '../../../routes/useAppNavigation';
import {
  deleteChartOrderSet,
  listChartOrderSets,
  saveChartOrderSet,
  type ChartOrderSetEntry,
  type ChartOrderSetSnapshot,
} from '../chartOrderSetStorage';
import { SOAP_SECTIONS, SOAP_SECTION_LABELS } from '../soapNote';

const cloneSnapshot = (snapshot: ChartOrderSetSnapshot): ChartOrderSetSnapshot => {
  return {
    ...snapshot,
    diagnoses: snapshot.diagnoses.map((item) => ({ ...item })),
    soapDraft: { ...snapshot.soapDraft },
    soapHistory: snapshot.soapHistory.map((entry) => ({ ...entry })),
    orderBundles: snapshot.orderBundles.map((bundle) => ({
      ...bundle,
      items: (bundle.items ?? []).map((item) => ({ ...item })),
    })),
    imageAttachments: snapshot.imageAttachments.map((item) => ({ ...item })),
  };
};

export function OrderSetEditorPage() {
  const session = useSession();
  const appNav = useAppNavigation({ facilityId: session.facilityId, userId: session.userId });
  const { registerDirty } = useNavigationGuard();
  const fallbackUrl = useMemo(() => buildFacilityPath(session.facilityId, '/charts'), [session.facilityId]);
  const [sets, setSets] = useState<ChartOrderSetEntry[]>(() => listChartOrderSets(session.facilityId));
  const [selectedId, setSelectedId] = useState<string>(() => listChartOrderSets(session.facilityId)[0]?.id ?? '');
  const [name, setName] = useState('');
  const [snapshot, setSnapshot] = useState<ChartOrderSetSnapshot | null>(null);
  const [notice, setNotice] = useState<{ tone: 'info' | 'success' | 'error'; message: string } | null>(null);

  const selectedSet = useMemo(
    () => sets.find((item) => item.id === selectedId) ?? null,
    [selectedId, sets],
  );

  const isDirty = useMemo(() => {
    if (!selectedSet || !snapshot) return false;
    if (name !== selectedSet.name) return true;
    try {
      return JSON.stringify(snapshot) !== JSON.stringify(selectedSet.snapshot);
    } catch {
      return true;
    }
  }, [name, selectedSet, snapshot]);

  useEffect(() => {
    if (!selectedSet) {
      setName('');
      setSnapshot(null);
      return;
    }
    setName(selectedSet.name);
    setSnapshot(cloneSnapshot(selectedSet.snapshot));
  }, [selectedSet]);

  useEffect(() => {
    registerDirty('orderSets', isDirty, 'オーダーセットの未保存変更');
  }, [isDirty, registerDirty]);

  useEffect(() => {
    return () => registerDirty('orderSets', false);
  }, [registerDirty]);

  const refreshSets = () => {
    const next = listChartOrderSets(session.facilityId);
    setSets(next);
    if (next.length === 0) {
      setSelectedId('');
      return;
    }
    if (!next.some((item) => item.id === selectedId)) {
      setSelectedId(next[0].id);
    }
  };

  const handleSave = () => {
    if (!selectedSet || !snapshot) return;
    const saved = saveChartOrderSet({
      facilityId: session.facilityId,
      userId: session.userId,
      id: selectedSet.id,
      name,
      snapshot,
    });
    refreshSets();
    setSelectedId(saved.id);
    setNotice({ tone: 'success', message: 'オーダーセットを更新しました。' });
  };

  const handleDelete = () => {
    if (!selectedSet) return;
    const confirmed = typeof window !== 'undefined' ? window.confirm(`「${selectedSet.name}」を削除しますか？`) : true;
    if (!confirmed) return;
    const deleted = deleteChartOrderSet({ facilityId: session.facilityId, id: selectedSet.id });
    if (!deleted) {
      setNotice({ tone: 'error', message: '削除対象のオーダーセットが見つかりませんでした。' });
      return;
    }
    refreshSets();
    setNotice({ tone: 'success', message: 'オーダーセットを削除しました。' });
  };

  return (
    <main className="page-shell" style={{ maxWidth: 1120, margin: '0 auto', padding: '1rem' }}>
      <ReturnToBar
        scope={{ facilityId: session.facilityId, userId: session.userId }}
        returnTo={appNav.returnToCandidate}
        from={appNav.fromCandidate}
        fallbackUrl={fallbackUrl}
      />
      <header style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ marginBottom: '0.5rem' }}>オーダーセット編集</h1>
          <p style={{ margin: 0, color: '#555' }}>
            セットの名称と内訳（病名/SOAP/オーダー/画像）を編集します。
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button type="button" onClick={refreshSets}>
            再読込
          </button>
        </div>
      </header>

      {notice ? (
        <div
          role={notice.tone === 'error' ? 'alert' : 'status'}
          style={{
            marginTop: '0.75rem',
            padding: '0.75rem',
            borderRadius: 8,
            border: '1px solid #d0d7de',
            background: notice.tone === 'error' ? '#fff1f0' : notice.tone === 'success' ? '#ecfdf3' : '#f6f8fa',
          }}
        >
          {notice.message}
        </div>
      ) : null}

      <section style={{ marginTop: '1rem', display: 'grid', gridTemplateColumns: '280px 1fr', gap: '1rem' }}>
        <aside style={{ border: '1px solid #d0d7de', borderRadius: 8, padding: '0.75rem' }}>
          <h2 style={{ fontSize: '1rem', marginTop: 0 }}>登録済みセット</h2>
          {sets.length === 0 ? <p>登録済みセットがありません。</p> : null}
          <div style={{ display: 'grid', gap: '0.5rem' }}>
            {sets.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setSelectedId(item.id)}
                aria-pressed={selectedId === item.id}
                style={{
                  textAlign: 'left',
                  border: selectedId === item.id ? '2px solid #0969da' : '1px solid #d0d7de',
                  borderRadius: 8,
                  padding: '0.5rem',
                  background: selectedId === item.id ? '#eaf3ff' : '#fff',
                }}
              >
                <strong style={{ display: 'block' }}>{item.name}</strong>
                <small>{item.snapshot.sourceVisitDate || '日付未設定'} / {item.snapshot.sourcePatientId || '患者未設定'}</small>
              </button>
            ))}
          </div>
        </aside>

        <section style={{ border: '1px solid #d0d7de', borderRadius: 8, padding: '0.75rem' }}>
          {!selectedSet || !snapshot ? (
            <p>左の一覧からオーダーセットを選択してください。</p>
          ) : (
            <>
              <div style={{ display: 'grid', gap: '0.75rem' }}>
                <label style={{ display: 'grid', gap: '0.25rem' }}>
                  <span>セット名称</span>
                  <input value={name} onChange={(event) => setName(event.target.value)} placeholder="例: 定期フォローセット" />
                </label>
                <p style={{ margin: 0, color: '#555' }}>
                  元データ: {snapshot.sourceVisitDate} / 患者ID {snapshot.sourcePatientId} / 取得日時 {snapshot.capturedAt}
                </p>
              </div>

              <details open style={{ marginTop: '1rem' }}>
                <summary>病名 ({snapshot.diagnoses.length}件)</summary>
                {snapshot.diagnoses.length === 0 ? <p>病名データはありません。</p> : null}
                <ul>
                  {snapshot.diagnoses.map((item, index) => (
                    <li key={`${item.diagnosisCode ?? 'd'}-${index}`}>
                      {item.diagnosisName ?? '名称未設定'}
                      {item.diagnosisCode ? ` (${item.diagnosisCode})` : ''}
                      <button
                        type="button"
                        onClick={() =>
                          setSnapshot((prev) =>
                            prev
                              ? {
                                  ...prev,
                                  diagnoses: prev.diagnoses.filter((_, idx) => idx !== index),
                                }
                              : prev,
                          )
                        }
                        style={{ marginLeft: '0.5rem' }}
                      >
                        削除
                      </button>
                    </li>
                  ))}
                </ul>
              </details>

              <details open style={{ marginTop: '0.75rem' }}>
                <summary>SOAPドラフト</summary>
                <div style={{ display: 'grid', gap: '0.5rem', marginTop: '0.5rem' }}>
                  {SOAP_SECTIONS.map((section) => (
                    <label key={section} style={{ display: 'grid', gap: '0.25rem' }}>
                      <span>{SOAP_SECTION_LABELS[section]}</span>
                      <textarea
                        value={snapshot.soapDraft[section] ?? ''}
                        onChange={(event) =>
                          setSnapshot((prev) =>
                            prev
                              ? {
                                  ...prev,
                                  soapDraft: {
                                    ...prev.soapDraft,
                                    [section]: event.target.value,
                                  },
                                }
                              : prev,
                          )
                        }
                        rows={3}
                      />
                    </label>
                  ))}
                </div>
              </details>

              <details open style={{ marginTop: '0.75rem' }}>
                <summary>オーダー ({snapshot.orderBundles.length}件)</summary>
                {snapshot.orderBundles.length === 0 ? <p>オーダーはありません。</p> : null}
                <ul>
                  {snapshot.orderBundles.map((bundle, index) => (
                    <li key={`${bundle.entity ?? 'order'}-${bundle.bundleName ?? index}-${index}`}>
                      {bundle.entity ?? 'entity未設定'} / {bundle.bundleName ?? '名称未設定'} / 項目 {bundle.items.length}件
                      <button
                        type="button"
                        onClick={() =>
                          setSnapshot((prev) =>
                            prev
                              ? {
                                  ...prev,
                                  orderBundles: prev.orderBundles.filter((_, idx) => idx !== index),
                                }
                              : prev,
                          )
                        }
                        style={{ marginLeft: '0.5rem' }}
                      >
                        削除
                      </button>
                    </li>
                  ))}
                </ul>
              </details>

              <details open style={{ marginTop: '0.75rem' }}>
                <summary>画像 ({snapshot.imageAttachments.length}件)</summary>
                {snapshot.imageAttachments.length === 0 ? <p>画像はありません。</p> : null}
                <ul>
                  {snapshot.imageAttachments.map((item, index) => (
                    <li key={`${item.id}-${index}`}>
                      {item.title || item.fileName || `画像ID:${item.id}`}
                      <button
                        type="button"
                        onClick={() =>
                          setSnapshot((prev) =>
                            prev
                              ? {
                                  ...prev,
                                  imageAttachments: prev.imageAttachments.filter((_, idx) => idx !== index),
                                }
                              : prev,
                          )
                        }
                        style={{ marginLeft: '0.5rem' }}
                      >
                        削除
                      </button>
                    </li>
                  ))}
                </ul>
              </details>

              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', flexWrap: 'wrap' }}>
                <button type="button" onClick={handleSave}>更新</button>
                <button type="button" onClick={handleDelete}>削除</button>
              </div>
            </>
          )}
        </section>
      </section>
    </main>
  );
}
