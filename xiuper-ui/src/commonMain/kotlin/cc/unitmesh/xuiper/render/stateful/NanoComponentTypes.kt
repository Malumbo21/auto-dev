package cc.unitmesh.xuiper.render.stateful

/**
 * NanoComponentTypes - Standard component type constants
 *
 * Defines all standard NanoDSL component types.
 * Use these constants instead of string literals for type safety.
 *
 * Categories:
 * - Layout: VStack, HStack, SplitView
 * - Container: Card, Form, Component
 * - Content: Text, Image, Badge, Icon, Divider, Code, Link, Blockquote
 * - Input: Button, Input, Checkbox, TextArea, Select, DatePicker, Radio, RadioGroup, Switch, NumberInput, SmartTextField, Slider, DateRangePicker
 * - Feedback: Modal, Alert, Progress, Spinner
 * - Data: DataChart, DataTable
 * - Control Flow: Conditional, ForLoop
 */
object NanoComponentTypes {
    // Layout
    const val VSTACK = "VStack"
    const val HSTACK = "HStack"
    const val SPLIT_VIEW = "SplitView"

    // Container
    const val CARD = "Card"
    const val FORM = "Form"
    const val COMPONENT = "Component"

    // Content
    const val TEXT = "Text"
    const val IMAGE = "Image"
    const val BADGE = "Badge"
    const val ICON = "Icon"
    const val DIVIDER = "Divider"
    const val CODE = "Code"
    const val LINK = "Link"
    const val BLOCKQUOTE = "Blockquote"

    // Input - Core
    const val BUTTON = "Button"
    const val INPUT = "Input"
    const val CHECKBOX = "Checkbox"
    const val TEXT_AREA = "TextArea"
    const val SELECT = "Select"

    // Input - Form
    const val DATE_PICKER = "DatePicker"
    const val RADIO = "Radio"
    const val RADIO_GROUP = "RadioGroup"
    const val SWITCH = "Switch"
    const val NUMBER_INPUT = "NumberInput"

    // Input - Advanced
    const val SMART_TEXT_FIELD = "SmartTextField"
    const val SLIDER = "Slider"
    const val DATE_RANGE_PICKER = "DateRangePicker"

    // Feedback
    const val MODAL = "Modal"
    const val ALERT = "Alert"
    const val PROGRESS = "Progress"
    const val SPINNER = "Spinner"

    // Data
    const val DATA_CHART = "DataChart"
    const val DATA_TABLE = "DataTable"

    // Control Flow
    const val CONDITIONAL = "Conditional"
    const val FOR_LOOP = "ForLoop"

    /**
     * All layout component types.
     */
    val LAYOUT_TYPES = setOf(VSTACK, HSTACK, SPLIT_VIEW)

    /**
     * All container component types.
     */
    val CONTAINER_TYPES = setOf(CARD, FORM, COMPONENT)

    /**
     * All content component types.
     */
    val CONTENT_TYPES = setOf(TEXT, IMAGE, BADGE, ICON, DIVIDER, CODE, LINK, BLOCKQUOTE)

    /**
     * All input component types.
     */
    val INPUT_TYPES = setOf(
        BUTTON, INPUT, CHECKBOX, TEXT_AREA, SELECT,
        DATE_PICKER, RADIO, RADIO_GROUP, SWITCH, NUMBER_INPUT,
        SMART_TEXT_FIELD, SLIDER, DATE_RANGE_PICKER
    )

    /**
     * All feedback component types.
     */
    val FEEDBACK_TYPES = setOf(MODAL, ALERT, PROGRESS, SPINNER)

    /**
     * All data component types.
     */
    val DATA_TYPES = setOf(DATA_CHART, DATA_TABLE)

    /**
     * All control flow component types.
     */
    val CONTROL_FLOW_TYPES = setOf(CONDITIONAL, FOR_LOOP)

    /**
     * All standard component types.
     */
    val ALL_TYPES = LAYOUT_TYPES + CONTAINER_TYPES + CONTENT_TYPES + 
                   INPUT_TYPES + FEEDBACK_TYPES + DATA_TYPES + CONTROL_FLOW_TYPES
}
