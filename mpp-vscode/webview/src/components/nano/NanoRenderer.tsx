/**
 * NanoUI Renderer for VSCode Webview
 *
 * Renders NanoIR components to React elements.
 * Follows the component-specific method pattern from the Kotlin NanoRenderer interface.
 *
 * Each component type has its own render function, making it easy to identify
 * missing implementations when new components are added.
 *
 * @see xiuper-ui/src/main/kotlin/cc/unitmesh/xuiper/render/NanoRenderer.kt
 * @see mpp-ui/src/commonMain/kotlin/cc/unitmesh/devins/ui/nano/StatefulNanoRenderer.kt
 */

import React from 'react';
import { NanoIR, NanoRenderContext, DEFAULT_THEME } from '../../types/nano';
import './NanoRenderer.css';

interface NanoRendererProps {
  ir: NanoIR;
  context?: Partial<NanoRenderContext>;
  className?: string;
}

// Default context
const defaultContext: NanoRenderContext = {
  state: {},
  theme: DEFAULT_THEME,
};

/**
 * Main NanoRenderer component
 */
export const NanoRenderer: React.FC<NanoRendererProps> = ({
  ir,
  context = {},
  className,
}) => {
  const fullContext = { ...defaultContext, ...context };
  
  return (
    <div className={`nano-renderer ${className || ''}`}>
      <RenderNode ir={ir} context={fullContext} />
    </div>
  );
};

function getByPath(obj: any, path: string): any {
  if (!path) return undefined;
  const normalized = path.startsWith('state.') ? path.slice('state.'.length) : path;
  const parts = normalized.split('.').filter(Boolean);
  let current: any = obj;
  for (const part of parts) {
    if (current == null) return undefined;
    current = current[part];
  }
  return current;
}

function tryParseStructuredLiteral(value: string): any {
  const trimmed = value.trim();
  if (!trimmed) return value;

  // Fast path: valid JSON
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      return JSON.parse(trimmed);
    } catch {
      // Fall through to a permissive normalization.
    }

    // Permissive: convert JS-ish object literals to JSON.
    // Example supported: [{key:"product", title:"Product"}, {key:"revenue"}]
    const normalized = trimmed
      .replace(/\b([A-Za-z_][A-Za-z0-9_]*)\s*:/g, '"$1":')
      .replace(/'/g, '"')
      .replace(/,\s*([\]}])/g, '$1');
    try {
      return JSON.parse(normalized);
    } catch {
      return value;
    }
  }

  return value;
}

function resolveValue(context: NanoRenderContext, raw: any): any {
  if (typeof raw !== 'string') return raw;
  const trimmed = raw.trim();

  // Handle binding prefixes from NanoDSL: "<< state.xxx" or ":= state.xxx"
  const bindingPrefixMatch = trimmed.match(/^(<<|:=)\s+(.+)$/);
  const expr = bindingPrefixMatch ? bindingPrefixMatch[2].trim() : trimmed;

  if (expr.startsWith('state.')) {
    return getByPath(context.state, expr);
  }

  // If it's a structured literal, parse it.
  return tryParseStructuredLiteral(trimmed);
}

function inferChartFields(data: any[], xField?: string, yField?: string): { x: string; y: string } | null {
  if (!Array.isArray(data) || data.length === 0) return null;
  const sample = data.find(v => v && typeof v === 'object') as any;
  if (!sample) return null;

  const x = xField || (('name' in sample) ? 'name' : ('label' in sample) ? 'label' : ('x' in sample) ? 'x' : 'name');
  const y = yField || (('value' in sample) ? 'value' : ('y' in sample) ? 'y' : ('count' in sample) ? 'count' : 'value');
  return { x, y };
}

function formatCell(value: any, format?: string): string {
  if (value == null) return '';
  if (!format) return String(value);
  const n = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(n)) return String(value);

  switch (format) {
    case 'currency':
      return `$${n.toFixed(2)}`;
    case 'percent':
      return `${n.toFixed(1)}%`;
    default:
      return String(value);
  }
}

/**
 * Dispatch rendering based on component type
 */
const RenderNode: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  switch (ir.type) {
    // Layout
    case 'VStack': return <RenderVStack ir={ir} context={context} />;
    case 'HStack': return <RenderHStack ir={ir} context={context} />;
    // Container
    case 'Card': return <RenderCard ir={ir} context={context} />;
    case 'Form': return <RenderForm ir={ir} context={context} />;
    // Content
    case 'Text': return <RenderText ir={ir} context={context} />;
    case 'Image': return <RenderImage ir={ir} context={context} />;
    case 'Badge': return <RenderBadge ir={ir} context={context} />;
    case 'Divider': return <RenderDivider />;
    // Input
    case 'Button': return <RenderButton ir={ir} context={context} />;
    case 'Input': return <RenderInput ir={ir} context={context} />;
    case 'Checkbox': return <RenderCheckbox ir={ir} context={context} />;
    case 'TextArea': return <RenderTextArea ir={ir} context={context} />;
    case 'Select': return <RenderSelect ir={ir} context={context} />;
    // P0: Core Form Input Components
    case 'DatePicker': return <RenderDatePicker ir={ir} context={context} />;
    case 'Radio': return <RenderRadio ir={ir} context={context} />;
    case 'RadioGroup': return <RenderRadioGroup ir={ir} context={context} />;
    case 'Switch': return <RenderSwitch ir={ir} context={context} />;
    case 'NumberInput': return <RenderNumberInput ir={ir} context={context} />;
    // P0: Feedback Components
    case 'Modal': return <RenderModal ir={ir} context={context} />;
    case 'Alert': return <RenderAlert ir={ir} context={context} />;
    case 'Progress': return <RenderProgress ir={ir} context={context} />;
    case 'Spinner': return <RenderSpinner ir={ir} context={context} />;
    // Tier 1-3: GenUI Components
    case 'SplitView': return <RenderSplitView ir={ir} context={context} />;
    case 'SmartTextField': return <RenderSmartTextField ir={ir} context={context} />;
    case 'Slider': return <RenderSlider ir={ir} context={context} />;
    case 'DateRangePicker': return <RenderDateRangePicker ir={ir} context={context} />;
    case 'DataChart': return <RenderDataChart ir={ir} context={context} />;
    case 'DataTable': return <RenderDataTable ir={ir} context={context} />;
    // Control Flow
    case 'Conditional': return <RenderConditional ir={ir} context={context} />;
    case 'ForLoop': return <RenderForLoop ir={ir} context={context} />;
    // Meta
    case 'Component': return <RenderComponent ir={ir} context={context} />;
    default: return <RenderUnknown ir={ir} />;
  }
};

// ============================================================================
// Layout Components
// ============================================================================

const RenderVStack: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const spacing = ir.props.spacing || 'md';
  const align = ir.props.align || 'stretch';
  
  return (
    <div className={`nano-vstack spacing-${spacing} align-${align}`}>
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
    </div>
  );
};

const RenderHStack: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const spacing = ir.props.spacing || 'md';
  const align = ir.props.align || 'center';
  const justify = ir.props.justify || 'start';

  // Count VStack/Card children to determine if we should auto-distribute space
  const vstackOrCardChildren = ir.children?.filter(
    child => child.type === 'VStack' || child.type === 'Card'
  ).length || 0;
  const shouldAutoDistribute = vstackOrCardChildren >= 2;

  return (
    <div className={`nano-hstack spacing-${spacing} align-${align} justify-${justify}`}>
      {ir.children?.map((child, i) => {
        // Support flex/weight property for space distribution
        const childFlex = child.props?.flex;
        const childWeight = child.props?.weight;
        const flex = childFlex || childWeight;

        // Check if child should get auto flex
        const isVStackOrCard = child.type === 'VStack' || child.type === 'Card';
        const shouldApplyFlex = flex || (shouldAutoDistribute && isVStackOrCard);
        const flexValue = shouldApplyFlex ? (Number(flex) || 1) : undefined;

        // Avoid unnecessary wrapper div when no flex is needed
        if (!flexValue) {
          return <RenderNode key={i} ir={child} context={context} />;
        }

        return (
          <div key={i} style={{ flex: flexValue }}>
            <RenderNode ir={child} context={context} />
          </div>
        );
      })}
    </div>
  );
};

// ============================================================================
// Container Components
// ============================================================================

const RenderCard: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const padding = ir.props.padding || 'md';
  const shadow = ir.props.shadow || 'sm';
  
  return (
    <div className={`nano-card padding-${padding} shadow-${shadow}`}>
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
    </div>
  );
};

const RenderForm: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (ir.props.onSubmit && context.dispatch) {
      context.dispatch({ type: 'Fetch', url: ir.props.onSubmit, method: 'POST' });
    }
  };
  
  return (
    <form className="nano-form" onSubmit={handleSubmit}>
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
    </form>
  );
};

// ============================================================================
// Content Components
// ============================================================================

const RenderText: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const content = ir.props.content || '';
  const style = ir.props.style || 'body';
  
  const Tag = getTextTag(style);
  return <Tag className={`nano-text style-${style}`}>{content}</Tag>;
};

function getTextTag(style: string): keyof JSX.IntrinsicElements {
  switch (style) {
    case 'h1': return 'h1';
    case 'h2': return 'h2';
    case 'h3': return 'h3';
    case 'h4': return 'h4';
    case 'caption': return 'small';
    default: return 'p';
  }
}

const RenderImage: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const src = ir.props.src || '';
  const radius = ir.props.radius || 'none';
  const alt = ir.props.alt ?? 'Image';

  return <img src={src} className={`nano-image radius-${radius}`} alt={alt} />;
};

const RenderBadge: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const text = ir.props.text || '';
  const color = ir.props.color || 'default';

  return <span className={`nano-badge color-${color}`}>{text}</span>;
};

const RenderDivider: React.FC = () => {
  return <hr className="nano-divider" />;
};

// ============================================================================
// Input Components
// ============================================================================

const RenderButton: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const label = ir.props.label || 'Button';
  const intent = ir.props.intent || 'default';
  const icon = ir.props.icon;

  const handleClick = () => {
    if (ir.actions?.onClick && context.dispatch) {
      const action = ir.actions.onClick;
      if (action.type === 'Navigate' && action.payload?.to) {
        context.dispatch({ type: 'Navigate', to: action.payload.to });
      } else if (action.type === 'Fetch' && action.payload?.url) {
        context.dispatch({ type: 'Fetch', url: action.payload.url, method: action.payload.method });
      } else if (action.type === 'ShowToast' && action.payload?.message) {
        context.dispatch({ type: 'ShowToast', message: action.payload.message });
      }
    }
  };

  return (
    <button className={`nano-button intent-${intent}`} onClick={handleClick}>
      {icon && <span className="icon">{icon}</span>}
      {label}
    </button>
  );
};

const RenderInput: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const placeholder = ir.props.placeholder || '';
  const type = ir.props.type || 'text';

  return (
    <input
      type={type}
      className="nano-input"
      placeholder={placeholder}
    />
  );
};

const RenderCheckbox: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const label = ir.props.label;
  return (
    <label className="nano-checkbox-wrapper">
      <input type="checkbox" className="nano-checkbox" />
      {label && <span>{label}</span>}
    </label>
  );
};

const RenderTextArea: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const placeholder = ir.props.placeholder || '';
  const rows = ir.props.rows || 4;

  return (
    <textarea
      className="nano-textarea"
      placeholder={placeholder}
      rows={rows}
    />
  );
};

const RenderSelect: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const placeholder = ir.props.placeholder;

  return (
    <select className="nano-select" defaultValue="">
      {placeholder && (
        <option value="" disabled>{placeholder}</option>
      )}
    </select>
  );
};

// ============================================================================
// Control Flow Components
// ============================================================================

const RenderConditional: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  // In static preview, render the then branch
  return (
    <div className="nano-conditional">
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
    </div>
  );
};

const RenderForLoop: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  // In static preview, show a single iteration
  return (
    <div className="nano-loop">
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
    </div>
  );
};

// ============================================================================
// Meta Components
// ============================================================================

const RenderComponent: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const name = ir.props.name || 'Component';

  // Initialize state defaults declared on the Component node.
  const defaults: Record<string, any> = {};
  if (ir.state?.variables) {
    for (const [key, def] of Object.entries(ir.state.variables)) {
      const type = (def as any)?.type;
      const rawDefault = (def as any)?.defaultValue;
      if (rawDefault === undefined) continue;

      if (typeof rawDefault === 'string' && (type === 'list' || type === 'dict' || type === 'map' || type === 'object')) {
        defaults[key] = tryParseStructuredLiteral(rawDefault);
      } else {
        defaults[key] = rawDefault;
      }
    }
  }
  const childContext: NanoRenderContext = {
    ...context,
    state: { ...defaults, ...context.state },
  };

  return (
    <div className="nano-component" data-name={name}>
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={childContext} />
      ))}
    </div>
  );
};

// ============================================================================
// P0: Core Form Input Components
// ============================================================================

const RenderDatePicker: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const placeholder = ir.props.placeholder || 'Select date';
  return <input type="date" className="nano-datepicker" placeholder={placeholder} />;
};

const RenderRadio: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const label = ir.props.label || '';
  const option = ir.props.option || '';
  const name = ir.props.name || '';
  return (
    <label className="nano-radio-wrapper">
      <input type="radio" className="nano-radio" name={name} value={option} />
      <span>{label}</span>
    </label>
  );
};

const RenderRadioGroup: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  return (
    <div className="nano-radiogroup">
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
    </div>
  );
};

const RenderSwitch: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const label = ir.props.label;
  return (
    <label className="nano-switch-wrapper">
      <input type="checkbox" className="nano-switch" role="switch" />
      {label && <span>{label}</span>}
    </label>
  );
};

const RenderNumberInput: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const placeholder = ir.props.placeholder || '';
  const min = ir.props.min || 0;
  const max = ir.props.max || 100;
  const step = ir.props.step || 1;
  return (
    <div className="nano-numberinput">
      <button className="nano-numberinput-decrement">-</button>
      <input type="number" className="nano-numberinput-input" min={min} max={max} step={step} placeholder={placeholder} />
      <button className="nano-numberinput-increment">+</button>
    </div>
  );
};

// ============================================================================
// P0: Feedback Components
// ============================================================================

const RenderModal: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const title = ir.props.title;
  const size = ir.props.size || 'md';
  return (
    <div className={`nano-modal size-${size}`}>
      <div className="nano-modal-backdrop"></div>
      <div className="nano-modal-content">
        {title && <div className="nano-modal-header"><h3>{title}</h3><button className="nano-modal-close">×</button></div>}
        <div className="nano-modal-body">
          {ir.children?.map((child, i) => (
            <RenderNode key={i} ir={child} context={context} />
          ))}
        </div>
      </div>
    </div>
  );
};

const RenderAlert: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const type = ir.props.type || 'info';
  const message = ir.props.message;
  const closable = ir.props.closable || false;
  return (
    <div className={`nano-alert type-${type}`}>
      {message && <span className="nano-alert-message">{message}</span>}
      {ir.children?.map((child, i) => (
        <RenderNode key={i} ir={child} context={context} />
      ))}
      {closable && <button className="nano-alert-close">×</button>}
    </div>
  );
};

const RenderProgress: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const valueStr = ir.props.value;
  const maxStr = ir.props.max;
  const value = typeof valueStr === 'number' ? valueStr : (parseFloat(valueStr) || 0);
  const max = typeof maxStr === 'number' ? maxStr : (parseFloat(maxStr) || 100);
  const showText = ir.props.showText !== false;
  const status = ir.props.status || 'normal';
  const isBinding = (valueStr && typeof valueStr === 'string' && isNaN(parseFloat(valueStr))) ||
                   (maxStr && typeof maxStr === 'string' && isNaN(parseFloat(maxStr)));
  const percentage = max > 0 ? Math.round((value / max) * 100) : 0;
  const displayText = isBinding ? `${valueStr || '0'} / ${maxStr || '100'}` : `${percentage}%`;
  return (
    <div className={`nano-progress status-${status}`} data-value={valueStr} data-max={maxStr}>
      <div className="nano-progress-bar" style={{ width: `${percentage}%` }}></div>
      {showText && <span className="nano-progress-text">{displayText}</span>}
    </div>
  );
};

const RenderSpinner: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const size = ir.props.size || 'md';
  const text = ir.props.text;
  return (
    <div className={`nano-spinner size-${size}`}>
      <div className="nano-spinner-circle"></div>
      {text && <span className="nano-spinner-text">{text}</span>}
    </div>
  );
};

// ============================================================================
// Tier 1-3: GenUI Components
// ============================================================================

const RenderSplitView: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const ratio = ir.props.ratio || 0.5;
  const leftWidth = ratio * 100;
  const rightWidth = 100 - leftWidth;
  return (
    <div className="nano-splitview">
      <div className="nano-splitview-left" style={{ width: `${leftWidth}%` }}>
        {ir.children?.[0] && <RenderNode ir={ir.children[0]} context={context} />}
      </div>
      <div className="nano-splitview-right" style={{ width: `${rightWidth}%` }}>
        {ir.children?.[1] && <RenderNode ir={ir.children[1]} context={context} />}
      </div>
    </div>
  );
};

const RenderSmartTextField: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const label = ir.props.label;
  const placeholder = ir.props.placeholder || '';
  const validation = ir.props.validation;
  return (
    <div className="nano-smarttextfield">
      {label && <label className="nano-smarttextfield-label">{label}</label>}
      <input type="text" className="nano-smarttextfield-input" placeholder={placeholder} pattern={validation} />
    </div>
  );
};

const RenderSlider: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir }) => {
  const label = ir.props.label;
  const min = ir.props.min || 0;
  const max = ir.props.max || 100;
  const step = ir.props.step || 1;
  return (
    <div className="nano-slider">
      {label && <label className="nano-slider-label">{label}</label>}
      <input type="range" className="nano-slider-input" min={min} max={max} step={step} />
    </div>
  );
};

const RenderDateRangePicker: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = () => {
  return (
    <div className="nano-daterangepicker">
      <input type="date" className="nano-daterangepicker-start" />
      <input type="date" className="nano-daterangepicker-end" />
    </div>
  );
};

const RenderDataChart: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const type = ir.props.type || 'bar';
  const resolvedData = resolveValue(context, ir.props.data);
  const dataArray = Array.isArray(resolvedData) ? resolvedData : (typeof resolvedData === 'string' ? tryParseStructuredLiteral(resolvedData) : resolvedData);
  const data = Array.isArray(dataArray) ? dataArray : [];

  const xField = ir.props.xField;
  const yField = ir.props.yField;
  const inferred = inferChartFields(data, xField, yField);

  if (!inferred || data.length === 0) {
    return <div className="nano-datachart nano-datachart-empty">(no chart data)</div>;
  }

  // Minimal bar chart (SVG). Uses VSCode theme vars for color.
  const values = data.map((d: any) => Number(d?.[inferred.y]) || 0);
  const max = Math.max(...values, 0);
  const width = 420;
  const height = 160;
  const padding = 16;
  const barGap = 6;
  const barCount = Math.max(data.length, 1);
  const barWidth = (width - padding * 2 - barGap * (barCount - 1)) / barCount;

  return (
    <div className={`nano-datachart type-${type}`}>
      <svg className="nano-datachart-svg" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="chart">
        {data.map((d: any, i: number) => {
          const v = Number(d?.[inferred.y]) || 0;
          const ratio = max > 0 ? v / max : 0;
          const h = Math.max(0, (height - padding * 2) * ratio);
          const x = padding + i * (barWidth + barGap);
          const y = height - padding - h;
          return <rect key={i} className="nano-datachart-bar" x={x} y={y} width={barWidth} height={h} rx={2} />;
        })}
      </svg>
      <div className="nano-datachart-legend">
        {data.slice(0, 8).map((d: any, i: number) => (
          <span key={i} className="nano-datachart-legend-item">
            {String(d?.[inferred.x] ?? '')}: {String(d?.[inferred.y] ?? '')}
          </span>
        ))}
      </div>
    </div>
  );
};

const RenderDataTable: React.FC<{ ir: NanoIR; context: NanoRenderContext }> = ({ ir, context }) => {
  const resolvedColumns = resolveValue(context, ir.props.columns);
  const resolvedData = resolveValue(context, ir.props.data);

  const rows = Array.isArray(resolvedData)
    ? resolvedData
    : Array.isArray(tryParseStructuredLiteral(String(resolvedData ?? '')))
      ? (tryParseStructuredLiteral(String(resolvedData ?? '')) as any[])
      : [];

  type ColumnDef = { key: string; title?: string; format?: string };
  const parsedColumns: ColumnDef[] = (() => {
    if (Array.isArray(resolvedColumns)) {
      return resolvedColumns
        .map((c: any) => ({
          key: String(c?.key ?? c?.field ?? ''),
          title: c?.title,
          format: c?.format,
        }))
        .filter(c => c.key);
    }
    if (typeof resolvedColumns === 'string') {
      const parsed = tryParseStructuredLiteral(resolvedColumns);
      if (Array.isArray(parsed)) {
        return parsed
          .map((c: any) => ({
            key: String(c?.key ?? c?.field ?? ''),
            title: c?.title,
            format: c?.format,
          }))
          .filter(c => c.key);
      }
      // Fallback: "a,b,c"
      return resolvedColumns.split(',').map(s => s.trim()).filter(Boolean).map(k => ({ key: k, title: k }));
    }
    // Infer from first row
    const first = rows.find(r => r && typeof r === 'object') as any;
    if (!first) return [];
    return Object.keys(first).map(k => ({ key: k, title: k }));
  })();

  return (
    <div className="nano-datatable-wrap">
      <table className="nano-datatable">
        <thead>
          <tr>
            {parsedColumns.map((c) => (
              <th key={c.key}>{c.title ?? c.key}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r: any, rowIndex: number) => (
            <tr key={rowIndex}>
              {parsedColumns.map((c) => (
                <td key={c.key}>{formatCell(r?.[c.key], c.format)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const RenderUnknown: React.FC<{ ir: NanoIR }> = ({ ir }) => {
  return (
    <div className="nano-unknown">
      Unknown component: {ir.type}
    </div>
  );
};

