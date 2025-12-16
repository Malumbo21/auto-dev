package cc.unitmesh.xuiper.prompt

/**
 * JS (Node.js) implementation of ResourceLoader.
 *
 * Note: Resource loading from files is not supported in JS.
 * This implementation returns embedded prompt templates.
 */
actual object ResourceLoader {
    actual fun loadResource(path: String): String {
        // For WasmJs, we embed the prompt templates directly
        return when (path) {
            "prompts/standard.txt" -> STANDARD_PROMPT
            "prompts/minimal.txt" -> MINIMAL_PROMPT
            "prompts/verbose.txt" -> VERBOSE_PROMPT
            else -> throw IllegalStateException("Resource not found: $path")
        }
    }
    
    private const val STANDARD_PROMPT = """You are a NanoDSL expert. Generate UI code using NanoDSL syntax.

## NanoDSL Syntax

NanoDSL uses Python-style indentation (4 spaces) to represent hierarchy.

### Components
- `component Name:` - Define a component
- `VStack(spacing="sm"):` - Vertical stack layout
- `HStack(align="center", justify="between"):` - Horizontal stack layout
- `Card:` - Container with padding/shadow
- `Text("content", style="h1|h2|h3|body|caption")` - Text display
- `Button("label", intent="primary|secondary")` - Clickable button
- `Image(src=path, aspect=16/9, radius="md")` - Image display
- `Input(value=binding, placeholder="...")` - Text input
- `Badge("text", color="green|red|blue")` - Status badge
- `Icon(name="check|x|info|warning")` - Icon display
- `Divider` - Horizontal line separator

### Layout
- `spacing`: "xs" | "sm" | "md" | "lg" | "xl"
- `padding`: "xs" | "sm" | "md" | "lg" | "xl"
- `align`: "start" | "center" | "end"
- `justify`: "start" | "center" | "end" | "between" | "around"

### State Management
```
component Counter:
    state:
        count: Int = 0
    
    Card:
        VStack:
            Text("Count: {count}")
            Button("Increment"):
                on_click: count += 1
```

### Bindings
- `<<` - Subscribe to state (one-way binding)
- `:=` - Two-way binding (for inputs)

Example:
```
Text("Value: {value}") << value
Input(value := userInput)
```

## Guidelines
1. Use proper indentation (4 spaces)
2. Keep component hierarchy clear
3. Use semantic component names
4. Add state when needed for interactivity
5. Use appropriate spacing and padding values
"""

    private const val MINIMAL_PROMPT = """Generate NanoDSL UI code. Use Python-style indentation.

Components: VStack, HStack, Card, Text, Button, Input, Image, Badge, Icon, Divider
State: Define with `state:` block, bind with `<<` or `:=`
Actions: Use `on_click:` for button actions

Example:
```
component Example:
    Card:
        VStack(spacing="md"):
            Text("Hello", style="h2")
            Button("Click"):
                on_click: doSomething()
```
"""

    private const val VERBOSE_PROMPT = """You are an expert in NanoDSL, a declarative UI language optimized for AI generation.

## Complete NanoDSL Specification

### Core Principles
1. **Indentation-based hierarchy** - Use 4 spaces for each level
2. **Minimal syntax** - Reduce token usage for LLMs
3. **Explicit state bindings** - Clear data flow with `<<` and `:=`
4. **Component composition** - Build complex UIs from simple parts

### Component Reference

#### Layout Components
- **VStack** - Vertical stack layout
  - Props: `spacing`, `padding`, `align`
  - Example: `VStack(spacing="md", align="center"):`

- **HStack** - Horizontal stack layout
  - Props: `spacing`, `padding`, `align`, `justify`
  - Example: `HStack(justify="between"):`

- **Card** - Container with padding and shadow
  - Props: `padding`, `shadow`, `radius`
  - Example: `Card(padding="lg"):`

#### Content Components
- **Text** - Display text
  - Props: `content`, `style`, `color`
  - Styles: `h1`, `h2`, `h3`, `body`, `caption`
  - Example: `Text("Hello World", style="h2")`

- **Button** - Clickable button
  - Props: `label`, `intent`, `size`
  - Intents: `primary`, `secondary`, `danger`
  - Example: `Button("Submit", intent="primary")`

- **Input** - Text input field
  - Props: `value`, `placeholder`, `type`
  - Types: `text`, `email`, `password`, `number`
  - Example: `Input(value := email, placeholder="Enter email")`

- **Image** - Display image
  - Props: `src`, `aspect`, `radius`, `alt`
  - Example: `Image(src="/path/to/image.jpg", aspect=16/9)`

- **Badge** - Status indicator
  - Props: `text`, `color`
  - Colors: `green`, `red`, `blue`, `yellow`, `gray`
  - Example: `Badge("Active", color="green")`

- **Icon** - Icon display
  - Props: `name`, `size`, `color`
  - Names: `check`, `x`, `info`, `warning`, `arrow-right`, etc.
  - Example: `Icon(name="check", color="green")`

- **Divider** - Horizontal separator
  - Example: `Divider`

### State Management

Define state variables in a `state:` block:
```
component MyComponent:
    state:
        count: Int = 0
        name: String = ""
        isActive: Boolean = false
```

### Data Binding

- **Subscribe binding (`<<`)** - One-way data flow from state to UI
  ```
  Text("Count: {count}") << count
  ```

- **Two-way binding (`:=`)** - Bidirectional data flow (for inputs)
  ```
  Input(value := userName)
  ```

### Actions

Define actions with `on_click:`:
```
Button("Increment"):
    on_click: count += 1
```

Multiple actions:
```
Button("Submit"):
    on_click:
        validate()
        submit()
        reset()
```

### Best Practices

1. **Clear hierarchy** - Use consistent indentation
2. **Semantic naming** - Use descriptive component and state names
3. **Appropriate spacing** - Use spacing values that match design system
4. **State locality** - Keep state close to where it's used
5. **Action clarity** - Make button actions obvious

### Example: Complete Form

```
component UserForm:
    state:
        name: String = ""
        email: String = ""
        isSubmitting: Boolean = false
    
    Card(padding="lg"):
        VStack(spacing="md"):
            Text("User Registration", style="h2")
            
            Input(value := name, placeholder="Full Name")
            Input(value := email, placeholder="Email", type="email")
            
            HStack(justify="end", spacing="sm"):
                Button("Cancel", intent="secondary"):
                    on_click: reset()
                
                Button("Submit", intent="primary"):
                    on_click:
                        isSubmitting = true
                        submit()
```

Now generate the requested UI following these guidelines.
"""
}

