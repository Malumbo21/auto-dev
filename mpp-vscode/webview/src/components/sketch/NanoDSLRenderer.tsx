/**
 * NanoDSL Renderer for VSCode Webview
 * 
 * Parses NanoDSL code and renders live UI preview using NanoRenderer.
 * Features:
 * - Live UI preview when parsing succeeds
 * - Toggle between preview and source code view
 * - Parse error display
 * - Streaming support (shows loading state)
 */

import React, { useState, useEffect } from 'react';
import { CodeBlockRenderer } from './CodeBlockRenderer';
import { NanoRenderer } from '../nano/NanoRenderer';
import { NanoIR } from '../../types/nano';
import './NanoDSLRenderer.css';

interface NanoDSLRendererProps {
  nanodslCode: string;
  isComplete?: boolean;
  onAction?: (action: string, data: any) => void;
}

export const NanoDSLRenderer: React.FC<NanoDSLRendererProps> = ({
  nanodslCode,
  isComplete = false,
  onAction
}) => {
  const [showPreview, setShowPreview] = useState(true);
  const [parseError, setParseError] = useState<string | null>(null);
  const [nanoIR, setNanoIR] = useState<NanoIR | null>(null);

  // Parse NanoDSL to IR when code changes
  useEffect(() => {
    if (isComplete && nanodslCode.trim()) {
      try {
        // Use the WASM parser from @xiuper/ui
        const { parseNanoDSL } = require('@xiuper/ui');
        const ir = parseNanoDSL(nanodslCode);
        setNanoIR(ir);
        setParseError(null);
      } catch (e: any) {
        setParseError(e.message || 'Unknown parse error');
        setNanoIR(null);
      }
    } else if (!isComplete) {
      // Reset during streaming
      setParseError(null);
      setNanoIR(null);
    }
  }, [nanodslCode, isComplete]);

  return (
    <div className="nanodsl-renderer">
      {/* Header */}
      <div className="nanodsl-header">
        <div className="nanodsl-header-left">
          <span className="nanodsl-label">NanoDSL</span>
          
          {parseError && (
            <span className="nanodsl-badge nanodsl-badge-error">Parse Error</span>
          )}
          
          {nanoIR && !parseError && (
            <span className="nanodsl-badge nanodsl-badge-success">Valid</span>
          )}
          
          {!isComplete && !nanoIR && !parseError && (
            <span className="nanodsl-loading">
              <span className="spinner"></span>
            </span>
          )}
        </div>

        {/* Toggle button (only show if we have valid IR or parse error) */}
        {(nanoIR || parseError) && (
          <button
            className="nanodsl-toggle-btn"
            onClick={() => setShowPreview(!showPreview)}
          >
            {showPreview && nanoIR ? '<\/>' : 'Preview'}
          </button>
        )}
      </div>

      {/* Content */}
      <div className="nanodsl-content">
        {showPreview && nanoIR ? (
          // Live UI Preview
          <div className="nanodsl-preview">
            <NanoRenderer ir={nanoIR} />
          </div>
        ) : (
          // Source code view
          <CodeBlockRenderer
            code={nanodslCode}
            language="nanodsl"
            isComplete={isComplete}
            onAction={onAction}
          />
        )}

        {/* Show parse error details */}
        {parseError && !showPreview && (
          <div className="nanodsl-error">
            <span className="nanodsl-error-label">Error:</span> {parseError}
          </div>
        )}
      </div>
    </div>
  );
};

