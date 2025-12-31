/**
 * Skill Command Processor
 *
 * Handles Claude Skill commands like /skill.pdf, /skill.code-review
 * Skills are loaded from project directories containing SKILL.md files
 * or from ~/.claude/skills/ directory
 */

import type { InputProcessor, ProcessorContext, ProcessorResult } from './InputRouter.js';
import { getCurrentProjectPath } from '../utils/domainDictUtils.js';

// Import mpp-core for skill management
let mppCore: any = null;
let skillManager: any = null;

/**
 * Initialize mpp-core module
 */
async function initMppCore(): Promise<boolean> {
  if (mppCore) return true;
  
  try {
    // @ts-ignore - Runtime import
    mppCore = await import('@xiuper/mpp-core');
    return true;
  } catch (error) {
    console.error('Failed to load mpp-core:', error);
    return false;
  }
}

/**
 * Get or create skill manager for the current project
 */
async function getSkillManager(projectPath: string): Promise<any> {
  if (!await initMppCore()) {
    return null;
  }
  
  try {
    const exports = mppCore['module.exports'] || mppCore.default || mppCore;
    const JsClaudeSkillManager = exports?.cc?.unitmesh?.llm?.JsClaudeSkillManager;
    
    if (!JsClaudeSkillManager) {
      console.error('JsClaudeSkillManager not found in mpp-core exports');
      return null;
    }
    
    // Create new manager if project path changed or not initialized
    if (!skillManager || skillManager._projectPath !== projectPath) {
      skillManager = new JsClaudeSkillManager(projectPath);
      skillManager._projectPath = projectPath;
      // Pre-load skills
      await skillManager.loadSkills();
    }
    
    return skillManager;
  } catch (error) {
    console.error('Failed to create skill manager:', error);
    return null;
  }
}

/**
 * Skill Command Processor
 * Handles /skill.* commands
 */
export class SkillCommandProcessor implements InputProcessor {
  name = 'SkillCommandProcessor';

  /**
   * Check if this processor can handle the input
   */
  canHandle(input: string): boolean {
    const trimmed = input.trim();
    return trimmed.startsWith('/skill.');
  }

  /**
   * Process skill command
   */
  async process(input: string, context: ProcessorContext): Promise<ProcessorResult> {
    const trimmed = input.trim();
    
    // Parse command: /skill.name arguments
    const match = trimmed.match(/^\/skill\.([^\s]+)(?:\s+(.*))?$/);
    
    if (!match) {
      return {
        type: 'error',
        message: 'Invalid skill command format. Use /skill.<name> <arguments>'
      };
    }
    
    const skillName = match[1];
    const arguments_ = match[2] || '';
    
    context.logger.info(`[SkillCommandProcessor] Processing skill: ${skillName}`);
    
    // Get project path
    const projectPath = getCurrentProjectPath();
    if (!projectPath) {
      return {
        type: 'error',
        message: 'No project path available. Please run from a project directory.'
      };
    }
    
    // Get skill manager
    const manager = await getSkillManager(projectPath);
    if (!manager) {
      return {
        type: 'error',
        message: 'Failed to initialize skill manager. Make sure mpp-core is available.'
      };
    }
    
    try {
      // Find the skill
      const skill = manager.findSkill(skillName);
      
      if (!skill) {
        // List available skills
        const skills = manager.getSkills();
        const availableSkills = skills.map((s: any) => s.skillName).join(', ');
        
        return {
          type: 'error',
          message: `Skill not found: ${skillName}\nAvailable skills: ${availableSkills || 'none'}`
        };
      }
      
      // Execute the skill
      context.logger.info(`[SkillCommandProcessor] Executing skill: ${skill.fullCommandName}`);
      const output = await manager.executeSkill(skillName, arguments_);
      
      // Return the compiled template as LLM query
      return {
        type: 'llm-query',
        query: output
      };
      
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      context.logger.error(`[SkillCommandProcessor] Error executing skill: ${errorMessage}`);
      
      return {
        type: 'error',
        message: `Failed to execute skill '${skillName}': ${errorMessage}`
      };
    }
  }
}

/**
 * List available skills for completion
 */
export async function listAvailableSkills(): Promise<Array<{name: string, description: string}>> {
  const projectPath = getCurrentProjectPath();
  if (!projectPath) return [];
  
  const manager = await getSkillManager(projectPath);
  if (!manager) return [];
  
  try {
    const skills = manager.getSkills();
    return skills.map((s: any) => ({
      name: s.skillName,
      description: s.description
    }));
  } catch {
    return [];
  }
}

