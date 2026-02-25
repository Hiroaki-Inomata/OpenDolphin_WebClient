import type { OrderGroupKey } from './orderCategoryRegistry';
import type { RightUtilityTool } from './RightUtilityDrawer';

type RightUtilityDockProps = {
  activeTool: RightUtilityTool;
  onSelectTool: (tool: RightUtilityTool) => void;
};

const dockItems: Array<{ tool: RightUtilityTool; label: string }> = [
  { tool: 'prescription', label: '処方' },
  { tool: 'injection', label: '注射' },
  { tool: 'treatment', label: '処置' },
  { tool: 'test', label: '検査' },
  { tool: 'charge', label: '算定' },
  { tool: 'document', label: '文書' },
];

const resolveToolLabel = (tool: RightUtilityTool) => {
  const matched = dockItems.find((item) => item.tool === tool);
  return matched?.label ?? (tool as OrderGroupKey);
};

export function RightUtilityDock({ activeTool, onSelectTool }: RightUtilityDockProps) {
  return (
    <aside className="soap-note__right-dock" aria-label="右ドック">
      <div className="soap-note__right-dock-scroll">
        {dockItems.map((item) => {
          const isActive = item.tool === activeTool;
          return (
            <button
              key={`right-dock-${item.tool}`}
              type="button"
              className="soap-note__right-dock-button order-dock__subtype-tab"
              data-active={isActive ? 'true' : 'false'}
              aria-pressed={isActive}
              aria-label={`${resolveToolLabel(item.tool)}を開く`}
              onClick={() => onSelectTool(item.tool)}
            >
              {item.label}
            </button>
          );
        })}
      </div>
    </aside>
  );
}
