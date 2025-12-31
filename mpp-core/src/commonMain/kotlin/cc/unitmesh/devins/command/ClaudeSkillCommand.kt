package cc.unitmesh.devins.command

import cc.unitmesh.agent.Platform
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.devins.filesystem.ProjectFileSystem
import cc.unitmesh.yaml.YamlUtils

/**
 * ClaudeSkillCommand represents a Claude Skill loaded from directories containing SKILL.md files.
 * 
 * Claude Skills are organized folders of instructions, scripts, and resources that agents can
 * discover and load dynamically. Each skill is a directory containing a SKILL.md file with
 * YAML frontmatter (name, description) and markdown content.
 * 
 * Skills can be located in:
 * - Project root directories containing SKILL.md
 * - ~/.claude/skills/ directory (user-level skills)
 * 
 * Example usage:
 * ```
 * /skill.pdf <arguments>
 * ```
 */
data class ClaudeSkillCommand(
    val skillName: String,
    val description: String,
    val template: String,
    val skillPath: String
) {
    val fullCommandName: String get() = "skill.$skillName"

    companion object {
        private val logger = getLogger("ClaudeSkillCommand")
        private const val SKILL_FILE = "SKILL.md"
        private const val USER_SKILLS_DIR = ".claude/skills"

        /**
         * Load all Claude Skills from available locations:
         * 1. Project root directories containing SKILL.md
         * 2. User home ~/.claude/skills/ directory
         */
        fun loadAll(fileSystem: ProjectFileSystem): List<ClaudeSkillCommand> {
            val skills = mutableListOf<ClaudeSkillCommand>()
            
            // Load from project root
            skills.addAll(loadFromProjectRoot(fileSystem))
            
            // Load from user skills directory
            skills.addAll(loadFromUserSkillsDir(fileSystem))
            
            logger.info { "[ClaudeSkillCommand] Loaded ${skills.size} skills total" }
            return skills
        }

        /**
         * Load skills from project root directories
         */
        fun loadFromProjectRoot(fileSystem: ProjectFileSystem): List<ClaudeSkillCommand> {
            val projectPath = fileSystem.getProjectPath()
            if (projectPath == null) {
                logger.debug { "[ClaudeSkillCommand] No project path available" }
                return emptyList()
            }

            logger.debug { "[ClaudeSkillCommand] Looking for skills in project root: $projectPath" }

            return try {
                // List all directories in project root
                val entries = fileSystem.listFiles(projectPath, null)
                
                entries.mapNotNull { entry ->
                    val dirPath = "$projectPath/$entry"
                    if (fileSystem.isDirectory(dirPath)) {
                        val skillFilePath = "$dirPath/$SKILL_FILE"
                        if (fileSystem.exists(skillFilePath)) {
                            loadSkillFromFile(fileSystem, skillFilePath, dirPath, entry)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "[ClaudeSkillCommand] Error loading skills from project root: ${e.message}" }
                emptyList()
            }
        }

        /**
         * Load skills from user's ~/.claude/skills/ directory
         */
        fun loadFromUserSkillsDir(fileSystem: ProjectFileSystem): List<ClaudeSkillCommand> {
            val userHome = Platform.getUserHomeDir()
            if (userHome == "~" || userHome.isEmpty()) {
                logger.debug { "[ClaudeSkillCommand] User home directory not available" }
                return emptyList()
            }

            val skillsDir = "$userHome/$USER_SKILLS_DIR"
            logger.debug { "[ClaudeSkillCommand] Looking for skills in user directory: $skillsDir" }

            if (!fileSystem.exists(skillsDir)) {
                logger.debug { "[ClaudeSkillCommand] User skills directory does not exist: $skillsDir" }
                return emptyList()
            }

            return try {
                val entries = fileSystem.listFiles(skillsDir, null)
                
                entries.mapNotNull { entry ->
                    val dirPath = "$skillsDir/$entry"
                    if (fileSystem.isDirectory(dirPath)) {
                        val skillFilePath = "$dirPath/$SKILL_FILE"
                        if (fileSystem.exists(skillFilePath)) {
                            loadSkillFromFile(fileSystem, skillFilePath, dirPath, entry)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "[ClaudeSkillCommand] Error loading skills from user dir: ${e.message}" }
                emptyList()
            }
        }

        /**
         * Load a skill from a SKILL.md file
         */
        private fun loadSkillFromFile(
            fileSystem: ProjectFileSystem,
            skillFilePath: String,
            skillDirPath: String,
            dirName: String
        ): ClaudeSkillCommand? {
            return try {
                val template = fileSystem.readFile(skillFilePath)
                if (template == null) {
                    logger.warn { "[ClaudeSkillCommand] Failed to read skill file: $skillFilePath" }
                    return null
                }

                val (frontmatter, _) = parseFrontmatter(template)
                
                // Extract skill name from frontmatter or directory name
                val skillName = frontmatter?.get("name")?.toString()?.takeIf { it.isNotEmpty() }
                    ?: dirName
                
                // Extract description from frontmatter
                val description = frontmatter?.get("description")?.toString()?.takeIf { it.isNotEmpty() }
                    ?: "Claude Skill: $skillName"

                val skill = ClaudeSkillCommand(
                    skillName = skillName,
                    description = description,
                    template = template,
                    skillPath = skillDirPath
                )
                logger.debug { "[ClaudeSkillCommand] Loaded skill: ${skill.fullCommandName}" }
                skill
            } catch (e: Exception) {
                logger.error(e) { "[ClaudeSkillCommand] Error loading skill from $skillFilePath: ${e.message}" }
                null
            }
        }

        /**
         * Find a specific Claude Skill by skill name
         */
        fun findBySkillName(skills: List<ClaudeSkillCommand>, skillName: String): ClaudeSkillCommand? {
            return skills.find { it.skillName == skillName }
        }

        /**
         * Find a specific Claude Skill by full command name (e.g., "skill.pdf")
         */
        fun findByFullName(skills: List<ClaudeSkillCommand>, commandName: String): ClaudeSkillCommand? {
            return skills.find { it.fullCommandName == commandName }
        }

        /**
         * Check if Claude Skills are available
         */
        fun isAvailable(fileSystem: ProjectFileSystem): Boolean {
            return loadAll(fileSystem).isNotEmpty()
        }

        /**
         * Parse frontmatter from markdown content
         * Returns frontmatter data and remaining content
         */
        private fun parseFrontmatter(markdown: String): Pair<Map<String, Any>?, String> {
            val frontmatterRegex = Regex("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n", RegexOption.MULTILINE)
            val match = frontmatterRegex.find(markdown)

            if (match == null) {
                return Pair(null, markdown)
            }

            val yamlContent = match.groups[1]?.value ?: ""
            val endIndex = match.range.last + 1
            val contentWithoutFrontmatter = if (endIndex < markdown.length) {
                markdown.substring(endIndex)
            } else {
                ""
            }

            return try {
                val frontmatter = YamlUtils.load(yamlContent) ?: emptyMap()
                Pair(frontmatter, contentWithoutFrontmatter)
            } catch (e: Exception) {
                Pair(null, contentWithoutFrontmatter)
            }
        }
    }
}

