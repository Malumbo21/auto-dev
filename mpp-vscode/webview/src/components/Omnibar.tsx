/**
 * Omnibar Component
 * 
 * A wide floating command palette for quick actions.
 * Similar to Linear's command palette and Raycast.
 * Mirrors mpp-ui's Compose Omnibar component.
 */

import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useVSCode, ExtensionMessage } from '../hooks/useVSCode';
import './Omnibar.css';

export type OmnibarItemType = 'command' | 'custom_command' | 'file' | 'symbol' | 'agent' | 'recent' | 'setting';

export interface OmnibarItem {
  id: string;
  title: string;
  type: OmnibarItemType;
  description?: string;
  shortcutHint?: string;
  iconName?: string;
  category?: string;
  weight?: number;
}

interface OmnibarProps {
  isOpen: boolean;
  onClose: () => void;
  onAction?: (item: OmnibarItem, action: string) => void;
}

// Built-in commands for the Omnibar
const builtinItems: OmnibarItem[] = [
  { id: 'cmd_file', title: '/file', type: 'command', description: 'Read file content from project', category: 'Commands', weight: 100 },
  { id: 'cmd_write', title: '/write', type: 'command', description: 'Write content to a file', category: 'Commands', weight: 95 },
  { id: 'cmd_shell', title: '/shell', type: 'command', description: 'Execute shell commands', category: 'Commands', weight: 90 },
  { id: 'cmd_search', title: '/search', type: 'command', description: 'Search for files or content', category: 'Commands', weight: 85 },
  { id: 'cmd_patch', title: '/patch', type: 'command', description: 'Apply code patches', category: 'Commands', weight: 80 },
  { id: 'cmd_browse', title: '/browse', type: 'command', description: 'Browse web pages', category: 'Commands', weight: 75 },
  { id: 'cmd_commit', title: '/commit', type: 'command', description: 'Git commit changes', category: 'Commands', weight: 70 },
  { id: 'cmd_help', title: '/help', type: 'command', description: 'Show available commands', category: 'Commands', weight: 50 },
  { id: 'setting_config', title: 'Open Config', type: 'setting', description: 'Open configuration file', category: 'Settings', weight: 60, shortcutHint: '⌘,' },
  { id: 'setting_clear', title: 'Clear History', type: 'setting', description: 'Clear chat history', category: 'Settings', weight: 40 },
  { id: 'setting_mcp', title: 'Configure MCP', type: 'setting', description: 'Open MCP configuration', category: 'Settings', weight: 55 },
];

// Fuzzy search function
function fuzzyMatch(text: string, query: string): number {
  if (!query) return 1;
  const lowerText = text.toLowerCase();
  const lowerQuery = query.toLowerCase();
  
  if (lowerText === lowerQuery) return 1000;
  if (lowerText.startsWith(lowerQuery)) return 500;
  if (lowerText.includes(lowerQuery)) return 200;
  
  // Fuzzy match - all query chars appear in order
  let queryIndex = 0;
  for (const char of lowerText) {
    if (queryIndex < lowerQuery.length && char === lowerQuery[queryIndex]) {
      queryIndex++;
    }
  }
  return queryIndex === lowerQuery.length ? 100 : 0;
}

const getItemIcon = (type: OmnibarItemType): string => {
  switch (type) {
    case 'command': return '⌘';
    case 'custom_command': return '📝';
    case 'file': return '📄';
    case 'symbol': return '🔣';
    case 'agent': return '🤖';
    case 'recent': return '🕐';
    case 'setting': return '⚙️';
    default: return '•';
  }
};

export const Omnibar: React.FC<OmnibarProps> = ({ isOpen, onClose, onAction }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [recentItems, setRecentItems] = useState<OmnibarItem[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const { postMessage, onMessage } = useVSCode();

  // Filter and sort items based on search query
  const filteredItems = useMemo(() => {
    const allItems = [...recentItems, ...builtinItems];
    const uniqueItems = allItems.filter((item, index, self) => 
      index === self.findIndex(i => i.id === item.id)
    );
    
    if (!searchQuery) {
      return uniqueItems.sort((a, b) => (b.weight || 0) - (a.weight || 0));
    }
    
    return uniqueItems
      .map(item => ({
        item,
        score: Math.max(
          fuzzyMatch(item.title, searchQuery),
          fuzzyMatch(item.description || '', searchQuery) * 0.5
        ) + (item.weight || 0)
      }))
      .filter(({ score }) => score > 0)
      .sort((a, b) => b.score - a.score)
      .map(({ item }) => item);
  }, [searchQuery, recentItems]);

  // Focus input when opened
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
      setSearchQuery('');
      setSelectedIndex(0);
    }
  }, [isOpen]);

  // Listen for extension messages
  useEffect(() => {
    return onMessage((message: ExtensionMessage) => {
      if (message.type === 'omnibarItems' && message.data) {
        setRecentItems((message.data as { items: OmnibarItem[] }).items || []);
      }
    });
  }, [onMessage]);

  // Request recent items when opened
  useEffect(() => {
    if (isOpen) {
      postMessage({ type: 'getOmnibarItems' });
    }
  }, [isOpen, postMessage]);

  // Scroll selected item into view
  useEffect(() => {
    if (listRef.current) {
      const selectedEl = listRef.current.querySelector('.omnibar-item.selected');
      selectedEl?.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedIndex]);

  const handleSelect = useCallback((item: OmnibarItem) => {
    if (item.type === 'command' || item.type === 'custom_command') {
      onAction?.(item, 'insertText');
      postMessage({ type: 'omnibarAction', data: { action: 'insertText', item } });
    } else if (item.type === 'setting') {
      postMessage({ type: 'omnibarAction', data: { action: 'setting', itemId: item.id } });
    }
    onClose();
  }, [onAction, onClose, postMessage]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setSelectedIndex(i => Math.min(i + 1, filteredItems.length - 1));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setSelectedIndex(i => Math.max(i - 1, 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (filteredItems[selectedIndex]) {
          handleSelect(filteredItems[selectedIndex]);
        }
        break;
      case 'Escape':
        e.preventDefault();
        onClose();
        break;
    }
  }, [filteredItems, selectedIndex, handleSelect, onClose]);

  if (!isOpen) return null;

  return (
    <div className="omnibar-overlay" onClick={onClose}>
      <div className="omnibar-container" onClick={e => e.stopPropagation()}>
        <div className="omnibar-search">
          <span className="omnibar-search-icon">🔍</span>
          <input
            ref={inputRef}
            type="text"
            className="omnibar-input"
            placeholder="Type a command or search..."
            value={searchQuery}
            onChange={e => { setSearchQuery(e.target.value); setSelectedIndex(0); }}
            onKeyDown={handleKeyDown}
          />
          <span className="omnibar-hint">ESC to close</span>
        </div>
        <div className="omnibar-divider" />
        <div className="omnibar-list" ref={listRef}>
          {filteredItems.length === 0 ? (
            <div className="omnibar-empty">
              <span className="omnibar-empty-icon">🔍</span>
              <span>No results found</span>
            </div>
          ) : (
            filteredItems.map((item, index) => (
              <div
                key={item.id}
                className={`omnibar-item ${index === selectedIndex ? 'selected' : ''}`}
                onClick={() => handleSelect(item)}
                onMouseEnter={() => setSelectedIndex(index)}
              >
                <span className="omnibar-item-icon">{getItemIcon(item.type)}</span>
                <div className="omnibar-item-content">
                  <span className="omnibar-item-title">{item.title}</span>
                  {item.description && (
                    <span className="omnibar-item-description">{item.description}</span>
                  )}
                </div>
                {item.shortcutHint && (
                  <span className="omnibar-item-shortcut">{item.shortcutHint}</span>
                )}
                {item.category && (
                  <span className="omnibar-item-category">{item.category}</span>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

