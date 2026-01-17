import React, { useEffect, useMemo, useRef, useState } from 'react';

type CommonProps = {
  id?: string; // used for localStorage persistence
  className?: string;
  minPrimaryPx?: number;
  minSecondaryPx?: number;
  initialPrimarySizePx?: number;
  initialPrimarySizeRatio?: number; // 0-1
  maxPrimarySizePx?: number;
  maxPrimarySizeRatio?: number; // 0-1
};

type HorizontalSplitProps = CommonProps & {
  direction: 'horizontal';
  primary: React.ReactNode; // left
  secondary: React.ReactNode; // right
  dividerClassName?: string;
};

type VerticalSplitProps = CommonProps & {
  direction: 'vertical';
  primary: React.ReactNode; // top
  secondary: React.ReactNode; // bottom
  dividerClassName?: string;
};

type SplitPaneProps = HorizontalSplitProps | VerticalSplitProps;

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

function safeParseFloat(v: string | null | undefined): number | null {
  if (!v) return null;
  const n = Number.parseFloat(v);
  return Number.isFinite(n) ? n : null;
}

export const SplitPane: React.FC<SplitPaneProps> = (props) => {
  const {
    id,
    className,
    minPrimaryPx = 200,
    minSecondaryPx = 200,
    initialPrimarySizePx,
    initialPrimarySizeRatio,
    maxPrimarySizePx,
    maxPrimarySizeRatio,
    dividerClassName,
    primary,
    secondary,
  } = props;

  const containerRef = useRef<HTMLDivElement>(null);
  const isHorizontal = props.direction === 'horizontal';

  const storageKey = useMemo(() => (id ? `aiaf.splitpane.${id}.${props.direction}.primaryPx` : null), [id, props.direction]);

  const [primaryPx, setPrimaryPx] = useState<number | null>(() => {
    if (!storageKey) return null;
    return safeParseFloat(localStorage.getItem(storageKey));
  });

  // If there is no persisted value, compute it from initial props once we know container size.
  useEffect(() => {
    if (primaryPx != null) return;
    const el = containerRef.current;
    if (!el) return;

    const rect = el.getBoundingClientRect();
    const total = isHorizontal ? rect.width : rect.height;

    let next = initialPrimarySizePx ?? null;
    if (next == null) {
      const ratio = initialPrimarySizeRatio ?? 0.5;
      next = total * ratio;
    }

    setPrimaryPx(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [primaryPx, isHorizontal]);

  // Persist
  useEffect(() => {
    if (!storageKey) return;
    if (primaryPx == null) return;
    localStorage.setItem(storageKey, String(primaryPx));
  }, [storageKey, primaryPx]);

  const startDrag = (e: React.MouseEvent | React.PointerEvent) => {
    e.preventDefault();

    const el = containerRef.current;
    if (!el) return;

    const rect = el.getBoundingClientRect();

    const onMove = (ev: PointerEvent) => {
      const total = isHorizontal ? rect.width : rect.height;
      let next = isHorizontal ? ev.clientX - rect.left : ev.clientY - rect.top;

      const min = minPrimaryPx;
      const maxBySecondary = total - minSecondaryPx;

      let max = maxBySecondary;
      if (typeof maxPrimarySizePx === 'number') {
        max = Math.min(max, maxPrimarySizePx);
      }
      if (typeof maxPrimarySizeRatio === 'number') {
        max = Math.min(max, total * maxPrimarySizeRatio);
      }

      next = clamp(next, min, max);
      setPrimaryPx(next);
    };

    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
  };

  const primaryStyle = useMemo<React.CSSProperties>(() => {
    if (primaryPx == null) return {};
    return isHorizontal ? { width: primaryPx } : { height: primaryPx };
  }, [primaryPx, isHorizontal]);

  const dividerBase = isHorizontal
    ? 'w-1 cursor-col-resize bg-white/5 hover:bg-white/10 active:bg-white/15'
    : 'h-1 cursor-row-resize bg-white/5 hover:bg-white/10 active:bg-white/15';

  return (
    <div
      ref={containerRef}
      className={[
        // Ensure the split pane participates correctly in parent flex sizing and doesn't shrink-to-content.
        'flex flex-1 min-w-0 min-h-0 h-full w-full overflow-hidden',
        isHorizontal ? 'flex-row' : 'flex-col',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="min-w-0 min-h-0 overflow-hidden shrink-0" style={primaryStyle}>
        {primary}
      </div>

      <div
        role="separator"
        aria-orientation={isHorizontal ? 'vertical' : 'horizontal'}
        onPointerDown={startDrag}
        className={[dividerBase, dividerClassName].filter(Boolean).join(' ')}
        title="Drag to resize"
      />

      <div className="min-w-0 min-h-0 overflow-hidden flex-1">
        {secondary}
      </div>
    </div>
  );
};
