/**
 * ModelConfigDialog Component
 * 
 * Dialog for configuring LLM models in VSCode webview.
 * Communicates with extension via postMessage.
 */

import React, { useState, useEffect } from 'react';
import { useVSCode } from '../hooks/useVSCode';
import './ModelConfigDialog.css';

interface ModelConfigDialogProps {
  isOpen: boolean;
  onClose: () => void;
  currentConfig?: {
    name?: string;
    provider?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    temperature?: number;
    maxTokens?: number;
  } | null;
  isNewConfig?: boolean;
}

const PROVIDERS = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'anthropic', label: 'Anthropic' },
  { value: 'google', label: 'Google' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'ollama', label: 'Ollama' },
  { value: 'openrouter', label: 'OpenRouter' },
  { value: 'glm', label: 'GLM' },
  { value: 'qwen', label: 'Qwen' },
  { value: 'kimi', label: 'Kimi' },
  { value: 'custom-openai-base', label: 'Custom OpenAI-compatible' },
];

export const ModelConfigDialog: React.FC<ModelConfigDialogProps> = ({
  isOpen,
  onClose,
  currentConfig,
  isNewConfig = false
}) => {
  const [configName, setConfigName] = useState(currentConfig?.name || '');
  const [provider, setProvider] = useState(currentConfig?.provider || 'openai');
  const [model, setModel] = useState(currentConfig?.model || '');
  const [apiKey, setApiKey] = useState(currentConfig?.apiKey || '');
  const [baseUrl, setBaseUrl] = useState(currentConfig?.baseUrl || '');
  const [temperature, setTemperature] = useState(currentConfig?.temperature?.toString() || '0.7');
  const [maxTokens, setMaxTokens] = useState(currentConfig?.maxTokens?.toString() || '8192');
  const [showApiKey, setShowApiKey] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const { postMessage } = useVSCode();

  useEffect(() => {
    if (isOpen && currentConfig) {
      setConfigName(currentConfig.name || '');
      setProvider(currentConfig.provider || 'openai');
      setModel(currentConfig.model || '');
      setApiKey(currentConfig.apiKey || '');
      setBaseUrl(currentConfig.baseUrl || '');
      setTemperature(currentConfig.temperature?.toString() || '0.7');
      setMaxTokens(currentConfig.maxTokens?.toString() || '8192');
    } else if (isOpen && isNewConfig) {
      // Reset to defaults for new config
      setConfigName('');
      setProvider('openai');
      setModel('');
      setApiKey('');
      setBaseUrl('');
      setTemperature('0.7');
      setMaxTokens('8192');
    }
  }, [isOpen, currentConfig, isNewConfig]);

  if (!isOpen) return null;

  const needsBaseUrl = ['ollama', 'glm', 'qwen', 'kimi', 'custom-openai-base'].includes(provider);
  const needsApiKey = provider !== 'ollama';

  const handleSave = () => {
    if (!configName.trim()) {
      alert('Please enter a configuration name');
      return;
    }
    if (!model.trim()) {
      alert('Please enter a model name');
      return;
    }
    if (needsApiKey && !apiKey.trim()) {
      alert('Please enter an API key');
      return;
    }
    if (needsBaseUrl && !baseUrl.trim()) {
      alert('Please enter a base URL');
      return;
    }

    postMessage({
      type: 'saveModelConfig',
      data: {
        name: configName.trim(),
        provider,
        model: model.trim(),
        apiKey: apiKey.trim(),
        baseUrl: baseUrl.trim() || undefined,
        temperature: parseFloat(temperature) || 0.7,
        maxTokens: parseInt(maxTokens) || 8192,
        isNewConfig
      }
    });

    onClose();
  };

  return (
    <div className="model-config-dialog-overlay" onClick={onClose}>
      <div className="model-config-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="model-config-dialog-header">
          <h2>{isNewConfig ? 'Add New Model Config' : 'Configure Model'}</h2>
          <button className="close-button" onClick={onClose}>×</button>
        </div>

        <div className="model-config-dialog-content">
          <div className="form-field">
            <label>Configuration Name</label>
            <input
              type="text"
              value={configName}
              onChange={(e) => setConfigName(e.target.value)}
              placeholder="e.g., my-glm, work-gpt4"
            />
          </div>

          <div className="form-field">
            <label>Provider</label>
            <select value={provider} onChange={(e) => setProvider(e.target.value)}>
              {PROVIDERS.map(p => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
          </div>

          <div className="form-field">
            <label>Model</label>
            <input
              type="text"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="Enter model name"
            />
          </div>

          {needsApiKey && (
            <div className="form-field">
              <label>API Key</label>
              <div className="input-with-icon">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="Enter API key"
                />
                <button
                  type="button"
                  className="toggle-visibility"
                  onClick={() => setShowApiKey(!showApiKey)}
                >
                  {showApiKey ? '👁️' : '👁️‍🗨️'}
                </button>
              </div>
            </div>
          )}

          {needsBaseUrl && (
            <div className="form-field">
              <label>Base URL</label>
              <input
                type="text"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder={
                  provider === 'ollama' ? 'http://localhost:11434' :
                  provider === 'glm' ? 'https://open.bigmodel.cn/api/paas/v4' :
                  provider === 'qwen' ? 'https://dashscope.aliyuncs.com/api/v1' :
                  provider === 'kimi' ? 'https://api.moonshot.cn/v1' :
                  'https://api.example.com/v1'
                }
              />
            </div>
          )}

          <div className="form-field">
            <button
              className="toggle-advanced"
              onClick={() => setShowAdvanced(!showAdvanced)}
            >
              {showAdvanced ? '▼' : '▶'} Advanced Parameters
            </button>
          </div>

          {showAdvanced && (
            <>
              <div className="form-field">
                <label>Temperature</label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  max="2"
                  value={temperature}
                  onChange={(e) => setTemperature(e.target.value)}
                  placeholder="0.7"
                />
              </div>

              <div className="form-field">
                <label>Max Tokens</label>
                <input
                  type="number"
                  value={maxTokens}
                  onChange={(e) => setMaxTokens(e.target.value)}
                  placeholder="8192"
                />
              </div>
            </>
          )}
        </div>

        <div className="model-config-dialog-footer">
          <button className="cancel-button" onClick={onClose}>Cancel</button>
          <button className="save-button" onClick={handleSave}>Save</button>
        </div>
      </div>
    </div>
  );
};

