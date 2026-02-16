import type { DeliverySection } from './types';
import { DELIVERY_SECTION_ITEMS } from './types';

type DeliverySubNavProps = {
  activeSection: DeliverySection;
  onChange: (section: DeliverySection) => void;
};

export function DeliverySubNav({ activeSection, onChange }: DeliverySubNavProps) {
  return (
    <nav className="admin-subnav" aria-label="設定配信サブナビ">
      {DELIVERY_SECTION_ITEMS.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`admin-subnav__item${activeSection === item.id ? ' is-active' : ''}`}
          aria-current={activeSection === item.id ? 'page' : undefined}
          onClick={() => onChange(item.id)}
          title={item.description}
        >
          <span className="admin-subnav__label">{item.label}</span>
          <span className="admin-subnav__desc">{item.description}</span>
        </button>
      ))}
    </nav>
  );
}
