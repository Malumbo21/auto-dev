package cc.unitmesh.xuiper.spec.v1

import cc.unitmesh.xuiper.spec.*

/**
 * NanoDSL Specification Version 1.0
 * 
 * The initial specification optimized for current LLM capabilities.
 * Key design decisions:
 * - Python-style indentation (familiar to LLMs)
 * - Minimal syntax (reduce token usage)
 * - Explicit state bindings (<< and :=)
 */
object NanoSpecV1 : NanoSpec {
    override val version = "1.0"
    override val name = "NanoDSL-V1"

    // ==================== Component Definitions ====================
    
    private val SPACING_VALUES = listOf("xs", "sm", "md", "lg", "xl")
    private val STYLE_VALUES = listOf("h1", "h2", "h3", "h4", "body", "caption")
    private val INTENT_VALUES = listOf("primary", "secondary", "danger", "default")
    private val SHADOW_VALUES = listOf("none", "sm", "md", "lg")
    private val RADIUS_VALUES = listOf("none", "sm", "md", "lg", "full")
    private val ALIGN_VALUES = listOf("start", "center", "end", "stretch")
    private val JUSTIFY_VALUES = listOf("start", "center", "end", "between", "around")
    private val CHART_TYPE_VALUES = listOf("line", "bar", "pie", "area", "scatter")
    private val ALERT_TYPE_VALUES = listOf("success", "info", "warning", "error")
    private val PROGRESS_STATUS_VALUES = listOf("normal", "success", "exception", "active")
    private val MODAL_SIZE_VALUES = listOf("sm", "md", "lg", "xl")
    
    override val components: Map<String, ComponentSpec> = mapOf(
        // Layout
        "VStack" to ComponentSpec(
            name = "VStack",
            category = ComponentCategory.LAYOUT,
            optionalProps = listOf(
                PropSpec("spacing", PropType.ENUM, "md", allowedValues = SPACING_VALUES),
                PropSpec("align", PropType.ENUM, "stretch", allowedValues = ALIGN_VALUES)
            ),
            allowsChildren = true,
            description = "Vertical stack layout"
        ),
        "HStack" to ComponentSpec(
            name = "HStack",
            category = ComponentCategory.LAYOUT,
            optionalProps = listOf(
                PropSpec("spacing", PropType.ENUM, "md", allowedValues = SPACING_VALUES),
                PropSpec("align", PropType.ENUM, "center", allowedValues = ALIGN_VALUES),
                PropSpec("justify", PropType.ENUM, "start", allowedValues = JUSTIFY_VALUES)
            ),
            allowsChildren = true,
            description = "Horizontal stack layout"
        ),
        // Container
        "Card" to ComponentSpec(
            name = "Card",
            category = ComponentCategory.CONTAINER,
            optionalProps = listOf(
                PropSpec("padding", PropType.ENUM, "md", allowedValues = SPACING_VALUES),
                PropSpec("shadow", PropType.ENUM, "sm", allowedValues = SHADOW_VALUES)
            ),
            allowsChildren = true,
            description = "Card container with shadow"
        ),
        // Content
        "Text" to ComponentSpec(
            name = "Text",
            category = ComponentCategory.CONTENT,
            requiredProps = listOf(PropSpec("content", PropType.STRING)),
            optionalProps = listOf(
                PropSpec("style", PropType.ENUM, "body", allowedValues = STYLE_VALUES)
            ),
            description = "Text display component"
        ),
        "Image" to ComponentSpec(
            name = "Image",
            category = ComponentCategory.CONTENT,
            requiredProps = listOf(PropSpec("src", PropType.STRING)),
            optionalProps = listOf(
                PropSpec("aspect", PropType.STRING),
                PropSpec("radius", PropType.ENUM, "none", allowedValues = RADIUS_VALUES),
                PropSpec("width", PropType.INT)
            ),
            description = "Image display component"
        ),
        "Badge" to ComponentSpec(
            name = "Badge",
            category = ComponentCategory.CONTENT,
            requiredProps = listOf(PropSpec("text", PropType.STRING)),
            optionalProps = listOf(PropSpec("color", PropType.STRING)),
            description = "Badge/tag component"
        ),
        "Divider" to ComponentSpec(
            name = "Divider",
            category = ComponentCategory.CONTENT,
            description = "Horizontal divider line"
        ),
        // Input
        "Button" to ComponentSpec(
            name = "Button",
            category = ComponentCategory.INPUT,
            requiredProps = listOf(PropSpec("label", PropType.STRING)),
            optionalProps = listOf(
                PropSpec("intent", PropType.ENUM, "default", allowedValues = INTENT_VALUES),
                PropSpec("icon", PropType.STRING),
                PropSpec("disabled_if", PropType.EXPRESSION, description = "Conditional disable expression")
            ),
            allowsActions = true,
            description = "Clickable button"
        ),
        "Input" to ComponentSpec(
            name = "Input",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING),
                PropSpec("placeholder", PropType.STRING),
                PropSpec("type", PropType.STRING, "text")
            ),
            description = "Text input field"
        ),
        "Checkbox" to ComponentSpec(
            name = "Checkbox",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(PropSpec("checked", PropType.BINDING)),
            description = "Checkbox input"
        ),
        "TextArea" to ComponentSpec(
            name = "TextArea",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING),
                PropSpec("placeholder", PropType.STRING),
                PropSpec("rows", PropType.INT, "4")
            ),
            description = "Multi-line text input"
        ),
        "Select" to ComponentSpec(
            name = "Select",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING),
                PropSpec("options", PropType.STRING),
                PropSpec("placeholder", PropType.STRING)
            ),
            description = "Dropdown select input"
        ),
        // ============ P0: Core Form Input Components ============
        "DatePicker" to ComponentSpec(
            name = "DatePicker",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING, description = "Selected date binding"),
                PropSpec("format", PropType.STRING, "YYYY-MM-DD", description = "Date format string"),
                PropSpec("minDate", PropType.STRING, description = "Minimum selectable date"),
                PropSpec("maxDate", PropType.STRING, description = "Maximum selectable date"),
                PropSpec("placeholder", PropType.STRING)
            ),
            allowsActions = true,
            description = "Single date picker component"
        ),
        "Radio" to ComponentSpec(
            name = "Radio",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING, description = "Selected value binding"),
                PropSpec("option", PropType.STRING, description = "This radio option value"),
                PropSpec("label", PropType.STRING, description = "Radio label text"),
                PropSpec("name", PropType.STRING, description = "Radio group name")
            ),
            description = "Single radio button"
        ),
        "RadioGroup" to ComponentSpec(
            name = "RadioGroup",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING, description = "Selected value binding"),
                PropSpec("options", PropType.STRING, description = "Array of options"),
                PropSpec("name", PropType.STRING, description = "Radio group name")
            ),
            allowsChildren = true,
            description = "Radio button group"
        ),
        "Switch" to ComponentSpec(
            name = "Switch",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("checked", PropType.BINDING, description = "Switch state binding"),
                PropSpec("label", PropType.STRING, description = "Switch label"),
                PropSpec("size", PropType.ENUM, "md", allowedValues = SPACING_VALUES)
            ),
            allowsActions = true,
            description = "Toggle switch component"
        ),
        "NumberInput" to ComponentSpec(
            name = "NumberInput",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("value", PropType.BINDING, description = "Number value binding"),
                PropSpec("min", PropType.FLOAT, description = "Minimum value"),
                PropSpec("max", PropType.FLOAT, description = "Maximum value"),
                PropSpec("step", PropType.FLOAT, "1", description = "Step increment"),
                PropSpec("precision", PropType.INT, description = "Decimal precision"),
                PropSpec("placeholder", PropType.STRING)
            ),
            allowsActions = true,
            description = "Number input with increment/decrement buttons"
        ),
        // ============ P0: Feedback Components ============
        "Modal" to ComponentSpec(
            name = "Modal",
            category = ComponentCategory.CONTAINER,
            optionalProps = listOf(
                PropSpec("open", PropType.BINDING, description = "Modal visibility binding"),
                PropSpec("title", PropType.STRING, description = "Modal title"),
                PropSpec("size", PropType.ENUM, "md", allowedValues = MODAL_SIZE_VALUES),
                PropSpec("closable", PropType.BOOLEAN, "true", description = "Show close button")
            ),
            allowsChildren = true,
            allowsActions = true,
            description = "Modal dialog component"
        ),
        "Alert" to ComponentSpec(
            name = "Alert",
            category = ComponentCategory.CONTENT,
            optionalProps = listOf(
                PropSpec("type", PropType.ENUM, "info", allowedValues = ALERT_TYPE_VALUES),
                PropSpec("message", PropType.STRING, description = "Alert message text"),
                PropSpec("closable", PropType.BOOLEAN, "false", description = "Show close button"),
                PropSpec("icon", PropType.STRING, description = "Custom icon name")
            ),
            allowsChildren = true,
            allowsActions = true,
            description = "Alert notification component"
        ),
        "Progress" to ComponentSpec(
            name = "Progress",
            category = ComponentCategory.CONTENT,
            optionalProps = listOf(
                PropSpec("value", PropType.FLOAT, description = "Progress value (0-100)"),
                PropSpec("max", PropType.FLOAT, "100", description = "Maximum value"),
                PropSpec("showText", PropType.BOOLEAN, "true", description = "Show percentage text"),
                PropSpec("status", PropType.ENUM, "normal", allowedValues = PROGRESS_STATUS_VALUES)
            ),
            description = "Progress bar component"
        ),
        "Spinner" to ComponentSpec(
            name = "Spinner",
            category = ComponentCategory.CONTENT,
            optionalProps = listOf(
                PropSpec("size", PropType.ENUM, "md", allowedValues = SPACING_VALUES),
                PropSpec("color", PropType.STRING, description = "Spinner color"),
                PropSpec("text", PropType.STRING, description = "Loading text")
            ),
            description = "Loading spinner component"
        ),
        // Form
        "Form" to ComponentSpec(
            name = "Form",
            category = ComponentCategory.CONTAINER,
            optionalProps = listOf(
                PropSpec("onSubmit", PropType.STRING)
            ),
            allowsChildren = true,
            allowsActions = true,
            description = "Form container with submit handling"
        ),
        // ============ Tier 1: GenUI Foundation Components ============
        "SplitView" to ComponentSpec(
            name = "SplitView",
            category = ComponentCategory.LAYOUT,
            optionalProps = listOf(
                PropSpec("ratio", PropType.FLOAT, "0.5", description = "Split ratio (0.0-1.0)")
            ),
            allowsChildren = true,
            description = "Split view layout (left chat, right canvas)"
        ),
        // ============ Tier 2: Structured Input Components ============
        "SmartTextField" to ComponentSpec(
            name = "SmartTextField",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("label", PropType.STRING),
                PropSpec("bind", PropType.BINDING, description = "State binding path"),
                PropSpec("validation", PropType.STRING, description = "Regex validation pattern"),
                PropSpec("placeholder", PropType.STRING)
            ),
            description = "Smart text input with validation support"
        ),
        "Slider" to ComponentSpec(
            name = "Slider",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("label", PropType.STRING),
                PropSpec("bind", PropType.BINDING, description = "State binding path"),
                PropSpec("min", PropType.FLOAT, "0"),
                PropSpec("max", PropType.FLOAT, "100"),
                PropSpec("step", PropType.FLOAT, "1")
            ),
            allowsActions = true,
            description = "Range slider input"
        ),
        "DateRangePicker" to ComponentSpec(
            name = "DateRangePicker",
            category = ComponentCategory.INPUT,
            optionalProps = listOf(
                PropSpec("bind", PropType.BINDING, description = "State binding path for date range"),
                PropSpec("on_change", PropType.EXPRESSION, description = "Action on date change")
            ),
            allowsActions = true,
            description = "Date range picker with AI pre-fill support"
        ),
        // ============ Tier 3: Data Artifacts ============
        "DataChart" to ComponentSpec(
            name = "DataChart",
            category = ComponentCategory.CONTENT,
            requiredProps = listOf(
                PropSpec("data", PropType.EXPRESSION, description = "Data source (state binding or literal)")
            ),
            optionalProps = listOf(
                PropSpec("type", PropType.ENUM, "line", allowedValues = CHART_TYPE_VALUES),
                PropSpec("x_axis", PropType.STRING, description = "X-axis field name"),
                PropSpec("y_axis", PropType.STRING, description = "Y-axis field name"),
                PropSpec("color", PropType.STRING, description = "Chart color")
            ),
            description = "Data visualization chart component"
        ),
        "DataTable" to ComponentSpec(
            name = "DataTable",
            category = ComponentCategory.CONTENT,
            requiredProps = listOf(
                PropSpec("data", PropType.EXPRESSION, description = "Data source (state binding or literal)"),
                PropSpec("columns", PropType.STRING, description = "Column definitions (array or state binding)")
            ),
            optionalProps = listOf(
                PropSpec("on_row_click", PropType.EXPRESSION, description = "Action on row click")
            ),
            allowsActions = true,
            description = "Interactive data table component"
        )
    )

    override val layoutComponents = setOf("VStack", "HStack", "SplitView")
    override val containerComponents = setOf("Card", "Form", "Modal")
    override val contentComponents = setOf("Text", "Image", "Badge", "Divider", "DataChart", "DataTable", "Alert", "Progress", "Spinner")
    override val inputComponents = setOf("Button", "Input", "Checkbox", "TextArea", "Select", "SmartTextField", "Slider", "DateRangePicker", "DatePicker", "Radio", "RadioGroup", "Switch", "NumberInput")
    override val controlFlowKeywords = setOf("if", "for", "state", "component", "request")
    override val actionTypes = setOf("Navigate", "Fetch", "ShowToast", "StateMutation")

    override val bindingOperators = listOf(
        BindingOperatorSpec("<<", "Subscribe", "One-way binding (read-only)", isOneWay = true),
        BindingOperatorSpec(":=", "TwoWay", "Two-way binding (read-write)", isOneWay = false)
    )

    override fun getComponent(name: String) = components[name]
    override fun isValidComponent(name: String) = name in components
    override fun isReservedKeyword(keyword: String) = keyword in controlFlowKeywords
}

