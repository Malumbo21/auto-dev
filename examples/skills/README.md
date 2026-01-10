# Claude Skills Examples

This directory contains example Claude Skills that demonstrate how to create and use skills in AutoDev.

## What are Claude Skills?

Claude Skills are reusable prompt templates stored in directories with a `SKILL.md` file. They allow you to:

- Create standardized workflows for common tasks
- Share prompts across projects and team members
- Organize complex instructions with variable substitution
- Build a library of AI-powered tools

## Available Example Skills

### 1. PDF Skill (`pdf/`)
Extract information from PDF documents with structured prompts.

**Usage**: `/skill.pdf Extract all tables from quarterly-report.pdf`

### 2. Code Review Skill (`code-review/`)
Perform comprehensive code reviews with consistent criteria.

**Usage**: `/skill.code-review Review src/main.ts for security issues`

### 3. Test Generation Skill (`test-gen/`)
Generate unit tests for code files with proper structure.

**Usage**: `/skill.test-gen Create tests for UserService.kt`

### 4. Documentation Skill (`doc-gen/`)
Generate documentation from code with consistent formatting.

**Usage**: `/skill.doc-gen Document the API endpoints in routes.ts`

### 5. Refactoring Skill (`refactor/`)
Suggest refactoring improvements for code quality.

**Usage**: `/skill.refactor Improve the performance of calculateTotal function`

## How to Use These Skills

### Option 1: Copy to Project
```bash
# Copy a skill to your project
cp -r examples/skills/pdf ./pdf-skill
```

### Option 2: Install to User Directory
```bash
# Install to user skills directory
mkdir -p ~/.claude/skills
cp -r examples/skills/pdf ~/.claude/skills/
```

### Option 3: Use Directly (if in AutoDev project)
Skills in the project root are automatically discovered.

## Creating Your Own Skills

### Basic Structure

```
my-skill/
└── SKILL.md
```

### SKILL.md Format

```markdown
---
name: my-skill
description: Brief description of what this skill does
variables:
  OPTIONAL_VAR: "default value"
  FILE_PATH: "path/to/file.md"
---

# Skill Instructions

Your prompt template here.

Use $ARGUMENTS for user input.
Use $OPTIONAL_VAR for defined variables.
Use $PROJECT_NAME and $PROJECT_PATH for project info.
```

### Variable Types

1. **Built-in Variables** (always available):
   - `$ARGUMENTS` - User-provided arguments
   - `$COMMAND` - The command name
   - `$INPUT` - Raw input text
   - `$PROJECT_PATH` - Project root path
   - `$PROJECT_NAME` - Project name

2. **Custom Variables** (defined in frontmatter):
   - Simple values: `VAR_NAME: "value"`
   - File content: `TEMPLATE: "path/to/template.md"` (auto-loaded)

### Best Practices

1. **Clear Descriptions**: Write helpful descriptions for auto-completion
2. **Structured Prompts**: Use markdown headers and lists
3. **Variable Defaults**: Provide sensible defaults for optional variables
4. **Examples**: Include usage examples in comments
5. **Validation**: Add instructions for validating inputs

## Testing Your Skills

### In IntelliJ IDEA
1. Open DevIns console
2. Type `/skill.<name> <arguments>`
3. Press Enter

### In VSCode
1. Open AutoDev chat panel
2. Type `/skill.<name> <arguments>`
3. Send message

### In CLI
```bash
$ xiuper chat
> /skill.<name> <arguments>
```

## Skill Discovery

Skills are discovered from:
1. **Project root**: Any directory with `SKILL.md` in project root
2. **User directory**: `~/.claude/skills/*/SKILL.md`

## Advanced Features

### File Content Loading

Variables that look like file paths are automatically loaded:

```yaml
variables:
  SPEC: "docs/spec.md"          # Content loaded from file
  TEMPLATE: "templates/api.md"  # Content loaded from file
```

### Multi-line Templates

Use YAML multi-line syntax for complex templates:

```yaml
---
name: complex
description: Complex multi-line skill
---

# Part 1
Instructions here...

# Part 2
More instructions...

$ARGUMENTS
```

## Contributing

To contribute a new example skill:

1. Create a new directory: `examples/skills/your-skill/`
2. Add `SKILL.md` with frontmatter and template
3. Update this README with your skill description
4. Test the skill in at least one platform
5. Submit a pull request

## License

These example skills are provided under the same license as AutoDev (MPL 2.0).

